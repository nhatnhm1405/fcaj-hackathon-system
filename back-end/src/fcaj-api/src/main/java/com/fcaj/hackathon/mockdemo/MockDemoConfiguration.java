package com.fcaj.hackathon.mockdemo;

import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("mock-demo")
public class MockDemoConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateMockDemo(Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("aws") || !(profiles.contains("local") || profiles.contains("test"))) {
            throw new IllegalStateException("mock-demo requires local or test and cannot run with aws");
        }
        if (!"127.0.0.1".equals(environment.getProperty("server.address"))) {
            throw new IllegalStateException("mock-demo must bind server.address=127.0.0.1");
        }
        String url = environment.getProperty("app.ai.jobs.mock-store-url", "");
        boolean file = "jdbc:h2:file:./target/mock-demo/ai-jobs;DB_CLOSE_ON_EXIT=FALSE".equals(url);
        boolean test = profiles.contains("test") && url.matches("jdbc:h2:mem:mock_[a-zA-Z0-9_]+;DB_CLOSE_DELAY=-1");
        if (!file && !test) {
            throw new IllegalStateException("mock-demo requires its isolated target H2 store (or a mock_* test database)");
        }
        return beanFactory -> { };
    }
}
