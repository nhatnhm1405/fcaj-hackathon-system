package com.fcaj.hackathon.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class MigrationEnvironmentConfiguration {
    @Bean static BeanFactoryPostProcessor validateMigrationPolicy(Environment env) {
        validate(env);return beans -> {};
    }
    static void validate(Environment env) {
        if(!env.getProperty("spring.flyway.enabled",Boolean.class,false)) return;
        if(env.getProperty("spring.flyway.baseline-on-migrate",Boolean.class,false)
                || !env.getProperty("spring.flyway.clean-disabled",Boolean.class,true)
                || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
            throw new IllegalStateException("Migrations require explicit baselining, clean-disabled=true and Hibernate validate");
    }
}
