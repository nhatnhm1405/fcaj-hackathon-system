package com.fcaj.hackathon.db;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatabaseMigrationTargetTest {
    @Test void initializerRejectsMissingOrDifferentRuntimeTargetBeforeConnecting() {
        String url="jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY";
        assertThatThrownBy(() -> DatabaseMigrationMain.initializeEmpty(url,"unused","unused","fcaj_poc",null,null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Exact runtime target required");
        assertThatThrownBy(() -> DatabaseMigrationMain.initializeEmpty(url,"unused","unused","fcaj_poc",url+"&other=value",null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Migration/runtime target mismatch");
    }
    @Test void allowsOnlyExactCatalogAndVerifiedRemoteTls() {
        assertThatCode(() -> DatabaseMigrationMain.validateTarget("jdbc:mysql://127.0.0.1:33060/fcaj_restore_lab?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=1000&socketTimeout=30000","fcaj_restore_lab")).doesNotThrowAnyException();
        assertThatCode(() -> DatabaseMigrationMain.validateTarget("jdbc:mysql://db.example.test/fcaj_poc?sslMode=VERIFY_IDENTITY","fcaj_poc")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseMigrationMain.validateTarget("jdbc:mysql://db.example.test/wrong?sslMode=VERIFY_IDENTITY","fcaj_poc")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsEmbeddedCredentialsAndAmbiguousTlsWithoutNetworkAccess() {
        for(String query:java.util.List.of("useSSL=false","sslMode=VERIFY_IDENTITY&sslMode=DISABLED","sslMode=VERIFY_IDENTITY&%73slMode=DISABLED",
                "sslMode=VERIFY_IDENTITY&password=secret","sslMode=VERIFY_IDENTITY&password2=secret","sslMode=VERIFY_IDENTITY&createDatabaseIfNotExist=true"))
            assertThatThrownBy(() -> DatabaseMigrationMain.validateTarget("jdbc:mysql://db.example.test/fcaj_poc?"+query,"fcaj_poc")).isInstanceOf(IllegalArgumentException.class);
    }
}
