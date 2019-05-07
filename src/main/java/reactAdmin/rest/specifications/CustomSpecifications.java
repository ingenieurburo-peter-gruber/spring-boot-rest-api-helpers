package reactAdmin.rest.specifications;


import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import java.util.*;

//from: https://github.com/zifnab87/react-admin-java-rest/blob/master/src/main/java/reactAdmin/rest/specifications/ReactAdminSpecifications.java
@Service
public class CustomSpecifications<T> {

    public Specification<T> seachInAllAttributes(String text, List<String> includeOnlyFields) {

        if (!text.contains("%")) {
            text = "%" + text + "%";
        }
        final String finalText = text;

        return (Specification<T>) (root, cq, builder) -> builder.or(root.getModel().getAttributes().stream().filter(a ->
                        (a.getJavaType().getSimpleName().equalsIgnoreCase("string")
                                && (includeOnlyFields.isEmpty() || includeOnlyFields.contains(a.getName())))
                ).map(a -> builder.like(root.get(a.getName()), finalText)
                ).toArray(Predicate[]::new)
        );
    }


    public Specification<T> equalToEachColumn(Map<String, Object> map) {

        return (Specification<T>) (root, query, builder) -> {

            final List<Predicate> predicates = new ArrayList<>();

            Set<SingularAttribute<? super T, ?>> singularAttributes = root.getModel().getSingularAttributes();
            Set<PluralAttribute<? super T, ?, ?>> pluralAttributes = root.getModel().getPluralAttributes();

            Set<Attribute<? super T, ?>> attributes = root.getModel().getAttributes();

            for (Map.Entry e : map.entrySet()) {
                String key = (String) e.getKey();
                Object val = extractId(e.getValue());
                String cleanKey = cleanUpKey(key);
                Attribute a = root.getModel().getAttribute(cleanKey);
                Predicate pred = builder.conjunction();
                if (cleanKey.equals(key) && attributes.contains(a)) {
                    //val = extractId(val);
//                    boolean isAttributeSingular = singularAttributes.contains(a);
//                    boolean isAttributePlural = pluralAttributes.contains(a);
                    boolean isAttributePrimitive = isPrimitive(a);
                    boolean isAttributeReferenced = a.isAssociation();//isCollection(a) || (!isPrimitive(a) && !isEnum(a));
                    boolean isAttributeEnum = isEnum(a);
                    boolean isValueNull = val == null;
                    boolean isValueCollection = val instanceof Collection;

                    if (isValueNull && !isAttributeReferenced) {
                        pred = root.get(a.getName()).isNull();
                    } else if (isAttributePrimitive) {

                        if (!isValueCollection) {
                            pred = builder.equal(root.get(a.getName()), val);
                        } else {
                            Collection colVal = (Collection) val;
                            List<Predicate> orPredicates = new ArrayList<>();
                            for (Object el : colVal) {
                                Predicate orPred = builder.equal(root.get(a.getName()), el);
                                orPredicates.add(orPred);
                            }
                            pred = builder.or(orPredicates.toArray(new Predicate[0]));
                        }
                    } else if (isAttributeEnum) {
                        pred = builder.equal(root.get(a.getName()), Enum.valueOf(Class.class.cast(a.getJavaType()), (String) val));

                    } else if (isAttributeReferenced) {
                        if (isValueNull) {
                            pred = root.get(a.getName()).isNull();
                        } else if (!isValueCollection) {
                            pred = root.join(a.getName()).get("id").in(val);
                        } else {
                            Collection colVal = (Collection) val;
                            List<Predicate> orPredicates = new ArrayList<>();
                            for (Object el : colVal) {
                                Predicate orPred = root.join(a.getName()).get("id").in(el);
                                orPredicates.add(orPred);
                            }
                            pred = builder.or(orPredicates.toArray(new Predicate[0]));
                        }
                    } else {
                        pred = builder.equal(root.join(a.getName()).get("id"), val);
                    }
                }

                else if (key.endsWith("Lte")) {
                    if (val instanceof String) {
                        pred = builder.lessThanOrEqualTo(root.get(a.getName()), ((String) val).toLowerCase());
                    } else if (val instanceof Integer) {
                        pred = builder.lessThanOrEqualTo(root.get(a.getName()), (Integer) val);
                    }
                }
                else if (key.endsWith("Gte")) {
                    if (val instanceof String) {
                        pred = builder.greaterThanOrEqualTo(root.get(a.getName()), ((String) val).toLowerCase());
                    } else if (val instanceof Integer) {
                        pred = builder.greaterThanOrEqualTo(root.get(a.getName()), (Integer) val);
                    }
                }
                else if (key.endsWith("Lt")) {
                    if (val instanceof String) {
                        pred = builder.lessThan(root.get(a.getName()), ((String) val).toLowerCase());
                    } else if (val instanceof Integer) {
                        pred = builder.lessThan(root.get(a.getName()), (Integer) val);
                    }
                }
                else if (key.endsWith("Gt")) {
                    if (val instanceof String) {
                        pred = builder.greaterThan(root.get(a.getName()), ((String) val).toLowerCase());
                    } else if (val instanceof Integer) {
                        pred = builder.greaterThan(root.get(a.getName()), (Integer) val);
                    }
                }
                if (pred == null) {
                    pred = builder.conjunction();
                }
                predicates.add(pred);


            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String cleanUpKey(String key) {

        List<String> postfixes = Arrays.asList("Gte", "Gt", "Lte", "Lt", "Not", "Or");
        for (String postfix: postfixes) {
            if (key.endsWith(postfix)) {
                return key.substring(0, key.length() - postfix.length());
            }
        }
        return key;

    }


    private Object extractId(Object val) {
        if (val instanceof Map) {
            val = ((Map) val).get("id");
        } else if (val instanceof ArrayList && !((ArrayList) val).isEmpty() && ((ArrayList) val).get(0) instanceof Map) {
            val = ((Map) ((ArrayList) val).get(0)).get("id");
        }

        return val;
    }

    private boolean isPrimitive(Attribute attribute) {
        String attributeJavaClass = attribute.getJavaType().getSimpleName().toLowerCase();
        return attributeJavaClass.startsWith("int") ||
                attributeJavaClass.equals("boolean") ||
                attributeJavaClass.equals("string") ||
                attributeJavaClass.equals("float") ||
                attributeJavaClass.equals("double");
    }

//    private boolean isCollection(Attribute attribute) {
//        String attributeJavaClass = attribute.getJavaType().getSimpleName().toLowerCase();
//        List<String> allowdRefTypes = new ArrayList<>();
//        allowdRefTypes.add("set");
//        allowdRefTypes.add("list");
//        return allowdRefTypes.contains(attributeJavaClass.toLowerCase());
//    }

    private boolean isEnum(Attribute attribute) {
        String parentJavaClass = "";
        if (attribute.getJavaType().getSuperclass() != null) {
            parentJavaClass = attribute.getJavaType().getSuperclass().getSimpleName().toLowerCase();
        }
        return parentJavaClass.equals("enum");
    }

}