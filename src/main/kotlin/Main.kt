import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class Main : CliktCommand() {
    private val url by option(
        "-u", "--url",
        help = "The URL of the Pixiv novel to download."
    ).required().check {
        val urlRegex = Regex("^https://www.pixiv.net/novel/show.php\\?id=\\d+(#\\d+)?\$")
        it.matches(urlRegex)
    }

    override fun run() {
        val driver = ChromeDriver(
            ChromeOptions().apply {
                addArguments(
                    "--headless",
                    "--window-size=1920,1080",
                    "--lang=en-US",
                    "--accept-lang=en-US"
                )
            }
        )

        val novelUrl = url.split("#")[0]

        driver.get(novelUrl)

        // Anchor Element
        val anchorElement = driver.withRetry {
            driver.findElement(By.xpath("//*[contains(text(),'character(s)')]"))
        }

        // Details Element
        val detailsElements = anchorElement.findElements(By.xpath("./../../..//*"))

        // Novel Title
        val title = detailsElements.find { it.tagName == "h1" }?.text ?: "Untitled Novel" // Probably never happens

        println("Downloading $title")

        // Novel Stats
        val (charactersCount, readMinutes) = detailsElements
            .filter { it.tagName == "span" }
            .map { it.text }

        val description = detailsElements
            .find { it.tagName == "p" }
            ?.text
            ?.split("\n")
            ?.joinToString("\n") { "> $it  " }
            ?: "> No description.  "

        val tags = detailsElements
            .find { it.tagName == "footer" }
            ?.findElements(By.xpath(".//*"))
            ?.filter { it.tagName == "a" }
            ?.joinToString(" ") { "[#${it.text}](${it.getAttribute("href")})" }
            ?: "No tags."

        val (likes, views, bookmarks) = detailsElements
            .findLast { it.tagName == "ul" }
            ?.findElements(By.xpath(".//*"))
            ?.filter { it.tagName == "dd" }
            ?.map { it.text }
            ?: listOf("Unknown", "Unknown", "Unknown")

        val postingDate = detailsElements
            .find { it.tagName == "time" }?.text
            ?: "No posting date."

        val titleMarkdown = """
            # $title  
            
            [View this novel on Pixiv]($novelUrl)
            
            $charactersCount $readMinutes  
            
            $description  
            
            $tags  
            
            `😊 $likes` `❤️ $views` `👀 $bookmarks`  
            
            $postingDate  
            
            ***
        """.split("\n").map { it.trimStart() }

        fun findNextButton() = driver.findElements(
            By.cssSelector(".gtm-novel-work-footer-pager-next")
        ).let {
            if (it.isEmpty()) null else it.first()
        }

        var nextButton = findNextButton()
        var pageNumber = 1

        val pages = mutableListOf(titleMarkdown)

        while (nextButton != null) {
            if (pageNumber != 1) {
                driver.executeScript("arguments[0].scrollIntoView(true);", nextButton)

                driver.withRetry {
                    try {
                        nextButton!!.click()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            println("Processing page $pageNumber")

            val pageContent = driver.findElement(
                By.xpath("//*[@id=\"root\"]/div[2]/div/div[4]/div/div/div/main/section/div[2]/div[3]/div/div[1]/main/div")
            )

            val pageElements = pageContent.findElements(By.xpath(".//*"))

            val markdown = processPage(pageNumber, pageElements)

            pages.add(markdown)

            nextButton = findNextButton()

            pageNumber++
        }

        val markdownString = pages.flatten().joinToString("\n")

        File("$title.md").writeText(markdownString)

        driver.quit()

        println("Done!")
    }
}

fun main(args: Array<String>) = Main().main(args)

fun <T> WebDriver.withRetry(act: (WebDriver) -> T): T =
    WebDriverWait(this, Duration.ofSeconds(10)).pollingEvery(Duration.ofMillis(10)).until(act)
