package zechs.zplex.sync.utils

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.IOException

class GoogleDrive(private val applicationName: String) {

    private val SCOPES = listOf(DriveScopes.DRIVE_READONLY)
    private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    private var httpTransport: HttpTransport? = null

    init {
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Google Drive API", e)
        }
    }

    private fun authorize(): GoogleCredentials {
        try {
            return ServiceAccountCredentials.fromPkcs8(
                /* clientId */ System.getenv("GOOGLE_DRIVE_CLIENT_ID"),
                /* clientEmail */ System.getenv("GOOGLE_DRIVE_CLIENT_EMAIL"),
                /* privateKeyPkcs8 */ System.getenv("GOOGLE_DRIVE_PRIVATE_KEY_PKCS8"),
                /* privateKeyId */ System.getenv("GOOGLE_DRIVE_PRIVATE_KEY_ID"),
                /* scopes */ SCOPES
            )
        } catch (e: IOException) {
            throw RuntimeException("Failed to authorize Google Drive API", e)
        }
    }

    val service: Drive
        get() {
            val credential = authorize()
            val requestInitializer: HttpRequestInitializer = HttpCredentialsAdapter(credential)
            return Drive.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(applicationName)
                .build()
        }
}
