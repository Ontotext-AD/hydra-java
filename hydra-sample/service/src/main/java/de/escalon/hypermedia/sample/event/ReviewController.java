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

package de.escalon.hypermedia.sample.event;

import de.escalon.hypermedia.action.Action;
import de.escalon.hypermedia.sample.beans.event.Review;
import de.escalon.hypermedia.spring.AffordanceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Sample controller for reviews. Created by dschulten on 16.09.2014.
 */
@Controller
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    EventBackend eventBackend;

    @GetMapping(value = "/events/{eventId}")
    @ResponseBody
    public ResponseEntity<CollectionModel<Review>> getReviews(@PathVariable int eventId) {
        List<Review> reviews = eventBackend.getReviews()
                .get(eventId);

        ResponseEntity<CollectionModel<Review>> ret;
        if (reviews != null) {
            final CollectionModel<Review> reviewResources = CollectionModel.of(reviews);

            reviewResources.add(AffordanceBuilder.linkTo(AffordanceBuilder.methodOn(EventController.class)
                    .getEvent(eventId)) // passing null requires that method takes Integer, not int
                    .withRel("hydra:search"));
            reviewResources.add(AffordanceBuilder.linkTo(AffordanceBuilder.methodOn(this.getClass())
                    .addReview
                            (eventId, null))
                    .withSelfRel());
            ret = new ResponseEntity<>(reviewResources, HttpStatus.OK);
        } else {
            ret = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ret;
    }

    @Action("ReviewAction")
    @PostMapping(value = "/events/{eventId}")
    public
    @ResponseBody
    ResponseEntity<Void> addReview(@PathVariable int eventId, @RequestBody Review review) {
        Assert.notNull(review, () -> "The review argument is required; it must not be null");
        Assert.notNull(review.getReviewRating(), () -> "The review rating argument is required; it must not be null");
        Assert.notNull(review.getReviewRating()
                .getRatingValue(), () -> "The review rating value argument is required; it must not be null");
        ResponseEntity<Void> responseEntity;
        try {
            eventBackend.addReview(eventId, review.getReviewBody(), review.getReviewRating());
            final HttpHeaders headers = new HttpHeaders();
            headers.setLocation(AffordanceBuilder.linkTo(AffordanceBuilder.methodOn(this.getClass())
                    .getReviews(eventId))
                    .toUri());
            responseEntity = new ResponseEntity<>(headers, HttpStatus.CREATED);
        } catch (NoSuchElementException ex) {
            responseEntity = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return responseEntity;
    }
}
