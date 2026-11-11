import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Build-time tool: public roots from the runtime JRE plus the regional RDS bundle. */
public class BuildRdsTruststore {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("source-cacerts bundle destination required");
        // 'changeit' is the public JRE cacerts integrity password, not an app secret.
        var source = KeyStore.getInstance(Path.of(args[0]).toFile(), "changeit".toCharArray());
        Security.setProperty("keystore.pkcs12.certProtectionAlgorithm", "NONE");
        Security.setProperty("keystore.pkcs12.macAlgorithm", "NONE");
        var target = KeyStore.getInstance("PKCS12");
        target.load(null, null);
        var originalAliases = Collections.list(source.aliases());
        if (originalAliases.isEmpty()) throw new IllegalStateException("Default roots missing");
        for (var alias : originalAliases) {
            if (!source.isCertificateEntry(alias)) throw new IllegalStateException("Only public certificates allowed");
            target.setCertificateEntry(alias, source.getCertificate(alias));
        }
        int rdsCount = 0;
        try (var input = Files.newInputStream(Path.of(args[1]))) {
            for (var cert : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                var ca = (X509Certificate) cert;
                if (ca.getBasicConstraints() < 0) throw new IllegalStateException("RDS bundle entry is not a CA");
                ca.checkValidity();
                target.setCertificateEntry("fcaj-rds-" + (++rdsCount), ca);
            }
        }
        if (rdsCount == 0) throw new IllegalStateException("RDS CA bundle empty");
        try (var output = Files.newOutputStream(Path.of(args[2]))) { target.store(output, null); }
        var check = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(args[2]))) { check.load(input, null); }
        if (check.size() != originalAliases.size() + rdsCount) throw new IllegalStateException("Passwordless reload lost roots");
        for (var alias : Collections.list(target.aliases())) {
            if (!check.isCertificateEntry(alias) || !target.getCertificate(alias).equals(check.getCertificate(alias)))
                throw new IllegalStateException("Trust material changed during export");
        }
        System.out.println("Verified public passwordless truststore: defaultRoots=" + originalAliases.size() + "; rdsRoots=" + rdsCount);
    }
}
