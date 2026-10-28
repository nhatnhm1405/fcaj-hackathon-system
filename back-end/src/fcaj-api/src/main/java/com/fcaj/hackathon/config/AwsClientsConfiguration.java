package com.fcaj.hackathon.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods=false)
public class AwsClientsConfiguration {
    private static ClientOverrideConfiguration limits() {
        return ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(40))
                .apiCallAttemptTimeout(Duration.ofSeconds(30)).build();
    }
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="app.storage.provider", havingValue="s3")
    S3Client s3(@Value("${app.aws.region}") String region) {
        return S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(25)))
                .overrideConfiguration(limits()).build();
    }
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
    SqsClient sqs(@Value("${app.aws.region}") String region) {
        return SqsClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(25)))
                .overrideConfiguration(limits()).build();
    }
}
