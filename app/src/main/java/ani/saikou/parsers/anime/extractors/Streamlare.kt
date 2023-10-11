package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.parsers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Streamlare(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val id = Regex("hashid&quot;:&quot;(.+?)&quot").find(client.get(server.embed.url).text)?.groupValues?.last()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody =
            "{\"id\":\"$id\"}".toRequestBody(mediaType)
        val linkData = client.post(
            "https://sltube.org/api/video/stream/get",
            requestBody = requestBody
        ).parsedSafe<DataLink>()
        linkData?.result?.original?.file?.let {
            return VideoContainer(
                listOf(
                    Video(
                        null,
                        VideoType.CONTAINER,
                        FileUrl(it, mapOf("Referer" to server.embed.url)),
                        getSize(it)
                    )
                )
            )
        }
        return VideoContainer(listOf())
    }
    @Serializable
    private data class DataLink(
        @SerialName("status") val status: String? = null,
        @SerialName("message") val message: String? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("token") val token: String? = null,
        @SerialName("result") val result: Result? = null
    )

    @Serializable
    private data class Result(
        @SerialName("Original") val original: Original? = null
    )

    @Serializable
    private data class Original(
        @SerialName("label") val label: String? = null,
        @SerialName("file") val file: String? = null,
        @SerialName("type") val type: String? = null
    )
}