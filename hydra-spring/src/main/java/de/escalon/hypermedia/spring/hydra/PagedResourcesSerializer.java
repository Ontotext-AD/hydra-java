/*
 * Copyright (c) 2014. Escalon System-Entwicklung, Dietrich Schulten
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package de.escalon.hypermedia.spring.hydra;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.NameTransformer;
import de.escalon.hypermedia.hydra.serialize.*;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.PagedModel;

import java.io.IOException;
import java.util.*;

import static de.escalon.hypermedia.hydra.serialize.JacksonHydraSerializer.KEY_LD_CONTEXT;

/**
 * Serializer for Resources. Created by dschulten on 15.09.2014.
 */
@SuppressWarnings("unused")
public class PagedResourcesSerializer extends StdSerializer<PagedModel> {

  private static final Set<String> navigationRels = new HashSet<>();

  static {
    Collections.addAll(
        navigationRels,
        IanaLinkRelations.FIRST_VALUE,
        IanaLinkRelations.NEXT_VALUE,
        IanaLinkRelations.PREV_VALUE,
        IanaLinkRelations.LAST_VALUE);
  }

    private final LdContextFactory ldContextFactory;
    private final ProxyUnwrapper proxyUnwrapper;

    @SuppressWarnings("unused")
    public PagedResourcesSerializer(ProxyUnwrapper proxyUnwrapper) {
        super(PagedModel.class);
        this.ldContextFactory = new LdContextFactory();
        this.proxyUnwrapper = proxyUnwrapper;
        ldContextFactory.setProxyUnwrapper(proxyUnwrapper);
    }

    @Override
    public void serialize(PagedModel pagedResources, JsonGenerator jgen, SerializerProvider serializerProvider)
            throws
            IOException {

        final SerializationConfig config = serializerProvider.getConfig();
        JavaType javaType = config.constructType(pagedResources.getClass());

        JsonSerializer<Object> serializer = BeanSerializerFactory.instance.createSerializer(serializerProvider,
                javaType);

        // replicate pretty much everything from JacksonHydraSerializer
        // since we must reorganize the internals of pagedResources to get a hydra collection
        // with partial page view, we have to serialize pagedResources with an
        // unwrapping serializer
        Deque<LdContext> contextStack = (Deque<LdContext>) serializerProvider.getAttribute(KEY_LD_CONTEXT);
        if (contextStack == null) {
            contextStack = new ArrayDeque<>();
            serializerProvider.setAttribute(KEY_LD_CONTEXT, contextStack);
        }

        // TODO: filter next/previous/first/last from link list - maybe create new PagedModel without them?
        Links links = pagedResources.getLinks();
        List<Link> filteredLinks = new ArrayList<>();
        for (Link link : links) {
            String rel = link.getRel().value();
            if (navigationRels.contains(rel)) {
                continue;
            } else {
                filteredLinks.add(link);
            }
        }

        PagedModel toRender = PagedModel.of(pagedResources.getContent(), pagedResources.getMetadata(),
                filteredLinks);

        jgen.writeStartObject();

        serializeContext(toRender, jgen, serializerProvider, contextStack);

        jgen.writeStringField(JsonLdKeywords.AT_TYPE, "hydra:Collection");


        // serialize with PagedResourcesMixin
        serializer.unwrappingSerializer(NameTransformer.NOP)
                .serialize(toRender, jgen, serializerProvider);

        PagedModel.PageMetadata metadata = pagedResources.getMetadata();
        jgen.writeNumberField("hydra:totalItems", metadata.getTotalElements());

        // begin hydra:view
        jgen.writeObjectFieldStart("hydra:view");
        jgen.writeStringField(JsonLdKeywords.AT_TYPE, "hydra:PartialCollectionView");
        writeRelLink(pagedResources, jgen, IanaLinkRelations.NEXT_VALUE);
        writeRelLink(pagedResources, jgen, "previous");
        // must also translate prev to its synonym previous
        writeRelLink(pagedResources, jgen, IanaLinkRelations.PREV_VALUE, "previous");
        writeRelLink(pagedResources, jgen, IanaLinkRelations.FIRST_VALUE);
        writeRelLink(pagedResources, jgen, IanaLinkRelations.LAST_VALUE);
        jgen.writeEndObject();
        // end hydra:view


        jgen.writeEndObject();

        contextStack = (Deque<LdContext>) serializerProvider.getAttribute(KEY_LD_CONTEXT);
        if (!contextStack.isEmpty()) {
            contextStack.pop();
        }

    }

    protected void serializeContext(Object bean, JsonGenerator jgen,
                                    SerializerProvider serializerProvider, Deque<LdContext> contextStack) throws
            IOException {
        // TODO: this code is duplicated from JacksonHydraSerializer, see there for considerations
        if (proxyUnwrapper != null) {
            bean = proxyUnwrapper.unwrapProxy(bean);
        }
        MixinSource mixinSource = new JacksonMixinSource(serializerProvider.getConfig());
        final Class<?> mixInClass = mixinSource.findMixInClassFor(bean.getClass());

        final LdContext parentContext = contextStack.peek();
        LdContext currentContext = new LdContext(parentContext, ldContextFactory.getVocab(mixinSource, bean,
                mixInClass), ldContextFactory.getTerms(mixinSource, bean, mixInClass));
        contextStack.push(currentContext);
        // check if we need to write a context for the current bean at all
        // If it is in the same vocab: no context
        // If the terms are already defined in the context: no context
        boolean mustWriteContext = parentContext == null || !parentContext.contains(currentContext);

        if (mustWriteContext) {
            // begin context
            // default context: schema.org vocab or vocab package annotation
            jgen.writeObjectFieldStart("@context");
            // do not repeat vocab if already defined in current context
            if (parentContext == null || parentContext.vocab == null ||
                    (currentContext.vocab != null && !currentContext.vocab.equals(parentContext.vocab))) {
                jgen.writeStringField(JsonLdKeywords.AT_VOCAB, currentContext.vocab);
            }

            for (Map.Entry<String, Object> termEntry : currentContext.terms.entrySet()) {
                if (termEntry.getValue() instanceof String) {
                    jgen.writeStringField(termEntry.getKey(), termEntry.getValue()
                            .toString());
                } else {
                    jgen.writeObjectField(termEntry.getKey(), termEntry.getValue());
                }
            }
            jgen.writeEndObject();
            // end context
        }
    }

    private void writeRelLink(PagedModel value, JsonGenerator jgen, String rel) throws IOException {
        writeRelLink(value, jgen, rel, rel);
    }

    private void writeRelLink(PagedModel value, JsonGenerator jgen, String rel, String hydraPredicate) throws
            IOException {
        Optional<Link> link = value.getLink(rel);
        if (link.isPresent()) {
            jgen.writeStringField("hydra:" + hydraPredicate, link.get().getHref());
        }
    }

}

