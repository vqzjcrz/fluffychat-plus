package app.sift.engine

/**
 * A commonly-impersonated brand.
 * @param keyword lowercase name as it appears in domains.
 * @param official registrable domains that legitimately belong to the brand.
 * @param anyCountryTld if true, `keyword.<ccTLD>` (amazon.de, google.co.uk) is accepted as official.
 */
data class Brand(
    val keyword: String,
    val official: Set<String>,
    val anyCountryTld: Boolean = false,
    val display: String = keyword.replaceFirstChar { it.uppercase() },
) {
    /** Brand names that are also everyday words (ledger, discover, steam) need more evidence. */
    val commonWord: Boolean get() = keyword in Data.COMMON_WORD_BRANDS
}

object Data {

    private fun b(keyword: String, vararg official: String, cc: Boolean = false, display: String? = null) =
        Brand(keyword, official.toSet(), cc, display ?: keyword.replaceFirstChar { it.uppercase() })

    private val MS = arrayOf(
        "microsoft.com", "live.com", "outlook.com", "office.com", "office.net", "microsoftonline.com",
        "office365.com", "sharepoint.com", "onedrive.com", "onedrive.live.com", "azure.com", "windows.net",
        "xbox.com", "skype.com", "bing.com", "msn.com", "hotmail.com", "microsoft365.com", "msauth.net",
        "msftauth.net", "aka.ms", "1drv.ms", "sway.cloud.microsoft", "cloud.microsoft",
    )
    private val GOOGLE = arrayOf(
        "google.com", "gmail.com", "youtube.com", "youtu.be", "googleusercontent.com", "gstatic.com",
        "googleapis.com", "goo.gl", "g.co", "withgoogle.com", "google", "forms.gle", "googlemail.com",
        "googlevideo.com", "ggpht.com", "android.com", "chrome.com",
    )
    private val AMAZON = arrayOf(
        "amazon.com", "amazonaws.com", "a2z.com", "amzn.to", "a.co", "amazon.dev", "media-amazon.com",
        "ssl-images-amazon.com", "awsapps.com", "amazontrust.com", "primevideo.com", "audible.com",
    )
    private val META = arrayOf(
        "facebook.com", "fb.com", "fb.me", "facebook.net", "fbcdn.net", "meta.com", "messenger.com",
        "instagram.com", "cdninstagram.com", "ig.me", "whatsapp.com", "whatsapp.net", "wa.me", "threads.net",
    )

