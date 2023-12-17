import org.openqa.selenium.WebElement

fun processPage(page: Int, elements: List<WebElement>) = buildList {
    // Page link anchor
    add("<div id=\"page$page\"></div>")

    for ((index, element) in elements.withIndex()) {
        val text = element.text
        val elementBefore = elements.getOrNull(index - 1)
        val elementAfter = elements.getOrNull(index + 1)

        val markdownText = when (element.tagName) {
            "span" -> {
                val tagBefore = elementBefore?.tagName ?: continue

                if (tagBefore.matches(Regex("^h[1-6]$"))) { // headers
                    val hLevel = tagBefore.removePrefix("h").toInt()
                    "${"#".repeat(hLevel)} $text  "
                } else if (elementAfter?.tagName == "a") { // links
                    val internalLinkPrefix = "https://www.pixiv.net/#"

                    val href = elementAfter.getAttribute("href").let {
                        if (it.startsWith(internalLinkPrefix)) {
                            "#page${it.removePrefix(internalLinkPrefix)}" // internal link
                        } else it
                    }

                    "$text [${elementAfter.text}]($href)  "
                } else "$text  "
            }

            "p" -> ""

            "img" -> "![](${element.getAttribute("src")})  "

            else -> continue
        }

        add(markdownText)
    }

    addAll(listOf("", "`p$page`  ", "", "***"))
}