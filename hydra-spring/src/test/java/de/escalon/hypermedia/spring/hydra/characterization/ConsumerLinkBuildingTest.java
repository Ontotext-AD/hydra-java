package de.escalon.hypermedia.spring.hydra.characterization;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static de.escalon.hypermedia.spring.AffordanceBuilder.methodOn;
import static org.junit.Assert.assertEquals;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

/**
 * Downstream consumers build paging links by recording an invocation with this library's
 * {@code AffordanceBuilder.methodOn(..)} and handing it to Spring HATEOAS' own
 * {@code WebMvcLinkBuilder.linkTo(Object)}. Both sides must keep agreeing on the invocation proxy, and
 * unnamed {@code @RequestParam}s must still resolve (via {@code -parameters}).
 */
public class ConsumerLinkBuildingTest {

    @RequestMapping("/soml")
    public static class PagedController {

        @GetMapping
        public ResponseEntity<PagedModel<String>> readAll(
                @RequestParam(required = false, defaultValue = "0") int page,
                @RequestParam(required = false, defaultValue = "10") int size) {
            return null;
        }
    }

    @Before
    public void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/soml");
        request.setServerName("example.org");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @After
    public void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void hateoasLinkToAcceptsInvocationRecordedByAffordanceBuilderMethodOn() {
        Link link = linkTo(methodOn(PagedController.class).readAll(2, 5)).withRel("next");

        assertEquals("http://example.org:8080/soml?page=2&size=5", link.getHref());
        assertEquals("next", link.getRel().value());
    }
}
