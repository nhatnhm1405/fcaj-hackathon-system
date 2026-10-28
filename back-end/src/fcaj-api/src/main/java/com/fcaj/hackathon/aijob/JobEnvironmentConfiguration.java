package com.fcaj.hackathon.aijob;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class JobEnvironmentConfiguration {
    @Bean static BeanFactoryPostProcessor validateJobEnvironment(Environment env) {
        validate(env); return beans -> {};
    }
    static void validate(Environment env) {
        var profiles=Arrays.asList(env.getActiveProfiles());
        if(profiles.contains("mock-demo")) throw new IllegalStateException("Business AI jobs cannot be combined with mock-demo");
        String region=env.getProperty("app.aws.region","");
        if(!region.matches("[a-z]{2}(?:-[a-z]+)+-[0-9]+")) throw new IllegalStateException("AI jobs require app.aws.region");
        for(String name:java.util.List.of("jobs","results")) {
            try {
                URI uri=URI.create(env.getProperty("app.ai.jobs."+name+"-queue-url",""));
                if(!"https".equals(uri.getScheme()) || !("sqs."+region+".amazonaws.com").equals(uri.getHost())
                        || uri.getPort()!=-1 || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                        || !uri.getPath().matches("/[0-9]{12}/[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException();
            } catch(IllegalArgumentException invalid) { throw new IllegalStateException("AI jobs require a standard regional "+name+" SQS queue URL"); }
        }
        if(env.getProperty("app.ai.jobs.jobs-queue-url").equals(env.getProperty("app.ai.jobs.results-queue-url")))
            throw new IllegalStateException("Jobs and results must use different queues");
        if(!profiles.contains("test")) {
            if(!env.getProperty("spring.flyway.enabled",Boolean.class,false)
                    || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
                throw new IllegalStateException("Business AI jobs require Flyway and Hibernate validate");
            if(!"s3".equals(env.getProperty("app.storage.provider")))
                throw new IllegalStateException("External AI workers require S3 storage");
        }
    }
}
