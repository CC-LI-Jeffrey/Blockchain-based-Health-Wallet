package com.fyp.blockchainhealthwallet.model

import com.google.gson.annotations.SerializedName

data class UploadResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: UploadData? = null,
    @SerializedName("error")
    val error: String? = null
)

data class UploadData(
    @SerializedName("ipfsHash")
    val ipfsHash: String,
    @SerializedName("fileUrl")
    val fileUrl: String,
    @SerializedName("pinSize")
    val pinSize: Long,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("metadata")
    val metadata: FileMetadata
)

data class FileMetadata(
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("mimeType")
    val mimeType: String,
    @SerializedName("size")
    val size: Long
)
