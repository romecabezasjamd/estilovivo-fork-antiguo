package com.estilovivo.app.core.data.net

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato REST del backend (rutas relativas a `https://host/api/`). Sólo uso interno de los repositorios. */
@Suppress("TooManyFunctions")
internal interface ApiService {

    // ───── auth ─────
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): AuthResponseDto
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponseDto
    @POST("auth/logout") suspend fun logout(): OkDto
    @POST("auth/change-password") suspend fun changePassword(@Body body: ChangePasswordRequest): AuthResponseDto
    @POST("auth/forgot-password") suspend fun forgotPassword(@Body body: EmailRequest): OkDto
    @POST("auth/reset-password") suspend fun resetPassword(@Body body: ResetPasswordRequest): OkDto
    @POST("auth/verify-email") suspend fun verifyEmail(@Body body: TokenRequest): OkDto
    @POST("auth/resend-verification") suspend fun resendVerification(@Body body: EmailRequest): OkDto
    @DELETE("auth/profile") suspend fun deleteAccount(): OkDto
    @GET("auth/me") suspend fun me(): UserDto
    @PUT("auth/profile") suspend fun updateProfile(@Body body: JsonObject): UserDto

    @Multipart
    @PUT("auth/profile")
    suspend fun updateProfileMultipart(
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part avatar: MultipartBody.Part?,
        @Part fullBodyAvatar: MultipartBody.Part?,
    ): UserDto

    @PUT("auth/preferences") suspend fun updatePreferences(@Body body: JsonObject): UserDto

    // ───── prendas ─────
    @GET("products") suspend fun products(@Query("limit") limit: Int, @Query("cursor") cursor: String?): PageDto<ProductDto>
    @GET("products/shop") suspend fun shop(@Query("search") search: String?, @Query("category") category: String?): List<ProductDto>

    @Multipart
    @POST("products")
    suspend fun addProduct(
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part images: MultipartBody.Part?,
    ): ProductDto

    @Multipart
    @PUT("products/{id}")
    suspend fun updateProductMultipart(
        @Path("id") id: String,
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part images: MultipartBody.Part?,
    ): ProductDto

    @PUT("products/{id}") suspend fun updateProduct(@Path("id") id: String, @Body body: JsonObject): ProductDto
    @DELETE("products/{id}") suspend fun deleteProduct(@Path("id") id: String): OkDto
    @POST("products/{id}/wear") suspend fun wearProduct(@Path("id") id: String): ProductDto

    // ───── looks ─────
    @GET("looks") suspend fun looks(@Query("limit") limit: Int, @Query("cursor") cursor: String?): PageDto<LookDto>
    @GET("looks/feed") suspend fun feed(@Query("limit") limit: Int, @Query("cursor") cursor: String?): PageDto<LookDto>
    @POST("looks") suspend fun createLook(@Body body: JsonObject): LookDto

    @Multipart
    @POST("looks")
    suspend fun createLookMultipart(
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part images: MultipartBody.Part?,
    ): LookDto

    @PUT("looks/{id}") suspend fun updateLook(@Path("id") id: String, @Body body: JsonObject): LookDto
    @DELETE("looks/{id}") suspend fun deleteLook(@Path("id") id: String): OkDto

    // ───── probador ─────
    @GET("tryon-presets") suspend fun tryonPresets(): List<TryonPresetDto>
    @POST("tryon-presets") suspend fun createTryonPreset(@Body body: TryonPresetRequest): TryonPresetDto
    @PUT("tryon-presets/{id}") suspend fun updateTryonPreset(@Path("id") id: String, @Body body: TryonPresetRequest): TryonPresetDto
    @DELETE("tryon-presets/{id}") suspend fun deleteTryonPreset(@Path("id") id: String): OkDto

    // ───── planner / viajes ─────
    @GET("planner/me") suspend fun planner(): List<PlannerEntryDto>
    @POST("planner") suspend fun savePlannerEntry(@Body body: PlannerRequest): PlannerEntryDto
    @DELETE("planner/{date}") suspend fun deletePlannerEntry(@Path("date") date: String): OkDto

    @GET("trips/me") suspend fun trips(): List<TripDto>
    @POST("trips") suspend fun createTrip(@Body body: TripRequest): TripDto
    @PUT("trips/{id}") suspend fun updateTrip(@Path("id") id: String, @Body body: TripRequest): TripDto
    @DELETE("trips/{id}") suspend fun deleteTrip(@Path("id") id: String): OkDto
    @POST("trips/{tripId}/items") suspend fun addTripItem(@Path("tripId") tripId: String, @Body body: JsonObject): TripItemDto
    @PUT("trips/{tripId}/items/{itemId}") suspend fun updateTripItem(@Path("tripId") tripId: String, @Path("itemId") itemId: String, @Body body: JsonObject): TripItemDto
    @DELETE("trips/{tripId}/items/{itemId}") suspend fun deleteTripItem(@Path("tripId") tripId: String, @Path("itemId") itemId: String): OkDto

    // ───── social ─────
    @POST("social/like") suspend fun like(@Body body: LikeRequest): LikeResponseDto
    @POST("social/comment") suspend fun addComment(@Body body: CommentRequest): CommentDto
    @GET("social/comments/{lookId}") suspend fun comments(@Path("lookId") lookId: String): List<CommentDto>
    @DELETE("social/comment/{id}") suspend fun deleteComment(@Path("id") id: String): OkDto
    @POST("social/favorite") suspend fun favorite(@Body body: FavoriteRequest): FavoriteResponseDto
    @GET("social/favorites") suspend fun favorites(): List<FavoriteDto>
    @POST("social/follow") suspend fun follow(@Body body: FollowRequestBody): FollowResponseDto
    @GET("social/follow-requests") suspend fun followRequests(): List<FollowRequestDto>
    @PUT("social/follow-requests/{id}/approve") suspend fun approveFollowRequest(@Path("id") id: String): JsonObject
    @PUT("social/follow-requests/{id}/reject") suspend fun rejectFollowRequest(@Path("id") id: String): JsonObject
    @GET("users/top") suspend fun topUsers(): List<TopUserDto>
    @GET("users/{id}") suspend fun userProfile(@Path("id") id: String): PublicProfileDto
    @GET("stats") suspend fun stats(): StatsDto

    // ───── historias ─────
    @GET("stories") suspend fun stories(): List<StoryDto>

    @Multipart
    @POST("stories")
    suspend fun createStory(
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part image: MultipartBody.Part?,
    ): StoryDto

    @POST("stories/{id}/view") suspend fun viewStory(@Path("id") id: String): JsonObject
    @DELETE("stories/{id}") suspend fun deleteStory(@Path("id") id: String): OkDto
    @POST("stories/{id}/reaction") suspend fun reactToStory(@Path("id") id: String, @Body body: EmojiRequest): ReactionResponseDto

    // ───── chat ─────
    @GET("chat/conversations") suspend fun conversations(): List<ConversationDto>
    @POST("chat/conversations") suspend fun createConversation(@Body body: ConversationRequest): ConversationDto
    @GET("chat/conversations/{id}/messages") suspend fun messages(@Path("id") id: String): List<MessageDto>
    @POST("chat/conversations/{id}/messages") suspend fun sendMessage(@Path("id") id: String, @Body body: SendMessageRequest): MessageDto

    @Multipart
    @POST("chat/upload")
    suspend fun uploadChatImage(@Part image: MultipartBody.Part): UploadResponseDto

    // ───── notificaciones ─────
    @GET("notifications") suspend fun notifications(): List<NotificationDto>
    @PUT("notifications/read-all") suspend fun markAllNotificationsRead(): OkDto
    @PUT("notifications/{id}/read") suspend fun markNotificationRead(@Path("id") id: String): NotificationDto

    // ───── retos ─────
    @GET("challenges/current") suspend fun currentChallenge(): ChallengeDto
    @GET("challenges/my-submissions") suspend fun mySubmissions(): List<SubmissionDto>
    @GET("challenges/history") suspend fun challengeHistory(): ChallengeHistoryDto

    @Multipart
    @POST("challenges/submit")
    suspend fun submitChallenge(
        @PartMap fields: @JvmSuppressWildcards Map<String, RequestBody>,
        @Part image: MultipartBody.Part?,
    ): SubmissionDto

    @DELETE("challenges/submissions/{id}") suspend fun deleteSubmission(@Path("id") id: String): OkDto

    // ───── gamificación / tendencias ─────
    @GET("gamification/progress") suspend fun gamificationProgress(): ProgressDto
    @GET("gamification/achievements") suspend fun achievements(): List<CatalogItemDto>
    @GET("gamification/badges") suspend fun badges(): List<CatalogItemDto>
    @POST("gamification/check-achievements") suspend fun checkAchievements(): CheckAchievementsDto
    @POST("gamification/login") suspend fun gamificationLogin(): DailyLoginDto
    @GET("trends") suspend fun trends(): List<TrendDto>
}
