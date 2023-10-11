package ani.saikou.parsers.anime

import ani.saikou.client
import ani.saikou.media.Media
import ani.saikou.parsers.*
import ani.saikou.sortByTitle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

class HentaiWorld : AnimeParser() {

    override val name = "HentaiWorld"
    override val saveName = "hentaiworld_to"
    override val hostUrl = "https://www.hentaiworld.me"
    override val isDubAvailableSeparately = true
    override val allowsPreloading = false
    override val isNSFW = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val document = client.get(animeLink).document

        val widget = document.select(".widget.servers")
        return widget.select(".server[data-name=\"18\"] > ul a").map {
            val num = it.attr("data-base")
            val id = "$hostUrl/ajax/episode/info?id=${it.attr("data-id")}"
            Episode(number = num, link = id)
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val link = client.get(episodeLink, headers = mapOf("x-requested-with " to "XMLHttpRequest")).parsed<AWHtmlResponse>().link ?: return emptyList()
        return  listOf(VideoServer("HentaiWorld", link))

    }

    @Serializable
    data class AWHtmlResponse(
        @SerialName("grabber") val link: String? = null
    )

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = AnimeWorldExtractor(server)
    class AnimeWorldExtractor(override val server: VideoServer) : VideoExtractor() {
        override suspend fun extract(): VideoContainer {
            return VideoContainer(
                listOf(
                    Video(null, VideoType.CONTAINER, server.embed)
                )
            )

        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val encoded = query.replace(" ", "+")
        val document = client.get(
            "$hostUrl/search?&keyword=$encoded"
        ).document
        return document.select(".film-list > .item").map {
            val anchor = it.select("a.name").firstOrNull() ?: throw Error("Error")
            val title = anchor.text()
            val link = anchor.attr("href")
            val cover = it.select("a.poster img").attr("src")
            ShowResponse(title, link, cover)
        }
    }

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        var response = loadSavedShowResponse(mediaObj.id)
        if (response != null) {
            saveShowResponse(mediaObj.id, response, true)
        } else {
            setUserText("Cerco : ${mediaObj.mainName()}")
            response = search(mediaObj.mainName()).let { if (it.isNotEmpty()) it[0] else null }

            if (response == null) {
                setUserText("Cerco : ${mediaObj.nameRomaji}")
                response = search(mediaObj.nameRomaji).let { if (it.isNotEmpty()) it[0] else null }
            }

            if (response == null && mediaObj.name?.isNotEmpty() == true){
                setUserText("Cerco : ${mediaObj.name}")
                response = search(mediaObj.name).let { if (it.isNotEmpty()) it[0] else null }
            }
            saveShowResponse(mediaObj.id, response)
        }
        return response
    }


}