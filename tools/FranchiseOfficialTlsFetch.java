import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
import java.io.InputStream;

/** Narrow official-source transport using the JVM's unchanged TLS validation. */
public class FranchiseOfficialTlsFetch {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected one official URL");
        URI uri = URI.create(args[0]);
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                || !"www.norangtongdak.co.kr".equals(uri.getHost())
                || !("/menu/chicken_list.html".equals(uri.getPath())
                    || "/menu/best.html".equals(uri.getPath())
                    || "/menu/side.html".equals(uri.getPath()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Unapproved official TLS source");
        }
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "HealthcareMenuAudit/1.0 (once-daily official-menu check)");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Official HTTP status " + connection.getResponseCode());
            try (InputStream stream = connection.getInputStream()) {
                byte[] body = stream.readNBytes(2_000_001);
                if (body.length == 0 || body.length > 2_000_000) throw new IllegalStateException("Invalid official response size");
                System.err.println("DEFAULT_JVM_TLS status=200 cipher=" + connection.getCipherSuite());
                System.out.write(body);
            }
        } finally {
            connection.disconnect();
        }
    }
}
