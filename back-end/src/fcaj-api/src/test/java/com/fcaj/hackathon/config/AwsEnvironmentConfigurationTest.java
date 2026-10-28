package com.fcaj.hackathon.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsEnvironmentConfigurationTest {

    @Test
    void acceptsAwsConfigWithoutDisabledFeatureCredentials() {
        assertThatCode(() -> AwsEnvironmentConfiguration.validate(validEnvironment())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "app.jwt.secret", "app.frontend.url", "spring.jpa.hibernate.ddl-auto", "app.cors.allowed-origins"})
    void requiresRuntimeConfig(String key) {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment().withProperty(key, "")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void rejectsMixedEnvironmentProfiles(String profile) {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("aws", profile);
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(environment))
                .hasMessageContaining("must not combine aws");
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "create-drop", "update", "none", "invalid"})
    void rejectsUnapprovedSchemaPolicy(String ddl) {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(
                validEnvironment().withProperty("spring.jpa.hibernate.ddl-auto", ddl)))
                .hasMessageContaining("spring.jpa.hibernate.ddl-auto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://app.example.test", "https://app.example.test/path", "https://app.example.test/",
            "https://app.example.test?secret=do-not-print", "https://user:do-not-print@app.example.test", "*"})
    void rejectsUnsafeFrontendOriginWithoutEchoingInput(String origin) {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(
                validEnvironment().withProperty("app.frontend.url", origin)))
                .hasMessageContaining("app.frontend.url")
                .hasMessageNotContaining(origin).hasMessageNotContaining("do-not-print").hasNoCause();
    }

    @Test
    void validatesEveryCorsOrigin() {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("app.cors.allowed-origins", "https://app.example.test,http://localhost:5173")))
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.jwt.cookie.secure", "server.servlet.session.cookie.secure"})
    void rejectsInsecureCookies(String key) {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment().withProperty(key, "false")))
                .hasMessageContaining(key);
    }

    @Test
    void requiresVerifiedDatabaseTls() {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.test:3306/fcaj_poc?useSSL=false")))
                .hasMessageContaining("sslMode=VERIFY_IDENTITY");
    }

    @Test
    void rejectsDatabaseCredentialsInUrlWithoutEchoingThem() {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY&password=do-not-print")))
                .hasMessageContaining("must not embed credentials").hasMessageNotContaining("do-not-print").hasNoCause();
    }

    @Test
    void rejectsAutomaticDatabaseCreation() {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY&createDatabaseIfNotExist=true")))
                .hasMessageContaining("provisioned before");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sslMode=DISABLED", "%73slMode=DISABLED"})
    void rejectsTlsParameterAmbiguity(String extraParameter) {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY&" + extraParameter)))
                .hasMessageContaining("must not repeat");
    }

    @Test
    void requiresCredentialsOnlyForEnabledOauthProvider() {
        MockEnvironment environment = validEnvironment().withProperty("app.oauth2.enabled", "true");
        environment.setActiveProfiles("aws", "oauth-google");
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(environment))
                .hasMessageContaining("registration.google.client-id");
        environment.withProperty("spring.security.oauth2.client.registration.google.client-id", "test-client")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret", "test-client-secret");
        assertThatCode(() -> AwsEnvironmentConfiguration.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCsrfCookieDomainOutsideFrontend() {
        assertThatThrownBy(() -> AwsEnvironmentConfiguration.validate(validEnvironment()
                .withProperty("app.csrf.cookie.domain", "elsewhere.test")))
                .hasMessageContaining("app.csrf.cookie.domain");
    }

    @Test
    void failsBeforeRuntimeBeansCanConnectOrSeed() {
        AtomicBoolean runtimeBeanCreated = new AtomicBoolean();
        new ApplicationContextRunner()
                .withUserConfiguration(AwsEnvironmentConfiguration.class)
                .withPropertyValues("spring.profiles.active=aws")
                .withBean("runtimeClient", Object.class, () -> {
                    runtimeBeanCreated.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(runtimeBeanCreated).isFalse();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "AWS configuration: spring.datasource.url is required for the aws profile.");
                });
    }

    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.test:3306/fcaj_poc?sslMode=VERIFY_IDENTITY&serverTimezone=UTC")
                .withProperty("spring.datasource.username", "test-app")
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("app.jwt.secret", "test-signing-secret")
                .withProperty("app.frontend.url", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.jwt.cookie.secure", "true")
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("app.csrf.cookie.domain", "example.test")
                .withProperty("app.oauth2.enabled", "false");
        environment.setActiveProfiles("aws");
        return environment;
    }
}
