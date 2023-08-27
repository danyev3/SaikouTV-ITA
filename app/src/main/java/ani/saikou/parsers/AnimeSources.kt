package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.anime.AllAnime
import ani.saikou.parsers.anime.AnimePahe
import ani.saikou.parsers.anime.Gogo
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream
import ani.saikou.parsers.anime.Marin
import ani.saikou.parsers.anime.AniWave
import ani.saikou.parsers.anime.AnimeDao
import ani.saikou.parsers.anime.Kaido

//Parser Italiani
import ani.saikou.parsers.anime.AnimeWorld
import ani.saikou.parsers.anime.AnimeSaturn
import ani.saikou.parsers.anime.AniPlay
import ani.saikou.parsers.anime.AnimeUnity
import ani.saikou.parsers.anime.HentaiWorld
import ani.saikou.parsers.anime.HentaiSaturn

object AnimeSources : WatchSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "AnimeWorld (IT)" to ::AnimeWorld,
        "AnimeSaturn (IT)" to ::AnimeSaturn,
        "AnyPlay (IT)" to ::AniPlay,
        "AnimeUnity (IT)" to ::AnimeUnity,
        "AllAnime (EN)" to ::AllAnime,
        "AniWave (EN)" to ::AniWave,
        "AnimeDao (EN)" to ::AnimeDao,
        "AnimePahe (EN)" to ::AnimePahe,
        "Gogo (EN)" to ::Gogo,
        "Kaido (EN)" to ::Kaido,
        "Marin (EN)" to ::Marin
    )
}

object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>>  = lazyList(
        "HentaiWorld (IT)" to ::HentaiWorld,
        "HentaiSaturn (IT)" to ::HentaiSaturn,
        "HentaiMama (EN)" to ::HentaiMama,
        "Haho (EN)" to ::Haho,
        "HentaiStream (EN)" to ::HentaiStream,
        "HentaiFF (EN)" to ::HentaiFF,
    )

    override val list = listOf(aList,AnimeSources.list).flatten()
}
