package de.escalon.hypermedia.spring.hydra.characterization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import de.escalon.hypermedia.hydra.mapping.Terms;
import de.escalon.hypermedia.hydra.mapping.Vocab;
import de.escalon.hypermedia.spring.HypermediaTypes;
import de.escalon.hypermedia.spring.hydra.HydraMessageConverter;
import org.junit.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization tests: pin the exact JSON-LD bytes that {@link HydraMessageConverter} writes for
 * {@link PagedModel} and {@link CollectionModel}, using a converter subclass shaped like the one
 * downstream consumers use. The expected documents were captured on Spring 5.3 / Spring HATEOAS 1.5
 * (hydra-java 0.4.x) and must not change across framework upgrades.
 */
public class HydraJsonLdCharacterizationTest {

    private static final String BASE = "http://localhost:8080/soml";

    /**
     * Mirrors the downstream subclass: uses the (ProxyUnwrapper, Module...) constructor, replaces the
     * supported media types and overrides canWrite and setObjectMapper.
     */
    static class ConsumerStyleHydraMessageConverter extends HydraMessageConverter {

        private final Collection<Class<?>> classesToSkip;

        ConsumerStyleHydraMessageConverter(Collection<Class<?>> classesToSkip) {
            super(null, new JavaTimeModule());
            setSupportedMediaTypes(Arrays.asList(HypermediaTypes.APPLICATION_JSONLD, MediaType.APPLICATION_JSON));
            this.classesToSkip = classesToSkip;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return !classesToSkip.contains(clazz) && super.canWrite(clazz, mediaType);
        }

        @Override
        public void setObjectMapper(ObjectMapper objectMapper) {
            objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
            super.setObjectMapper(objectMapper);
        }
    }

    @Vocab("http://ontotext.com/ontology/status/")
    @Terms({
            @Term(define = "@base", as = "http://data.ontotext.com/"),
            @Term(define = "xsd", as = "http://www.w3.org/2001/XMLSchema#"),
            @Term(define = "hydra", as = "http://www.w3.org/ns/hydra/core#")
    })
    @Expose("SOML")
    public static class SchemaItem {

        @JsonProperty("@id")
        private final String id;

        @JsonProperty("name")
        private final String name;

        @JsonProperty("enabled")
        private final Boolean enabled;

        @JsonProperty("created")
        private final LocalDate created;

        @JsonProperty("modified")
        private final Instant modified;

        @JsonProperty("description")
        private final String description;

        SchemaItem(String id, String name, Boolean enabled, LocalDate created, Instant modified,
                   String description) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.created = created;
            this.modified = modified;
            this.description = description;
        }
    }

    @Vocab("http://ontotext.com/ontology/status/")
    @Terms({
            @Term(define = "@base", as = "https://data.ontotext.com/resource/status/"),
            @Term(define = "xsd", as = "http://www.w3.org/2001/XMLSchema#"),
            @Term(define = "hydra", as = "http://www.w3.org/ns/hydra/core#")
    })
    @Expose("InvalidSchema")
    public static class InvalidSchemaCollection<T> extends CollectionModel<T> {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("schema")
        private final String schema;

        InvalidSchemaCollection(Iterable<T> content, String schema) {
            super(content);
            this.schema = schema;
        }
    }

    @Vocab("http://ontotext.com/ontology/status/")
    @Expose("Message")
    public static class Message {

        @JsonProperty("message")
        private final String message;

        @JsonProperty("severity")
        private final String severity;

        Message(String message, String severity) {
            this.message = message;
            this.severity = severity;
        }
    }

    private final ConsumerStyleHydraMessageConverter converter =
            new ConsumerStyleHydraMessageConverter(Collections.<Class<?>>singletonList(String.class));

    private static SchemaItem item(int i) {
        return new SchemaItem("/soml/schema" + i, "schema" + i, i % 2 == 0,
                LocalDate.of(2020, 7, i), Instant.parse("2020-07-1" + i + "T10:15:30.123Z"),
                i == 1 ? null : "description " + i);
    }

    private static Link pageLink(int page, String rel) {
        return Link.of(BASE + "?page=" + page + "&size=2", rel);
    }

    @Test
    public void consumerConverterHonoursCanWriteOverride() {
        assertFalse(converter.canWrite(String.class, HypermediaTypes.APPLICATION_JSONLD));
        assertTrue(converter.canWrite(PagedModel.class, HypermediaTypes.APPLICATION_JSONLD));
        assertTrue(converter.canWrite(CollectionModel.class, MediaType.APPLICATION_JSON));
    }

    @Test
    public void writesMiddlePageOfPagedModel() throws Exception {
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(2, 1, 5);
        List<Link> links = Arrays.asList(
                pageLink(1, IanaLinkRelations.SELF_VALUE),
                pageLink(0, IanaLinkRelations.FIRST_VALUE),
                pageLink(2, IanaLinkRelations.LAST_VALUE),
                pageLink(0, IanaLinkRelations.PREV_VALUE),
                pageLink(2, IanaLinkRelations.NEXT_VALUE));
        PagedModel<SchemaItem> model = PagedModel.of(Arrays.asList(item(1), item(2)), metadata, links);

        assertWritten("paged-model-middle-page.jsonld", model);
    }

    @Test
    public void writesSinglePagePagedModel() throws Exception {
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(2, 0, 1);
        List<Link> links = Arrays.asList(
                pageLink(0, IanaLinkRelations.SELF_VALUE),
                pageLink(0, IanaLinkRelations.FIRST_VALUE),
                pageLink(0, IanaLinkRelations.LAST_VALUE));
        PagedModel<SchemaItem> model = PagedModel.of(Collections.singletonList(item(3)), metadata, links);

        assertWritten("paged-model-single-page.jsonld", model);
    }

    @Test
    public void writesEmptyPagedModel() throws Exception {
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(2, 0, 0);
        PagedModel<SchemaItem> model = PagedModel.of(Collections.<SchemaItem>emptyList(), metadata,
                Collections.singletonList(pageLink(0, IanaLinkRelations.SELF_VALUE)));

        assertWritten("paged-model-empty.jsonld", model);
    }

    @Test
    public void writesCollectionModel() throws Exception {
        CollectionModel<SchemaItem> model = CollectionModel.of(Arrays.asList(item(4), item(5)),
                Link.of(BASE, IanaLinkRelations.SELF),
                Link.of(BASE + "/schema4", "hydra:first"));

        assertWritten("collection-model.jsonld", model);
    }

    @Test
    public void writesCollectionModelSubclassWithOwnContext() throws Exception {
        InvalidSchemaCollection<Message> model = new InvalidSchemaCollection<>(Arrays.asList(
                new Message("Unknown property foo", "error"),
                new Message("Deprecated property bar", "warning")), "id: /soml/broken");

        assertWritten("collection-model-subclass.jsonld", model);
    }

    private void assertWritten(String expectedResource, Object model) throws IOException {
        MockHttpOutputMessage out = new MockHttpOutputMessage();
        converter.write(model, HypermediaTypes.APPLICATION_JSONLD, out);
        String actual = out.getBodyAsString(StandardCharsets.UTF_8);

        String expected = readResource(expectedResource);
        assertNotNull("Missing expected resource " + expectedResource + "; actual output was:\n" + actual,
                expected);
        assertEquals(expected.trim(), actual);
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = HydraJsonLdCharacterizationTest.class.getResourceAsStream(name)) {
            if (in == null) {
                return null;
            }
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
