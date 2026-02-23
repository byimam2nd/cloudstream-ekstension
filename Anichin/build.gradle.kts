// use an integer for version numbers
version = 15


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anichin (Anime Chinese)"
    language    = "id"
    authors = listOf("byimam2nd")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Anime")
    iconUrl="https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjD5KTf3taknWZ6YLFfkHAvDCoeC79LCjfh1T5VsBajkI0hFV4afH6WBwDeiHrGtkYKaRMX4pAWa_M2kG8LXefpk8g6Ug35PnJyXpiro5lPw8tcXMPA_6RFcRw3dYQkoZgXDdjiMdHde2k8ZfwTlazvlVerXAhdrLDNAeo5NysoU-CScOAcVmsosVl_cA/s1600/NewLogo.png"

    isCrossPlatform = true
}