    val BRANDS: List<Brand> = listOf(
        b("paypal", "paypal.com", "paypal.me", "paypalobjects.com", "paypal-community.com", cc = true, display = "PayPal"),
        b("apple", "apple.com", "icloud.com", "apple.news", "me.com", "mzstatic.com", "cdn-apple.com"),
        b("appleid", "apple.com", display = "Apple ID"),
        b("icloud", "icloud.com", "apple.com", display = "iCloud"),
        b("microsoft", *MS),
        b("outlook", *MS),
        b("office365", *MS, display = "Office 365"),
        b("microsoftonline", *MS, display = "Microsoft Online"),
        b("onedrive", *MS, display = "OneDrive"),
        b("sharepoint", *MS, display = "SharePoint"),
        b("hotmail", *MS),
        b("xbox", *MS),
        b("google", *GOOGLE, cc = true),
        b("gmail", *GOOGLE),
        b("youtube", *GOOGLE, display = "YouTube"),
        b("amazon", *AMAZON, cc = true),
        b("netflix", "netflix.com", "nflxext.com", "nflximg.net", "nflxvideo.net", "netflix.net"),
        b("facebook", *META),
        b("instagram", *META),
        b("whatsapp", *META, display = "WhatsApp"),
        b("messenger", *META),
        b("chase", "chase.com", "jpmorganchase.com", "jpmorgan.com"),
        b("wellsfargo", "wellsfargo.com", "wf.com", display = "Wells Fargo"),
        b("bankofamerica", "bankofamerica.com", "bofa.com", display = "Bank of America"),
        b("citibank", "citi.com", "citibank.com", "citigroup.com", display = "Citi"),
        b("capitalone", "capitalone.com", display = "Capital One"),
        b("usaa", "usaa.com", display = "USAA"),
        b("navyfederal", "navyfederal.org", display = "Navy Federal"),
        b("penfed", "penfed.org", display = "PenFed"),
        b("armyfcu", "armyfcu.com", display = "Army FCU"),
        b("americanexpress", "americanexpress.com", "aexp.com", "amex.com", display = "American Express"),
        b("amex", "americanexpress.com", "aexp.com", "amex.com", display = "American Express"),
        b("discover", "discover.com", "discovercard.com"),
        b("venmo", "venmo.com"),
        b("cashapp", "cash.app", "cashapp.com", "squareup.com", "square.com", display = "Cash App"),
        b("zelle", "zellepay.com", "zelle.com"),
        b("coinbase", "coinbase.com"),
        b("binance", "binance.com", "binance.us"),
        b("kraken", "kraken.com"),
        b("metamask", "metamask.io", display = "MetaMask"),
        b("ledger", "ledger.com"),
        b("trezor", "trezor.io"),
        b("steam", "steampowered.com", "steamcommunity.com", "steamstatic.com", "steam.tv", "steamgames.com"),
        b("steampowered", "steampowered.com", "steamcommunity.com", display = "Steam"),
        b("steamcommunity", "steampowered.com", "steamcommunity.com", display = "Steam"),
        b("discord", "discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media", "discordstatus.com", "discord.new", "discord.gift"),
        b("roblox", "roblox.com", "rbxcdn.com"),
        b("epicgames", "epicgames.com", "unrealengine.com", "fortnite.com", display = "Epic Games"),
        b("dropbox", "dropbox.com", "dropboxusercontent.com", "db.tt"),
        b("docusign", "docusign.com", "docusign.net", display = "DocuSign"),
        b("adobe", "adobe.com", "adobe.io", "adobelogin.com", "typekit.net"),
        b("linkedin", "linkedin.com", "lnkd.in", "licdn.com", display = "LinkedIn"),
        b("twitter", "twitter.com", "x.com", "t.co", "twimg.com"),
        b("tiktok", "tiktok.com", "tiktokcdn.com", "tiktokv.com", display = "TikTok"),
        b("snapchat", "snapchat.com", "snap.com"),
        b("spotify", "spotify.com", "spoti.fi", "scdn.co"),
        b("github", "github.com", "github.io", "githubusercontent.com", "githubassets.com", display = "GitHub"),
        b("okta", "okta.com", "oktacdn.com", "okta-emea.com"),
        b("yahoo", "yahoo.com", "yahoo.net", "yimg.com", cc = true),
        b("verizon", "verizon.com", "verizonwireless.com", "vzw.com"),
        b("tmobile", "t-mobile.com", display = "T-Mobile"),
        b("walmart", "walmart.com", cc = true),
        b("costco", "costco.com", cc = true),
        b("bestbuy", "bestbuy.com", cc = true, display = "Best Buy"),
        b("ebay", "ebay.com", "ebay.us", "ebaystatic.com", cc = true, display = "eBay"),
        b("irs", "irs.gov", display = "IRS"),
        b("usps", "usps.com", "usps.gov", display = "USPS"),
        b("ups", "ups.com", display = "UPS"),
        b("fedex", "fedex.com", display = "FedEx"),
        b("dhl", "dhl.com", cc = true, display = "DHL"),
        b("ssa", "ssa.gov", display = "Social Security"),
        b("dfas", "dfas.mil", display = "DFAS"),
        b("mypay", "dfas.mil", display = "myPay"),
        b("army", "army.mil", "goarmy.com", "armyfcu.com", "army.com"),
        b("ippsa", "army.mil", display = "IPPS-A"),
        b("tricare", "tricare.mil", display = "TRICARE"),
        b("milconnect", "osd.mil", display = "milConnect"),
        b("militaryonesource", "militaryonesource.mil", display = "Military OneSource"),
        b("myvaccount", "va.gov", display = "VA"),
        b("login.gov", "login.gov", display = "Login.gov"),
        b("idme", "id.me", display = "ID.me"),
    )

