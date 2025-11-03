package com.fyp.blockchainhealthwallet.network

import com.fyp.blockchainhealthwallet.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    
    @Multipart
    @POST("api/ipfs/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody? = null
    ): Response<UploadResponse>
}
