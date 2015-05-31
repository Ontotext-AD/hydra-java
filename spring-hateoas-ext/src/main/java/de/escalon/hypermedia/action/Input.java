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

package de.escalon.hypermedia.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows to define the input characteristics for an input field. E.g. this is useful to specify possible value ranges
 * as in <code>&#64;Input(min=0)</code>, and it can also be used to mark a method parameter as
 * <code>&#64;Input(Type.HIDDEN)</code>, e.g. when used as a POST parameter for a form which is not supposed to be
 * changed by the client.
 *
 * @author Dietrich Schulten
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Input {

    String MIN = "min";
    String MAX = "max";
    String STEP = "step";
    String MIN_LENGTH = "minLength";
    String MAX_LENGTH = "maxLength";
    String PATTERN = "pattern";

    /**
     * Input type. With the default type FROM_JAVA the type will be number or text, depending on the parameter type.
     *
     * @return input type
     */
    Type value() default Type.FROM_JAVA;

    int max() default Integer.MAX_VALUE;

    int min() default Integer.MIN_VALUE;

    int minLength() default Integer.MIN_VALUE;

    int maxLength() default Integer.MAX_VALUE;

    String pattern() default "";

    int step() default 0;

    /**
     * Dot separated property names or paths of read-only properties on input bean. Allows to define expected input bean
     * attributes with read-only values, so that a media type can render them as read-only attribute. This allows to use
     * the same bean for input and output in different contexts. E.g. all product attributes should be editable when a
     * new product is added, but not when an order is created which contains that product. Thus, if a POST expects an
     * object Product with certain fixed values, you can annotate the POST handler:
     * <pre>
     *     public void makeOrder(@Input(readOnly={"productID"}) Product orderedProduct} {...}
     * </pre>
     * Typically, a readOnly attribute should have a predefined value. Defining a readOnly property effectively makes
     * that property an {@link #include} property, i.e. other attributes are ignored by default.
     *
     * @return property paths which should be shown as read-only
     * @see #include
     * @see #exclude
     */
    String[] readOnly() default {};

    /**
     * Dot separated property names or paths of properties that should be ignored on input bean. This allows to use the
     * same bean for input and output in different contexts. If a POST expects an object Product without certain values,
     * you can annotate the POST handler:
     * <pre>
     *     public void makeOrder(@Input(exclude={"name"}) Product orderedProduct} {...}
     * </pre>
     * If excluded attributes are present, the assumption is that all other attributes should be considered expected
     * inputs.
     *
     * @return property paths which should be ignored
     */
    String[] exclude() default {};

    /**
     * Dot separated property names or paths of properties that are expected on input bean. If a POST expects an object
     * Review having only certain attributes, you can annotate the POST handler:
     * <pre>
     *     public void addReview(include={"rating.ratingValue", "reviewBody"}) Review review} {...}
     * </pre>
     * If included attributes are present, the assumption is that all other attributes should be considered ignored
     * inputs.
     *
     * @return property paths which should be ignored
     */
    String[] include() default {};

}
