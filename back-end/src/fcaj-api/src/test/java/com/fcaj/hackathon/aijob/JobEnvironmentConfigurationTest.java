package com.fcaj.hackathon.aijob;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JobEnvironmentConfigurationTest {
    private MockEnvironment valid() {
        var env=new MockEnvironment().withProperty("app.aws.region","ap-southeast-1")
                .withProperty("app.ai.jobs.jobs-queue-url","https://sqs.ap-southeast-1.amazonaws.com/123456789012/be1-jobs")
                .withProperty("app.ai.jobs.results-queue-url","https://sqs.ap-southeast-1.amazonaws.com/123456789012/be1-results")
                .withProperty("app.storage.provider","s3").withProperty("spring.flyway.enabled","true")
                .withProperty("spring.jpa.hibernate.ddl-auto","validate");env.setActiveProfiles("aws");return env;
    }
    @Test void completeConfigurationIsAcceptedWithoutNetworkAccess() { assertThatCode(() -> JobEnvironmentConfiguration.validate(valid())).doesNotThrowAnyException(); }
    @Test void sameQueueWrongRegionAndArbitraryEndpointsAreRejected() {
        for(String url:java.util.List.of("http://localhost:4566/queue","https://sqs.us-east-1.amazonaws.com/123456789012/be1-results",
                "https://sqs.ap-southeast-1.amazonaws.com/123456789012/be1-jobs"))
            assertThatThrownBy(() -> JobEnvironmentConfiguration.validate(valid().withProperty("app.ai.jobs.results-queue-url",url))).isInstanceOf(IllegalStateException.class);
    }
    @Test void realJobsRequireMigratedDatabaseAndS3() {
        assertThatThrownBy(() -> JobEnvironmentConfiguration.validate(valid().withProperty("spring.flyway.enabled","false"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JobEnvironmentConfiguration.validate(valid().withProperty("app.storage.provider","local"))).isInstanceOf(IllegalStateException.class);
        var env=valid();env.setActiveProfiles("local","mock-demo");assertThatThrownBy(() -> JobEnvironmentConfiguration.validate(env)).hasMessageContaining("mock-demo");
    }
}
