package com.fcaj.hackathon.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Validates runtime settings before datasource, seeders or clients are instantiated. */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
public class AwsEnvironmentConfiguration {

    @Bean
    static BeanFactoryPostProcessor validateAwsEnvironment(Environment environment) {
        validate(environment);
        return beanFactory -> { };
    }

    static void validate(Environment environment) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("local") || profiles.contains("test")) {
            throw invalid("spring.profiles.active", "must not combine aws with local or test");
        }

        String jdbcUrl = required(environment, "spring.datasource.url");
        validateJdbcUrl(jdbcUrl);
        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        required(environment, "app.jwt.secret");
        String ddl = required(environment, "spring.jpa.hibernate.ddl-auto");
        if (!"validate".equals(ddl)) {
            throw invalid("spring.jpa.hibernate.ddl-auto", "must be validate; Flyway owns schema changes");
        }

        URI frontend = httpsOrigin(required(environment, "app.frontend.url"), "app.frontend.url");
        String origins = required(environment, "app.cors.allowed-origins");
        for (String origin : origins.split(",", -1)) {
            httpsOrigin(origin.trim(), "app.cors.allowed-origins");
        }
        requireTrue(environment, "app.jwt.cookie.secure");
        requireTrue(environment, "server.servlet.session.cookie.secure");
        String csrfDomain = value(environment, "app.csrf.cookie.domain").trim();
        if (!csrfDomain.isEmpty()) {
            String domain = csrfDomain.startsWith(".") ? csrfDomain.substring(1) : csrfDomain;
            if (!domain.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                    || domain.contains("..")
                    || !(frontend.getHost().equalsIgnoreCase(domain)
                    || frontend.getHost().toLowerCase(Locale.ROOT).endsWith("." + domain.toLowerCase(Locale.ROOT)))) {
                throw invalid("app.csrf.cookie.domain", "must be a domain matching the frontend host, without a scheme or path");
            }
        }

        boolean oauthEnabled = "true".equalsIgnoreCase(value(environment, "app.oauth2.enabled"));
        if (oauthEnabled) {
            boolean hasProvider = false;
            for (String provider : List.of("google", "github")) {
                if (profiles.contains("oauth-" + provider)) {
                    hasProvider = true;
                    String prefix = "spring.security.oauth2.client.registration." + provider;
                    required(environment, prefix + ".client-id");
                    required(environment, prefix + ".client-secret");
                }
            }
            if (!hasProvider) {
                throw invalid("app.oauth2.enabled", "requires an oauth-google or oauth-github profile");
            }
        }
    }

    private static void validateJdbcUrl(String value) {
        String key = "spring.datasource.url";
        if (!value.startsWith("jdbc:mysql://")) {
            throw invalid(key, "must be a MySQL JDBC URL with an existing database");
        }
        try {
            URI uri = new URI(value.substring("jdbc:".length()));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPath() == null || uri.getPath().length() < 2) {
                throw invalid(key, "must specify a host/database without embedded credentials");
            }
            boolean verifiedTls = false;
            Set<String> seenParameters = new java.util.HashSet<>();
            for (String part : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
                if (part.isBlank()) {
                    continue;
                }
                String[] pair = part.split("=", 2);
                String decodedName = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String name = decodedName.toLowerCase(Locale.ROOT);
                String parameterValue = pair.length == 2
                        ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                if (!seenParameters.add(name)) {
                    throw invalid(key, "must not repeat connection parameters");
                }
                if (Set.of("user", "password", "password1", "password2", "password3").contains(name)) {
                    throw invalid(key, "must not embed credentials; use datasource username/password settings");
                }
                if ("sslmode".equals(name)) {
                    verifiedTls = "sslMode".equals(decodedName) && "VERIFY_IDENTITY".equals(parameterValue);
                }
                if ("createdatabaseifnotexist".equals(name) && Boolean.parseBoolean(parameterValue)) {
                    throw invalid(key, "must use a database provisioned before application startup");
                }
            }
            if (!verifiedTls) {
                throw invalid(key, "must include sslMode=VERIFY_IDENTITY and a trusted server CA");
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            // Do not attach the exception: it contains the original URL.
            throw invalid(key, "must be a valid MySQL JDBC URL");
        }
    }

    private static URI httpsOrigin(String value, String key) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw invalid(key, "must contain only an absolute HTTPS origin (scheme, host and optional port)");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(key, "must contain a valid absolute HTTPS origin");
        }
    }

    private static void requireTrue(Environment environment, String key) {
        if (!"true".equalsIgnoreCase(required(environment, key))) {
            throw invalid(key, "must be true for HTTPS");
        }
    }

    private static String required(Environment environment, String key) {
        String value = value(environment, key);
        if (value.isBlank()) {
            throw invalid(key, "is required for the aws profile");
        }
        return value;
    }

    private static String value(Environment environment, String key) {
        try {
            return environment.getProperty(key, "");
        } catch (IllegalArgumentException exception) {
            throw invalid(key, "contains an unresolved configuration placeholder");
        }
    }

    private static IllegalStateException invalid(String key, String rule) {
        return new IllegalStateException("AWS configuration: " + key + " " + rule + ".");
    }
}
