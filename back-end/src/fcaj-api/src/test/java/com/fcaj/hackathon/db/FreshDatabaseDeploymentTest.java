package com.fcaj.hackathon.db;

import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Runs only inside the new loopback MySQL lab created by Test/run-ai-mysql-tests. */
@EnabledIfEnvironmentVariable(named="FCAJ_AI_TEST_JDBC_URL",matches=".+")
class FreshDatabaseDeploymentTest {
    private String url,user="root",password,catalog;
    @BeforeEach void isolatedCatalog() throws Exception {
        String lab=System.getenv("FCAJ_AI_TEST_JDBC_URL");
        if(!lab.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fcaj_ai_integration_lab\\?.*"))
            throw new IllegalStateException("Only the dedicated loopback MySQL lab is allowed");
        catalog="fcaj_deploy_"+UUID.randomUUID().toString().replace("-","");
        password=System.getenv("MYSQL_ROOT_PASSWORD");
        String server=lab.substring(0,lab.indexOf("/fcaj_ai_integration_lab"));
        String options=lab.substring(lab.indexOf('?'));
        try(var c=DriverManager.getConnection(server+"/"+options,user,password);var s=c.createStatement()) {
            s.execute("CREATE DATABASE "+catalog+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        url=server+"/"+catalog+options;
    }
    private Flyway flyway() {
        return Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).createSchemas(false).load();
    }
    private void initialize() throws Exception { DatabaseMigrationMain.initializeEmpty(url,user,password,catalog,url,flyway()); }
    private void sql(String sql) throws Exception { try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) { s.execute(sql); } }
    private long count(String sql) throws Exception { try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery(sql)) { r.next();return r.getLong(1); } }
    private long tables() throws Exception { return count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"); }
    @Test void emptyDatabaseReachesV3AndRerunPreservesData() throws Exception {
        initialize();
        assertThat(flyway().info().current().getVersion().toString()).isEqualTo("3");
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND type='SQL'")).isEqualTo(3);
        sql("INSERT INTO Role(role_name) VALUES ('DEPLOY_SENTINEL')");
        initialize();
        assertThat(count("SELECT COUNT(*) FROM Role WHERE role_name='DEPLOY_SENTINEL'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo(3);
    }
    @Test void unknownNonemptyDatabaseRemainsUntouched() throws Exception {
        sql("CREATE TABLE sentinel(id INT PRIMARY KEY)");sql("INSERT INTO sentinel VALUES(1)");
        assertThatThrownBy(this::initialize).isInstanceOf(Exception.class);
        assertThat(tables()).isEqualTo(1);assertThat(count("SELECT COUNT(*) FROM sentinel")).isEqualTo(1);
    }
    @Test void existingV2RequiresExplicitUpgradeRatherThanAutomaticMigration() throws Exception {
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("2").cleanDisabled(true).load().migrate();
        assertThatThrownBy(this::initialize).isInstanceOf(Exception.class);
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='row_version'")).isZero();
    }
    @Test void artificialBaselineV3CannotBypassInitialization() throws Exception {
        Flyway.configure().dataSource(url,user,password).baselineVersion("3").cleanDisabled(true).load().baseline();
        assertThatThrownBy(this::initialize).isInstanceOf(Exception.class);
        assertThat(tables()).isEqualTo(1);
    }
    @Test void schemaDriftIsRejectedAndNotRepaired() throws Exception {
        initialize();sql("ALTER TABLE Track DROP COLUMN row_version");
        assertThatThrownBy(this::initialize).isInstanceOf(Exception.class);
        assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='track' AND COLUMN_NAME='row_version'")).isZero();
    }
    @Test void checksumMismatchIsRejectedWithoutRepairingHistory() throws Exception {
        initialize();sql("UPDATE flyway_schema_history SET checksum=123 WHERE version='2'");
        assertThatThrownBy(this::initialize).isInstanceOf(Exception.class);
        assertThat(count("SELECT checksum FROM flyway_schema_history WHERE version='2'")).isEqualTo(123);
    }
    @Test void wrongCollationRejectsBeforeCreatingAnyTable() throws Exception {
        sql("ALTER DATABASE "+catalog+" COLLATE utf8mb4_general_ci");
        assertThatThrownBy(this::initialize).isInstanceOf(IllegalStateException.class).hasMessage("Target charset/collation not ready");
        assertThat(tables()).isZero();
    }
    @Test void packagedCandidateInitializesAndVerifiesFreshDatabase() throws Exception {
        Path jar=Path.of("target/hackathon-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(Files.isRegularFile(jar)).as("Package the candidate before running the MySQL lab").isTrue();
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Path log=Path.of("target",catalog+"-packaged.log");
        var builder=new ProcessBuilder(java,"-Dloader.main=com.fcaj.hackathon.db.DatabaseMigrationMain","-cp",jar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher","initialize-empty",catalog);
        builder.environment().put("BE1_MIGRATION_JDBC_URL",url);builder.environment().put("BE1_EXPECTED_RUNTIME_JDBC_URL",url);
        builder.environment().put("BE1_MIGRATION_USERNAME",user);builder.environment().put("BE1_MIGRATION_PASSWORD",password);
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        var process=builder.start();
        // Provisioning 31 core tables plus indexes/FKs involves many DDL fsyncs on a cold VM.
        // This bound is for the migration process, not the 15s/45s HTTP snapshot budgets.
        if(!process.waitFor(180,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Packaged migration CLI timed out"); }
        assertThat(process.exitValue()).as("Packaged CLI exit; inspect local log %s",log).isZero();
        assertThat(flyway().info().current().getVersion().toString()).isEqualTo("3");
        DatabaseMigrationMain.verifyDeploy(url,user,password,catalog,flyway());
    }
    @Test void runtimeIdentityCanUseCurrentSchemaWithoutDdlPrivileges() throws Exception {
        initialize();
        String runtime="fcaj_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String secret=UUID.randomUUID().toString()+UUID.randomUUID();
        sql("CREATE USER '"+runtime+"'@'%' IDENTIFIED BY '"+secret+"'");
        sql("GRANT SELECT,INSERT,UPDATE,DELETE ON "+catalog+".* TO '"+runtime+"'@'%'");
        var runtimeFlyway=Flyway.configure().dataSource(url,runtime,secret).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).createSchemas(false).load();
        assertThat(runtimeFlyway.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,runtime,secret);var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO Role(role_name) VALUES ('RUNTIME_SENTINEL')");
            assertThatThrownBy(() -> s.execute("CREATE TABLE unauthorized_ddl(id INT)" )).isInstanceOf(SQLException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM Role WHERE role_name='RUNTIME_SENTINEL'")).isEqualTo(1);
    }
}
