package com.vocawik.module.persistence.autoconfigure;

import java.util.List;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Validates and identifies auditing infrastructure owned by a persistence consumer. */
final class PersistenceAuditingInfrastructureValidator {

    private static final String HANDLER_BEAN_NAME = "jpaAuditingHandler";
    private static final String LISTENER_BEAN_NAME = AuditingEntityListener.class.getName();
    private static final String POST_PROCESSOR_BEAN_NAME =
            AuditingBeanFactoryPostProcessor.class.getName();
    private static final String ASPECT_BEAN_NAME =
            "org.springframework.context.config.internalBeanConfigurerAspect";
    private static final String MAPPING_CONTEXT_BEAN_NAME = "jpaMappingContext";
    private static final String ASPECT_CLASS_NAME =
            "org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect";

    /** Prevents construction because auditing infrastructure validation has no instance state. */
    private PersistenceAuditingInfrastructureValidator() {}

    /**
     * Determines whether a consumer has registered any marker for owning JPA auditing.
     *
     * @param registry bean definition registry
     * @return whether a consumer owned auditing marker exists
     */
    static boolean hasConsumerOwnedInfrastructure(BeanDefinitionRegistry registry) {
        return ownershipMarkers().stream().anyMatch(registry::containsBeanDefinition);
    }

    /**
     * Determines whether the assembled bean factory contains any marker that reserves JPA auditing
     * ownership for the consumer.
     *
     * @param beanFactory bean factory containing the assembled application definitions
     * @return whether a consumer owned auditing marker exists
     */
    static boolean hasConsumerOwnedInfrastructure(ConfigurableListableBeanFactory beanFactory) {
        return ownershipMarkers().stream().anyMatch(beanFactory::containsBeanDefinition);
    }

    /**
     * Validates all auditing infrastructure definitions after user and module configuration are
     * parsed.
     *
     * @param beanFactory bean factory containing the assembled auditing definitions
     * @throws IllegalStateException if consumer owned auditing is incomplete, a required auditing
     *     type is unavailable, or a registered infrastructure type is incompatible
     */
    static void validate(ConfigurableListableBeanFactory beanFactory) {
        if (!hasConsumerOwnedInfrastructure(beanFactory)) {
            return;
        }

        requireBeanType(beanFactory, HANDLER_BEAN_NAME, AuditingHandler.class);
        requireBeanType(beanFactory, LISTENER_BEAN_NAME, AuditingEntityListener.class);
        requireBeanType(
                beanFactory, POST_PROCESSOR_BEAN_NAME, AuditingBeanFactoryPostProcessor.class);
        requireBeanTypeByName(beanFactory, ASPECT_BEAN_NAME, ASPECT_CLASS_NAME);
        requireBeanTypeByName(
                beanFactory,
                MAPPING_CONTEXT_BEAN_NAME,
                "org.springframework.data.jpa.mapping.JpaMetamodelMappingContext");
    }

    /**
     * Returns bean names whose presence signals that the consumer claims auditing ownership.
     * Complete and compatible infrastructure is verified separately by {@link
     * #validate(ConfigurableListableBeanFactory)}.
     *
     * @return immutable auditing ownership marker names
     */
    private static List<String> ownershipMarkers() {
        return List.of(HANDLER_BEAN_NAME, LISTENER_BEAN_NAME, POST_PROCESSOR_BEAN_NAME);
    }

    /**
     * Requires a named auditing bean definition with the expected runtime type.
     *
     * @param beanFactory bean factory under validation
     * @param beanName expected bean name
     * @param expectedType expected assignable type
     * @throws IllegalStateException if the definition is absent or its runtime type is not
     *     assignable to the expected type
     */
    private static void requireBeanType(
            ConfigurableListableBeanFactory beanFactory, String beanName, Class<?> expectedType) {
        requireBeanDefinition(beanFactory, beanName);
        Class<?> actualType = beanFactory.getType(beanName, false);
        if (actualType == null || !expectedType.isAssignableFrom(actualType)) {
            throw new IllegalStateException(
                    "Auditing bean '"
                            + beanName
                            + "' must be assignable to "
                            + expectedType.getName());
        }
    }

    /**
     * Requires a named auditing infrastructure definition whose expected type is resolved by class
     * name.
     *
     * @param beanFactory bean factory under validation
     * @param beanName expected bean name
     * @param expectedTypeName expected assignable type name
     * @throws IllegalStateException if the definition is absent, the expected type is unavailable,
     *     or the runtime type is not assignable to it
     */
    private static void requireBeanTypeByName(
            ConfigurableListableBeanFactory beanFactory, String beanName, String expectedTypeName) {
        requireBeanDefinition(beanFactory, beanName);
        Class<?> actualType = beanFactory.getType(beanName, false);
        if (actualType == null || !isAssignableTo(expectedTypeName, actualType, beanFactory)) {
            throw new IllegalStateException(
                    "Auditing bean '" + beanName + "' must be assignable to " + expectedTypeName);
        }
    }

    /**
     * Checks assignability without introducing an optional AspectJ type as a production dependency.
     *
     * @param expectedTypeName expected type name
     * @param actualType registered bean type
     * @param beanFactory bean factory supplying the application class loader
     * @return whether the registered type is assignable to the expected type
     * @throws IllegalStateException if the expected auditing type is unavailable to the application
     *     class loader
     */
    private static boolean isAssignableTo(
            String expectedTypeName,
            Class<?> actualType,
            ConfigurableListableBeanFactory beanFactory) {
        try {
            ClassLoader classLoader = beanFactory.getBeanClassLoader();
            Class<?> expectedType = Class.forName(expectedTypeName, false, classLoader);
            return expectedType.isAssignableFrom(actualType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Required JPA auditing type is not available: " + expectedTypeName, exception);
        }
    }

    /**
     * Requires a named auditing infrastructure definition.
     *
     * @param beanFactory bean factory under validation
     * @param beanName expected bean name
     * @throws IllegalStateException if the required definition is absent
     */
    private static void requireBeanDefinition(
            ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            throw new IllegalStateException(
                    "Consumer owned JPA auditing infrastructure is incomplete; missing bean '"
                            + beanName
                            + "'");
        }
    }
}
