package de.escalon.hypermedia.spring.siren;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.escalon.hypermedia.PropertyUtils;
import de.escalon.hypermedia.action.Type;
import de.escalon.hypermedia.affordance.ActionDescriptor;
import de.escalon.hypermedia.affordance.ActionInputParameter;
import de.escalon.hypermedia.affordance.Affordance;
import de.escalon.hypermedia.affordance.DataType;
import de.escalon.hypermedia.spring.DefaultDocumentationProvider;
import de.escalon.hypermedia.spring.DocumentationProvider;
import de.escalon.hypermedia.spring.SpringActionInputParameter;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.core.DefaultLinkRelationProvider;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Maps spring-hateoas response data to siren data. Created by Dietrich on 17.04.2016.
 */
public class SirenUtils {

    private static final Set<String> FILTER_RESOURCE_SUPPORT = new HashSet<>(Arrays.asList("class", "links",
            "id"));
    private String requestMediaType;

    private final Set<String> navigationalRels = new HashSet<>(Arrays.asList("self", "next", "previous", "prev"));

    private LinkRelationProvider relProvider = new DefaultLinkRelationProvider();

    private DocumentationProvider documentationProvider = new DefaultDocumentationProvider();

    public void toSirenEntity(SirenEntityContainer objectNode, Object object) {
        if (object == null) {
            return;
        }
        try {
            if (object instanceof EntityModel) {
                EntityModel<?> resource = (EntityModel<?>) object;
                objectNode.setLinks(this.toSirenLinks(
                        getNavigationalLinks(resource.getLinks())));
                objectNode.setEmbeddedLinks(this.toSirenEmbeddedLinks(
                        getEmbeddedLinks(resource.getLinks())));
                objectNode.setActions(this.toSirenActions(getActions(resource.getLinks())));
                toSirenEntity(objectNode, resource.getContent());
                return;
            } else if (object instanceof CollectionModel) {
                CollectionModel<?> resources = (CollectionModel<?>) object;

                objectNode.setLinks(this.toSirenLinks(getNavigationalLinks(resources.getLinks())));
                Collection<?> content = resources.getContent();
                toSirenEntity(objectNode, content);
                objectNode.setActions(this.toSirenActions(getActions(resources.getLinks())));
                return;
            } else if (object instanceof RepresentationModel) {
                RepresentationModel resource = (RepresentationModel) object;
                objectNode.setLinks(this.toSirenLinks(
                        getNavigationalLinks(resource.getLinks())));
                objectNode.setEmbeddedLinks(this.toSirenEmbeddedLinks(
                        getEmbeddedLinks(resource.getLinks())));
                objectNode.setActions(this.toSirenActions(
                        getActions(resource.getLinks())));

                // wrap object attributes below to avoid endless loop

            } else if (object instanceof Collection) {
                Collection<?> collection = (Collection<?>) object;
                for (Object item : collection) {
                    SirenEmbeddedRepresentation child = new SirenEmbeddedRepresentation();
                    toSirenEntity(child, item);
                    objectNode.addSubEntity(child);
                }
                return;
            }
            if (object instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) object;
                Map<String, Object> propertiesNode = new HashMap<>();
                objectNode.setProperties(propertiesNode);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = entry.getKey()
                            .toString();
                    Object content = entry.getValue();

                    String docUrl = documentationProvider.getDocumentationUrl(key, content);
                    traverseAttribute(objectNode, propertiesNode, key, docUrl, content);
                }
            } else { // bean or RepresentationModel
                objectNode.setSirenClasses(getSirenClasses(object));
                Map<String, Object> propertiesNode = new HashMap<>();
                createRecursiveSirenEntitiesFromPropertiesAndFields(objectNode, propertiesNode, object);
                objectNode.setProperties(propertiesNode);
            }
        } catch (Exception ex) {
            throw new RuntimeException("failed to transform object " + object, ex);
        }
    }

    private List<String> getSirenClasses(Object object) {
        List<String> sirenClasses;
        String sirenClass = relProvider.getItemResourceRelFor(object.getClass()).value();
        if (sirenClass != null) {
            sirenClasses = Collections.singletonList(sirenClass);
        } else {
            sirenClasses = Collections.emptyList();
        }
        return sirenClasses;
    }

    private List<Link> getEmbeddedLinks(Links links) {
        List<Link> ret = new ArrayList<>();
        for (Link link : links) {
            if (!navigationalRels.contains(link.getRel().value())) {
                if (link instanceof Affordance) {
                    Affordance affordance = (Affordance) link;
                    List<ActionDescriptor> actionDescriptors = affordance.getActionDescriptors();
                    for (ActionDescriptor actionDescriptor : actionDescriptors) {
                        if ("GET".equals(actionDescriptor.getHttpMethod()) && !affordance.isTemplated()) {
                            ret.add(link);
                        }
                    }
                } else {
                    // templated links are actions, not embedded links
                    if(!link.isTemplated()) {
                        ret.add(link);
                    }
                }
            }
        }
        return ret;
    }

    private List<Link> getNavigationalLinks(Links links) {
        List<Link> ret = new ArrayList<>();
        for (Link link : links) {
            if (navigationalRels.contains(link.getRel().value())) {
                ret.add(link);
            }
        }
        return ret;
    }

    private List<Link> getActions(Links links) {
        List<Link> ret = new ArrayList<>();
        for (Link link : links) {
            if (link instanceof Affordance) {
                Affordance affordance = (Affordance) link;

                List<ActionDescriptor> actionDescriptors = affordance.getActionDescriptors();
                for (ActionDescriptor actionDescriptor : actionDescriptors) {
                    // non-self GET non-GET and templated links are actions
                    if (!("GET".equals(actionDescriptor.getHttpMethod())) || affordance.isTemplated()) {
                        ret.add(link);
                        // add just once for eligible link
                        break;
                    }
                }
            } else {
                // templated links are actions
                if (!navigationalRels.contains(link.getRel().value()) && link.isTemplated()) {
                    ret.add(link);
                }
            }
        }
        return ret;
    }


    private void createRecursiveSirenEntitiesFromPropertiesAndFields(SirenEntityContainer objectNode, Map<String,
            Object> propertiesNode,
                                                                     Object object) throws InvocationTargetException,
            IllegalAccessException {
        Map<String, PropertyDescriptor> propertyDescriptors = PropertyUtils.getPropertyDescriptors(object);
        for (PropertyDescriptor propertyDescriptor : propertyDescriptors.values()) {
            String name = propertyDescriptor.getName();
            if (FILTER_RESOURCE_SUPPORT.contains(name)) {
                continue;
            }

            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null) {
                Object content = readMethod
                        .invoke(object);
                String docUrl = documentationProvider.getDocumentationUrl(readMethod, content);
                traverseAttribute(objectNode, propertiesNode, name, docUrl, content);
            }
        }

        Field[] fields = object.getClass()
                .getFields();
        for (Field field : fields) {
            String name = field.getName();
            if (!propertyDescriptors.containsKey(name)) {
                Object content = field.get(object);
                String docUrl = documentationProvider.getDocumentationUrl(field, content);
                traverseAttribute(objectNode, propertiesNode, name, docUrl, content);
            }
        }
    }

    private void traverseAttribute(SirenEntityContainer objectNode, Map<String, Object> propertiesNode,
                                   String name, String docUrl, Object content) throws
            InvocationTargetException, IllegalAccessException {
        Object value = getContentAsScalarValue(content);

        if (value != NULL_VALUE) {
            if (value != null) {
                // for each scalar property of a simple bean, add valuepair
                propertiesNode.put(name, value);
            } else {
                if (content instanceof CollectionModel) {
                    toSirenEntity(objectNode, content);
                } else if (content instanceof RepresentationModel) {
                    traverseSingleSubEntity(objectNode, content, name, docUrl);
                } else if (content instanceof Collection) {
                    Collection<?> collection = (Collection<?>) content;
                    for (Object item : collection) {
                        if (DataType.isSingleValueType(item.getClass())) {
                            Object listObject = propertiesNode.get(name);
                            if (listObject == null) {
                                listObject = new ArrayList();
                                propertiesNode.put(name, listObject);
                            }
                            if (listObject instanceof Collection) {
                                ((Collection) listObject).add(item);
                            }
                        } else if (item != null) {
                            traverseSingleSubEntity(objectNode, item, name, docUrl);
                        }
                    }
                } else if (content instanceof Map) {
                    Set<Map.Entry<String, Object>> entries = ((Map<String, Object>) content).entrySet();
                    Map<String, Object> subProperties = new HashMap<>();
                    propertiesNode.put(name, subProperties);
                    for (Map.Entry<String, Object> entry : entries) {
                        traverseAttribute(objectNode, subProperties, entry.getKey(), docUrl, entry.getValue());
                    }
                } else {
                    Map<String, Object> nestedProperties = new HashMap<>();
                    propertiesNode.put(name, nestedProperties);
                    createRecursiveSirenEntitiesFromPropertiesAndFields(objectNode, nestedProperties, content);
                }
            }
        }
    }

    private void traverseSingleSubEntity(SirenEntityContainer objectNode, Object content,
                                         String name, String docUrl)
            throws InvocationTargetException, IllegalAccessException {

        Object bean;
        Links links;
        if (content instanceof EntityModel) {
            bean = ((EntityModel) content).getContent();
            links = ((EntityModel) content).getLinks();
        } else if (content instanceof RepresentationModel) {
            bean = content;
            links = ((RepresentationModel) content).getLinks();
        } else {
            bean = content;
            links = Links.NONE;
        }

        Map<String, Object> properties = new HashMap<>();
        List<String> rels = Collections.singletonList(docUrl != null ? docUrl : name);
        SirenEmbeddedRepresentation subEntity = new SirenEmbeddedRepresentation(
                getSirenClasses(bean), properties, null, toSirenActions(getActions(links)),
                toSirenLinks(getNavigationalLinks(links)), rels, null);
        //subEntity.setProperties(properties);
        objectNode.addSubEntity(subEntity);
        List<SirenEmbeddedLink> sirenEmbeddedLinks = toSirenEmbeddedLinks(getEmbeddedLinks(links));
        for (SirenEmbeddedLink sirenEmbeddedLink : sirenEmbeddedLinks) {
            subEntity.addSubEntity(sirenEmbeddedLink);
        }
        createRecursiveSirenEntitiesFromPropertiesAndFields(subEntity, properties, bean);
    }

    private List<SirenAction> toSirenActions(List<Link> links) {
        List<SirenAction> ret = new ArrayList<>();
        for (Link link : links) {
            if (link instanceof Affordance) {
                Affordance affordance = (Affordance) link;
                List<ActionDescriptor> actionDescriptors = affordance.getActionDescriptors();
                for (ActionDescriptor actionDescriptor : actionDescriptors) {
                    List<SirenField> fields = toSirenFields(actionDescriptor);
                    // TODO integrate getActions and this method so we do not need this check:
                    // only templated affordances or non-get affordances are actions
                    if (!"GET".equals(actionDescriptor.getHttpMethod()) || affordance.isTemplated()) {
                        String href;
                        if (affordance.isTemplated()) {
                            href = affordance.getUriTemplateComponents()
                                    .getBaseUri();
                        } else {
                            href = affordance.getHref();
                        }

                        SirenAction sirenAction = new SirenAction(null, actionDescriptor.getActionName(), null,
                                actionDescriptor.getHttpMethod(), href, requestMediaType, fields);
                        ret.add(sirenAction);
                    }
                }
            } else if (link.isTemplated()) {
                List<SirenField> fields = new ArrayList<>();
                List<TemplateVariable> variables = link.getVariables();
                boolean queryOnly = false;
                for (TemplateVariable variable : variables) {
                    queryOnly = isQueryParam(variable);
                    if (!queryOnly) {
                        break;
                    }
                    fields.add(new SirenField(variable.getName(), "text", (String) null, variable.getDescription(),
                            null));
                }
                // no support for non-query fields in siren
                if (queryOnly) {
                    String baseUri = UriTemplate.of(link.getHref()).expand()
                            .toASCIIString();
                    SirenAction sirenAction = new SirenAction(null, null, null, "GET",
                            baseUri, null, fields);
                    ret.add(sirenAction);
                }
            }
        }
        return ret;
    }

    private boolean isQueryParam(TemplateVariable variable) {
        boolean queryOnly;
        switch (variable.getType()) {
            case REQUEST_PARAM:
            case REQUEST_PARAM_CONTINUED:
                queryOnly = true;
                break;
            default:
                queryOnly = false;
        }
        return queryOnly;
    }

    private List<SirenField> toSirenFields(ActionDescriptor actionDescriptor) {
        List<SirenField> ret = new ArrayList<>();
        if (actionDescriptor.hasRequestBody()) {
            recurseBeanCreationParams(ret, actionDescriptor.getRequestBody()
                    .getParameterType(), actionDescriptor, actionDescriptor.getRequestBody(), actionDescriptor
                    .getRequestBody()
                    .getValue(), "", Collections.<String>emptySet());
        } else {
            Collection<String> paramNames = actionDescriptor.getRequestParamNames();
            for (String paramName : paramNames) {
                ActionInputParameter inputParameter = actionDescriptor.getActionInputParameter(paramName);
                Object[] possibleValues = inputParameter.getPossibleValues(actionDescriptor);

                ret.add(createSirenField(paramName, inputParameter.getValueFormatted(), inputParameter,
                        possibleValues));
            }
        }
        return ret;
    }

    /**
     * Renders input fields for bean properties of bean to add or update or patch.
     *
     * @param sirenFields to add to
     * @param beanType to render
     * @param annotatedParameters which describes the method
     * @param annotatedParameter which requires the bean
     * @param currentCallValue sample call value
     */
    private void recurseBeanCreationParams(List<SirenField> sirenFields, Class<?> beanType,
                                           ActionDescriptor annotatedParameters,
                                           ActionInputParameter annotatedParameter, Object currentCallValue,
                                           String parentParamName, Set<String> knownFields) {
        // TODO collection, map and object node creation are only describable by an annotation, not via type reflection
        if (ObjectNode.class.isAssignableFrom(beanType) || Map.class.isAssignableFrom(beanType)
                || Collection.class.isAssignableFrom(beanType) || beanType.isArray()) {
            return; // use @Input(include) to list parameter names, at least? Or mix with hdiv's form builder?
        }
        try {
            Constructor[] constructors = beanType.getConstructors();
            // find default ctor
            Constructor constructor = PropertyUtils.findDefaultCtor(constructors);
            // find ctor with JsonCreator ann
            if (constructor == null) {
                constructor = PropertyUtils.findJsonCreator(constructors, JsonCreator.class);
            }
            Assert.notNull(constructor, "no default constructor or JsonCreator found for type " + beanType
                    .getName());
            int parameterCount = constructor.getParameterTypes().length;

            if (parameterCount > 0) {
                Annotation[][] annotationsOnParameters = constructor.getParameterAnnotations();

                Class[] parameters = constructor.getParameterTypes();
                int paramIndex = 0;
                for (Annotation[] annotationsOnParameter : annotationsOnParameters) {
                    for (Annotation annotation : annotationsOnParameter) {
                        if (JsonProperty.class == annotation.annotationType()) {
                            JsonProperty jsonProperty = (JsonProperty) annotation;

                            // TODO use required attribute of JsonProperty for required fields
                            String paramName = jsonProperty.value();
                            Class parameterType = parameters[paramIndex];
                            Object propertyValue = PropertyUtils.getPropertyOrFieldValue(currentCallValue,
                                    paramName);
                            MethodParameter methodParameter = new MethodParameter(constructor, paramIndex);

                            addSirenFieldsForMethodParameter(sirenFields, methodParameter, annotatedParameter,
                                    annotatedParameters,
                                    parentParamName, paramName, parameterType, propertyValue,
                                    knownFields);
                            paramIndex++; // increase for each @JsonProperty
                        }
                    }
                }
                Assert.isTrue(parameters.length == paramIndex,
                        "not all constructor arguments of @JsonCreator " + constructor.getName() +
                                " are annotated with @JsonProperty");
            }

            Set<String> knownConstructorFields = new HashSet<>(sirenFields.size());
            for (SirenField sirenField : sirenFields) {
                knownConstructorFields.add(sirenField.getName());
            }

            // TODO support Option provider by other method args?
            Map<String, PropertyDescriptor> propertyDescriptors = PropertyUtils.getPropertyDescriptors(beanType);

            // add input field for every setter
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors.values()) {
                final Method writeMethod = propertyDescriptor.getWriteMethod();
                String propertyName = propertyDescriptor.getName();

                if (writeMethod == null || knownFields.contains(parentParamName + propertyName)) {
                    continue;
                }
                final Class<?> propertyType = propertyDescriptor.getPropertyType();

                Object propertyValue = PropertyUtils.getPropertyOrFieldValue(currentCallValue, propertyName);
                MethodParameter methodParameter = new MethodParameter(propertyDescriptor.getWriteMethod(), 0);

                addSirenFieldsForMethodParameter(sirenFields, methodParameter, annotatedParameter,
                        annotatedParameters,
                        parentParamName, propertyName, propertyType, propertyValue, knownConstructorFields);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write input fields for constructor", e);
        }
    }

    private void addSirenFieldsForMethodParameter(List<SirenField> sirenFields, MethodParameter
            methodParameter, ActionInputParameter annotatedParameter, ActionDescriptor annotatedParameters, String
                                                          parentParamName, String paramName, Class
                                                          parameterType, Object propertyValue, Set<String>
                                                          knownFields) {
        if (DataType.isSingleValueType(parameterType)
                || DataType.isArrayOrCollection(parameterType)) {

            if (annotatedParameter.isIncluded(paramName) && !knownFields.contains(parentParamName + paramName)) {

                ActionInputParameter constructorParamInputParameter =
                        new SpringActionInputParameter(methodParameter, propertyValue);

                final Object[] possibleValues =
                        annotatedParameter.getPossibleValues(methodParameter, annotatedParameters);

                // dot-separated property path as field name
                SirenField sirenField = createSirenField(parentParamName + paramName,
                        propertyValue, constructorParamInputParameter, possibleValues);
                sirenFields.add(sirenField);
            }
        } else {
            Object callValueBean;
            if (propertyValue instanceof EntityModel) {
                callValueBean = ((EntityModel) propertyValue).getContent();
            } else {
                callValueBean = propertyValue;
            }
            recurseBeanCreationParams(sirenFields, parameterType, annotatedParameters,
                    annotatedParameter,
                    callValueBean, paramName + ".", knownFields);
        }
    }

    private SirenField createSirenField(String paramName, Object propertyValue,
                                        ActionInputParameter inputParameter, Object[] possibleValues) {
        SirenField sirenField;
        if (possibleValues.length == 0) {
            String propertyValueAsString = propertyValue == null ? null : propertyValue
                    .toString();
            Type htmlInputFieldType = inputParameter.getHtmlInputFieldType();
            // TODO: null -> array or bean parameter without possible values
            String type = htmlInputFieldType == null ? "text" :
                    htmlInputFieldType
                            .name()
                            .toLowerCase();
            sirenField = new SirenField(paramName,
                    type,
                    propertyValueAsString, null, null);
        } else {
            List<SirenFieldValue> sirenPossibleValues = new ArrayList<>();
            String type;
            if (inputParameter.isArrayOrCollection()) {
                type = "checkbox";
                for (Object possibleValue : possibleValues) {
                    boolean selected = ObjectUtils.containsElement(
                            inputParameter.getValues(),
                            possibleValue);
                    // TODO have more useful value title
                    sirenPossibleValues.add(new SirenFieldValue(possibleValue.toString(), possibleValue, selected));
                }
            } else {
                type = "radio";
                for (Object possibleValue : possibleValues) {
                    boolean selected = possibleValue.equals(propertyValue);
                    sirenPossibleValues.add(new SirenFieldValue(possibleValue.toString(), possibleValue, selected));
                }
            }
            sirenField = new SirenField(paramName,
                    type,
                    sirenPossibleValues, null, null);
        }
        return sirenField;
    }


    private List<SirenLink> toSirenLinks(List<Link> links) {
        List<SirenLink> ret = new ArrayList<>();
        for (Link link : links) {
            if (link instanceof Affordance) {
                ret.add(new SirenLink(null, ((Affordance) link).getRels(), link.getHref(), null, null));
            } else {
                ret.add(new SirenLink(null, Collections.singletonList(link.getRel().value()), link.getHref(), null, null));
            }
        }
        return ret;
    }

    private List<SirenEmbeddedLink> toSirenEmbeddedLinks(List<Link> links) {
        List<SirenEmbeddedLink> ret = new ArrayList<>();
        for (Link link : links) {
            if (link instanceof Affordance) {
                // TODO: how to determine classes? type of target resource? collection/item?
                ret.add(new SirenEmbeddedLink(null, ((Affordance) link).getRels(), link
                        .getHref(), null, null));
            } else {
                ret.add(new SirenEmbeddedLink(null, Collections.singletonList(link.getRel().value()), link
                        .getHref(), null, null));
            }
        }
        return ret;
    }


    static class NullValue {

    }

    public static final NullValue NULL_VALUE = new NullValue();

    private Object getContentAsScalarValue(Object content) {
        Object value = null;

        if (content == null) {
            value = NULL_VALUE;
        } else if (DataType.isSingleValueType(content.getClass())) {
            value = DataType.asScalarValue(content);
        }
        return value;
    }

    public void setRequestMediaType(String requestMediaType) {
        this.requestMediaType = requestMediaType;
    }

    public void setRelProvider(LinkRelationProvider relProvider) {
        this.relProvider = relProvider;
    }

    public void setDocumentationProvider(DocumentationProvider documentationProvider) {
        this.documentationProvider = documentationProvider;
    }

    public void setAdditionalNavigationalRels(Collection<String> additionalNavigationalRels) {
        this.navigationalRels.addAll(additionalNavigationalRels);
    }
}
