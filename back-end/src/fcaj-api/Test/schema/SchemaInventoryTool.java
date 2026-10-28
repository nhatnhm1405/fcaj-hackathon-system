import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.Version;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;

/** Offline review aid. Compiles entities only; never starts Spring or connects to a DB. */
public class SchemaInventoryTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected entity source directory, NEW output directory, source revision");
        }
        Path sources = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output directory already exists; use a new directory");
        }
        Files.createDirectories(output);
        var files = Files.list(sources);
        java.util.List<Path> entities;
        try (files) {
            entities = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("hibernate.dialect", new MySQLDialect(DatabaseVersion.make(8, 4)));
        settings.put("hibernate.boot.allow_jdbc_metadata_access", false);
        // Any accidental attempt to open a connection fails instead of reaching local MySQL.
        settings.put("hibernate.connection.provider_class", UserSuppliedConnectionProviderImpl.class);
        settings.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target",
                output.resolve("hibernate-mysql84-reference.sql").toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        settings.put("hibernate.format_sql", true);
        settings.put("hibernate.hbm2ddl.halt_on_error", true);

        var registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var mappings = new MetadataSources(registry);
            for (Path entity : entities) {
                String name = entity.getFileName().toString().replaceFirst("\\.java$", "");
                mappings.addAnnotatedClass(Class.forName("com.fcaj.hackathon.entity." + name));
            }
            var metadata = (MetadataImplementor) mappings.buildMetadata();
            metadata.orderColumns(false);
            metadata.validate();
            SchemaManagementToolCoordinator.process(metadata, registry, settings, null);

            var tables = metadata.getDatabase().getNamespaces().iterator().next().getTables().stream()
                    .filter(Table::isPhysicalTable).sorted(Comparator.comparing(Table::getName)).toList();
            if (tables.size() != entities.size()) {
                throw new IllegalStateException("Entity/table count differs; review export assumptions");
            }
            StringBuilder report = new StringBuilder("# Entity schema inventory — offline MySQL 8.4 export\n\n");
            report.append("Source revision: `").append(args[2]).append("` plus current working tree.\n\n")
                    .append("Hibernate: `").append(Version.getVersionString()).append("`. ")
                    .append("Dialect: MySQL 8.4; physical naming: StandardImpl. ")
                    .append("No connection, Spring startup, DB data or migration execution.\n\n")
                    .append("SQL file is a reference for review, not an approved V1. ")
                    .append("Charset/collation and lower_case_table_names require a real DB check. ")
                    .append("Java builder defaults and lifecycle callbacks are not SQL defaults.\n\n")
                    .append("| Table | Columns | PK | FKs | Unique keys | Declared indexes |\n")
                    .append("|---|---:|---|---:|---:|---:|\n");
            int columns = 0;
            int foreignKeys = 0;
            for (Table table : tables) {
                columns += table.getColumnSpan();
                foreignKeys += table.getForeignKeyCollection().size();
                report.append("| ").append(table.getName()).append(" | ").append(table.getColumnSpan())
                        .append(" | ").append(names(table.getPrimaryKey().getColumns())).append(" | ")
                        .append(table.getForeignKeyCollection().size()).append(" | ")
                        .append(table.getUniqueKeys().size()).append(" | ")
                        .append(table.getIndexes().size()).append(" |\n");
            }
            report.append("\nDeclared indexes exclude MySQL's automatic FK indexes. ")
                    .append("Nullable columns in MySQL unique keys permit multiple NULL entries.\n");
            for (Table table : tables) {
                report.append("\n## ").append(table.getName()).append("\n\n")
                        .append("| Column | SQL type | Nullable | Identity | SQL default |\n")
                        .append("|---|---|---|---|---|\n");
                for (Column column : table.getColumns()) {
                    report.append("| ").append(column.getName()).append(" | ")
                            .append(column.getSqlType(metadata)).append(" | ").append(column.isNullable())
                            .append(" | ").append(column.isIdentity()).append(" | ")
                            .append(column.getDefaultValue() == null ? "—" : column.getDefaultValue())
                            .append(" |\n");
                }
                for (var key : table.getUniqueKeys().values().stream()
                        .sorted(Comparator.comparing(org.hibernate.mapping.UniqueKey::getName)).toList()) {
                    report.append("\nUnique `").append(key.getName()).append("`: ")
                            .append(names(key.getColumns())).append(".\n");
                }
                for (var key : table.getForeignKeyCollection().stream()
                        .sorted(Comparator.comparing(org.hibernate.mapping.ForeignKey::getName)).toList()) {
                    var referenced = key.isReferenceToPrimaryKey()
                            ? key.getReferencedTable().getPrimaryKey().getColumns() : key.getReferencedColumns();
                    report.append("\nFK `").append(key.getName()).append("`: ")
                            .append(names(key.getColumns())).append(" → ").append(key.getReferencedTable().getName())
                            .append("(").append(names(referenced)).append("). Delete action: ")
                            .append(key.getOnDeleteAction() == null ? "default (NO ACTION)" : key.getOnDeleteAction())
                            .append(".\n");
                }
            }
            report.append("\n## Source manifest\n\nSHA-256 hashes capture uncommitted entity changes too.\n\n")
                    .append("| Source | SHA-256 |\n|---|---|\n");
            for (Path entity : entities) {
                report.append("| ").append(entity.getFileName()).append(" | ")
                        .append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(entity)))).append(" |\n");
            }
            Files.writeString(output.resolve("entity-schema-inventory.md"), report, StandardCharsets.UTF_8);
            System.out.printf("Offline export: %d entities, %d tables, %d columns, %d foreign keys.%n",
                    entities.size(), tables.size(), columns, foreignKeys);
            System.out.println("Review artifacts: " + output);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static String names(java.util.List<Column> columns) {
        return columns.stream().map(Column::getName).collect(Collectors.joining(", "));
    }
}
