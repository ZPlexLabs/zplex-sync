package zechs.zplex.sync.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Configuration
public class GoogleDriveConfig {
    private final List<String> scopes = List.of(DriveScopes.DRIVE_READONLY);
    private final JsonFactory jsonFactory;
    private final HttpTransport httpTransport;

    public GoogleDriveConfig() {
        this.jsonFactory = GsonFactory.getDefaultInstance();
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Google Drive API", e);
        }
    }

    private GoogleCredentials authorize() {
        try {
            return ServiceAccountCredentials.fromPkcs8(
                    /* clientId */ System.getenv("GOOGLE_DRIVE_CLIENT_ID"),
                    /* clientEmail */ System.getenv("GOOGLE_DRIVE_CLIENT_EMAIL"),
                    /* privateKeyPkcs8 */ System.getenv("GOOGLE_DRIVE_PRIVATE_KEY_PKCS8"),
                    /* privateKeyId */ System.getenv("GOOGLE_DRIVE_PRIVATE_KEY_ID"),
                    /* scopes */ scopes
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to authorize Google Drive API", e);
        }
    }

    @Bean
    public Drive getService() {
        final GoogleCredentials credential = authorize();
        final HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);
        final String applicationName = "ZPlex Sync Agent";
        return new Drive.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName(applicationName)
                .build();
    }
}
