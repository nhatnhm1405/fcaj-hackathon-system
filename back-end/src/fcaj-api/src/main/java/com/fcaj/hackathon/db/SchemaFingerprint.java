package com.fcaj.hackathon.db;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Structural metadata only; no row values, auto-increment counters, catalog or index names. */
public final class SchemaFingerprint {
    private SchemaFingerprint() {}
    public static String of(Connection connection) throws Exception {
        var shape=new ArrayList<String>();
        for(String sql:List.of(
                "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME",
                "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,COLUMN_NAME",
                "SELECT k.TABLE_NAME,k.COLUMN_NAME,k.ORDINAL_POSITION,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME AND k.TABLE_NAME=r.TABLE_NAME WHERE k.TABLE_SCHEMA=DATABASE() ORDER BY k.TABLE_NAME,k.COLUMN_NAME",
                "SELECT t.TABLE_NAME,c.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME<>'flyway_schema_history' ORDER BY t.TABLE_NAME,c.CHECK_CLAUSE",
                "SELECT EVENT_OBJECT_TABLE,EVENT_MANIPULATION,ACTION_TIMING,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY EVENT_OBJECT_TABLE,EVENT_MANIPULATION,ACTION_TIMING,ACTION_STATEMENT")) {
            try(var statement=connection.createStatement();var rows=statement.executeQuery(sql)) {
                while(rows.next()) {
                    var values=new ArrayList<String>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++) values.add(rows.getString(i));
                    shape.add(values.toString());
                }
            }
        }
        var indexes=new TreeMap<String,StringBuilder>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,EXPRESSION,IS_VISIBLE,COLLATION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX")) {
            while(rows.next()) {
                String key=rows.getString(1)+":"+rows.getString(2);
                var index=indexes.computeIfAbsent(key,unused -> new StringBuilder(values(rows,1,3,4)));
                index.append('|').append(values(rows,5,6,7,8,9,10));
            }
        }
        // Sorting complete index descriptions removes the incidental ordering by index name.
        shape.addAll(indexes.values().stream().map(StringBuilder::toString).sorted().toList());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",shape).getBytes(StandardCharsets.UTF_8)));
    }
    private static String values(ResultSet rows,int... columns) {
        try { var values=new ArrayList<String>();for(int column:columns) values.add(rows.getString(column));return values.toString(); }
        catch(SQLException bad) { throw new IllegalStateException("Schema metadata unavailable"); }
    }
}
