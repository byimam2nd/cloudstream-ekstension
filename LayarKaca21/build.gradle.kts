// use an integer for version numbers
version = 4


cloudstream {
    description = "LayarKaca21 - Nonton Film Streaming Sub Indo"
    language    = "id"
    authors = listOf("Hexated,Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=d21.team&sz=%size%"

    isCrossPlatform = true
}