    val COMMON_WORD_BRANDS = setOf("apple", "chase", "discover", "ledger", "steam", "kraken", "messenger", "army", "outlook", "amazon", "zelle", "okta", "ups", "venmo", "spotify")

    val ALL_OFFICIAL: Set<String> by lazy { BRANDS.flatMap { it.official }.toSet() }

    /** Words that phishing kits bolt onto brand names (paypal-secure-login, appleid-verify). */
    val PHISH_WORDS = setOf(
        "login", "logon", "signin", "sign", "secure", "security", "verify", "verification", "account", "accounts",
        "update", "confirm", "support", "help", "service", "services", "online", "auth", "authenticate", "wallet",
        "billing", "pay", "payment", "center", "centre", "team", "alert", "alerts", "my", "web", "webmail", "mail",
        "app", "official", "recovery", "recover", "unlock", "unlocked", "suspended", "locked", "id", "portal",
        "access", "reset", "refund", "rewards", "gift", "giftcard", "prize", "claim", "delivery", "parcel",
        "tracking", "track", "notice", "case", "appeal", "member", "members", "customer", "client", "info",
        "us", "usa", "inc", "bank", "banking", "card", "cards", "invoice", "document", "docs", "file", "share",
        "drive", "cloud", "store", "shop", "tax", "irs", "gov", "benefits", "check", "safety", "protect",
        "fraud", "dispute", "pending", "sso", "mfa", "2fa", "otp", "nft", "airdrop", "connect", "sync", "dapp",
        "restore", "validate", "validation", "renew", "renewal", "subscription", "premium", "free", "bonus",
    )

    /** Path/host words that show up on credential-harvesting pages. */
    val CREDENTIAL_WORDS = setOf(
        "login", "log-in", "logon", "signin", "sign-in", "verify", "verification", "account", "password", "passwd",
        "credential", "credentials", "unlock", "suspended", "suspend", "confirm", "billing", "authenticate",
        "webscr", "ssn", "wallet", "seed", "seedphrase", "recovery-phrase", "mnemonic", "2fa", "otp", "mfa",
        "reset", "validate", "reactivate", "restore", "update-payment", "payment-update", "secure-login",
    )

    val SHORTENERS = setOf(
        "bit.ly", "bitly.com", "j.mp", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "v.gd", "buff.ly",
        "rebrand.ly", "cutt.ly", "shorturl.at", "rb.gy", "tiny.cc", "s.id", "t.ly", "lnkd.in", "bl.ink",
        "short.io", "shorte.st", "adf.ly", "linktr.ee", "qrco.de", "tr.ee", "surl.li", "urlz.fr", "clck.ru",
        "u.to", "x.gd", "l.ead.me", "han.gl", "bit.do", "soo.gd", "0rz.tw", "trib.al", "dlvr.it", "ift.tt",
        "amzn.to", "a.co", "youtu.be", "spoti.fi", "db.tt", "1drv.ms", "aka.ms", "fb.me", "wa.me", "ig.me",
        "tinyurl.is", "shorturl.gg", "rotf.lol", "gg.gg", "qr.net", "2no.co", "iplogger.org", "grabify.link",
        "blasze.com", "lstu.fr", "cli.re", "go2l.ink", "kutt.it", "shrtco.de", "rebrandly.com", "snip.ly",
        "smarturl.it", "hyperurl.co", "urlr.me", "mcaf.ee", "po.st", "q.gs", "tny.im", "chilp.it", "shorturl.com",
    )

