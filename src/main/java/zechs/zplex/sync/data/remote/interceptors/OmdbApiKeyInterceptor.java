package zechs.zplex.sync.data.remote.interceptors;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static zechs.zplex.sync.utils.Constants.OMDB_API_KEY;
import static zechs.zplex.sync.utils.Constants.OMDB_API_URL;

@Component
public final class OmdbApiKeyInterceptor implements Interceptor {

    private static final String OMDB_API_KEY_QUERY_PARAM = "apikey";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String requestUrl = request.url().toString();

        if (requestUrl.startsWith(OMDB_API_URL)) {
            HttpUrl newRequestUrl = addApiKeyIfNotPresent(request);
            Request newRequest = request.newBuilder().url(newRequestUrl).build();
            return chain.proceed(newRequest);
        }
        return chain.proceed(request);
    }

    private HttpUrl addApiKeyIfNotPresent(Request request) {
        HttpUrl requestUrl = request.url();
        boolean hasApiKey = requestUrl.queryParameter(OMDB_API_KEY_QUERY_PARAM) != null;
        if (!hasApiKey) {
            return requestUrl.newBuilder().addQueryParameter(OMDB_API_KEY_QUERY_PARAM, OMDB_API_KEY).build();
        }
        return requestUrl;
    }
}
