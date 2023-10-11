package ani.saikou.parsers.anime

import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.media.Media
import ani.saikou.parsers.*
import ani.saikou.parsers.anime.extractors.StreamSB
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.FileMoon
import org.jsoup.nodes.Document

class AnimeSaturn : AnimeParser() {

    override val name = "AnimeSaturn"
    override val saveName = "animesaturn_to"
    override val hostUrl = "https://www.animesaturn.cc"
    override val isDubAvailableSeparately = true
    override val allowsPreloading = false

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val response = client.get(animeLink).document

        return response.select("a.bottone-ep").mapNotNull{
            var episode = it.text().split(" ")[1]
            if(episode.contains("."))
            if(episode.contains("-"))
                episode = episode.split("-")[0]
            Episode(
                    link = it.attr("href"),
                    number = episode
                )
        }
    }

    private fun getUrl(episodePage : Document): String?{
        val episodeUrl: String?
        if(episodePage.select("video.afterglow > source").isNotEmpty()) //Old player
            episodeUrl = episodePage.select("video.afterglow > source").first()!!.attr("src")

        else{                                                                   //New player
            val script = episodePage.select("script").find {
                it.toString().contains("jwplayer('player_hls').setup({")
            }!!.toString()
            episodeUrl = script.split(" ").find { it.contains(".m3u8") and !it.contains(".replace") }!!.replace("\"","").replace(",", "")
        }
        return episodeUrl
    }
    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val page = client.get(episodeLink).document
        val epLink = page.select("div.card-body > a[href]").find {it1 ->
            it1.attr("href").contains("watch?")
        }?.attr("href")
        val episodePage = client.get(epLink!!).document
        val videoServersList = mutableListOf(
            VideoServer("AnimeSaturn", getUrl(episodePage)?.trim()!!))

        episodePage.select("a.dropdown-item").forEach {
            val doc = client.get(it.attr("href")).document
            val serverInfos = doc.select("#wtf > a")

            videoServersList.add(VideoServer(serverInfos.text().substringAfterLast(" "), serverInfos.attr("href")))

        }

        return  videoServersList

    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? =
    when (server.name) {
        "Streamtape"        -> StreamTape(server)
        "DoodStream"        -> DoodStream(server)
        "AnimeSaturn"       -> AnimeSaturnExtractor(server)
        "StreamSB"          -> StreamSB(server)
        "Streamlare"        -> Streamlare(server)
        "FileMoon"          -> FileMoon(server)
        else             -> null
    }

    class AnimeSaturnExtractor(override val server: VideoServer) : VideoExtractor() {
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
        val response = client.get("$hostUrl/animelist?search=$encoded").document
        return response.select("div.item-archivio").toList()
            .filter { (it.select("a.badge-archivio").first()!!.text().contains("(ITA)") === selectDub) }
            .map {
            val title = it.select("a.badge-archivio").first()!!.text()
            val link = it.select("a.badge-archivio").first()!!.attr("href")
            val cover = it.select("img.locandina-archivio[src]").first()!!.attr("src")
            ShowResponse(title, link, cover)
        }
    }

    private suspend fun getMedia(searchList : List<ShowResponse>?, id:Int): ShowResponse?{
        if (searchList != null) {
            return searchList.firstOrNull {
                getID(client.get(it.link).document) == id
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

    private suspend fun matchDoSearch(title:String, id: Int): ShowResponse? {
        return if(TitleTransform().checkMatch(title)){
            getMedia(search(TitleTransform().replaceTitleSeason(title)), id)
        } else null


    }
    private fun getID(document: Document): Int? {
        var aniListId : Int? = null
        document.select("[rel=\"noopener noreferrer\"]").forEach {
            if(it.attr("href").contains("anilist")) aniListId = it.attr("href").removeSuffix("/").split('/').last().toIntOrNull()
        }
        return aniListId
    }
}