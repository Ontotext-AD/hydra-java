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

package de.escalon.hypermedia.spring;

import de.escalon.hypermedia.affordance.*;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.LinkBuilder;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

/**
 * Builder for hypermedia affordances, usable as rfc-5988 web links and optionally holding information about request
 * body requirements. Created by dschulten on 07.09.2014.
 */
public class AffordanceBuilder implements LinkBuilder {

    private static final AffordanceBuilderFactory FACTORY = new AffordanceBuilderFactory();

    private PartialUriTemplateComponents partialUriTemplateComponents;
    private List<ActionDescriptor> actionDescriptors = new ArrayList<>();

    private MultiValueMap<String, String> linkParams = new LinkedMultiValueMap<>();
    private List<String> rels = new ArrayList<>();
    private List<String> reverseRels = new ArrayList<>();
    private TypedResource collectionHolder;

    /**
     * Creates a new {@link AffordanceBuilder} with a base of the mapping annotated to the given controller class.
     *
     * @param controller
     *         the class to discover the annotation on, must not be {@literal null}.
     * @return builder
     */
    public static AffordanceBuilder linkTo(Class<?> controller) {
        return linkTo(controller, new Object[0]);
    }

    /**
     * Creates a new {@link AffordanceBuilder} with a base of the mapping annotated to the given controller class. The
     * additional parameters are used to fill up potentially available path variables in the class scope request
     * mapping.
     *
     * @param controller
     *         the class to discover the annotation on, must not be {@literal null}.
     * @param parameters
     *         additional parameters to bind to the URI template declared in the annotation, must not be {@literal
     *         null}.
     * @return builder
     */
    public static AffordanceBuilder linkTo(Class<?> controller, Object... parameters) {
        return FACTORY.linkTo(controller, parameters);
    }

    /**
     * @see org.springframework.hateoas.server.MethodLinkBuilderFactory#linkTo(Method, Object...)
     */
    public static AffordanceBuilder linkTo(Method method, Object... parameters) {
        return linkTo(method.getDeclaringClass(), method, parameters);
    }

    /**
     * Creates a new {@link AffordanceBuilder} with a base of the mapping annotated to the given controller class. The
     * additional parameters are used to fill up potentially available path variables in the class scop request
     * mapping.
     *
     * @param controller
     *         the class to discover the annotation on, must not be {@literal null}.
     * @param parameters
     *         additional parameters to bind to the URI template declared in the annotation, must not be {@literal
     *         null}.
     * @return
     */
    public static AffordanceBuilder linkTo(Class<?> controller, Map<String, ?> parameters) {
        return FACTORY.linkTo(controller, parameters);
    }

    /**
     * @see org.springframework.hateoas.server.MethodLinkBuilderFactory#linkTo(Class, Method, Object...)
     */
    public static AffordanceBuilder linkTo(Class<?> controller, Method method, Object... parameters) {
        return FACTORY.linkTo(controller, method, parameters);
    }

    /**
     * Creates a {@link AffordanceBuilder} pointing to a controller method. Hand in a dummy method invocation result
     * you
     * can create via {@link #methodOn(Class, Object...)} or {@link OntoDummyInvocationUtils#methodOn(Class, Object...)}.
     * <pre>
     * &#64;RequestMapping("/customers")
     * class CustomerController {
     *   &#64;RequestMapping("/{id}/addresses")
     *   HttpEntity&lt;Addresses&gt; showAddresses(@PathVariable Long id) { � }
     * }
     * Link link = linkTo(methodOn(CustomerController.class).showAddresses(2L)).withRel("addresses");
     * </pre>
     * The resulting {@link Link} instance will point to {@code /customers/2/addresses} and have a rel of {@code
     * addresses}. For more details on the method invocation constraints, see {@link
     * OntoDummyInvocationUtils#methodOn(Class, Object...)}.
     *
     * @param methodInvocation
     * @return
     */
    public static AffordanceBuilder linkTo(Object methodInvocation) {
        return FACTORY.linkTo(methodInvocation);
    }


