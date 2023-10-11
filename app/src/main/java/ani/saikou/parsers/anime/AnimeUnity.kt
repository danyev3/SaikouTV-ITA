package ani.saikou.parsers.anime

import android.annotation.SuppressLint
import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.media.Media
import ani.saikou.parsers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64

class AnimeUnity : AnimeParser() {

    override val name = "AnimeUnity"
    override val saveName = "animeunity_tv"
    override val hostUrl = "https://www.animeunity.it"
    override val isDubAvailableSeparately = true
    override val allowsPreloading = false

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val response = client.get(animeLink)
        return Regex("number&quot;:&quot;(\\d{0,10}).+?scws_id&quot;:(\\d{0,20}),&quot").findAll(response.text)
            .map { Episode(
                number = it.groupValues[1],
                link = it.groupValues[2]
            ) }.
            drop(1)
            .toList()
    }
    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {

        val expire = (System.currentTimeMillis() / 1000 + 172800).toString()

        val ip = client.get("https://api64.ipify.org/").text
        val token0 = "$expire$ip Yc8U6r8KjAKAepEA".toByteArray()
        val token1 = MessageDigest.getInstance("MD5").digest(token0)
        val token2 = base64Encode(token1)
        val token = token2.replace("=", "").replace("+", "-").replace("/", "_")
        return  listOf(VideoServer("AnimeUnity", "https://scws.work/master/$episodeLink?token=$token&expires=$expire&n=1"))

    }
    @SuppressLint("NewApi")
    private fun base64Encode(array: ByteArray): String {
        return try {
            String(android.util.Base64.encode(array, android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
        } catch (e: Exception) {
            String(Base64.getEncoder().encode(array))
        }
    }
    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = AnimeUnityExtractor(server)
    class AnimeUnityExtractor(override val server: VideoServer) : VideoExtractor() {
        override suspend fun extract(): VideoContainer {
                        return VideoContainer(
                listOf(
                    Video(null, VideoType.M3U8, server.embed)
                )
            )

        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val page = client.get(hostUrl)
        val csrfToken = Regex("name=\"csrf-token\" content=\"([^\"]+)\"").find(page.text)?.groupValues?.get(1)
        val headers = mapOf(
            "content-type" to "application/json;charset=UTF-8",
            "x-csrf-token" to csrfToken!!
        )
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        val requestBody =
            "{\"title\":\"$query\"}".toRequestBody(mediaType)

        val response = client.post(
            "$hostUrl/archivio/get-animes",
            headers = headers,
            cookies = page.cookies,
            requestBody = requestBody
        ).parsed<Records>().records!!

        if (response.isEmpty()) return listOf()

        return response.filter { ((it.title + it.titleEng + it.titleIt).contains("(ITA)")) === selectDub }.map {
                ShowResponse(
                    it.title?:it.titleEng?:it.titleIt?:"noTitle",
                    "$hostUrl/anime/${it.id}--",
                    it.imageurl?:it.imageurlCover!!,
                    extra = mapOf("id" to it.anilistID.toString()),
                    total = it.episodesCount?.toInt()
                )
                }
        }


    private fun getMedia(searchList : List<ShowResponse>?, id:Int): ShowResponse?{
        if (searchList != null) {
            return searchList.firstOrNull {
                it.extra?.get("id") == id.toString()
            }
        }
        return null
    }

    private suspend fun ArrayList<String>.getMedia(id: Int): ShowResponse?{
        this.forEach {
            val media = getMedia(search(it), id)?:
            matchDoSearch(it, id)
            if (media != null) return media
        }
        return null
    }
    private suspend fun matchDoSearch(title:String, id: Int): ShowResponse? {
        return if (TitleTransform().checkMatch(title)) {
            getMedia(search(TitleTransform().replaceTitleSeason(title)), id)
        } else null
    }

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val media =
            getMedia(search(mediaObj.nameRomaji), mediaObj.id)?:
            matchDoSearch(mediaObj.nameRomaji, mediaObj.id)?:
            getMedia(mediaObj.name?.let { it1 -> search(it1) }, mediaObj.id)?:
            mediaObj.name?.let { it1 -> matchDoSearch(it1, mediaObj.id)}?:
            when (mediaObj.typeMAL){
                "Movie" ->
                    getMedia(search(mediaObj.nameRomaji.substringBeforeLast(":").trim()), mediaObj.id) ?:
                    getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim())}, mediaObj.id) ?:
                    getMedia(search(mediaObj.nameRomaji.substringAfterLast(":").trim()), mediaObj.id) ?:
                    getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim())}, mediaObj.id)
                else -> null
            } ?:
            mediaObj.synonyms.getMedia(mediaObj.id)
        if (media != null) {
            saveShowResponse(mediaObj.id, media, true)
        }
        return loadSavedShowResponse(mediaObj.id)
    }

    @Serializable
    data class Records (
        val records: List<Record>? = null,
        val tot: Long? = null
    )
    @Serializable
    data class Record (
        val id: Long? = null,

        @SerialName("user_id")
        val userID: Long? = null,

        val title: String? = null,
        val imageurl: String? = null,
        val plot: String? = null,
        val date: String? = null,

        @SerialName("episodes_count")
        val episodesCount: Long? = null,

        @SerialName("episodes_length")
        val episodesLength: Long? = null,

        val author: String? = null,

        @SerialName("created_at")
        val createdAt: String? = null,

        val status: String? = null,

        @SerialName("imageurl_cover")
        val imageurlCover: String? = null,

        val type: String? = null,
        val slug: String? = null,

        @SerialName("title_eng")
        val titleEng: String? = null,

        val day: String? = null,
        val favorites: Long? = null,
        val score: String? = null,
        val visite: Long? = null,
        val studio: String? = null,
        val dub: Long? = null,

        @SerialName("always_home")
        val alwaysHome: Long? = null,

        val members: Long? = null,

        @SerialName("anilist_id")
        val anilistID: Long? = null,

        val season: String? = null,

        @SerialName("title_it")
        val titleIt: String? = null,

        @SerialName("mal_id")
        val malID: Long? = null,

        val episodes: List<EpisodeJson>? = null,
        val genres: List<GenreJson>? = null
    )

    @Serializable
    data class EpisodeJson (
        val id: Long? = null,

        @SerialName("anime_id")
        val animeID: Long? = null,

        @SerialName("user_id")
        val userID: Long? = null,

        val number: String? = null,

        @SerialName("created_at")
        val createdAt: String? = null,

        val link: String? = null,
        val visite: Long? = null,
        val hidden: Long? = null,
        val public: Long? = null,

        @SerialName("scws_id")
        val scwsID: Long? = null,

        @SerialName("file_name")
        val fileName: String? = null,

        @SerialName("tg_post")
        val tgPost: Long? = null
    )

    @Serializable
    data class GenreJson (
        val id: Long? = null,
        val name: String? = null,
        val pivot: Pivot? = null
    )

    @Serializable
    data class Pivot (
        @SerialName("anime_id")
        val animeID: Long? = null,

        @SerialName("genre_id")
        val genreID: Long? = null
    )}