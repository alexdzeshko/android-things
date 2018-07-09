package com.epam.goldeneye.facerecognition

import retrofit2.http.GET
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Retrofit
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import retrofit2.Call


object RecognitionService {

    private val retrofit: Retrofit
    val api: Api

    private const val HOST = "http://10.7.13.203:8888"

    init {
        val httpClient = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor()
                        .setLevel(HttpLoggingInterceptor.Level.BODY))
                .build()
        retrofit = Retrofit.Builder()
                .baseUrl(HOST)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        api = retrofit.create(Api::class.java)
    }

    interface Api {

        @GET("/")
        fun ping(): Call<PingDto>

        fun recognize()
    }

    data class PingDto(val data: String)

    data class RecognitionDto(val title: String)
}