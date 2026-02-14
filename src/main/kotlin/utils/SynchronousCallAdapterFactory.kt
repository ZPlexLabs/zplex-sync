package zechs.zplex.sync.utils

import java.lang.reflect.Type
import retrofit2.CallAdapter
import retrofit2.Retrofit

// https://stackoverflow.com/a/35104080
class SynchronousCallAdapterFactory : CallAdapter.Factory() {

    companion object {
        fun create() = SynchronousCallAdapterFactory()
    }

    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {

        // if returnType is retrofit2.Call, do nothing
        if (returnType.toString().contains("retrofit2.Call")) {
            return null
        }
        return object : CallAdapter<Any, Any?> {
            override fun responseType(): Type = returnType
            override fun adapt(call: retrofit2.Call<Any>): Any? {
                try {
                    return call.execute().body()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
    }

}