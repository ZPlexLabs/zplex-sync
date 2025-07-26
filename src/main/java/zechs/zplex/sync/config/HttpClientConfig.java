package zechs.zplex.sync.config;

import com.squareup.moshi.Moshi;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import zechs.zplex.sync.data.remote.OmdbApi;
import zechs.zplex.sync.data.remote.TmdbApi;
import zechs.zplex.sync.data.remote.interceptors.OmdbApiKeyInterceptor;
import zechs.zplex.sync.data.remote.interceptors.TmdbApiKeyInterceptor;
import zechs.zplex.sync.utils.SynchronousCallAdapterFactory;

import static zechs.zplex.sync.utils.Constants.IS_DEBUG;
import static zechs.zplex.sync.utils.Constants.OMDB_API_URL;
import static zechs.zplex.sync.utils.Constants.TMDB_API_URL;

@Configuration
public class HttpClientConfig {

    @Bean
    public Moshi getMoshi() {
        return new Moshi.Builder().build();
    }

    @Bean
    public HttpLoggingInterceptor getHttpLoggingInterceptor() {
        return new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);
    }

    @Bean
    public OkHttpClient getOkHttpClient(TmdbApiKeyInterceptor tmdbApiKeyInterceptor, OmdbApiKeyInterceptor omdbApiKeyInterceptor) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        if (IS_DEBUG) {
            // client.addInterceptor(getHttpLoggingInterceptor());
        }
        client.addInterceptor(tmdbApiKeyInterceptor);
        client.addInterceptor(omdbApiKeyInterceptor);
        return client.build();
    }

    @Bean
    public SynchronousCallAdapterFactory getSynchronousCallAdapterFactory() {
        return new SynchronousCallAdapterFactory();
    }

    @Bean
    public TmdbApi getTmdbApi(OkHttpClient client, Moshi moshi, SynchronousCallAdapterFactory synchronousCallAdapterFactory) {
        return new Retrofit.Builder()
                .baseUrl(TMDB_API_URL)
                .client(client)
                .addCallAdapterFactory(synchronousCallAdapterFactory)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TmdbApi.class);
    }

    @Bean
    public OmdbApi getOmdbApi(OkHttpClient client, Moshi moshi, SynchronousCallAdapterFactory synchronousCallAdapterFactory) {
        return new Retrofit.Builder()
                .baseUrl(OMDB_API_URL)
                .client(client)
                .addCallAdapterFactory(synchronousCallAdapterFactory)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OmdbApi.class);
    }
}
