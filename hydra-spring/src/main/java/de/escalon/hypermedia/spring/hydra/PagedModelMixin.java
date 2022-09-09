package de.escalon.hypermedia.spring.hydra;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.escalon.hypermedia.hydra.mapping.ContextProvider;
import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.PagedModel;

/**
 * Mixin for json-ld serialization of PagedResources.
 */
@JsonInclude
@Term(define = "hydra", as = "http://www.w3.org/ns/hydra/core#")
@Expose("hydra:Collection")
public abstract class PagedModelMixin<T> extends PagedModel<T> {
    @NotNull
    @Override
    @JsonProperty("hydra:member")
    @ContextProvider
    public Collection<T> getContent() {
        return super.getContent();
    }

    @NotNull
    @Override
    @JsonSerialize(using = LinkListSerializer.class)
    @JsonUnwrapped
    public Links getLinks() {
        return super.getLinks();
    }

    @Override
    @JsonIgnore // used by PagedResourcesSerializer instead
    public PageMetadata getMetadata() {
        return super.getMetadata();
    }
}
