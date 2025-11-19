package com.example.a22i1066_b_socially.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.example.a22i1066_b_socially.UserProfileResponse
import com.example.a22i1066_b_socially.LoginRequest
data class SignupRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val dob: String,
    val username: String,
    val profilePicUrl: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val userId: String?,
    val error: String?
)

data class UploadResponse(
    val success: Boolean,
    val url: String?,
    val error: String?
)
data class ChatItem(
    val chatId: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherProfilePic: String?,
    val lastMessage: String?,
    val lastMessageType: String?,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val isOnline: Boolean = false
)

data class ChatListResponse(
    val success: Boolean,
    val chats: List<ChatItem>?,
    val error: String?
)

data class MessageItem(
    val id: String,
    val senderId: String,
    val text: String,
    val type: String,
    val imageUrls: List<String>,
    val timestamp: Long,
    val delivered: Boolean,
    val read: Boolean
)

data class MessagesResponse(
    val success: Boolean,
    val messages: List<MessageItem>?,
    val error: String?
)
data class UserListItem(
    val userId: String,
    val username: String,
    val profilePic: String?,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null
)

data class UserListResponse(
    val success: Boolean,
    val users: List<UserListItem>?,
    val error: String?
)

data class SendMessageRequest(
    val receiverId: String,
    val text: String,
    val imageUrls: List<String>
)

data class SendMessageResponse(
    val success: Boolean,
    val message: MessageItem?,
    val error: String?
)

data class EditMessageRequest(
    val messageId: String,
    val text: String
)

data class DeleteMessageRequest(
    val messageId: String
)

data class SimpleResponse(
    val success: Boolean,
    val error: String?,
    val message: String? = null
)

data class FollowStatusResponse(
    val success: Boolean,
    val isFollowing: Boolean = false,
    val error: String? = null
)

data class FollowRequest(
    val targetUserId: String
)

data class UpdateProfileRequest(
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null,
    val title: String? = null,
    val threadsUsername: String? = null,
    val website: String? = null,
    val profilePicUrl: String? = null
)

// Call-related data classes
data class InitiateCallRequest(
    val receiverId: String,
    val callType: String // "voice" or "video"
)

data class InitiateCallResponse(
    val success: Boolean,
    val callId: String?,
    val channelName: String?,
    val isOnline: Boolean?,
    val username: String?,
    val error: String?
)

data class UpdateCallStatusRequest(
    val callId: String,
    val status: String // "accepted", "rejected", "ended", "missed"
)

data class CallLogRequest(
    val receiverId: String,
    val callType: String,
    val duration: Long // in seconds
)

data class UserInfoResponse(
    val success: Boolean,
    val user: UserInfo?,
    val error: String?
)

data class UserInfo(
    val id: String,
    val username: String,
    val profile_pic_url: String?
)

data class CheckOnlineResponse(
    val success: Boolean,
    val isOnline: Boolean = false,
    val error: String?
)

data class IncomingCallData(
    val callId: String,
    val channelName: String,
    val callerId: String,
    val callerUsername: String,
    val callerProfileUrl: String?,
    val callType: String
)

data class IncomingCallPollResponse(
    val success: Boolean,
    val hasIncomingCall: Boolean = false,
    val call: IncomingCallData?,
    val error: String?
)

data class CallStatusResponse(
    val success: Boolean,
    val status: String?
)

interface ApiService {
    @GET("test.php")
    suspend fun testConnection(): Response<TestResponse>

    data class TestResponse(
        val success: Boolean,
        val message: String,
        val time: String
    )

