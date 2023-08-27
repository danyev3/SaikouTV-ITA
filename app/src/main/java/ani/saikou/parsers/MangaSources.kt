package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.manga.*

object MangaSources : MangaReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "MangaWorld (IT)" to ::MangaWorld,
        "MangaDexIta (IT)" to ::MangaDexIta,
        "MangaKakalot (EN)" to ::MangaKakalot,
        "MangaBuddy (EN)" to ::MangaBuddy,
        "MangaPill (EN)" to ::MangaPill,
        "MangaDex (EN)" to ::MangaDex,
        "MangaReaderTo (EN)" to ::MangaReaderTo,
        "AllAnime (EN)" to ::AllAnime,
        "Toonily (EN)" to ::Toonily,
//        "MangaHub (EN)" to ::MangaHub,
        "MangaKatana (EN)" to ::MangaKatana,
        "Manga4Life (EN)" to ::Manga4Life,
        "MangaRead (EN)" to ::MangaRead,
        "ComickFun (EN)" to ::ComickFun,
    )
}

object HMangaSources : MangaReadSources() {
    val aList: List<Lazier<BaseParser>> = lazyList(
        "MangaWorldAdult (IT)" to ::MangaWorldAdult,
        "NineHentai (EN)" to ::NineHentai,
        "Manhwa18 (EN)" to ::Manhwa18,
        "NHentai (EN)" to ::NHentai,
    )
    override val list = listOf(aList,MangaSources.list).flatten()
}
