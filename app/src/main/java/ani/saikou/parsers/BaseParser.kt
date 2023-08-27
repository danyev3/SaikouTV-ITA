package ani.saikou.parsers

import ani.saikou.FileUrl
import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.saveData
import java.io.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

abstract class BaseParser {

    /**
     * Name that will be shown in Source Selection
     * **/
    open val name: String = ""

    /**
     * Name used to save the ShowResponse selected by user or by autoSearch
     * **/
    open val saveName: String = ""

    /**
     * The main URL of the Site
     * **/
    open val hostUrl: String = ""

    /**
     * override as `true` if the site **only** has NSFW media
     * **/
    open val isNSFW = false

    /**
     * mostly redundant for official app, But override if you want to add different languages
     * **/
    open val language = "English"

    /**
     *  Search for Anime/Manga/Novel, returns a List of Responses
     *
     *  use `encode(query)` to encode the query for making requests
     * **/
    abstract suspend fun search(query: String): List<ShowResponse>

    /**
     * replaceTitleSeason takes in a title of a TV series as a string and returns a
     * new string with keywords replaced by the associated number
     * **/
    class TitleTransform {
        private val seasonRegex = "(SEASON|Season|season|SEASON\\s|Season\\s|season\\s)(\\d+)".toRegex()
        private val nthSeasonRegex = "(\\d+)(st|nd|rd|th)\\s(SEASON|season|Season)".toRegex()
        private val partRegex = "(?<!\\d)(\\sPART|\\sPart|\\spart|\\sPart\\s|\\spart\\s)(\\d+)".toRegex()
        private val courRegex = "(COUR|Cour|cour|Cour\\s|cour\\s)(\\d+)".toRegex()
        private val chapterRegex = "(CHAPTER|Chapter|chapter|Chapter\\s|chapter\\s)(\\d+)".toRegex()
        private val volumeRegex = "(VOLUME|Volume|volume|Volume\\s|volume\\s)(\\d+)".toRegex()
        private val seriesRegex = "(SERIES|Series|series|Series\\s|series\\s)(\\d+)".toRegex()
        private val secondSeasonPartRegex = "(-\\sSeconda\\sStagione\\sParte\\s|Seconda\\sStagione\\sParte\\s|Seconda\\sStagione,\\sParte\\s)(\\d+)".toRegex()
        private val thirdSeasonPartRegex = "(-\\sTerza\\sStagione\\sParte\\s|Terza\\sStagione\\sParte\\s|Terza\\sStagione,\\sParte\\s)(\\d+)".toRegex()
        private val fourthSeasonPartRegex = "(-\\sQuarta\\sStagione\\sParte\\s|Quarta\\sStagione\\sParte\\s|Quarta\\sStagione,\\sParte\\s)(\\d+)".toRegex()
        private val regexList = listOf(seasonRegex, nthSeasonRegex, partRegex, courRegex, chapterRegex,
            volumeRegex, seriesRegex, secondSeasonPartRegex, thirdSeasonPartRegex, fourthSeasonPartRegex)
        private fun checkRegexMatch(title: String, regexList: List<Regex>): Boolean {
            for (regex in regexList) {
                if (regex.containsMatchIn(title)) {
                    return true
                }
            }
            return false
        }
        fun checkMatch(title: String): Boolean {
            return checkRegexMatch(title, regexList)
        }
        fun replaceTitleSeason(title: String): String {
            return when {
                seasonRegex.containsMatchIn(title) -> seasonRegex.replace(title, "$2")
                nthSeasonRegex.containsMatchIn(title) -> nthSeasonRegex.replace(title, "2")
                partRegex.containsMatchIn(title) -> partRegex.replace(title, " $2")
                courRegex.containsMatchIn(title) -> courRegex.replace(title, "part $2")
                chapterRegex.containsMatchIn(title) -> chapterRegex.replace(title, "$2")
                volumeRegex.containsMatchIn(title) -> volumeRegex.replace(title, "$2")
                seriesRegex.containsMatchIn(title) -> seriesRegex.replace(title, "$2")
                secondSeasonPartRegex.containsMatchIn(title) -> secondSeasonPartRegex.replace(title, "2 parte $2")
                thirdSeasonPartRegex.containsMatchIn(title) -> thirdSeasonPartRegex.replace(title, "3 parte $2")
                fourthSeasonPartRegex.containsMatchIn(title) -> fourthSeasonPartRegex.replace(title, "4 parte $2")
                else -> title
            }
        }
    }
    /**
     * The function app uses to auto find the anime/manga using Media data provided by anilist
     *
     * Isn't necessary to override, but recommended, if you want to improve auto search results
     * **/
    open suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        var response = loadSavedShowResponse(mediaObj.id)
        if (response != null) {
            saveShowResponse(mediaObj.id, response, true)
        } else {
            setUserText("Searching : ${mediaObj.mainName()}")
            response = search(mediaObj.mainName()).let { if (it.isNotEmpty()) it[0] else null }

            if (response == null) {
                setUserText("Searching : ${mediaObj.nameRomaji}")
                response = search(mediaObj.nameRomaji).let { if (it.isNotEmpty()) it[0] else null }
            }
            saveShowResponse(mediaObj.id, response)
        }
        return response
    }

    /**
     * Used to get an existing Search Response which was selected by the user.
     * **/
    open suspend fun loadSavedShowResponse(mediaId: Int): ShowResponse? {
        checkIfVariablesAreEmpty()
        return loadData("${saveName}_$mediaId")
    }

    /**
     * Used to save Shows Response using `saveName`.
     * **/
    open fun saveShowResponse(mediaId: Int, response: ShowResponse?, selected: Boolean = false) {
        if (response != null) {
            checkIfVariablesAreEmpty()
            setUserText("${if (selected) "Selected" else "Found"} : ${response.name}")
            saveData("${saveName}_$mediaId", response)
        }
    }

    fun checkIfVariablesAreEmpty() {
        if (hostUrl.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `hostUrl` for the Parser")
        if (name.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `name` for the Parser")
        if (saveName.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `saveName` for the Parser")
    }

    open var showUserText = ""
    open var showUserTextListener: ((String) -> Unit)? = null

    /**
     * Used to show messages & errors to the User, a useful way to convey what's currently happening or what was done.
     * **/
    fun setUserText(string: String) {
        showUserText = string
        showUserTextListener?.invoke(showUserText)
    }

    fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
    fun decode(input: String): String = URLDecoder.decode(input, "utf-8")
}


/**
 * A single show which contains some episodes/chapters which is sent by the site using their search function.
 *
 * You might wanna include `otherNames` & `total` too, to further improve user experience.
 *
 * You can also store a Map of Strings if you want to save some extra data.
 * **/
data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: FileUrl,

    //would be Useful for custom search, ig
    val otherNames: List<String> = listOf(),

    //Total number of Episodes/Chapters in the show.
    val total: Int? = null,

    //In case you want to sent some extra data
    val extra : Map<String,String>?=null,
) : Serializable {
    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf(), total: Int? = null, extra: Map<String, String>?=null)
            : this(name, link, FileUrl(coverUrl), otherNames, total, extra)

    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf(), total: Int? = null)
            : this(name, link, FileUrl(coverUrl), otherNames, total)

    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf())
            : this(name, link, FileUrl(coverUrl), otherNames)

    constructor(name: String, link: String, coverUrl: String)
            : this(name, link, FileUrl(coverUrl))
}


