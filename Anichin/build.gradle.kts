import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // No special build config fields needed for Anichin
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}


cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    authors = listOf("Based on HiAnime by Stormunblessed, KillerDogeEmpire, RowdyRushya, Phisher98")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Anime",
        "OVA",
        "Donghua",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=anichin.cafe&sz=%size%"
    requiresResources = false
    isCrossPlatform = false  // Changed: Contains Android imports
}
