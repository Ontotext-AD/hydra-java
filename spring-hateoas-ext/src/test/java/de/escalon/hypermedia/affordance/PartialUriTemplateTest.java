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

package de.escalon.hypermedia.affordance;

import com.damnhandy.uri.template.UriTemplate;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class PartialUriTemplateTest {

    final static String LAWNMOWER_TEMPLATE_STRING =
            "http://localhost/things/{id}/widgets?type={widgetType}&redirect=http://example" +
            ".com/{widgetName}?preorder=true#/order/{widgetId}";

    @Test
    public void testToStringWithQueryVariablesContainingDot() {
        PartialUriTemplate partialUriTemplateComponents = new PartialUriTemplate
                ("http://localhost/events/query{?foo1,foo2,bar.baz,bars.empty,offset,size,strings.empty}");
        assertThat(partialUriTemplateComponents.getVariableNames(), contains("foo1", "foo2", "bar.baz",
                "bars.empty", "offset", "size", "strings.empty"));
    }

    @Test
    public void testExpandAllComponents() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events{/city}{?eventName," +
                "location}{#section}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#description",
                expanded.toString());
    }

    @Test
    public void testExpandAllComponentsWithStringStringMap() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events{/city}{?eventName," +
                "location}{#section}");
        Map<String, String> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#description",
                expanded.toString());
    }

    @Test
    public void testExpandQueryWithTwoVariables() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events/Wiesbaden{?eventName," +
                "location}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof",
                expanded.toString());
    }

    @Test
    public void testExpandQueryWithOneVariable() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events/Wiesbaden{?eventName}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour",
                expanded.toString());
    }

    @Test
    public void testExpandLevelOnePathSegment() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events/{city}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden",
                expanded.toString());
    }

    @Test
    public void testExpandLevelOnePathSegmentWithRegex() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events/{city:+}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden",
                expanded.toString());
    }

    @Test
    public void testExpandLevelOnePathSegmentWithPrefix() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events/v{version}/Wiesbaden");
        Map<String, Object> val = new HashMap<>();
        val.put("version", "1.2.0");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/v1.2.0/Wiesbaden",
                expanded.toString());
    }

    @Test
    public void testExpandLevelOneQueryWithOneVariable() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events/Wiesbaden?eventName={eventName}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour",
                expanded.toString());
    }

    @Test
    public void testExpandLevelOneQueryWithTwoVariables() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events/Wiesbaden?eventName={eventName}&location={location}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof",
                expanded.toString());
    }


    @Test
    public void testExpandDoesNotChangeUrlWithoutVariables() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#description");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#description",
                expanded.toString());
    }


    @Test
    public void testExpandWithFixedQuery() {
        final PartialUriTemplate template =
                new PartialUriTemplate("http://example" +
                        ".com/events{/city}?eventName=Revo+Tour&location=Schlachthof{#section}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#description",
                expanded.toString());
    }


    @Test
    public void testExpandWithFixedFragmentIdentifier() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events{/city}{?eventName," +
                "location}#price");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        val.put("section", "description");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof#price",
                expanded.toString());
    }


    @Test
    public void testExpandAllComponentsButFragmentIdentifier() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example.com/events{/city}{?eventName," +
                "location}{#section}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("eventName", "Revo Tour");
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden?eventName=Revo+Tour&location=Schlachthof{#section}",
                expanded.toString());
    }

    @Test
    public void testExpandOneOfTwoQueryVariables() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events{/city}/concerts{?eventName,location}");
        Map<String, Object> val = new HashMap<>();
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events{/city}/concerts?location=Schlachthof{&eventName}", expanded
                .toString());
    }

    @Test
    public void testExpandSegmentVariable() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events/{city}/concerts{?eventName,location}");
        Map<String, Object> val = new HashMap<>();
        val.put("city", "Wiesbaden");
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events/Wiesbaden/concerts?location=Schlachthof{&eventName}", expanded
                .toString());
    }

    @Test
    public void testExpandQueryContinuationTemplate() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events{/city}/concerts?eventName=Revo+Tour{&location}");
        Map<String, Object> val = new HashMap<>();
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example.com/events{/city}/concerts?eventName=Revo+Tour&location=Schlachthof",
                expanded.toString());
    }

    @Test
    public void testExpandQueryContinuationTemplateAfterFixedQueryContinuation() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events{/city}/concerts?eventName=Revo+Tour&foo=bar{&location}");
        Map<String, Object> val = new HashMap<>();
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example" +
                ".com/events{/city}/concerts?eventName=Revo+Tour&foo=bar&location=Schlachthof", expanded.toString());
    }

    @Test
    public void testExpandQueryContinuationTemplatesAfterFixedQueryContinuation() {
        final PartialUriTemplate template = new PartialUriTemplate("http://example" +
                ".com/events{/city}/concerts?eventName=Revo+Tour&foo=bar{&location,baz}");
        Map<String, Object> val = new HashMap<>();
        val.put("baz", "Gnarf");
        val.put("location", "Schlachthof");
        final PartialUriTemplateComponents expanded = template.expand(val);
        Assert.assertEquals("http://example" +
                ".com/events{/city}/concerts?eventName=Revo+Tour&foo=bar&location=Schlachthof&baz=Gnarf", expanded
                .toString());
    }

    @Test
    public void testExpandSimpleStringVariablesWithUrl() {
        final PartialUriTemplate template = new PartialUriTemplate
                ("http://localhost/things/{id}/widgets?type={widgetType}&redirect={url}");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 101);

        assertEquals("http://localhost/things/22/widgets?type=ACME+lawn+mower&redirect=http%3A%2F%2Fexample" +
                        ".com%2FLM%2B6000%3Fpreorder%3Dtrue%23%2Forder%2Facme-lm-6000",
                template.expand("22", "ACME lawn mower",
                        "http://example.com/LM+6000?preorder=true#/order/acme-lm-6000")
                        .toString());
    }

    @Test
    public void testExpandSimpleStringVariablesInUrl() throws URISyntaxException, UnsupportedEncodingException {

        final PartialUriTemplate template = new PartialUriTemplate
                (LAWNMOWER_TEMPLATE_STRING);

        // The redirect url is not correctly encoded, but encoding of the redirect value is up to the
        // template provider. We must ensure that we only encode variable values here and leave the rest alone.
        String expandedUri = template.expand("22", "ACME lawn mower", "LM 6000",
                "acme-lm-6000")
                .toString();
        assertEquals("http://localhost/things/22/widgets?type=ACME+lawn+mower&redirect=http://example" +
                ".com/LM+6000?preorder=true#/order/acme-lm-6000", expandedUri);

        // make sure our query is equivalent to the one created by damnhandy template engine
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 22);
        arguments.put("widgetType", "ACME lawn mower");
        arguments.put("widgetName", "LM 6000");
        arguments.put("widgetId", "acme-lm-6000");

        String damnHandyExpandedUri = UriTemplate.fromTemplate
                (LAWNMOWER_TEMPLATE_STRING)
                .set(arguments)
                .expandPartial();

        URI myUriTemplate = new URI(expandedUri);

        URI dhUriTemplate = new URI(damnHandyExpandedUri);

        assertEquals(URLDecoder.decode(myUriTemplate.getQuery(), StandardCharsets.UTF_8.name()),
                URLDecoder.decode(dhUriTemplate.getQuery(), StandardCharsets.UTF_8.name()));

    }

    @Test
    public void testPreservesUnexpandedSimpleStringVariables() {
        final PartialUriTemplate template = new PartialUriTemplate
                (LAWNMOWER_TEMPLATE_STRING);

        assertEquals("http://localhost/things/22/widgets?type={widgetType}&redirect=http://example" +
                ".com/{widgetName}?preorder=true#/order/{widgetId}", template.expand("22")
                .toString());
    }

    @Test
    public void testContextAbsolutePath() {
        final PartialUriTemplate template = new PartialUriTemplate("/protected/res/documents/index" +
                ".html?focus={contractId}&caller=BLUE&referrer=/protected/res/my_contracts/index" +
                ".html&fragment=/contractDetails/{ref}");

        assertEquals("/protected/res/documents/index" +
                ".html?focus={contractId}&caller=BLUE&referrer=/protected/res/my_contracts/index" +
                ".html&fragment=/contractDetails/{ref}", template.expand()
                .toString());
    }
}