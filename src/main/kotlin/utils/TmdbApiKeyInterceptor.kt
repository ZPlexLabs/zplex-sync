package zechs.zplex.sync.utils

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class TmdbApiKeyInterceptor(
    private val apiKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newRequestUrl = addApiKeyIfNotPresent(request)
        val newRequest = request.newBuilder().url(newRequestUrl).build()
        return chain.proceed(newRequest)
    }

    private fun addApiKeyIfNotPresent(request: Request): HttpUrl {
        val url = request.url
        val hasApiKey = url.queryParameter("api_key") != null
        return if (hasApiKey) {
            url
        } else {
            url.newBuilder().addQueryParameter("api_key", apiKey).build()
        }
    }

}