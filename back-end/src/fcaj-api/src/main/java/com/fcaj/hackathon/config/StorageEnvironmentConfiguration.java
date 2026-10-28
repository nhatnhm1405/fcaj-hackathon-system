package com.fcaj.hackathon.config;

import com.fcaj.hackathon.storage.ObjectKeys;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class StorageEnvironmentConfiguration {
    @Bean static BeanFactoryPostProcessor validateStoragePolicy(Environment env) {
        validate(env);return beans -> {};
    }
    static void validate(Environment env) {
        String provider=env.getProperty("app.storage.provider","local");
        if("local".equals(provider)) return;
        if(!"s3".equals(provider)) throw new IllegalStateException("Storage provider must be local or s3");
        if(!env.getProperty("app.aws.region","").matches("[a-z]{2}(?:-[a-z]+)+-[0-9]+")
                || !env.getProperty("app.storage.bucket","").matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalStateException("S3 storage requires a region and private bucket");
        String prefix=env.getProperty("app.storage.prefix","");
        if(prefix.isBlank()) throw new IllegalStateException("S3 storage requires an isolated object prefix");
        ObjectKeys.valid(prefix);
    }
}
