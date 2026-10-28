package com.fcaj.hackathon.config;

import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentProfilesTest {

    @Test
    void testProfileIsIsolatedFromLocalSecretsAndNetworkProviders() {
        runner("test", Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:");
            assertThat(environment.getProperty("app.oauth2.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("app.bootstrap.admin.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id")).isNull();
            assertThat(environment.getProperty("spring.config.import")).isNull();
            assertThat(StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                    .map(source -> source.getName())).noneMatch(name -> name.contains(".env"));
        });
    }

    @Test
    void awsReadsStandardRuntimeVariablesWithoutLocalFallbacks() {
        runner("aws", Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY",
                "SPRING_DATASOURCE_USERNAME", "runtime-user",
                "SPRING_DATASOURCE_PASSWORD", "runtime-password",
                "DB_USERNAME", "legacy-user",
                "DB_PASSWORD", "legacy-password",
                "JWT_SECRET", "runtime-signing-key",
                "APP_FRONTEND_URL", "https://app.example.test",
                "DDL_AUTO", "validate"))
                .withUserConfiguration(AwsEnvironmentConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("runtime-user");
                    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("runtime-password");
                    assertThat(environment.getProperty("app.jwt.secret")).isEqualTo("runtime-signing-key");
                    assertThat(environment.getProperty("app.cors.allowed-origins")).isEqualTo("https://app.example.test");
                    assertThat(environment.getProperty("app.jwt.cookie.secure")).isEqualTo("true");
                    assertThat(environment.getProperty("spring.jpa.show-sql")).isEqualTo("false");
                    assertThat(environment.getProperty("spring.config.import")).isNull();
                    assertThat(environment.getProperty("app.oauth2.enabled")).isEqualTo("false");
                    assertThat(environment.getProperty("app.seed.scenario")).isEqualTo("NONE");
                });
    }

    @Test
    void awsDoesNotInheritLocalDatasourceOrSigningSecret() {
        runner("aws", Map.of()).run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.url")).isEmpty();
            assertThat(environment.getProperty("spring.datasource.username")).isEmpty();
            assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
            assertThat(environment.getProperty("app.jwt.secret")).isEmpty();
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
        });
    }

    @Test
    void googleProfileDoesNotRequireOrConfigureGithub() {
        runner("aws,oauth-google", Map.of(
                "GOOGLE_CLIENT_ID", "test-google-client",
                "GOOGLE_CLIENT_SECRET", "test-google-secret"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("app.oauth2.enabled")).isEqualTo("true");
                    assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                            .isEqualTo("test-google-client");
                    assertThat(environment.getProperty("spring.security.oauth2.client.registration.github.client-id")).isNull();
                });
    }

    @Test
    void githubProfileDoesNotRequireOrConfigureGoogle() {
        runner("aws,oauth-github", Map.of(
                "GITHUB_CLIENT_ID", "test-github-client",
                "GITHUB_CLIENT_SECRET", "test-github-secret"))
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("app.oauth2.enabled")).isEqualTo("true");
                    assertThat(environment.getProperty("spring.security.oauth2.client.registration.github.client-id"))
                            .isEqualTo("test-github-client");
                    assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id")).isNull();
                });
    }

    private ApplicationContextRunner runner(String profiles, Map<String, Object> runtimeVariables) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // Isolate these tests from the developer/CI process environment.
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.addFirst(new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, runtimeVariables));
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profiles,
                        "spring.config.location=classpath:/application.properties");
    }
}
