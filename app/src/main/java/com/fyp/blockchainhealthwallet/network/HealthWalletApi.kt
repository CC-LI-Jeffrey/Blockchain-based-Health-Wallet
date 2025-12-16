package com.fyp.blockchainhealthwallet.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import retrofit2.http.*
import java.io.File

/**
 * API interface for backend services.
 * Backend handles: Admin operations, IPFS uploads, and view functions.
 */
interface HealthWalletApi {
    
    /**
     * Upload encrypted file to IPFS via backend.
     * Backend cannot decrypt this file - it only stores it.
     * @return IPFS hash
     */
    @Multipart
    @POST("/api/ipfs/upload")
    suspend fun uploadToIPFS(
        @Part file: MultipartBody.Part
    ): Response<IPFSUploadResponse>
    
    /**
     * Get file from IPFS via backend gateway.
     * @param ipfsHash The IPFS hash to retrieve
     * @return Raw file content
     */
    @GET("/api/ipfs/file/{ipfsHash}")
    suspend fun getFromIPFS(
        @Path("ipfsHash") ipfsHash: String
    ): Response<okhttp3.ResponseBody>
    
    /**
     * Get total number of records (view function).
     */
    @GET("/api/records/total")
    suspend fun getTotalRecords(): Response<TotalRecordsResponse>
    
    /**
     * Get patient's record IDs (view function).
     */
    @GET("/api/records/patient/{address}")
    suspend fun getPatientRecords(
        @Path("address") patientAddress: String
    ): Response<PatientRecordsResponse>
    
    /**
     * Get specific record details (view function).
     */
    @GET("/api/records/{recordId}")
    suspend fun getRecord(
        @Path("recordId") recordId: Int
    ): Response<RecordResponse>
    
    /**
     * Check if an address has access to a record (view function).
     */
    @GET("/api/records/has-access/{patientAddress}/{requesterAddress}/{recordId}")
    suspend fun hasAccess(
        @Path("patientAddress") patientAddress: String,
        @Path("requesterAddress") requesterAddress: String,
        @Path("recordId") recordId: Int
    ): Response<HasAccessResponse>
    
    /**
     * Get access grant details (view function).
     */
    @GET("/api/records/access-grant/{patientAddress}/{granteeAddress}")
    suspend fun getAccessGrant(
        @Path("patientAddress") patientAddress: String,
        @Path("granteeAddress") granteeAddress: String
    ): Response<AccessGrantResponse>
    
    /**
     * Admin endpoint: Authorize a provider.
     * Only admin can call this.
     */
    @POST("/api/admin/authorize-provider")
    suspend fun authorizeProvider(
        @Body request: AuthorizeProviderRequest
    ): Response<TransactionResponse>
    
    /**
     * Admin endpoint: Revoke provider authorization.
     * Only admin can call this.
     */
    @POST("/api/admin/revoke-provider")
    suspend fun revokeProviderAuthorization(
        @Body request: RevokeProviderRequest
    ): Response<TransactionResponse>
    
    /**
     * Check if an address is an authorized provider.
     */
    @GET("/api/records/is-provider/{address}")
    suspend fun isAuthorizedProvider(
        @Path("address") providerAddress: String
    ): Response<IsProviderResponse>
}

// Request/Response data classes

data class IPFSUploadResponse(
    val success: Boolean,
    val message: String?,
    val ipfsHash: String?,
    val fileUrl: String?,
    val pinSize: String?,
    val timestamp: String?,
    val error: String?
)

data class TotalRecordsResponse(
    val success: Boolean,
    val totalRecords: Int,
    val error: String?
)

data class PatientRecordsResponse(
    val success: Boolean,
    val recordIds: List<Int>,
    val error: String?
)

data class RecordResponse(
    val success: Boolean,
    val record: RecordData?,
    val error: String?
)

data class RecordData(
    val recordId: Int,
    val patientAddress: String,
    val ipfsHash: String,
    val recordType: Int,
    val timestamp: Long,
    val issuedBy: String,
    val isActive: Boolean,
    val encryptedKey: String,
    val version: Int
)

data class HasAccessResponse(
    val success: Boolean,
    val hasAccess: Boolean,
    val error: String?
)

data class AccessGrantResponse(
    val success: Boolean,
    val grant: AccessGrantData?,
    val error: String?
)

data class AccessGrantData(
    val grantee: String,
    val recordIds: List<Int>,
    val expiryTime: Long,
    val isActive: Boolean
)

data class AuthorizeProviderRequest(
    val providerAddress: String
)

data class RevokeProviderRequest(
    val providerAddress: String
)

data class TransactionResponse(
    val success: Boolean,
    val transactionHash: String?,
    val gasUsed: String?,
    val error: String?
)

data class IsProviderResponse(
    val success: Boolean,
    val isProvider: Boolean,
    val error: String?
)
