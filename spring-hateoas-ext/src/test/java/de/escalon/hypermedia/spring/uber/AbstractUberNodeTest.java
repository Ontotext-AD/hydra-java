/*
 * Copyright (c) 2015. Escalon System-Entwicklung, Dietrich Schulten
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

package de.escalon.hypermedia.spring.uber;

import org.junit.Before;
import org.junit.Test;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;

import java.util.Arrays;

import static org.junit.Assert.*;

public class AbstractUberNodeTest {

    private static final String URL_PREVIOUS = "http://www.example.com/previous";
    private static final String URL_NEXT = "http://www.example.com/next";
    private DummyUberNode dummyUberNode;
    private UberNode foo = new UberNode();
    private UberNode bar = new UberNode();
    private UberNode baz = new UberNode();
    private Link linkPrevious = Link.of(URL_PREVIOUS, IanaLinkRelations.PREVIOUS);
    private Link linkNext = Link.of(URL_NEXT, IanaLinkRelations.NEXT);

    class DummyUberNode extends AbstractUberNode {

    }

    @Before
    public void setUp() throws Exception {
        dummyUberNode = new DummyUberNode();

        foo.setName("foo");
        foo.addData(new UberNode());

        bar.setName("bar");
        bar.addData(new UberNode());
    }

    @Test
    public void iteratesOverDataIgnoringLinksInBetween() {

        dummyUberNode.addData(foo);
        dummyUberNode.addLink(linkNext);
        dummyUberNode.addData(bar);

        int i = 0;
        String[] expected = {"foo", "bar"};
        for (UberNode uberNode : dummyUberNode) {
            assertEquals(expected[i++], uberNode.getName());
        }
    }

    @Test
    public void iteratesOverDataIgnoringLinksAtStart() {
        dummyUberNode.addLink(linkNext);
        dummyUberNode.addData(foo);
        dummyUberNode.addData(bar);

        int i = 0;
        String[] expected = {"foo", "bar"};
        for (UberNode uberNode : dummyUberNode) {
            assertEquals(expected[i++], uberNode.getName());
        }
    }

    @Test
    public void iteratesOverDataIgnoringLinksAtEnd() {

        dummyUberNode.addData(foo);
        dummyUberNode.addData(bar);
        dummyUberNode.addLink(linkNext);

        int i = 0;
        String[] expected = {"foo", "bar"};
        for (UberNode uberNode : dummyUberNode) {
            assertEquals(expected[i++], uberNode.getName());
        }
    }

    @Test
    public void iteratesOverData() {
        dummyUberNode.addData(foo);
        dummyUberNode.addData(bar);

        int i = 0;
        String[] expected = {"foo", "bar",};
        for (UberNode uberNode : dummyUberNode) {
            assertEquals(expected[i++], uberNode.getName());
        }
    }

    @Test
    public void iteratesInOrder() {
        dummyUberNode.addData(bar);
        dummyUberNode.addData(foo);
        dummyUberNode.addData(baz);

        int i = 0;
        String[] expected = {"bar", "foo", "baz"};
        for (UberNode uberNode : dummyUberNode) {
            assertEquals(expected[i++], uberNode.getName());
        }
    }

    @Test
    public void getsFirstNodeByRel() {
        dummyUberNode.addData(bar);
        dummyUberNode.addLink(linkPrevious);

        assertNotNull("rel previous not found", dummyUberNode.getFirstByRel(IanaLinkRelations.PREVIOUS_VALUE));
    }

    @Test
    public void getsFirstNodeByName() {
        dummyUberNode.addLink(linkPrevious);
        dummyUberNode.addData(foo);
        dummyUberNode.addData(bar);

        assertNotNull("item bar not found", dummyUberNode.getFirstByName("bar"));
        assertNotNull("item foo not found", dummyUberNode.getFirstByName("foo"));
        assertNull(dummyUberNode.getFirstByName("noSuchName"));
    }

    @Test
    public void findsAddedLinks() {
        dummyUberNode.addLinks(Arrays.asList(linkNext, linkPrevious));
        assertEquals(URL_NEXT, dummyUberNode.getFirstByRel(IanaLinkRelations.NEXT_VALUE)
                .getUrl());
        assertEquals(URL_PREVIOUS, dummyUberNode.getFirstByRel(IanaLinkRelations.PREVIOUS_VALUE)
                .getUrl());
        assertNull(dummyUberNode.getFirstByRel("noSuchRel"));
    }
}