    /** IP-loggers: links that exist to record your IP / device info. */
    val IP_LOGGERS = setOf(
        "iplogger.org", "iplogger.com", "iplogger.ru", "2no.co", "yip.su", "grabify.link", "blasze.com",
        "ps3cfw.com", "iplis.ru", "02ip.ru", "ezstat.ru", "bmwforum.co", "leancoding.co", "spottyfly.com",
        "stopify.co", "yoütu.be", "xn--yotu-1ra.be", "discörd.com", "minecräft.com", "freegiftcards.co",
        "quickmessage.us", "sportshub.bar", "headshot.monster", "gamingfun.me", "crabrave.pw", "lovebird.guru",
        "trulove.guru", "dateing.club", "otherhalf.life", "shrekis.life", "imageshare.best", "screenshot.best",
        "gyazo.nl", "joinmy.site", "screenshare.pics", "myprivate.pics", "noodshare.pics", "partpicker.shop",
        "canvasgift.com",
    )

    /** Tunnels and dynamic DNS: anyone can point these at their own machine in seconds. */
    val TUNNEL_DDNS = setOf(
        "ngrok.io", "ngrok-free.app", "ngrok.app", "ngrok-free.dev", "ngrok.dev", "trycloudflare.com", "loca.lt",
        "serveo.net", "localhost.run", "lhr.life", "pinggy.link", "pinggy.online", "localtunnel.me", "telebit.io",
        "devtunnels.ms", "duckdns.org", "ddns.net", "hopto.org", "zapto.org", "sytes.net", "no-ip.org", "no-ip.biz",
        "no-ip.info", "noip.me", "servebeer.com", "serveblog.net", "servehttp.com", "myftp.biz", "myftp.org",
        "redirectme.net", "dynu.net", "dynu.com", "mooo.com", "afraid.org", "chickenkiller.com", "strangled.net",
        "dynv6.net", "freeddns.org", "ddnsking.com", "3utilities.com", "bounceme.net", "gotdns.ch", "kozow.com",
        "portmap.io", "portmap.host", "playit.gg", "bore.pub", "tunnelmole.net", "zrok.io",
    )

    /** Free site builders / hosting. Legit, but also where most throwaway phishing pages live. */
    val FREE_HOSTING = setOf(
        "000webhostapp.com", "weebly.com", "wixsite.com", "wix.com", "square.site", "godaddysites.com",
        "firebaseapp.com", "web.app", "glitch.me", "repl.co", "replit.app", "replit.dev", "r2.dev",
        "workers.dev", "pages.dev", "vercel.app", "netlify.app", "herokuapp.com", "onrender.com", "github.io",
        "gitlab.io", "blogspot.com", "ipfs.io", "dweb.link", "cloudflare-ipfs.com", "ipfs.w3s.link",
        "nftstorage.link", "fleek.co", "storage.googleapis.com", "s3.amazonaws.com", "blob.core.windows.net",
        "web.core.windows.net", "azurewebsites.net", "azureedge.net", "typedream.app", "webflow.io",
        "framer.website", "framer.app", "notion.site", "canva.site", "my.canva.site", "jimdosite.com",
        "yolasite.com", "pantheonsite.io", "mybluehost.me", "wordpress.com", "tilda.ws", "carrd.co",
        "strikingly.com", "mystrikingly.com", "webnode.page", "site123.me", "surge.sh", "codeanyway.com",
        "codesandbox.io", "csb.app", "stackblitz.io", "edgeone.app", "temp.domains", "bitbucket.io",
        "translate.goog", "appspot.com", "cloudfront.net", "b-cdn.net", "backblazeb2.com", "digitaloceanspaces.com",
        "linodeobjects.com", "wasabisys.com", "fly.dev", "railway.app", "up.railway.app", "deno.dev",
        "surge.sh", "neocities.org", "tiiny.site", "tiiny.co", "start.page", "linkr.bio", "beacons.ai",
    )

