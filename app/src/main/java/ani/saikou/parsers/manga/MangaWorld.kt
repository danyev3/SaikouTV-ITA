package ani.saikou.parsers.manga

import ani.saikou.client
import ani.saikou.media.Media
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class MangaWorld : MangaParser() {
    override val name = "MangaWorld"
    override val saveName = "mangaworld_manga"
    override val hostUrl = "https://www.mangaworld.so/"



    override suspend fun search(query: String): List<ShowResponse> {
        val resp = client.get("${hostUrl}archive?keyword=${encode(query)}").document

        return resp.select("div.entry").map { manga ->
            val mangaData = manga.select("a.manga-title")
            ShowResponse(
                name = mangaData.text(),
                link = mangaData.attr("href"),
                coverUrl = manga.selectFirst("img")!!.attr("src")
            ) // need this slug for loadChapters
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

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val resp = client.get(mangaLink).text
        val episodes = mutableListOf<MangaChapter>()

        var jsonData = resp.substringAfter("""pages":""").substringBefore(",\"singleChapters").substringAfter(":")
        val baseName = resp.substringAfter("slugFolder\":\"").substringBefore("\"")
        val manga = resp.substringAfter("\"manga\":\"").substringBefore("\"")
        if (jsonData.length === 2){
            jsonData = resp.substringAfter("singleChapters\":").substringBefore("}},{\"f\":1}]")
            Json{ignoreUnknownKeys = true }.decodeFromString<List<ChapterParser>>(jsonData).reversed().forEach { chapter ->
                val imageData =
                    "https://cdn.${hostUrl.substringAfter(".")}chapters/$baseName-$manga/${chapter.chapterSlug}-${chapter.id}/"
                val links = imageData+chapter.pages.joinToString()
                episodes.add(MangaChapter(number = chapter.name.replace("Capitolo ",""), link = links))

            }
        }
        else{
            Json{ignoreUnknownKeys = true }.decodeFromString<List<VolumesParser>>(jsonData).reversed().forEach { volume ->
                volume.chapters.reversed().forEach{ chapter ->
                    val imageData =
                        "https://cdn.${hostUrl.substringAfter(".")}chapters/$baseName-$manga/${volume.volumeData.volumeSlug}-${volume.volumeData.volumeId}/${chapter.chapterSlug}-${chapter.id}/"
                    val links = imageData+chapter.pages.joinToString()
                    episodes.add(MangaChapter(number = chapter.name.replace("Capitolo ",""), link = links))
                }
            }
        }
        return episodes

    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {

        val pageExtensions = chapterLink.substringAfterLast("/").split(", ")
        return pageExtensions.map { MangaImage(chapterLink.substringBeforeLast("/")+"/"+it)  }
    }

    @Serializable
    private data class VolumesParser(
        @SerialName("chapters") val chapters: List<ChapterParser>,
        @SerialName("volume") val volumeData: Volume

    )
    @Serializable
    private data class ChapterParser(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("slugFolder") val chapterSlug:String,
        @SerialName("pages") val pages:List<String>
    )
    @Serializable
    private data class Volume(
        @SerialName("id") val volumeId: String,
        @SerialName("slugFolder") val volumeSlug:String,
    )


}

