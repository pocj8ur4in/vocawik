package com.vocawik.module.security.autoconfigure;

import com.vocawik.module.security.error.ApiAccessDeniedHandler;
import com.vocawik.module.security.error.ApiAuthenticationEntryPoint;
import com.vocawik.module.security.guest.GuestAuthenticationFilter;
import com.vocawik.module.security.http.SecurityHttpProperties;
import com.vocawik.module.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Automatically configures the module's final, fail closed HTTP security catch all. */
@AutoConfiguration(
        before = SecurityAutoConfiguration.class,
        after = SecurityErrorAutoConfiguration.class)
@ConditionalOnClass({HttpServletRequest.class, HttpSecurity.class, SecurityFilterChain.class})
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityHttpProperties.class)
public class SecurityHttpAutoConfiguration {

    /** Creates the HTTP security automatic configuration. */
    public SecurityHttpAutoConfiguration() {}

    /**
     * Builds the final security filter chain with the module's API error handlers.
     *
     * @param http HTTP security builder
     * @param properties HTTP security properties
     * @param authenticationEntryPoint handler for authentication failures
     * @param accessDeniedHandler handler for authorization failures
     * @param guestAuthenticationFilter optional guest authentication filter
     * @param jwtAuthenticationFilter optional JWT authentication filter
     * @return configured security filter chain
     * @throws Exception if Spring Security fails to build the chain
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnProperty(
            prefix = "security.http",
            name = "default-chain-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityHttpProperties properties,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<GuestAuthenticationFilter> guestAuthenticationFilter,
            ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(
                        authorize -> {
                            String[] allows = properties.allows().toArray(String[]::new);
                            if (allows.length > 0) {
                                authorize.requestMatchers(allows).permitAll();
                            }
                            authorize.anyRequest().authenticated();
                        });

        guestAuthenticationFilter.ifAvailable(
                filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
        jwtAuthenticationFilter.ifAvailable(
                filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));

        return http.build();
    }

    /**
     * Fails startup when the module final chain is disabled without a consumer owned chain.
     *
     * <p>This guard is evaluated before Boot can contribute its default chain, so a framework
     * fallback cannot be mistaken for an explicit ownership transfer.
     *
     * @return post processor that rejects an incomplete ownership transfer
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "security.http",
            name = "default-chain-enabled",
            havingValue = "false")
    public static BeanFactoryPostProcessor missingConsumerSecurityFilterChainGuard() {
        return beanFactory -> {
            if (!hasApplicationSecurityFilterChain(beanFactory)) {
                throw new IllegalStateException(
                        "security.http.default-chain-enabled=false requires a consumer "
                                + "SecurityFilterChain bean");
            }
        };
    }

    /**
     * Checks whether an application owned security chain was registered before automatic
     * configuration.
     *
     * <p>Automatic configuration chains are infrastructure contributions and must not be treated as
     * an explicit transfer of security ownership. Bean definitions are inspected before any chain
     * is instantiated so this guard does not depend on framework fallback beans.
     *
     * @param beanFactory bean factory containing parsed application and automatic configuration
     *     beans
     * @return whether an application role {@link SecurityFilterChain} definition exists
     */
    private static boolean hasApplicationSecurityFilterChain(
            ConfigurableListableBeanFactory beanFactory) {
        for (String beanName :
                beanFactory.getBeanNamesForType(SecurityFilterChain.class, false, false)) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.getRole() == BeanDefinition.ROLE_APPLICATION
                    && !isAutoConfigurationBean(beanFactory, beanDefinition)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a bean definition is produced by an automatic configuration class.
     *
     * @param beanFactory bean factory containing the configuration bean
     * @param beanDefinition security chain bean definition
     * @return whether the chain is produced by an {@link AutoConfiguration} class
     */
    private static boolean isAutoConfigurationBean(
            ConfigurableListableBeanFactory beanFactory, BeanDefinition beanDefinition) {
        String factoryBeanName = beanDefinition.getFactoryBeanName();
        if (factoryBeanName == null) {
            return false;
        }

        Class<?> factoryType = beanFactory.getType(factoryBeanName, false);
        return factoryType != null
                && (AnnotationUtils.findAnnotation(factoryType, AutoConfiguration.class) != null
                        || factoryType
                                .getName()
                                .startsWith("org.springframework.boot.autoconfigure."));
    }
}