    /** Form builders — a fine place for surveys, a terrible place to type a password. */
    val FORM_HOSTS = setOf(
        "forms.gle", "forms.office.com", "forms.microsoft.com", "jotform.com", "typeform.com",
        "surveymonkey.com", "formstack.com", "cognitoforms.com", "123formbuilder.com", "wufoo.com",
        "zohopublic.com", "zoho.com", "paperform.co", "tally.so", "airtable.com", "formsite.com",
    )

    /** TLDs with disproportionate abuse rates (Spamhaus / Interisle reports) or confusing names. */
    val ABUSED_TLDS = setOf(
        "tk", "ml", "ga", "cf", "gq", "xyz", "top", "icu", "buzz", "cyou", "rest", "sbs", "cfd", "bond", "click",
        "country", "stream", "gdn", "work", "support", "cam", "quest", "lol", "monster", "mom", "beauty", "hair",
        "skin", "makeup", "boats", "autos", "yachts", "motorcycles", "bar", "casa", "loan", "win", "bid", "racing",
        "review", "party", "trade", "date", "faith", "science", "accountant", "download", "men", "kim", "fit",
        "surf", "pw", "ws", "su", "uno", "pics", "hokkaido", "okinawa", "zone", "rocks", "fun", "online", "site",
        "website", "space", "host", "press", "digital", "live", "life", "today", "world", "tokyo", "best", "shop",
        "store", "vip", "ink", "info", "biz", "help", "lat", "qpon", "dad", "tube", "christmas", "help",
    )
    /** The worst of the above — free or near-free registrations, historically saturated with abuse. */
    val VERY_ABUSED_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "xyz", "top", "icu", "cyou", "sbs", "cfd", "bond", "rest", "buzz", "su", "pw", "lat", "qpon")

    /** TLDs that look like file extensions. */
    val FILENAME_TLDS = mapOf("zip" to "a ZIP archive", "mov" to "a video file")

    /** Extension -> (severity, description) */
    val DANGEROUS_EXTENSIONS: Map<String, Pair<Severity, String>> = buildMap {
        for (e in listOf("apk", "xapk", "apks", "apkm", "aab")) put(e, Severity.HIGH to "Android app installer. Sideloaded APKs are the #1 way Android phones get malware.")
        for (e in listOf("exe", "scr", "msi", "msix", "bat", "cmd", "com", "pif", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "ps1", "psm1", "jar", "lnk", "cpl", "reg", "dll", "appx", "appinstaller", "msc", "xll", "iqy", "url", "inf", "sct", "chm"))
            put(e, Severity.HIGH to "Windows executable or script. If you forward this to a PC it can run code.")
        for (e in listOf("iso", "img", "vhd", "vhdx")) put(e, Severity.HIGH to "Disk image. Commonly used to smuggle malware past email filters.")
        for (e in listOf("dmg", "pkg", "command", "app")) put(e, Severity.MEDIUM to "macOS installer or app bundle.")
        for (e in listOf("sh", "bash", "deb", "rpm", "run", "bin", "elf", "py", "pl")) put(e, Severity.MEDIUM to "Linux executable, package, or script.")
        for (e in listOf("docm", "xlsm", "pptm", "dotm", "xltm", "xlam", "ppam", "potm", "xlsb")) put(e, Severity.MEDIUM to "Office document with macros enabled.")
        for (e in listOf("mobileconfig", "mobileprovision")) put(e, Severity.HIGH to "Apple configuration profile. Can install certificates or VPNs that intercept your traffic.")
        for (e in listOf("crt", "cer", "der", "p12", "pfx", "pem")) put(e, Severity.MEDIUM to "Certificate file. Installing an unknown CA lets someone intercept HTTPS.")
        for (e in listOf("zip", "rar", "7z", "gz", "tgz", "tar", "cab", "arj", "ace", "xz", "bz2", "zst")) put(e, Severity.LOW to "Archive. Check what's inside before extracting.")
        for (e in listOf("svg", "html", "htm", "shtml", "xhtml", "mht", "mhtml")) put(e, Severity.INFO to "")
    }.filterValues { it.second.isNotEmpty() }

    /** Extensions used as decoys in double-extension tricks: invoice.pdf.exe */
    val DECOY_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "gif", "txt", "mp3", "mp4", "csv", "ppt", "pptx", "rtf", "odt", "heic")

    /** Query parameter names that commonly carry a redirect target. */
    val REDIRECT_PARAMS = setOf(
        "url", "u", "q", "uri", "redirect", "redirect_uri", "redirect_url", "redirecturl", "redir", "redirect_to",
        "next", "return", "returnurl", "return_url", "returnto", "return_to", "goto", "go", "dest", "destination",
        "target", "continue", "link", "out", "to", "r", "rurl", "forward", "fwd", "location", "callback", "cb",
        "service", "page", "view", "jump", "jumpurl", "ref", "landing", "success", "successurl", "back", "backurl",
        "origin", "path", "file", "src", "source", "image_url", "img", "load", "open", "navigate", "exit", "checkout_url",
    )

    /** Hosts whose only job is to bounce you elsewhere (we unwrap these explicitly). */
    val KNOWN_REDIRECTORS = mapOf(
        "google.com" to "/url",
        "l.facebook.com" to "/l.php",
        "lm.facebook.com" to "/l.php",
        "l.instagram.com" to "/",
        "safelinks.protection.outlook.com" to "/",
        "urldefense.com" to "/",
        "urldefense.proofpoint.com" to "/",
        "linkprotect.cudasvc.com" to "/",
        "out.reddit.com" to "/",
        "href.li" to "/",
        "steamcommunity.com" to "/linkfilter",
        "youtube.com" to "/redirect",
        "slack-redir.net" to "/link",
        "click.mailchimp.com" to "/",
    )

    /**
     * Visual confusables mapped to the ASCII letter they imitate. Not exhaustive (Unicode's
     * confusables.txt has thousands), but covers what shows up in real homograph phishing.
     */
    val CONFUSABLES: Map<Char, Char> = buildMap {
        // Cyrillic
        "аa бb вb гr еe ёe ѐe ғf һh іi їi јj кk ӏl мm нh оo пn рp сc тt уy ўy хx ѕs ԁd ԛq ԝw ӑa ӓa ӧo ӕa ӱy ѵv ъb ьb ҝk қk ҳx ҫc ӡ3 ɡg ԍg ԃd ԑe ԧh ԓl ԏt ԋh ԉn ԥp џu"
            .split(' ').forEach { if (it.length == 2) put(it[0], it[1]) }
        // Greek
        "αa βb γy δd εe ζz ηn ιi κk νv οo ρp σo τt υu χx ωw ϲc ϳj ϵe ϱp ϻm ϝf ίi όo ύu ήn άa έe"
            .split(' ').forEach { if (it.length == 2) put(it[0], it[1]) }
        // Armenian / Cherokee / misc Latin lookalikes
        "օo սu ոn հh ցg զq ԁd ɑa ɩi ɪi ıi ȷj ʟl ʀr ʏy ɴn ᴀa ᴄc ᴅd ᴇe ɢg ʜh ᴋk ᴍm ᴏo ᴘp ᴛt ᴜu ᴠv ᴡw ᴢz ꮪs ꭰd ꮃw ꮇm ꮋh ꮯc ꮲp ꮶk ꮻo ꭱr ꭼe ꮓz ꮐg ꮮl ꭺa ꮟb"
            .split(' ').forEach { if (it.length == 2) put(it[0], it[1]) }
        put('ⅼ', 'l'); put('ⅰ', 'i'); put('ℓ', 'l'); put('ℯ', 'e'); put('ℴ', 'o'); put('ⅽ', 'c'); put('ⅾ', 'd'); put('ⅿ', 'm')
    }
}
