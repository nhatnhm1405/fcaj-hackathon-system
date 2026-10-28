package com.fcaj.hackathon.mockdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class MockDemoConfigurationTest {
    private ApplicationContextRunner runner(String profiles) {
        return new ApplicationContextRunner().withUserConfiguration(MockDemoConfiguration.class)
                .withPropertyValues("spring.profiles.active="+profiles,"server.address=127.0.0.1",
                        "app.ai.jobs.mock-store-url=jdbc:h2:file:./target/mock-demo/ai-jobs;DB_CLOSE_ON_EXIT=FALSE");
    }
    @Test void acceptsOnlyExplicitLocalMockMode() {
        runner("local,mock-demo").run(context->assertThat(context).hasNotFailed());
    }
    @ParameterizedTest @ValueSource(strings={"aws,mock-demo","mock-demo","aws,local,mock-demo"})
    void rejectsNonlocalModesBeforeConnecting(String profiles) {
        runner(profiles).run(context->assertThat(context).hasFailed());
    }
    @Test void rejectsPublicBindAndUntrustedStoreUrl() {
        runner("local,mock-demo").withPropertyValues("server.address=0.0.0.0")
                .run(context->assertThat(context).hasFailed());
        runner("local,mock-demo").withPropertyValues("app.ai.jobs.mock-store-url=jdbc:h2:tcp://remote/jobs")
                .run(context->assertThat(context).hasFailed());
    }
    @Test void acceptsMemoryStoreOnlyInTests() {
        runner("test,mock-demo").withPropertyValues("app.ai.jobs.mock-store-url=jdbc:h2:mem:mock_config;DB_CLOSE_DELAY=-1")
                .run(context->assertThat(context).hasNotFailed());
        runner("local,mock-demo").withPropertyValues("app.ai.jobs.mock-store-url=jdbc:h2:mem:mock_config;DB_CLOSE_DELAY=-1")
                .run(context->assertThat(context).hasFailed());
    }
}