    /**
     * Creates a new {@link AffordanceBuilder} pointing to this server, but without ActionDescriptor.
     */
    AffordanceBuilder() {
        this(new PartialUriTemplate(getBuilder().build()
                        .toString()).expand(Collections.emptyMap()),
                Collections.emptyList());
    }

    /**
     * Creates a new {@link AffordanceBuilder} using the given {@link ActionDescriptor}.
     *
     * @param partialUriTemplateComponents
     *         must not be {@literal null}
     * @param actionDescriptors
     *         must not be {@literal null}
     */
    public AffordanceBuilder(PartialUriTemplateComponents partialUriTemplateComponents, List<ActionDescriptor>
            actionDescriptors) {

        Assert.notNull(partialUriTemplateComponents, "Partial Uri template components should not be null");
        Assert.notNull(actionDescriptors, "Action descriptors should not be null");

        this.partialUriTemplateComponents = partialUriTemplateComponents;

        this.actionDescriptors.addAll(actionDescriptors);
    }

    public static <T> T methodOn(Class<T> clazz, Object... parameters) {
        return OntoDummyInvocationUtils.methodOn(clazz, parameters);
    }

    /**
     * Builds affordance with one or multiple rels which must have been defined previously using {@link #rel(String)} or
     * {@link #reverseRel(String, String)}. <p>The motivation for multiple rels is this statement in the web linking
     * rfc-5988: &quot;Note that link-values can convey multiple links between the same target and context IRIs; for
     * example:</p>
     * <pre>
     * Link: &lt;http://example.org/&gt;
     *       rel="start http://example.net/relation/other"
     * </pre>
     * Here, the link to 'http://example.org/' has the registered relation type 'start' and the extension relation type
     * 'http://example.net/relation/other'.&quot;
     *
     * @return affordance
     * @see <a href="https://tools.ietf.org/html/rfc5988#section-5.5">Web Linking Examples</a>
     */
    public Affordance build() {
        Assert.state(!(rels.isEmpty() && reverseRels.isEmpty()),
                "no rels or reverse rels found, call rel() or rev() before building the affordance");
        final Affordance affordance;
        affordance = new Affordance(new PartialUriTemplate(this.toString()), actionDescriptors,
                rels.toArray(new String[0]));
        for (Map.Entry<String, List<String>> linkParamEntry : linkParams.entrySet()) {
            final List<String> values = linkParamEntry.getValue();
            for (String value : values) {
                affordance.addLinkParam(linkParamEntry.getKey(), value);
            }
        }
        for (String reverseRel : reverseRels) {
            affordance.addRev(reverseRel);
        }
        affordance.setCollectionHolder(collectionHolder);
        return affordance;
    }

    /**
     * Allows to define one or more reverse link relations (a "rev" in terms of rfc-5988), where the resource that has
     * the affordance will be considered the object in a subject-predicate-object statement. <p>E.g. if you had a rel
     * <code>ex:parent</code> which connects a child to its father, you could also use ex:parent on the father to point
     * to the child by reverting the direction of ex:parent. This is mainly useful when you have no other way to
     * express
     * in your context that the direction of a relationship is inverted. </p>
     *
     * @param rev
     *         to be used as reverse relationship
     * @param revertedRel
     *         to be used in contexts which have no notion of reverse relationships. E.g. for a reverse rel
     *         <code>ex:parent</code> you can use a made-up rel name <code>ex:child</code> which will be used as rel
     *         when rendering HAL.
     * @return builder
     */
    public AffordanceBuilder reverseRel(String rev, String revertedRel) {
        this.rels.add(revertedRel);
        this.reverseRels.add(rev);
        return this;
    }

