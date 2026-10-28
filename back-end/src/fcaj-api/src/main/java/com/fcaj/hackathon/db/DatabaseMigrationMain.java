package com.fcaj.hackathon.db;

import java.nio.file.*;
import java.net.URI;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Explicit operator runner, separate from Spring startup. Never restores, drops, repairs or cleans a DB. */
public final class DatabaseMigrationMain {
    private DatabaseMigrationMain() {}
    public static void main(String[] args) {
        try { run(args); }
        catch(Exception failed) {
            System.err.println("Migration operation rejected or failed; type="+failed.getClass().getSimpleName()+". No connection values or row data displayed.");
            System.exit(1);
        }
    }
    static void run(String[] args) throws Exception {
        if(args.length!=2 || !Set.of("inspect","migrate","baseline-v1","verify-deploy","initialize-empty").contains(args[0]))
            throw new IllegalArgumentException("Expected inspect|migrate|baseline-v1|verify-deploy|initialize-empty and the exact target catalog name");
        String url=required("BE1_MIGRATION_JDBC_URL"),user=required("BE1_MIGRATION_USERNAME"),password=required("BE1_MIGRATION_PASSWORD");
        validateTarget(url,args[1]);
        validateRuntimeTarget(url,System.getenv("BE1_EXPECTED_RUNTIME_JDBC_URL"));
        var flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                .baselineOnMigrate(false).baselineVersion("1").cleanDisabled(true).validateOnMigrate(true).createSchemas(false).load();
        if ("initialize-empty".equals(args[0])) {
            initializeEmpty(url,user,password,args[1],required("BE1_EXPECTED_RUNTIME_JDBC_URL"),flyway);
            System.out.println("PASS: candidate database initialized or already current; deploy verification passed.");
            return;
        }
        if ("verify-deploy".equals(args[0])) {
            verifyDeploy(url,user,password,args[1],flyway);
            System.out.println("PASS: candidate schema and migration history ready for deploy.");
            return;
        }
        try(var connection=DriverManager.getConnection(url,user,password)) {
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT VERSION(),@@lower_case_table_names,DATABASE()")) {
                rows.next();if(!rows.getString(1).startsWith("8.4.") || rows.getInt(2)!=1 || !args[1].equals(rows.getString(3))) throw new IllegalStateException();
            }
            String shape=SchemaFingerprint.of(connection);
            System.out.println("Verified target catalog="+args[1]+"; MySQL8.4; case=1; schemaShape="+shape);
            if("baseline-v1".equals(args[0])) {
                try(var stream=DatabaseMigrationMain.class.getResourceAsStream("/db/v1-schema-sha256.txt")) {
                    if(stream==null || !new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim().equals(shape)) throw new IllegalStateException();
                }
                Path backup=Path.of(required("BE1_MIGRATION_BACKUP_PATH")).toAbsolutePath();
                if(!Files.isRegularFile(backup) || Files.size(backup)==0) throw new IllegalStateException();
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                try(var stream=Files.newInputStream(backup)) {byte[] buffer=new byte[65536];int n;while((n=stream.read(buffer))!=-1) digest.update(buffer,0,n);}
                String backupHash=HexFormat.of().formatHex(digest.digest());
                if(!backupHash.equals(required("BE1_MIGRATION_BACKUP_SHA256"))) throw new IllegalStateException();
                // The operator separately confirms the backup belongs to this target; a filename cannot prove that.
                if(!args[1].equals(required("BE1_MIGRATION_BACKUP_CATALOG"))) throw new IllegalStateException();
                System.out.println("Reviewed V1 shape and backup checksum match. Writing baseline marker only.");
                flyway.baseline();return;
            }
        }
        if("migrate".equals(args[0])) {var result=flyway.migrate();flyway.validate();System.out.println("Applied migrations="+result.migrationsExecuted);}
        else for(var info:flyway.info().all()) System.out.println("Migration "+info.getVersion()+" state="+info.getState());
    }
    static void validateRuntimeTarget(String url,String expected) {
        if(expected!=null && !expected.equals(url)) throw new IllegalArgumentException("Migration/runtime target mismatch");
    }
    /** First deployment only. A nonempty catalog is accepted solely when the full V3 gate already passes. */
    public static void initializeEmpty(String url,String user,String password,String catalog,String expected,Flyway flyway) throws Exception {
        validateTarget(url,catalog);
        if(expected==null || expected.isBlank()) throw new IllegalArgumentException("Exact runtime target required");
        validateRuntimeTarget(url,expected);
        boolean empty;
        try(var connection=DriverManager.getConnection(url,user,password)) {
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT VERSION(),@@lower_case_table_names,DATABASE()")) {
                if(!r.next() || !r.getString(1).startsWith("8.4.") || r.getInt(2)!=1 || !catalog.equals(r.getString(3)))
                    throw new IllegalStateException("Target not ready");
            }
            try(var s=connection.prepareStatement("SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?")) {
                s.setString(1,catalog);
                try(var r=s.executeQuery()) {
                    if(!r.next() || !"utf8mb4".equals(r.getString(1)) || !"utf8mb4_0900_ai_ci".equals(r.getString(2)))
                        throw new IllegalStateException("Target charset/collation not ready");
                }
            }
            empty=true;
            for(String view:List.of("TABLES","ROUTINES","EVENTS","TRIGGERS")) {
                String scope="ROUTINES".equals(view)?"ROUTINE_SCHEMA":"EVENTS".equals(view)?"EVENT_SCHEMA":"TRIGGERS".equals(view)?"TRIGGER_SCHEMA":"TABLE_SCHEMA";
                try(var s=connection.prepareStatement("SELECT COUNT(*) FROM information_schema."+view+" WHERE "+scope+"=?")) {
                    s.setString(1,catalog);
                    try(var r=s.executeQuery()) { if(!r.next() || r.getLong(1)!=0) empty=false; }
                }
            }
        }
        if(empty) {
            // No automatic baseline, repair or upgrade of an existing schema in this entry point.
            flyway.migrate();
        }
        verifyDeploy(url,user,password,catalog,flyway);
    }
    static void validateTarget(String url,String catalog) {
        if(!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
        URI uri=URI.create(url.substring(5));
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || !("/"+catalog).equals(uri.getPath())
                || !catalog.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException();
        boolean verifiedTls=false;var seen=new HashSet<String>();
        for(String part:(uri.getRawQuery()==null?"":uri.getRawQuery()).split("&")) {
            if(part.isBlank()) continue;
            String[] pair=part.split("=",2);
            String name=java.net.URLDecoder.decode(pair[0],java.nio.charset.StandardCharsets.UTF_8);
            String lower=name.toLowerCase(Locale.ROOT);
            if(!seen.add(lower) || Set.of("user","password","password1","password2","password3","createdatabaseifnotexist").contains(lower)) throw new IllegalArgumentException();
            if("sslmode".equals(lower)) verifiedTls="sslMode".equals(name) && pair.length==2 && "VERIFY_IDENTITY".equals(pair[1]);
        }
        boolean loopback=Set.of("localhost","127.0.0.1").contains(uri.getHost());
        if(!loopback && !verifiedTls) throw new IllegalArgumentException();
    }
    public static void verifyDeploy(String url,String user,String password,String catalog,Flyway flyway) throws Exception {
        validateTarget(url,catalog);
        validateRuntimeTarget(url,System.getenv("BE1_EXPECTED_RUNTIME_JDBC_URL"));
        try(var connection=DriverManager.getConnection(url,user,password)) {
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT VERSION(),@@lower_case_table_names,DATABASE()")) {
                if(!r.next() || !r.getString(1).startsWith("8.4.") || r.getInt(2)!=1 || !catalog.equals(r.getString(3))) throw new IllegalStateException("Target not ready");
            }
            flyway.validate();
            var info=flyway.info();
            if(info.pending().length!=0 || info.current()==null || !"3".equals(info.current().getVersion().toString())) throw new IllegalStateException("Migrations not current");
            for(var migration:info.all()) {
                String state=migration.getState().name();
                boolean reviewedBaselineV1=Set.of("BASELINE_IGNORED","BASELINE","BELOW_BASELINE").contains(state)
                        && migration.getVersion()!=null && "1".equals(migration.getVersion().toString());
                if(!reviewedBaselineV1 && !"SUCCESS".equals(state)) throw new IllegalStateException("Failed check: migration state "+state);
            }
            try(var ref=DatabaseMigrationMain.class.getResourceAsStream("/db/v3-schema-sha256.txt")) {
                if(ref==null || !new String(ref.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim().equals(SchemaFingerprint.of(connection)))
                    throw new IllegalStateException("Candidate schema fingerprint mismatch");
            }
            // JDBC-only job tables are not part of Hibernate's entity metadata.
            for(String query:List.of(
                    "SELECT job_id,requester_id,submission_id,event_id,round_id,input_revision,status,created_at,finished_at,request_json,source_json,rubric_json,result_json,terminal_digest,error_code FROM AiJob WHERE 1=0",
                    "SELECT message_id,job_id,payload_json,created_at,next_attempt_at,attempts,claim_token,claimed_until,sent_at FROM AiJobOutbox WHERE 1=0",
                    "SELECT message_id,job_id,payload_digest,processed_at FROM AiProcessedEvent WHERE 1=0")) {
                try(var s=connection.createStatement();var ignored=s.executeQuery(query)) { /* columns must exist */ }
            }
        }
        var settings=new HashMap<String,Object>();
        settings.put("hibernate.connection.url",url); settings.put("hibernate.connection.username",user);
        settings.put("hibernate.connection.password",password); settings.put("hibernate.hbm2ddl.auto","validate");
        settings.put("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        var registry=new org.hibernate.boot.registry.StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var sources=new org.hibernate.boot.MetadataSources(registry);
            var scanner=new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            for(var resource:scanner.getResources("classpath*:com/fcaj/hackathon/entity/*.class")) {
                String name=resource.getFilename();
                if(name!=null && !name.contains("$")) {
                    Class<?> entity=Class.forName("com.fcaj.hackathon.entity."+name.substring(0,name.length()-6));
                    if(entity.isAnnotationPresent(jakarta.persistence.Entity.class)) sources.addAnnotatedClass(entity);
                }
            }
            var metadata=sources.buildMetadata();
            if(metadata.getEntityBindings().isEmpty()) throw new IllegalStateException("No candidate mappings found");
            org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(metadata,registry,settings,null);
        } finally { org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry); }
    }
    private static String required(String name) {
        String value=System.getenv(name);if(value==null || value.isBlank()) throw new IllegalArgumentException();return value;
    }
}
