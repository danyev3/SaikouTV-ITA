package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ani.saikou.media.Media

class AniPlay : AnimeParser() {

    override val name = "AniPlay"
    override val saveName = "aniplay_to"
    override val hostUrl = "https://aniplay.co"
    override val isDubAvailableSeparately = true
    override val allowsPreloading = false

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val response = client.get(animeLink).parsed<ApiAnime>()
        val episodes =  if (response.seasons.isNullOrEmpty()) response.episodes.mapNotNull { it.toEpisode() } else response.seasons.map{ it.toEpisodeList(animeLink) }.flatten()
        (episodes as MutableList).sortBy { it.number.toInt() }
        return episodes
    }

    private fun ApiEpisode.toEpisode() : Episode? {
        val number = this.number.toIntOrNull() ?: return null
        return Episode(
            link = "$hostUrl/api/episode/${this.id}",
            number = number.toString(),
            title = this.title
        )
    }
    private suspend fun ApiSeason.toEpisodeList(url: String) : List<Episode> {
        return Json{ignoreUnknownKeys = true }.decodeFromString<List<ApiEpisode>>(client.get("$url/season/${this.id}").text).mapNotNull { it.toEpisode() }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val link = client.get(episodeLink).parsed<ApiEpisodeUrl>().url
        return  listOf(VideoServer("AniPlay",  FileUrl(link, mapOf("referer" to hostUrl))))

    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = AniPlayExtractor(server)
    class AniPlayExtractor(override val server: VideoServer) : VideoExtractor() {
        override suspend fun extract(): VideoContainer {
            val type = if (server.embed.url.contains("m3u8")) VideoType.M3U8 else VideoType.CONTAINER
            return VideoContainer(
                listOf(
                    Video(null, type, server.embed)
                )
            )

        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val encoded = query.replace(" ", "+")
        val jsonstring = client.get("$hostUrl/api/anime/advanced-search?page=0&size=36&query=$encoded").text
        val response = Json{ ignoreUnknownKeys = true }.decodeFromString<List<ApiSearchResult>>(jsonstring)
        return response.filter { (it.title.contains("(ITA)") === selectDub) }.map {
            val title = it.title
            val link = "$hostUrl/api/anime/${it.id}"
            val cover = it.posters.first().posterUrl
            ShowResponse(title, link, cover)
        }
    }

    private suspend fun getMedia(searchList : List<ShowResponse>?, id:Int): ShowResponse?{
        if (searchList != null) {
            return searchList.firstOrNull {
                getID(client.get(it.link).parsed()) == id
            }
        }
        return null
    }

    private suspend fun ArrayList<String>.getMedia(id: Int, mediaType: String): ShowResponse?{
        this.forEach {
            val media = getMedia(search(it + mediaType), id)?:
            matchDoSearch(it, id, mediaType)
            if (media != null) return media
        }
        return null
    }

    private suspend fun matchDoSearch(title:String, id: Int, mediaType: String): ShowResponse? {
        return if (TitleTransform().checkMatch(title)) {
            getMedia(search(TitleTransform().replaceTitleSeason(title) + mediaType), id)
        } else null
    }

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val mediaType : String =
            when (mediaObj.typeMAL){
                "Movie" -> "&typeIds=2"
                else -> "&typeIds=6,4,3,1,5"
            }
        val media =
            getMedia(search(mediaObj.nameRomaji + mediaType), mediaObj.id)?:
            matchDoSearch(mediaObj.nameRomaji, mediaObj.id, mediaType)?:
            getMedia(mediaObj.name?.let { it1 -> search(it1 + mediaType) }, mediaObj.id)?:
            mediaObj.name?.let { it1 -> matchDoSearch(it1, mediaObj.id, mediaType)}?:
            when (mediaObj.typeMAL){
                "Movie" ->
                    getMedia(search(mediaObj.nameRomaji.substringBeforeLast(":").trim() + mediaType + "&endYear=${mediaObj.endDate?.year}"), mediaObj.id) ?:
                    getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim() + mediaType + "&endYear=${mediaObj.endDate?.year}")}, mediaObj.id) ?:
                    getMedia(search(mediaObj.nameRomaji.substringAfterLast(":").trim() + mediaType), mediaObj.id) ?:
                    getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim() + mediaType)}, mediaObj.id)
                else -> null
            } ?:
            mediaObj.synonyms.getMedia(mediaObj.id, mediaType)
        if (media != null) {
            saveShowResponse(mediaObj.id, media, true)
        }
        return loadSavedShowResponse(mediaObj.id)
    }


    private fun getID(response: ApiAnime): Int?{
        return response.websites.find { it.websiteId == 4 }?.url?.removePrefix("https://anilist.co/anime/")?.split("/")?.first()?.toIntOrNull()
    }

    @Serializable
    data class ApiWebsite(
        @SerialName("listWebsiteId") val websiteId: Int,
        @SerialName("url") val url: String
    )

    @Serializable
    data class ApiSearchResult(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String,
        @SerialName("verticalImages") val posters: List<ApiPoster>
    )
    @Serializable
    data class ApiPoster(
        @SerialName("imageFull") val posterUrl: String
    )
    @Serializable
    data class ApiAnime(
        @SerialName("title") val title: String,
        @SerialName("episodes") val episodes: List<ApiEpisode>,
        @SerialName("seasons") val seasons: List<ApiSeason>?,
        @SerialName("listWebsites") val websites: List<ApiWebsite>,
    )
    @Serializable
    data class ApiEpisode(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String?,
        @SerialName("episodeNumber") val number: String,
    )
    @Serializable
    data class ApiSeason(
        @SerialName("id") val id: Int,
        @SerialName("name") val name: String
    )
    @Serializable
    data class ApiEpisodeUrl(
        @SerialName("videoUrl") val url: String
    )
}