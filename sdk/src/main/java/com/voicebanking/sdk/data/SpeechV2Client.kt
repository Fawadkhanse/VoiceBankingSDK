package com.voicebanking.sdk.data

import com.voicebanking.sdk.models.SpeechV2Request
import com.voicebanking.sdk.models.SpeechV2Response
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

private interface SpeechV2ApiService {
    @POST("v2/projects/{projectId}/locations/{location}/recognizers/_:recognize")
    suspend fun recognize(
        @Header("Authorization") bearerToken: String,
        @Path("projectId")       projectId:   String,
        @Path("location")        location:    String,
        @Body                    request:     SpeechV2Request
    ): Response<SpeechV2Response>
}

internal class SpeechV2Client(
    private val projectId:   String,
    private val auth:        ServiceAccountAuth,
    enableLogging:           Boolean = false
) {
    private val service: SpeechV2ApiService

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (enableLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }
        service = Retrofit.Builder()
            .baseUrl("https://speech.googleapis.com/")
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpeechV2ApiService::class.java)
    }

    suspend fun recognize(request: SpeechV2Request): Response<SpeechV2Response> {
        val token = auth.getBearerToken()
        return service.recognize(
            bearerToken = "Bearer $token",
            projectId   = projectId,
            location    = "global",
            request     = request
        )
    }
}
