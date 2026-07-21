package com.smartcampro.app.data.api
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var baseUrl = ""
    private var retrofit: Retrofit? = null

    fun setBaseUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        if (cleanUrl != baseUrl) {
            baseUrl = cleanUrl
            retrofit = null
        }
    }

    fun getApi(): ApiService {
        if (retrofit == null) {
            val url = if (baseUrl.isEmpty()) "http://10.0.0.1:3000" else baseUrl
            try {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                retrofit = Retrofit.Builder()
                    .baseUrl("$url/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            } catch (e: Exception) {
                // Last resort fallback
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build()
                retrofit = Retrofit.Builder()
                    .baseUrl("http://10.0.0.1:3000/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
        }
        return retrofit!!.create(ApiService::class.java)
    }

    fun reset() {
        baseUrl = ""
        retrofit = null
    }
}
