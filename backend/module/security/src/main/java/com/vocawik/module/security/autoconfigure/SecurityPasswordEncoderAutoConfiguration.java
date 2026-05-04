package com.vocawik.module.security.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Automatically configures the default password encoder. */
@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
public class SecurityPasswordEncoderAutoConfiguration {

    /** Creates the password encoder automatic configuration. */
    public SecurityPasswordEncoderAutoConfiguration() {}

    /**
     * Creates the module default password encoder.
     *
     * @return BCrypt password encoder
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
