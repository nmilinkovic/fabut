package eu.execom.testutil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.lang3.StringUtils;

import eu.execom.testutil.enums.CommentType;
import eu.execom.testutil.enums.NodeCheckType;
import eu.execom.testutil.enums.ReferenceCheckType;
import eu.execom.testutil.graph.NodesList;
import eu.execom.testutil.property.ChangedProperty;
import eu.execom.testutil.property.IMultiProperty;
import eu.execom.testutil.property.IProperty;
import eu.execom.testutil.property.ISingleProperty;
import eu.execom.testutil.property.IgnoreProperty;
import eu.execom.testutil.property.NotNullProperty;
import eu.execom.testutil.property.NullProperty;
import eu.execom.testutil.property.PropertyFactory;
import eu.execom.testutil.report.AssertReportBuilder;
import eu.execom.testutil.util.ConversionUtil;
import eu.execom.testutil.util.ReflectionUtil;

/**
 * ExeCom test util class. Should be used for asserting two entities or to assert single entity. TODO think of better
 * comment.
 * 
 * @param <EntityType>
 *            the generic type
 * @author Dusko Vesin
 * @author Nikola Olah
 * @author Bojan Babic
 * @author Nikola Trkulja
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class AbstractExecomAssert<EntityType> extends Assert implements EntityAssert<EntityType> {

    /** The Constant EMPTY_STRING. */
    protected static final String EMPTY_STRING = "";

    /** The Constant DOT. */
    private static final String DOT = ".";

    /** List of entity types that can be persisted. */
    protected List<Class<?>> entityTypes;
    /** List of types that are complex. */
    protected List<Class<?>> complexTypes;
    /** List of types that will be ignored during asserting. */
    protected List<Class<?>> ignoredTypes;

    /**
     * Instantiates a new abstract execom entity assert.
     */
    public AbstractExecomAssert() {
        super();
    }

    @Override
    public <X> void assertObjects(final List<X> expected, final X... actuals) {
        assertObjects(expected, ConversionUtil.createListFromArray(actuals));
    }

    @Override
    public <X> void assertObjects(final List<X> expected, final List<X> actual) {
        final AssertReportBuilder report = new AssertReportBuilder();
        if (!beforeListAssert(report, expected, actual)) {
            throw new AssertionError(report.getMessage());
        }
    }

    @Override
    public <X> void assertObjects(final X expected, final X actual, final IProperty... excludes) {
        assertObjects(EMPTY_STRING, expected, actual, excludes);
    }

    @Override
    public <X> void assertObjects(final String message, final X expected, final X actual, final IProperty... excludes) {
        assertObjects(message, expected, actual, ConversionUtil.createListFromArray(extractProperties(excludes)));
    }

    @Override
    public <X> void assertObjects(final String message, final X expected, final X actual,
            final List<ISingleProperty> excludedProperties) {
        final AssertReportBuilder report = new AssertReportBuilder(message);
        if (!(isSameInstance(expected, actual) || assertChangedProperty(EMPTY_STRING, report, expected, actual,
                excludedProperties, new NodesList(), false))) {
            throw new AssertionError(report.getMessage());
        }
        afterAssertObject(actual, false);
    }

    @Override
    public <X> void assertObject(final X actual, final IProperty... excludes) {
        assertObject(EMPTY_STRING, actual, excludes);
    }

    @Override
    public <X> void assertObject(final String message, final X actual, final IProperty... excludes) {
        final AssertReportBuilder report = new AssertReportBuilder(message);
        if (!preAssertObjectWithProperties(report, actual,
                ConversionUtil.createListFromArray(extractProperties(excludes)))) {
            throw new AssertionError(report.getMessage());
        }
        afterAssertObject(actual, false);
    }

    /**
     * Asserts two primitive types, if assert fails method must throw {@link AssertionError}.
     * 
     * @param <X>
     *            asserted object types
     * @param expected
     *            expected object
     * @param actual
     *            actual object
     */
    protected abstract <X> void customAssertEquals(X expected, X actual);

    /**
     * After method for entity assert.
     * 
     * @param <X>
     *            asserted object type
     * @param object
     *            asserted object.
     * @param isProperty
     *            <code>true</code> if entity is property of another object, <code>false</code> otherwise
     */
    protected <X> void afterAssertEntity(final X object, final boolean isProperty) {

    }

    /**
     * Init list of entity types.
     * 
     * @param entityTypes
     *            list of entity types
     */
    protected void setEntityTypes(final List<Class<?>> entityTypes) {
        // TODO(nolah) add null pointer checks
        this.entityTypes = entityTypes;
    }

    /**
     * Init list of complex types.
     * 
     * @param complexTypes
     *            list of complex types
     */
    protected void setComplexTypes(final List<Class<?>> complexTypes) {
        // TODO(nolah) add null pointer checks
        this.complexTypes = complexTypes;
    }

    /**
     * Init list of ignored types.
     * 
     * @param ignoredTypes
     *            list of ignored types
     */
    protected void setIgnoredTypes(final List<Class<?>> ignoredTypes) {
        // TODO(nolah) add null pointer checks
        this.ignoredTypes = ignoredTypes;
    }

    /**
     * Checks if list asserting can be performed and does asserting if it can be performed.
     * 
     * @param <X>
     *            asserted objects type
     * @param report
     *            assert report builder
     * @param expected
     *            list of expected values
     * @param actual
     *            - list of actual values
     * @return - <code>true</code> if both list are null or if lists succeed assert, <code>false</code> if only one of
     *         specified lists is null or list fail assert.
     */
    <X> boolean beforeListAssert(final AssertReportBuilder report, final List<X> expected, final List<X> actual) {
        final NodesList nodesList = new NodesList();

        final ReferenceCheckType referenceCheckType = referenceCheck(report, expected, actual, EMPTY_STRING);

        if (referenceCheckType != ReferenceCheckType.COMPLEX_ASSERT) {
            return referenceCheckType.getAssertResult();
        }

        nodesList.addPair(expected, actual);
        return assertList(EMPTY_STRING, report, expected, actual, new ArrayList<ISingleProperty>(), nodesList, false);
    }

    /**
     * Prepares object for asserting with specified list of properties. Checks if there is property for every field from
     * actual object, if so it does asserting, if not logs that information in report.
     * 
     * @param <X>
     *            asserted object type
     * @param report
     *            assert report builder
     * @param actual
     *            object to be asserted with specified properties
     * @param properties
     *            expected values for object fields
     * @return - <code>true</code> if and only if every field from actual object is assrted with its matching property,
     *         <code>false</code> otherwise.
     */
    <X> boolean preAssertObjectWithProperties(final AssertReportBuilder report, final X actual,
            final List<ISingleProperty> properties) {

        if (actual == null) {
            report.addNullReferenceAssertComment();
            return false;
        }

        final List<Method> methods = ReflectionUtil.getObjectGetMethods(actual, complexTypes, entityTypes);

        boolean assertResult = true;
        for (final Method method : methods) {
            final String fieldName = ReflectionUtil.getFieldName(method);
            final ISingleProperty property = getPropertyFromList(fieldName, properties);

            if (property == null) {
                // there is no matching property for field, log that in report,
                // assert fails
                report.addNoPropertyForFieldComment(fieldName, method, actual);
                assertResult = false;
            } else {
                // try to assert field and property
                try {
                    assertResult &= assertProperties(fieldName, report, property, method.invoke(actual), EMPTY_STRING,
                            properties, new NodesList(), true);
                } catch (final Exception e) {
                    report.reportUninvokableMethod(method.getName(), method.getDeclaringClass().getSimpleName(), actual
                            .getClass().getSimpleName());
                    assertResult = false;
                }
            }
        }
        return assertResult;
    }

    /**
     * Handles recurring objects in nodes list, disassembling object to its fields and asserting those field to matching
     * ones from expected object and logs the report. Returns value of assertion or if specified object pair
     * actual/expected is correctly recurring nodes list.
     * 
     * @param <X>
     *            asserted object types
     * @param propertyName
     *            name of field in parent object of type of actual object
     * @param report
     *            assert report builder
     * @param expected
     *            expected object
     * @param actual
     *            actual object
     * @param properties
     *            list of properties that exclude fields from expected object
     * @param nodesList
     *            list of object that had been asserted
     * @return <code>true</code> if actual and expected are null or fully asserted, <code>false</code> otherwise.
     */
    <X> boolean assertBySubproperty(final String propertyName, final AssertReportBuilder report, final X expected,
            final X actual, final List<ISingleProperty> properties, final NodesList nodesList) {

        final ReferenceCheckType referenceCheckType = referenceCheck(report, expected, actual, propertyName);
        if (referenceCheckType != ReferenceCheckType.COMPLEX_ASSERT) {
            return referenceCheckType.getAssertResult();
        }

        // check if any of the expected/actual object is recurring in nodes list
        final NodeCheckType nodeCheckType = nodesList.nodeCheck(expected, actual);
        if (nodeCheckType != NodeCheckType.NEW_PAIR) {
            report.reportPointsTo(propertyName, actual.getClass(), nodeCheckType.getAssertValue());
            return nodeCheckType.getAssertValue();
        }

        nodesList.addPair(expected, actual);
        final List<Method> getMethods = ReflectionUtil.getObjectGetMethods(expected, complexTypes, entityTypes);

        report.increaseDepth();
        boolean t = true;
        for (final Method expectedGetMethod : getMethods) {
            try {
                final String fieldName = ReflectionUtil.getFieldName(expectedGetMethod);
                final ISingleProperty property = obtainProperty(expectedGetMethod.invoke(expected), fieldName,
                        properties);
                final Method actualGetMethod = ReflectionUtil.getObjectGetMethodNamed(expectedGetMethod.getName(),
                        actual);
                // get actual field value by invoking its get method via
                // reflection
                t &= assertProperties(fieldName, report, property, actualGetMethod.invoke(actual), fieldName,
                        properties, nodesList, true);
            } catch (final Exception e) {
                report.reportUninvokableMethod(expectedGetMethod.getName(), expected.getClass().getSimpleName(), actual
                        .getClass().getSimpleName());
                t = false;
            }
        }
        report.decreaseDepth();
        return t;
    }

    /**
     * Handles asserting actual object by the specified expected property. Logs the result in the report and returns it.
     * 
     * @param <X>
     *            asserted object type
     * @param propertyName
     *            name of the current property
     * @param report
     *            assert report builder
     * @param expected
     *            property containing expected information
     * @param actual
     *            actual object
     * @param fieldName
     *            name of the field in parent actual object
     * @param properties
     *            list of properties that exclude fields from expected object
     * @param nodesList
     *            list of object that had been asserted
     * @param isProperty
     *            is actual property, important for entities
     * @return - <code>true</code> if object is asserted with expected property, <code>false</code> otherwise.
     */
    <X> boolean assertProperties(final String propertyName, final AssertReportBuilder report,
            final ISingleProperty expected, final X actual, final String fieldName,
            final List<ISingleProperty> properties, final NodesList nodesList, final boolean isProperty) {

        removeParentQualificationForProperties(fieldName, properties);

        // expected any not null value
        if (expected instanceof NotNullProperty) {
            final boolean ok = actual != null ? true : false;
            report.reportNotNullProperty(propertyName, ok);
            return ok;
        }

        // expected null value
        if (expected instanceof NullProperty) {
            final boolean ok = actual == null ? true : false;
            report.reportNullProperty(propertyName, ok);
            return ok;
        }

        // any value
        if (expected instanceof IgnoreProperty) {
            report.reportIgnoreProperty(propertyName);
            return true;
        }

        // assert by type
        if (expected instanceof ChangedProperty) {
            return assertChangedProperty(propertyName, report, ((ChangedProperty) expected).getExpectedValue(), actual,
                    properties, nodesList, isProperty);
        }

        throw new IllegalStateException();
    }

    /**
     * Handles asserting object by category of its type. Logs assertion result in report and returns it.
     * 
     * @param <X>
     *            asserted objects type
     * @param propertyName
     *            name of current property
     * @param report
     *            assert report builder
     * @param expected
     *            expected object
     * @param actual
     *            actual object
     * @param excludedProperties
     *            list of excluded properties
     * @param nodesList
     *            list of object that had been asserted
     * @param isProperty
     *            - is actual property, important for entities
     * @return <code>true</code> if actual object is asserted to expected object, <code>false</code> otherwise.
     */
    <X> boolean assertChangedProperty(final String propertyName, final AssertReportBuilder report, final X expected,
            final X actual, final List<ISingleProperty> excludedProperties, final NodesList nodesList,
            final boolean isProperty) {
        // assert ignored types
        if (ReflectionUtil.isIgnoredType(expected, actual, ignoredTypes)) {
            report.reportIgnoredType(expected, actual);
            return true;
        }

        // check if any of objects is null
        final ReferenceCheckType referenceCheckType = referenceCheck(report, expected, actual, propertyName);
        if (referenceCheckType != ReferenceCheckType.COMPLEX_ASSERT) {
            return referenceCheckType.getAssertResult();
        }

        // assert entities
        if (ReflectionUtil.isEntityType(expected.getClass(), entityTypes)) {
            if (isProperty) {
                return assertEntityById(report, propertyName, expected, actual);
            } else {
                return assertBySubproperty(propertyName, report, expected, actual, excludedProperties, nodesList);
            }
        }

        // assert lists
        if (ReflectionUtil.isListType(expected)) {
            return assertList(propertyName, report, (List) expected, (List) actual, excludedProperties, nodesList, true);
        }

        // assert complex type
        if (ReflectionUtil.isComplexType(expected.getClass(), complexTypes)) {
            return assertBySubproperty(propertyName, report, expected, actual, excludedProperties, nodesList);
        }
        // assert primitives
        return assertPrimitives(report, propertyName, expected, actual);

    }

    /**
     * Asserts two primitives using abstract method assertEqualsObjects, reports result and returns it. Primitives are
     * any class not marked as complex type, entity type or ignored type.
     * 
     * @param <X>
     *            the generic type
     * @param report
     *            assert report builder
     * @param propertyName
     *            name of the current property
     * @param expected
     *            expected object
     * @param actual
     *            actual object
     * @return - <code>true</code> if and only if objects are asserted, <code>false</code> if method assertEqualsObjects
     *         throws {@link AssertionError}.
     */
    <X> boolean assertPrimitives(final AssertReportBuilder report, final String propertyName, final X expected,
            final X actual) {

        try {
            customAssertEquals(expected, actual);
            report.addComment(propertyName, EMPTY_STRING, expected, actual, CommentType.SUCCESS);
            return true;
        } catch (final AssertionError e) {
            report.addComment(propertyName, EMPTY_STRING, expected, actual, CommentType.FAIL);
            return false;
        }
    }

    /**
     * Handles list asserting. It traverses trough the list by list index start from 0 and going up to list size and
     * asserts every two elements on matching index. Lists cannot be asserted if their sizes are different.
     * 
     * @param <X>
     *            object type
     * @param propertyName
     *            name of current property
     * @param report
     *            assert report builder
     * @param expected
     *            expected list
     * @param actual
     *            actual list
     * @param properties
     *            list of excluded properties
     * @param nodesList
     *            list of object that had been asserted
     * @param isProperty
     *            is it parent object or its member
     * @return - <code>true</code> if every element from expected list with index <em>i</em> is asserted with element
     *         from actual list with index <em>i</em>, <code>false</code> otherwise.
     */
    <X> boolean assertList(final String propertyName, final AssertReportBuilder report, final List<X> expected,
            final List<X> actual, final List<ISingleProperty> properties, final NodesList nodesList,
            final boolean isProperty) {

        // check sizes
        final int expectedSize = expected.size();
        final int actualSize = actual.size();
        if (expectedSize != actualSize) {
            report.addListDifferentSizeComment(propertyName, expectedSize, actualSize);
            return false;
        }

        // assert every element by index
        report.increaseDepth();

        boolean assertResult = true;
        for (int i = 0; i < actual.size(); i++) {
            report.reportAssertingListElement(propertyName, i);
            final X expectedElement = expected.get(i);
            final X actualElement = actual.get(i);
            final ISingleProperty property = obtainProperty(expectedElement, EMPTY_STRING, properties);
            assertResult &= assertProperties(EMPTY_STRING, report, property, actualElement, EMPTY_STRING, properties,
                    nodesList, false);
            afterAssertObject(actual, isProperty);
        }
        report.decreaseDepth();
        return assertResult;
    }

    /**
     * Asserts two entities by their id.
     * 
     * @param <X>
     *            entity type
     * @param <Id>
     *            entities id type
     * @param report
     *            assert report builder
     * @param propertyName
     *            name of current entity
     * @param expected
     *            expected entity
     * @param actual
     *            actual entity
     * @return - <code>true</code> if and only if ids of two specified objects are equal, <code>false</code> otherwise
     */
    <X, Id> boolean assertEntityById(final AssertReportBuilder report, final String propertyName, final X expected,
            final X actual) {

        final Id expectedId = ReflectionUtil.getIdValue(expected);
        final Id actualId = ReflectionUtil.getIdValue(actual);

        boolean ok = true;
        try {
            assertEquals(expectedId, actualId);
            ok = true;
        } catch (final AssertionError e) {
            ok = false;
        }
        report.reportEntityAssert(expectedId, actualId, ok);
        return ok;
    }

    /**
     * Cuts off parent property name from start of property path.
     * 
     * @param parentPropertyName
     *            parent name
     * @param properties
     *            list of excluded properties
     * @return List of properties without specified parent property name
     */
    List<ISingleProperty> removeParentQualificationForProperties(final String parentPropertyName,
            final List<ISingleProperty> properties) {

        final String parentPrefix = parentPropertyName + DOT;
        for (final ISingleProperty property : properties) {
            final String path = StringUtils.removeStart(property.getPath(), parentPrefix);
            property.setPath(path);
        }
        return properties;
    }

    /**
     * Obtains property by following rules: if there is {@link ISingleProperty} in the list of properties matching path
     * with fieldName, it removes it from the list and returns it. Otherwise, it makes new {@link ChangedProperty} with
     * fieldName as path and value of field.
     * 
     * @param <X>
     *            field type
     * @param field
     *            expected value for {@link ChangedProperty}
     * @param propertyPath
     *            path for property
     * @param properties
     *            list of excluded properties
     * @return generated property
     */
    <X> ISingleProperty obtainProperty(final X field, final String propertyPath, final List<ISingleProperty> properties) {
        final ISingleProperty property = getPropertyFromList(propertyPath, properties);
        if (property != null) {
            return property;
        }
        return PropertyFactory.changed(propertyPath, field);
    }

    /**
     * Searches for property with the specified path in the list of properties, removes it from the list and returns it.
     * 
     * @param propertyPath
     *            property path
     * @param properties
     *            list of properties
     * @return {@link ISingleProperty} if there is property with same path as specified in list of properties,
     *         <code>null</code> otherwise
     */
    ISingleProperty getPropertyFromList(final String propertyPath, final List<ISingleProperty> properties) {

        final Iterator<ISingleProperty> iterator = properties.iterator();
        while (iterator.hasNext()) {
            final ISingleProperty property = iterator.next();
            if (property.getPath().equalsIgnoreCase(propertyPath)) {
                iterator.remove();
                return property;
            }
        }
        return null;
    }

    /**
     * For two specified objects checks references and returns appropriate value.
     * 
     * @param <X>
     *            the generic type
     * @param report
     *            builder
     * @param expected
     *            object
     * @param actual
     *            object
     * @param propertyName
     *            name of the property
     * @return {@link ReferenceCheckType#EQUAL_REFERENCE} is expected and actual have same reference, if and only if one
     *         of them is null return {@link ReferenceCheckType#EXCLUSIVE_NULL}
     */
    <X> ReferenceCheckType referenceCheck(final AssertReportBuilder report, final X expected, final X actual,
            final String propertyName) {

        if (expected == actual) {
            report.addComment(propertyName, EMPTY_STRING, expected, actual, CommentType.SUCCESS);
            return ReferenceCheckType.EQUAL_REFERENCE;
        }

        if (expected == null ^ actual == null) {
            report.addComment(propertyName, EMPTY_STRING, expected, actual, CommentType.FAIL);
            return ReferenceCheckType.EXCLUSIVE_NULL;
        }
        return ReferenceCheckType.COMPLEX_ASSERT;
    }

    /**
     * Check is object of entity type and if it is mark it as asserted entity, in other case do nothing.
     * 
     * @param <X>
     *            the generic type
     * @param object
     *            the object
     * @param isSubproperty
     *            is object subproperty
     */
    <X> void afterAssertObject(final X object, final boolean isSubproperty) {
        if (ReflectionUtil.isEntityType(object.getClass(), entityTypes)) {
            afterAssertEntity(object, isSubproperty);
        }
    }

    /**
     * Checks if is same instance.
     * 
     * @param <X>
     *            the generic type
     * @param expected
     *            the expected
     * @param actual
     *            the actual
     * @return <code>true</code> if expected is same instance as actual, <code>false</code> otherwise.
     */
    <X> boolean isSameInstance(final X expected, final X actual) {
        return expected == actual;
    }

    /**
     * Extract properties and merge them into an array.
     * 
     * @param properties
     *            array/arrays of properties
     */
    ISingleProperty[] extractProperties(final IProperty... properties) {
        final ArrayList<ISingleProperty> list = new ArrayList<ISingleProperty>();

        for (final IProperty property : properties) {
            if (property instanceof ISingleProperty) {
                list.add((ISingleProperty) property);
            } else {
                list.addAll(((IMultiProperty) property).getProperties());
            }
        }

        final ISingleProperty[] array = new ISingleProperty[list.size()];
        return list.toArray(array);
    }

}