    /**
     * Allows to define one or more reverse link relations (a "rev" in terms of rfc-5988) to collections in cases where
     * the resource that has the affordance is not the object in a subject-predicate-object statement about each
     * collection item. See {@link #rel(TypedResource, String)} for explanation.
     *
     * @param rev
     *         to be used as reverse relationship
     * @param revertedRel
     *         to be used in contexts which have no notion of reverse relationships, e.g. HAL
     * @param object
     *         describing the object
     * @return builder
     */
    public AffordanceBuilder reverseRel(String rev, String revertedRel, TypedResource object) {
        this.collectionHolder = object;
        this.rels.add(0, revertedRel);
        this.reverseRels.add(rev);
        return this;
    }

    /**
     * Allows to define one or more link relations for the affordance.
     *
     * @param rel
     *         to be used as link relation
     * @return builder
     */
    public AffordanceBuilder rel(String rel) {
        this.rels.add(rel);
        return this;
    }


    /**
     * Allows to define one or more link relations for affordances that point to collections in cases where the
     * resource
     * that has the affordance is not the subject in a subject-predicate-object statement about each collection item.
     * E.g. a product might have a loose relationship to ordered items where it can be POSTed, but the ordered items do
     * not belong to the product, but to an order. You can express that by saying:
     * <pre>
     * TypedResource order = new TypedResource("http://schema.org/Order"); // holds the ordered items
     * EntityModel&lt;Product&gt; product = new EntityModel&lt;&gt;(); // has a loose relationship to ordered items
     * product.add(linkTo(methodOn(OrderController.class).postOrderedItem()
     *    .rel(order, "orderedItem")); // order has ordered items, not product has ordered items
     * </pre>
     * If the order doesn't exist yet, it cannot be identified. In that case you may pass null to express that
     *
     * @param rel
     *         to be used as link relation
     * @param subject
     *         describing the subject
     * @return builder
     */
    public AffordanceBuilder rel(TypedResource subject, String rel) {
        this.collectionHolder = subject;
        this.rels.add(rel);
        return this;
    }


    public AffordanceBuilder withTitle(String title) {
        this.linkParams.set("title", title);
        return this;
    }

    public AffordanceBuilder withTitleStar(String titleStar) {
        this.linkParams.set("title*", titleStar);
        return this;
    }

    /**
     * Allows to define link header params (not UriTemplate variables).
     *
     * @param name
     *         of the link header param
     * @param value
     *         of the link header param
     * @return builder
     */
    public AffordanceBuilder withLinkParam(String name, String value) {
        this.linkParams.add(name, value);
        return this;
    }

    public AffordanceBuilder withAnchor(String anchor) {
        this.linkParams.set("anchor", anchor);
        return this;
    }

    public AffordanceBuilder withHreflang(String hreflang) {
        this.linkParams.add("hreflang", hreflang);
        return this;
    }

    public AffordanceBuilder withMedia(String media) {
        this.linkParams.set("media", media);
        return this;
    }


    public AffordanceBuilder withType(String type) {
        this.linkParams.set("type", type);
        return this;
    }


    @Override
    public AffordanceBuilder slash(Object object) {

        if (object == null) {
            return this;
        }

        String urlPart = object.toString();

        // make sure one cannot delete the fragment
        if (urlPart.endsWith("#")) {
            urlPart = urlPart.substring(0, urlPart.length() - 1);
        }

        if (!StringUtils.hasText(urlPart)) {
            return this;
        }

        final PartialUriTemplateComponents urlPartComponents = new PartialUriTemplate(urlPart).expand(Collections
                .<String, Object>emptyMap());
        final PartialUriTemplateComponents affordanceComponents = partialUriTemplateComponents;

        final String path = !affordanceComponents.getBaseUri()
                .endsWith("/") && !urlPartComponents.getBaseUri()
                .startsWith("/") ?
                affordanceComponents.getBaseUri() + "/" + urlPartComponents.getBaseUri() :
                affordanceComponents.getBaseUri() + urlPartComponents.getBaseUri();
        final String queryHead = affordanceComponents.getQueryHead() +
                (StringUtils.hasText(urlPartComponents.getQueryHead()) ?
                        "&" + urlPartComponents.getQueryHead()
                                .substring(1) :
                        "");
        final String queryTail = affordanceComponents.getQueryTail() +
                (StringUtils.hasText(urlPartComponents.getQueryTail()) ?
                        "," + urlPartComponents.getQueryTail() :
                        "");
        final String fragmentIdentifier = StringUtils.hasText(urlPartComponents.getFragmentIdentifier()) ?
                urlPartComponents.getFragmentIdentifier() :
                affordanceComponents.getFragmentIdentifier();

        List<String> variableNames = new ArrayList<>();
        variableNames.addAll(affordanceComponents.getVariableNames());
        variableNames.addAll(urlPartComponents.getVariableNames());
        final PartialUriTemplateComponents mergedUriComponents =
                new PartialUriTemplateComponents(path, queryHead, queryTail, fragmentIdentifier, variableNames);

        return new AffordanceBuilder(mergedUriComponents, actionDescriptors);
    }

