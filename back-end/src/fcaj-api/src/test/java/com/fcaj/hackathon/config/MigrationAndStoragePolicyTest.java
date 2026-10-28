package com.fcaj.hackathon.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class MigrationAndStoragePolicyTest {
    @Test void migrationPolicyCannotBeOverriddenToAutomaticBaselineOrSchemaUpdate() {
        var env=new MockEnvironment().withProperty("spring.flyway.enabled","true").withProperty("spring.jpa.hibernate.ddl-auto","validate");
        assertThatCode(() -> MigrationEnvironmentConfiguration.validate(env)).doesNotThrowAnyException();
        env.withProperty("spring.flyway.baseline-on-migrate","true");assertThatThrownBy(() -> MigrationEnvironmentConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        env.withProperty("spring.flyway.baseline-on-migrate","false").withProperty("spring.jpa.hibernate.ddl-auto","update");
        assertThatThrownBy(() -> MigrationEnvironmentConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void s3RequiresRegionBucketAndAnIsolatedValidPrefixBeforeStartup() {
        var env=new MockEnvironment().withProperty("app.storage.provider","s3");
        assertThatThrownBy(() -> StorageEnvironmentConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        env.withProperty("app.aws.region","ap-southeast-1").withProperty("app.storage.bucket","test-bucket").withProperty("app.storage.prefix","be1/mock/");
        assertThatCode(() -> StorageEnvironmentConfiguration.validate(env)).doesNotThrowAnyException();
        env.withProperty("app.storage.prefix","../");assertThatThrownBy(() -> StorageEnvironmentConfiguration.validate(env)).isInstanceOf(IllegalArgumentException.class);
    }
}
