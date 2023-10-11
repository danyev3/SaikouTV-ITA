package ani.saikou.parsers.anime

import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.media.Media
import ani.saikou.parsers.*
import ani.saikou.parsers.anime.extractors.*
import com.google.common.net.MediaType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ani.saikou.parsers.anime.extractors.DoodStream

class AnimeWorld : AnimeParser() {

    override val name = "AnimeWorld"
    override val saveName = "animeworld_to"
    override val hostUrl = "https://www.animeworld.tv"
    override val isDubAvailableSeparately = true
    override val allowsPreloading = false

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val document = client.get("$hostUrl$animeLink").document

        val widget = document.select(".widget.servers")
        return widget.select(".server[data-name=\"9\"] .episode").map {
            val num = it.select("a").attr("data-episode-num")
            val serverNames = mutableMapOf<String?, String?>()
            document.select("span.tabs.servers-tabs span").forEach {server -> serverNames[server.attr("data-name")] = server.text() }
            val linkIds = mutableMapOf<String,String>()
            document.select("div.server").forEach {server ->
                val name = serverNames[server.attr("data-name")]!!
                val episodeLink = server.select("li.episode a").toList().firstOrNull{ ep -> ep.attr("data-episode-num")==num}
                episodeLink?.let {
                    linkIds[name] = it.attr("data-id")
                }
            }

            Episode(number = num, link = "$hostUrl$animeLink", extra = linkIds)
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val extraMap = extra as? Map<String, String>

        return extraMap?.map { id ->
            client.get("$hostUrl/api/episode/info?id=${id.value}").parsed<AWHtmlResponse>().link?.let { VideoServer(id.key, it) }
        }?.filterNotNull() ?: listOf()

    }

    @Serializable
    data class AWHtmlResponse(
        @SerialName("grabber") val link: String? = null
    )

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? =
            when (server.name) {
                "Streamtape"        -> StreamTape(server)
                "Doodstream"        -> DoodStream(server)
                "AnimeWorld Server" -> AnimeWorldExtractor(server)
                "StreamSB"          -> StreamSB(server)
                "Streamlare"        -> Streamlare(server)
                "FileMoon"          -> FileMoon(server)
                else             -> null
            }



    class AnimeWorldExtractor(override val server: VideoServer) : VideoExtractor() {
        override suspend fun extract(): VideoContainer {
            val type = if (server.embed.url.contains("m3u8")) VideoType.M3U8 else VideoType.CONTAINER
            return VideoContainer(
                listOf(
                    Video(null, type, server.embed, getSize(server.embed))
                )
            )
        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val encoded = query.replace(" ", "+")
        val document = client.get(
            "$hostUrl${if (selectDub) "/filter?language=it" else "/filter?language=jp&"}&keyword=$encoded"
        ).document
        return document.select(".film-list > .item").map {
            val anchor = it.select("a.name").firstOrNull() ?: throw Error("Error")
            val title = anchor.text()
            val link = anchor.attr("href")
            val cover = it.select("a.poster img").attr("src")
            ShowResponse(title, link, cover)
        }
    }

    private suspend fun getMedia(searchList : List<ShowResponse>?, id:Int): ShowResponse?{
        if (searchList != null) {
            return searchList.firstOrNull {
                client.get(hostUrl + it.link).document.selectFirst("#anilist-button")?.attr("href")?.substringAfterLast("/")
                    ?.toInt() == id
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
                "Movie" -> "&type=4"
                else -> "&type=0&type=1&type=2&type=3&type=5"
            }
        val media =
            getMedia(search(mediaObj.nameRomaji + mediaType), mediaObj.id)?:
            matchDoSearch(mediaObj.nameRomaji, mediaObj.id, mediaType)?:
            getMedia(mediaObj.name?.let { it1 -> search(it1 + mediaType) }, mediaObj.id)?:
            mediaObj.name?.let { it1 -> matchDoSearch(it1, mediaObj.id, mediaType)}?:
            when (mediaObj.typeMAL){
                    "Movie" ->
                        getMedia(search(mediaObj.nameRomaji.substringBeforeLast(":").trim() + mediaType + "&year=${mediaObj.endDate?.year}"), mediaObj.id) ?:
                        getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim() + mediaType + "&year=${mediaObj.endDate?.year}")}, mediaObj.id) ?:
                        getMedia(search(mediaObj.nameRomaji.substringAfterLast(":").trim() + mediaType), mediaObj.id) ?:
                        getMedia(mediaObj.name?.let {search(it.substringAfterLast(":").trim() + mediaType)}, mediaObj.id)                 else -> null
                } ?:
                mediaObj.synonyms.getMedia(mediaObj.id, mediaType)

        if (media != null) {
            saveShowResponse(mediaObj.id, media, true)
        }
        return loadSavedShowResponse(mediaObj.id)
    }



}