    @Override
    public URI toUri() {
        PartialUriTemplate partialUriTemplate = new PartialUriTemplate(partialUriTemplateComponents.toString());

        final String actionLink = partialUriTemplate.stripOptionalVariables(actionDescriptors)
                .toString();

        if (actionLink == null || actionLink.contains("{")) {
            throw new IllegalStateException("cannot convert template to URI");
        }
        return UriComponentsBuilder.fromUriString(actionLink)
                .build()
                .toUri();
    }

    @Override
    public Affordance withRel(String rel) {
        return rel(rel).build();
    }

    @Override
    public Link withRel(final LinkRelation rel) {
        return rel(rel.value()).build();
    }

    @Override
    public Affordance withSelfRel() {
        return rel(IanaLinkRelations.SELF_VALUE).build();
    }

    @Override
    public String toString() {
        return partialUriTemplateComponents.toString();
    }

    /**
     * Returns a {@link UriComponentsBuilder} obtained from the current servlet mapping with scheme tweaked in case the
     * request contains an {@code X-Forwarded-Ssl} header, which is not (yet) supported by the underlying
     * {@link UriComponentsBuilder}. If no {@link RequestContextHolder} exists (you're outside a Spring Web call), fall
     * back to relative URIs.
     *
     * @return
     */
    static UriComponentsBuilder getBuilder() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return UriComponentsBuilder.fromPath("/");
        }

        HttpServletRequest request = getCurrentRequest();
        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromServletMapping(request);

        // special case handling for X-Forwarded-Ssl:
        // apply it, but only if X-Forwarded-Proto is unset.

        String forwardedSsl = request.getHeader("X-Forwarded-Ssl");
        ForwardedHeader forwarded = ForwardedHeader.of(request.getHeader(ForwardedHeader.NAME));
        String proto = hasText(forwarded.getProto()) ? forwarded.getProto() : request.getHeader("X-Forwarded-Proto");

        if (!hasText(proto) && hasText(forwardedSsl) && forwardedSsl.equalsIgnoreCase("on")) {
            builder.scheme("https");
        }

        return builder;
    }

    /**
     * Copy of {@link ServletUriComponentsBuilder#getCurrentRequest()} until SPR-10110 gets fixed.
     *
     * @return request
     */
    private static HttpServletRequest getCurrentRequest() {

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Assert.state(requestAttributes != null, "Could not find current request via RequestContextHolder");
        Assert.isInstanceOf(ServletRequestAttributes.class, requestAttributes);
        HttpServletRequest servletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();
        Assert.state(servletRequest != null, "Could not find current HttpServletRequest");
        return servletRequest;
    }

    /**
     * Adds actionDescriptors of the given AffordanceBuilder to this affordanceBuilder.
     *
     * @param affordanceBuilder
     *         whose action descriptors should be added to this one
     * @return builder
     */
    public AffordanceBuilder and(AffordanceBuilder affordanceBuilder) {
        this.actionDescriptors.addAll(affordanceBuilder.actionDescriptors);
        return this;
    }
}
