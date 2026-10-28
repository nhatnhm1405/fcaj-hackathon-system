import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;

/** Integration checks confined to two new catalogs on a new loopback-only Docker server. */
public class DumpMigrationLab {
    private static final String RESTORED = "fcaj_restore_lab";
    private static final String EMPTY = "fcaj_empty_lab";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=1000&socketTimeout=30000";
    private static String url;
    private static String password;

    public static void main(String[] args) {
        try {
            if (args.length == 3 && "verify-migration-copy".equals(args[0])) {
                int referenceChecksum = migrationChecksum(Path.of(args[1]));
                int copyChecksum = migrationChecksum(Path.of(args[2]));
                require(referenceChecksum == copyChecksum, "promoted V1 Flyway checksum");
                System.out.println("PASS: tested reference and promoted V1 share Flyway checksum " + copyChecksum);
                return;
            }
            run(args);
        } catch (Exception error) {
            // JDBC exceptions may contain row values: never print their messages or causes.
            System.err.println("Lab failed: " + error.getClass().getSimpleName()
                    + (error instanceof IllegalStateException && error.getMessage() != null
                            && error.getMessage().startsWith("Failed check:") ? "; " + error.getMessage() : "")
                    + (error instanceof SQLException sql ? "; SQLState=" + sql.getSQLState() : "")
                    + ". No source data values displayed.");
            System.exit(1);
        }
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url + database + OPTIONS, "root", password);
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException();
        Path work = Path.of(args[0]).toAbsolutePath();
        Path reference = Path.of(args[1]).toAbsolutePath();
        String container = args[2];
        url = System.getenv("FCAJ_LAB_JDBC_URL");
        password = System.getenv("MYSQL_ROOT_PASSWORD");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
                || !container.matches("fcaj-migration-lab-[a-f0-9]{12}") || password == null) {
            throw new IllegalArgumentException();
        }
        boolean ready = false;
        for (int attempt = 0; attempt < 60; attempt++) {
            try (var connection = connect("")) { ready = true; break; }
            catch (SQLException notReady) { Thread.sleep(1000); }
        }
        require(ready, "server readiness");
        String version, driver;
        try (var connection = connect("")) {
            version = scalar(connection, "SELECT VERSION()");
            driver = connection.getMetaData().getDriverVersion();
            require(version.startsWith("8.4."), "MySQL version");
            require("1".equals(scalar(connection, "SELECT @@lower_case_table_names")), "table case");
            execute(connection, "CREATE DATABASE " + EMPTY + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            execute(connection, "CREATE DATABASE " + RESTORED + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        System.out.println("Dedicated MySQL " + version + " ready; restoring reviewed statements.");
        String legacyUrl=System.getenv("FCAJ_LEGACY_JDBC_URL"),legacyContainer=System.getenv("FCAJ_LEGACY_CONTAINER");
        String legacyVersion=null;
        if(legacyUrl!=null) {
            require(legacyUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/") && (container+"-legacy80").equals(legacyContainer),"isolated legacy target");
            boolean legacyReady=false;
            for(int n=0;n<60;n++) {try(var c=DriverManager.getConnection(legacyUrl+OPTIONS,"root",password)){legacyVersion=scalar(c,"SELECT VERSION()");execute(c,"CREATE DATABASE "+RESTORED+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");legacyReady=true;break;}catch(SQLException notReady){Thread.sleep(1000);}}
            require(legacyReady && legacyVersion.startsWith("8.0."),"legacy MySQL8.0 ready");
            boolean legacyGateRejected=false;
            try {com.fcaj.hackathon.db.DatabaseMigrationMain.verifyDeploy(legacyUrl+RESTORED+OPTIONS,"root",password,RESTORED,null);}
            catch(IllegalStateException expected){legacyGateRejected="Target not ready".equals(expected.getMessage());}
            require(legacyGateRejected,"deploy gate rejects actual MySQL8.0 engine");
        }
        String restoreUrl=legacyUrl==null?url:legacyUrl;
        Map<String,String> legacyBefore=null;
        try (var connection = DriverManager.getConnection(restoreUrl+RESTORED+OPTIONS,"root",password); var files = Files.list(work.resolve("restore-statements"))) {
            for (Path statement : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                execute(connection, Files.readString(statement));
            }
            if(legacyUrl!=null) legacyBefore=fingerprints(connection,new TreeMap<>());
        }
        if(legacyUrl!=null) {
            String docker="C:/Program Files/Docker/Docker/resources/bin/docker.exe";
            Path backup=work.resolve("legacy80-backup.sql");
            var dump=new ProcessBuilder(docker,"exec","--env","MYSQL_PWD",legacyContainer,"mysqldump","--user=root","--single-transaction","--no-tablespaces","--set-gtid-purged=OFF","--column-statistics=0",RESTORED);
            dump.environment().put("MYSQL_PWD",password);dump.redirectOutput(backup.toFile());dump.redirectError(work.resolve("legacy-backup-errors.log").toFile());
            var p=dump.start();require(p.waitFor(45,java.util.concurrent.TimeUnit.SECONDS) && p.exitValue()==0,"legacy backup");
            var restore=new ProcessBuilder(docker,"exec","--interactive","--env","MYSQL_PWD",container,"mysql","--user=root",RESTORED);
            restore.environment().put("MYSQL_PWD",password);restore.redirectInput(backup.toFile());restore.redirectOutput(work.resolve("legacy-restore-output.log").toFile());restore.redirectError(work.resolve("legacy-restore-errors.log").toFile());
            p=restore.start();require(p.waitFor(45,java.util.concurrent.TimeUnit.SECONDS) && p.exitValue()==0,"8.0 backup restores to separate8.4");
            try(var c=connect(RESTORED)){require(legacyBefore.equals(fingerprints(c,new TreeMap<>())),"legacy8.0 to target8.4 data fingerprints");}
        }
        Map<String, String> before;
        Map<String, Long> counts = new TreeMap<>();
        List<String> schemaRestore;
        try (var connection = connect(RESTORED)) {
            before = fingerprints(connection, counts);
            require(counts.size() == 31, "restored tables");
            require(counts.values().stream().mapToLong(Long::longValue).sum() == 627, "restored rows");
            var expectedCounts = new TreeMap<String, Long>();
            for (String row : Files.readAllLines(work.resolve("restore-statements/expected-counts.tsv"))) {
                String[] fields = row.split("\t");
                expectedCounts.put(fields[0], Long.parseLong(fields[1]));
            }
            require(counts.equals(expectedCounts), "every table count matches dump tuples");
            require(auditData(connection) == 55, "FK audit coverage");
            schemaRestore = schema(connection);
        }
        Path migrations = work.resolve("candidate-migrations");
        Files.createDirectory(migrations);
        Path v1 = migrations.resolve("V1__create_core_schema.sql");
        Files.copy(reference, v1);
        rejectGate(RESTORED,migrations,"unbaselined legacy DB");
        try(var c=connect(RESTORED)) {require("0".equals(scalar(c,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history'")),"gate does not create history");}
        Flyway empty = flyway(EMPTY, migrations);
        require(empty.migrate().migrationsExecuted == 1, "empty migrate V1");
        require(empty.migrate().migrationsExecuted == 0, "repeat migrate");
        empty.validate();
        try (var connection = connect(EMPTY)) {
            require(schemaRestore.equals(schema(connection)), "runtime schema equivalence including secondary indexes");
        }
        try (var emptyConnection = connect(EMPTY); var restoredConnection = connect(RESTORED)) {
            String shape = com.fcaj.hackathon.db.SchemaFingerprint.of(emptyConnection);
            require(shape.equals(com.fcaj.hackathon.db.SchemaFingerprint.of(restoredConnection)), "baseline structural fingerprint matches restored schema");
            Files.writeString(work.resolve("v1-schema-sha256.txt"), shape + "\n");
        }
        System.out.println("Restore and new V1 schemas agree; current ORM validation follows V3.");

        byte[] original = Files.readAllBytes(v1);
        Files.writeString(v1, new String(original, StandardCharsets.UTF_8) + "\n-- checksum fault probe\n");
        boolean checksumRejected = false;
        try { flyway(EMPTY, migrations).validate(); }
        catch (FlywayException expected) { checksumRejected = true; }
        finally { Files.write(v1, original); }
        require(checksumRejected, "checksum mismatch rejection");
        empty.validate();


        // Negative control: an unknown non-empty schema is never automatically baselined.
        boolean unknownRejected = false;
        try { flyway(RESTORED, migrations).migrate(); }
        catch (FlywayException expected) { unknownRejected = true; }
        require(unknownRejected, "nonempty database baseline safety");
        // Schema equality + data checks above are prerequisites for the explicit marker.
        Flyway restored = flyway(RESTORED, migrations);
        var baselineRunner = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dlogback.configurationFile=Test/schema/lab-logback.xml", "-cp",
                System.getProperty("java.class.path") + java.io.File.pathSeparator + "src/main/resources",
                "com.fcaj.hackathon.db.DatabaseMigrationMain", "baseline-v1", RESTORED);
        baselineRunner.environment().put("BE1_MIGRATION_JDBC_URL", url + RESTORED + OPTIONS);
        baselineRunner.environment().put("BE1_MIGRATION_USERNAME", "root");
        baselineRunner.environment().put("BE1_MIGRATION_PASSWORD", password);
        baselineRunner.environment().put("BE1_MIGRATION_BACKUP_PATH", System.getenv("FCAJ_LAB_BACKUP_PATH"));
        baselineRunner.environment().put("BE1_MIGRATION_BACKUP_SHA256", "ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802");
        baselineRunner.environment().put("BE1_MIGRATION_BACKUP_CATALOG", RESTORED);
        baselineRunner.redirectErrorStream(true).redirectOutput(work.resolve("operator-baseline.log").toFile());
        var process = baselineRunner.start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Failed check: operator baseline timeout"); }
        require(process.exitValue() == 0, "operator runner validates backup and V1 shape before baseline");
        require(restored.migrate().migrationsExecuted == 0, "explicit baseline skips V1");
        restored.validate();
        rejectGate(RESTORED,migrations,"baseline-only DB");
        Map<String, String> after;
        try (var connection = connect(RESTORED)) {
            after = fingerprints(connection, new TreeMap<>());
            require(before.equals(after), "row fingerprints preserved after baseline");
            require(schemaRestore.equals(schema(connection)), "restore schema unchanged after baseline");
            auditData(connection);
        }
        Path v2 = reference.getParent().resolve("V2__create_ai_job_delivery.sql");
        require(Files.isRegularFile(v2), "V2 migration available");
        Files.copy(v2, migrations.resolve(v2.getFileName()));
        require(flyway(EMPTY, migrations).migrate().migrationsExecuted == 1, "empty V2 migration");
        require(flyway(RESTORED, migrations).migrate().migrationsExecuted == 1, "restored V2 migration");
        require(flyway(RESTORED, migrations).migrate().migrationsExecuted == 0, "restored V2 restart no-op");
        boolean pendingRejected=false;
        try { com.fcaj.hackathon.db.DatabaseMigrationMain.verifyDeploy(url+RESTORED+OPTIONS,"root",password,RESTORED,flyway(RESTORED,migrations)); }
        catch(Exception expected) { pendingRejected=true; }
        require(pendingRejected,"deploy gate rejects pre-V3 target");
        Path v3=reference.getParent().resolve("V3__add_user_track_row_versions.sql");
        Files.copy(v3,migrations.resolve(v3.getFileName()));
        require(flyway(EMPTY,migrations).migrate().migrationsExecuted==1,"empty V3 migration");
        require(flyway(RESTORED,migrations).migrate().migrationsExecuted==1,"restored V3 migration");
        require(flyway(RESTORED,migrations).migrate().migrationsExecuted==0,"V3 restart no-op");
        try (var connection = connect(RESTORED)) {
            var latestCounts = new TreeMap<String, Long>();
            var latest = fingerprints(connection, latestCounts);
            require(before.entrySet().stream().allMatch(e -> e.getValue().equals(latest.get(e.getKey()))), "V2 preserves every legacy row fingerprint");
            require(latestCounts.size() == 34 && latestCounts.values().stream().mapToLong(Long::longValue).sum() == 627, "V2 adds three empty tables only");
            require(auditData(connection) == 61, "V2 FK audit coverage");
        }
        validateOrm(RESTORED);
        validateOrm(EMPTY);
        try(var c=connect(EMPTY)) {Files.writeString(work.resolve("v3-schema-sha256.txt"),com.fcaj.hackathon.db.SchemaFingerprint.of(c)+"\n");}
        com.fcaj.hackathon.db.DatabaseMigrationMain.verifyDeploy(url+RESTORED+OPTIONS,"root",password,RESTORED,flyway(RESTORED,migrations));
        Path candidateJar=Path.of("target/hackathon-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        require(Files.isRegularFile(candidateJar),"packaged candidate available");
        var packagedGate=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Dloader.main=com.fcaj.hackathon.db.DatabaseMigrationMain","-cp",candidateJar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher","verify-deploy",RESTORED);
        packagedGate.environment().put("BE1_MIGRATION_JDBC_URL",url+RESTORED+OPTIONS);
        packagedGate.environment().put("BE1_MIGRATION_USERNAME","root");
        packagedGate.environment().put("BE1_MIGRATION_PASSWORD",password);
        packagedGate.redirectErrorStream(true).redirectOutput(work.resolve("packaged-gate.log").toFile());
        var gateProcess=packagedGate.start();
        if(!gateProcess.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){gateProcess.destroyForcibly();throw new IllegalStateException("Failed check: packaged gate timeout");}
        require(gateProcess.exitValue()==0,"packaged candidate gate succeeds");
        try(var c=connect("")){execute(c,"CREATE DATABASE fcaj_baseline3_lab");}
        var badBaseline=Flyway.configure().dataSource(url+"fcaj_baseline3_lab"+OPTIONS,"root",password)
                .locations("filesystem:"+migrations.toString().replace('\\','/')).baselineVersion("3").cleanDisabled(true).createSchemas(false).load();
        badBaseline.baseline();
        boolean baseline3Rejected=false;
        try{com.fcaj.hackathon.db.DatabaseMigrationMain.verifyDeploy(url+"fcaj_baseline3_lab"+OPTIONS,"root",password,"fcaj_baseline3_lab",badBaseline);}
        catch(IllegalStateException expected){baseline3Rejected=expected.getMessage().startsWith("Failed check: migration state");}
        require(baseline3Rejected,"gate rejects synthetic baseline3 bypass");
        try(var connection=connect(RESTORED)) {
            require(before.entrySet().stream().allMatch(e->{try{return e.getValue().equals(fingerprints(connection,new TreeMap<>()).get(e.getKey()));}catch(Exception failed){throw new IllegalStateException(failed);}}),"gate preserves data");
        }
        try(var connection=connect(RESTORED)) {
            require("0".equals(scalar(connection,"SELECT COUNT(*) FROM `User` WHERE row_version IS NULL OR row_version<>0")),"User versions initialized");
            require("0".equals(scalar(connection,"SELECT COUNT(*) FROM Track WHERE row_version IS NULL OR row_version<>0")),"Track versions initialized");
        }
        try(var connection=connect(EMPTY)) { execute(connection,"ALTER TABLE Track DROP COLUMN row_version"); }
        rejectGate(EMPTY,migrations,"missing version column");
        boolean mismatchRejected=false;
        try { validateOrm(EMPTY); }
        catch(org.hibernate.tool.schema.spi.SchemaManagementException expected) { mismatchRejected=true; }
        finally { try(var connection=connect(EMPTY)) { execute(connection,"ALTER TABLE Track ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0"); } }
        require(mismatchRejected,"ORM missing version rejection");
        validateOrm(EMPTY);
        Files.writeString(v1,new String(original,StandardCharsets.UTF_8)+"\n-- candidate checksum negative\n");
        try {rejectGate(EMPTY,migrations,"checksum mismatch");}finally{Files.write(v1,original);}
        StringBuilder report = new StringBuilder("# Isolated MySQL dump migration lab\n\n");
        report.append("Container: `").append(container).append("` (stopped by wrapper after checks).\n\n")
                .append("Server: ").append(version).append("; Connector/J: ").append(driver)
                .append("; lower_case_table_names=1; utf8mb4 / utf8mb4_0900_ai_ci.\n\n")
                .append(legacyVersion==null?"Legacy MySQL8.0 server rehearsal was not selected.\n\n":"PASS: separate MySQL "+legacyVersion+" source restored reviewed statements, mysqldump backup restored to separate8.4 target; original-column fingerprints match before baseline/migration. Data-bearing backup stays under ignored target.\n\n")
                .append("Source dump SHA-256: ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802.\n\n")
                .append("Candidate V1 SHA-256: ").append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)))
                .append("; Flyway checksum: ").append(migrationChecksum(v1))
                .append(". Checks use isolated catalogs only.\n\n")
                .append("PASS: V2/V3 apply on empty and restored V1; restart no-op; all 627 legacy rows retain fingerprints over original columns; version columns initialized to zero; three job tables start empty; all 61 FK checks pass.\n\n")
                .append("PASS: deploy gate rejects no-history, baseline-only, pre-V3, missing-version and checksum faults; read-only gate and packaged candidate CLI pass on restored V3.\n\n")
                .append("PASS: restore 31 tables/627 rows; every table count matches dump tuples; JDBC import statements returned no warnings; all 55 FK orphan checks; every unique key duplicate check; auto-increment > max ID.\n\n")
                .append("PASS: Flyway empty V1, repeat no-op, checksum rejection; Hibernate validation on new/restored schemas, missing-column rejection.\n\n")
                .append("PASS: runtime column/default/collation/index/FK/engine equivalence, ignoring case, physical column order and constraint/index names.\n\n")
                .append("PASS: nonempty schema migrate rejected until explicit baseline 1; no V1 rerun; baseline preserves all table row fingerprints and schema.\n\n")
                .append("## Restored counts\n\n| Table | Rows |\n|---|---:|\n");
        counts.forEach((table, count) -> report.append("| ").append(table).append(" | ").append(count).append(" |\n"));
        report.append("\nRow fingerprints are SHA-256 over length-prefixed JDBC bytes, sorted by PK; compared in memory before/after baseline. No row values/hashes are published.\n\n")
                .append("This lab checks migrations and entities. Backend job API integration is covered separately by AiJobIntegrationTest. No RDS, source DB or source dump was modified.\n");
        Files.writeString(work.resolve("lab-evidence.md"), report, StandardCharsets.UTF_8);
        System.out.println("PASS: all isolated migration/restore checks. Full backend/API acceptance remains pending.");
    }

    private static Flyway flyway(String database, Path migrations) {
        return Flyway.configure().dataSource(url + database + OPTIONS, "root", password)
                .locations("filesystem:" + migrations.toString().replace('\\', '/'))
                .baselineOnMigrate(false).baselineVersion("1").cleanDisabled(true)
                .validateOnMigrate(true).createSchemas(false).load();
    }
    private static void rejectGate(String database,Path migrations,String label) throws Exception {
        boolean rejected=false;
        try {com.fcaj.hackathon.db.DatabaseMigrationMain.verifyDeploy(url+database+OPTIONS,"root",password,database,flyway(database,migrations));}
        catch(Exception expected){rejected=true;}
        require(rejected,"gate rejects "+label);
    }

    private static int migrationChecksum(Path path) {
        var absolute = path.toAbsolutePath();
        var resource = new org.flywaydb.core.internal.resource.filesystem.FileSystemResource(
                new org.flywaydb.core.api.Location("filesystem:" + absolute.getParent().toString().replace('\\', '/')),
                absolute.toString(), StandardCharsets.UTF_8, false);
        return org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(resource);
    }

    private static void validateOrm(String database) throws Exception {
        var settings = new LinkedHashMap<String, Object>();
        settings.put("hibernate.connection.url", url + database + OPTIONS);
        settings.put("hibernate.connection.username", "root");
        settings.put("hibernate.connection.password", password);
        settings.put("hibernate.hbm2ddl.auto", "validate");
        settings.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        var registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var sources = new MetadataSources(registry);
            Path entityDir = Path.of("src/main/java/com/fcaj/hackathon/entity");
            try (var files = Files.list(entityDir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    sources.addAnnotatedClass(Class.forName("com.fcaj.hackathon.entity." + file.getFileName().toString().replace(".java", "")));
                }
            }
            var metadata = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(metadata, registry, settings, null);
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    private static List<String> schema(Connection connection) throws Exception {
        var result = new ArrayList<String>();
        for (String query : List.of(
                "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME",
                "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,COLUMN_NAME",
                "SELECT k.TABLE_NAME,k.COLUMN_NAME,k.ORDINAL_POSITION,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME AND k.TABLE_NAME=r.TABLE_NAME WHERE k.TABLE_SCHEMA=DATABASE() ORDER BY k.TABLE_NAME,k.COLUMN_NAME")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
                while (rows.next()) {
                    var row = new ArrayList<String>();
                    for (int c = 1; c <= rows.getMetaData().getColumnCount(); c++) row.add(rows.getString(c));
                    result.add(row.toString());
                }
            }
        }
        var indexes = new TreeMap<String, StringBuilder>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,EXPRESSION,IS_VISIBLE,COLLATION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX")) {
            while (rows.next()) {
                String key = rows.getString(1) + ":" + rows.getString(2);
                var value = indexes.computeIfAbsent(key, k -> new StringBuilder(rowsString(rows, 1, 3, 4)));
                value.append('|').append(rowsString(rows, 5, 6, 7, 8, 9, 10));
            }
        }
        result.addAll(indexes.values().stream().map(StringBuilder::toString).sorted().toList());
        return result;
    }

    private static String rowsString(java.sql.ResultSet rows, int... columns) {
        try {
            var values = new ArrayList<String>();
            for (int column : columns) values.add(rows.getString(column));
            return values.toString();
        } catch (SQLException exception) { throw new IllegalStateException("Metadata unavailable"); }
    }

    private static Map<String, String> fingerprints(Connection connection, Map<String, Long> counts) throws Exception {
        var result = new TreeMap<String, String>();
        for (String table : tables(connection)) {
            String primary = scalar(connection, "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "' AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION LIMIT 1");
            var digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            String columns;
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' AND COLUMN_NAME<>'row_version' ORDER BY ORDINAL_POSITION")) {
                var names=new ArrayList<String>();while(r.next()) names.add("`"+r.getString(1)+"`");columns=String.join(",",names);
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT "+columns+" FROM `" + table + "` ORDER BY `" + primary + "`")) {
                while (rows.next()) {
                    count++;
                    for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                        byte[] value = rows.getBytes(column);
                        int length = value == null ? -1 : value.length;
                        digest.update(java.nio.ByteBuffer.allocate(4).putInt(length).array());
                        if (value != null) digest.update(value);
                    }
                }
            }
            counts.put(table, count);
            result.put(table, HexFormat.of().formatHex(digest.digest()));
        }
        return result;
    }

    private static List<String> tables(Connection connection) throws Exception {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME")) {
            while (rows.next()) {
                String table = rows.getString(1);
                require(table.matches("[a-z_]+"), "table identifier");
                result.add(table);
            }
        }
        return result;
    }

    private static int auditData(Connection connection) throws Exception {
        int checked = 0;
        // Current entity mappings use single-column FKs; assert that before building checks.
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME,ORDINAL_POSITION FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL")) {
            while (rows.next()) {
                require(rows.getInt(5) == 1, "single-column FK shape");
                String t = rows.getString(1), c = rows.getString(2), rt = rows.getString(3), rc = rows.getString(4);
                String query = "SELECT COUNT(*) FROM `"+t+"` c LEFT JOIN `"+rt+"` p ON c.`"+c+"`=p.`"+rc+"` WHERE c.`"+c+"` IS NOT NULL AND p.`"+rc+"` IS NULL";
                require("0".equals(scalar(connection, query)), "orphan FK"); checked++;
            }
        }
        var uniques = new TreeMap<String, List<String>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT TABLE_NAME,INDEX_NAME,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND NON_UNIQUE=0 AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX")) {
            while (rows.next()) uniques.computeIfAbsent(rows.getString(1)+":"+rows.getString(2), k -> new ArrayList<>()).add(rows.getString(3));
        }
        for (var entry : uniques.entrySet()) {
            String table = entry.getKey().split(":")[0];
            String columns = entry.getValue().stream().map(c -> "`"+c+"`").collect(java.util.stream.Collectors.joining(","));
            String nonnull = entry.getValue().stream().map(c -> "`"+c+"` IS NOT NULL").collect(java.util.stream.Collectors.joining(" AND "));
            require("0".equals(scalar(connection, "SELECT COUNT(*) FROM (SELECT "+columns+" FROM `"+table+"` WHERE "+nonnull+" GROUP BY "+columns+" HAVING COUNT(*)>1) duplicates")), "duplicate unique key");
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT t.TABLE_NAME,t.AUTO_INCREMENT,c.COLUMN_NAME FROM information_schema.TABLES t JOIN information_schema.COLUMNS c ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME WHERE t.TABLE_SCHEMA=DATABASE() AND c.EXTRA LIKE '%auto_increment%' AND t.TABLE_NAME<>'flyway_schema_history'")) {
            while (rows.next()) {
                long next = rows.getLong(2);
                long max = Long.parseLong(scalar(connection, "SELECT COALESCE(MAX(`"+rows.getString(3)+"`),0) FROM `"+rows.getString(1)+"`"));
                require(next > max, "auto-increment counter");
            }
        }
        return checked;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
            require(statement.getWarnings() == null, "SQL statement warning (possible coercion/truncation)");
        }
    }
    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new SQLException("No metadata");
            return rows.getString(1);
        }
    }
    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException("Failed check: " + check);
    }
}
