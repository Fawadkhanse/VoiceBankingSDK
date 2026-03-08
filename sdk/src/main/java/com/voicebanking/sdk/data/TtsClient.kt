package com.voicebanking.sdk.data

import com.voicebanking.sdk.models.TtsSynthesizeRequest
import com.voicebanking.sdk.models.TtsSynthesizeResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private interface TtsApiService {
    @POST("v1/text:synthesize")
    suspend fun synthesize(
        @Query("key") apiKey:  String,
        @Body         request: TtsSynthesizeRequest
    ): Response<TtsSynthesizeResponse>
}

internal class TtsClient(
    private val apiKey:      String,
    enableLogging:           Boolean = false
) {
    private val service: TtsApiService

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (enableLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }
        service = Retrofit.Builder()
            .baseUrl("https://texttospeech.googleapis.com/")
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TtsApiService::class.java)
    }

    suspend fun synthesize(request: TtsSynthesizeRequest): Response<TtsSynthesizeResponse> =
        service.synthesize(apiKey, request)
}