    @POST("auth.php?action=signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @POST("auth.php?action=login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("user.php")
    suspend fun getUserProfile(
        @Query("action") action: String = "profile",
        @Query("userId") userId: String,
        @Query("currentUserId") currentUserId: String = ""
    ): Response<UserProfileResponse>

    @GET("user.php?action=checkFollow")
    suspend fun checkFollowStatus(
        @Header("Authorization") token: String,
        @Query("targetUserId") targetUserId: String
    ): Response<FollowStatusResponse>

    @POST("user.php?action=follow")
    suspend fun followUser(
        @Header("Authorization") token: String,
        @Body request: FollowRequest
    ): Response<SimpleResponse>

    @POST("user.php?action=unfollow")
    suspend fun unfollowUser(
        @Header("Authorization") token: String,
        @Body request: FollowRequest
    ): Response<SimpleResponse>

    @POST("user.php?action=updateProfile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<SimpleResponse>

    @GET("users.php?action=getAll")
    suspend fun getAllUsers(
        @Header("Authorization") token: String
    ): Response<UserListResponse>

    @GET("user.php?action=getFollowers")
    suspend fun getFollowers(
        @Query("userId") userId: String
    ): Response<UserListResponse>

    @GET("user.php?action=getFollowing")
    suspend fun getFollowing(
        @Query("userId") userId: String
    ): Response<UserListResponse>


    @Multipart
    @POST("upload.php")
    suspend fun uploadProfilePic(
        @Part("user_id") userId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
    @GET("messages.php?action=getChatList")
    suspend fun getChatList(
        @Header("Authorization") token: String
    ): Response<ChatListResponse>

    @GET("messages.php?action=getMessages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Query("chat_id") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("before_timestamp") beforeTimestamp: Long = Long.MAX_VALUE
    ): Response<MessagesResponse>

    @POST("messages.php?action=send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @POST("messages.php?action=edit")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Body request: EditMessageRequest
    ): Response<SimpleResponse>

    @POST("messages.php?action=delete")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Body request: DeleteMessageRequest
    ): Response<SimpleResponse>

    @Multipart
    @POST("messages.php?action=uploadImage")
    suspend fun uploadMessageImage(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("messages.php?action=updateActivity")
    suspend fun updateActivity(
        @Header("Authorization") token: String
    ): Response<SimpleResponse>

    // Highlights API
    @POST("highlights.php?action=create")
    suspend fun createHighlight(
        @Header("Authorization") token: String,
        @Body request: CreateHighlightRequest
    ): Response<CreateHighlightResponse>

    @GET("highlights.php?action=getUserHighlights")
    suspend fun getUserHighlights(
        @Query("userId") userId: String
    ): Response<HighlightsResponse>

    @GET("highlights.php?action=getHighlight")
    suspend fun getHighlight(
        @Query("highlightId") highlightId: String
    ): Response<SingleHighlightResponse>

    @DELETE("highlights.php?action=delete")
    suspend fun deleteHighlight(
        @Header("Authorization") token: String,
        @Query("highlightId") highlightId: String
    ): Response<SimpleResponse>

    // Story endpoints
    @GET("stories.php?action=getActive")
    suspend fun getActiveStories(
        @Header("Authorization") token: String
    ): Response<StoryResponse>

    @GET("stories.php?action=getUserStories")
    suspend fun getUserStories(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<StoryResponse>

    @POST("stories.php?action=upload")
    suspend fun uploadStory(
        @Header("Authorization") token: String,
        @Body request: UploadStoryRequest
    ): Response<UploadStoryResponse>

    @POST("stories.php?action=delete")
    suspend fun deleteStory(
        @Header("Authorization") token: String,
        @Body request: DeleteStoryRequest
    ): Response<DeleteStoryResponse>

    // Post endpoints
    @POST("posts.php?action=create")
    suspend fun createPost(
        @Header("Authorization") token: String,
        @Body request: CreatePostRequest
    ): Response<CreatePostResponse>

    @GET("posts.php?action=getFeed")
    suspend fun getPostsFeed(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("before_timestamp") beforeTimestamp: Long = Long.MAX_VALUE
    ): Response<PostsResponse>

    @GET("posts.php?action=getUserPosts")
    suspend fun getUserPosts(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<PostsResponse>

    @POST("posts.php?action=toggleLike")
    suspend fun togglePostLike(
        @Header("Authorization") token: String,
        @Body request: ToggleLikeRequest
    ): Response<ToggleLikeResponse>

    @POST("posts.php?action=addComment")
    suspend fun addComment(
        @Header("Authorization") token: String,
        @Body request: AddCommentRequest
    ): Response<AddCommentResponse>

    @GET("posts.php?action=getComments")
    suspend fun getComments(
        @Header("Authorization") token: String,
        @Query("postId") postId: String
    ): Response<CommentsResponse>

    @POST("posts.php?action=deletePost")
    suspend fun deletePost(
        @Header("Authorization") token: String,
        @Body request: DeletePostRequest
    ): Response<SimpleResponse>

    // Search endpoint
    @GET("search.php?action=search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 200
    ): Response<SearchUsersResponse>

    // Call endpoints
    @POST("calls.php?action=initiate")
    suspend fun initiateCall(
        @Header("Authorization") token: String,
        @Body request: InitiateCallRequest
    ): Response<InitiateCallResponse>

    @POST("calls.php?action=updateStatus")
    suspend fun updateCallStatus(
        @Header("Authorization") token: String,
        @Body request: UpdateCallStatusRequest
    ): Response<SimpleResponse>

    @GET("calls.php?action=getUserInfo")
    suspend fun getUserInfo(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<UserInfoResponse>

    @POST("calls.php?action=logCall")
    suspend fun logCall(
        @Header("Authorization") token: String,
        @Body request: CallLogRequest
    ): Response<SimpleResponse>

    @GET("calls.php?action=checkOnline")
    suspend fun checkUserOnline(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<CheckOnlineResponse>

    @GET("calls.php?action=pollIncomingCall")
    suspend fun pollIncomingCall(
        @Header("Authorization") token: String
    ): Response<IncomingCallPollResponse>

    @GET("calls.php?action=getCallStatus")
    suspend fun getCallStatus(
        @Header("Authorization") token: String,
        @Query("callId") callId: String
    ): Response<CallStatusResponse>
}

// Post-related data classes
data class CreatePostRequest(
    val postId: String,
    val caption: String,
    val imageUrls: List<String>,
    val timestamp: Long
)

data class CreatePostResponse(
    val success: Boolean,
    val postId: String?,
    val timestamp: Long?,
    val error: String?
)

data class PostItem(
    val id: String,
    val userId: String,
    val username: String,
    val profilePicUrl: String,
    val caption: String,
    val imageUrls: List<String>,
    val likesCount: Int,
    val commentsCount: Int,
    val timestamp: Long,
    val isLikedByCurrentUser: Boolean,
    val previewComments: List<CommentItem>? = emptyList()
)

data class PostsResponse(
    val success: Boolean,
    val posts: List<PostItem>?,
    val error: String?
)

data class ToggleLikeRequest(
    val postId: String
)

data class ToggleLikeResponse(
    val success: Boolean,
    val isLiked: Boolean?,
    val likesCount: Int?,
    val error: String?
)

data class AddCommentRequest(
    val postId: String,
    val commentId: String,
    val text: String,
    val timestamp: Long
)

data class CommentItem(
    val id: String,
    val postId: String,
    val userId: String,
    val username: String,
    val profilePicUrl: String,
    val text: String,
    val timestamp: Long
)

data class AddCommentResponse(
    val success: Boolean,
    val comment: CommentItem?,
    val error: String?
)

data class CommentsResponse(
    val success: Boolean,
    val comments: List<CommentItem>?,
    val error: String?
)

data class DeletePostRequest(
    val postId: String
)

// Search-related data classes
data class SearchUserItem(
    val id: String,
    val username: String,
    val displayName: String?,
    val subtitle: String?,
    val profilePicUrl: String?
)

data class SearchUsersResponse(
    val success: Boolean,
    val users: List<SearchUserItem>?,
    val error: String?
)

// Highlights API data classes
data class CreateHighlightRequest(
    val title: String,
    val imageUrls: List<String>,
    val date: Long // Unix timestamp in seconds
)

data class CreateHighlightResponse(
    val success: Boolean,
    val highlightId: String?,
    val message: String?,
    val error: String?
)

data class HighlightItem(
    val id: String,
    val userId: String? = null,
    val user_id: String? = null,
    val title: String,
    val imageUrls: List<String>,
    val date: Long
)

data class HighlightsResponse(
    val success: Boolean,
    val highlights: List<HighlightItem>?,
    val error: String?
)

data class SingleHighlightResponse(
    val success: Boolean,
    val highlight: HighlightItem?,
    val error: String?
)
