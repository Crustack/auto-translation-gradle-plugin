package io.github.philkes.auto.translation.plugin.util

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Helper for reading and writing Android strings.xml files. */
class StringsXmlHelper(private val logger: Logger) {

    /**
     * Escape special characters for Android string resources. Android requires escaping
     * apostrophes, double quotes, backslashes, and @ or ? at the start of a string.
     *
     * This function first normalises the input by removing any existing Android escapes,
     * then applies consistent escaping. This prevents double-escaping if the translation
     * provider returns pre-escaped text.
     */
    internal fun escapeForAndroid(text: String): String {
        if (text.isEmpty()) return text

        // First, unescape any Android escapes that might already be present.
        // Order matters: unescape \\ last to avoid interference.
        val normalised =
            text
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\@", "@")
                .replace("\\?", "?")
                .replace("\\\\", "\\")

        // Then apply consistent escaping.
        // Order matters: escape \\ first to avoid double-escaping.
        val escaped =
            normalised
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")

        return when {
            escaped.startsWith("@") -> "\\$escaped"
            escaped.startsWith("?") -> "\\$escaped"
            else -> escaped
        }
    }

    /**
     * Parse a strings.xml file and return a map of name -> text, skipping entries with
     * translatable="false". Also supports <plurals> by expanding each quantity into a separate
     * entry using the key pattern: "name[quantity]". Throws GradleException if the file cannot be
     * parsed.
     */
    fun parse(file: File): Map<String, String> {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val builder = dbf.newDocumentBuilder()
            val doc = builder.parse(file)
            val map = LinkedHashMap<String, String>()

            // Parse <string> entries
            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node is Element) {
                    val name = node.getAttribute("name")
                    val translatable = node.getAttribute("translatable")
                    if (name.isNullOrBlank()) continue
                    if (translatable.equals("false", ignoreCase = true)) continue
                    val text = node.textContent ?: ""
                    map[name] = text
                }
            }

            // Parse <plurals> entries
            val pluralsNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralsNodes.length) {
                val pluralsEl = pluralsNodes.item(i)
                if (pluralsEl is Element) {
                    val baseName = pluralsEl.getAttribute("name")
                    val translatable = pluralsEl.getAttribute("translatable")
                    if (baseName.isNullOrBlank()) continue
                    if (translatable.equals("false", ignoreCase = true)) continue

                    val childNodes = pluralsEl.getElementsByTagName("item")
                    for (j in 0 until childNodes.length) {
                        val itemEl = childNodes.item(j)
                        if (itemEl is Element) {
                            val quantity = itemEl.getAttribute("quantity")
                            if (quantity.isNullOrBlank()) continue
                            val text = itemEl.textContent ?: ""
                            // Use safe intermediate key that won't collide with valid Android names
                            map["$baseName[$quantity]"] = text
                        }
                    }
                }
            }

            return map
        } catch (e: Exception) {
            logger.error("Failed to parse strings.xml at ${file.absolutePath}", e)
            throw GradleException(
                "Failed to parse strings.xml: ${file.absolutePath}. ${e.message}",
                e,
            )
        }
    }

    /**
     * Write the provided entries to a strings.xml file. Overwrites the file if it exists. Supports
     * plural entries using the key pattern: "name[quantity]". Such entries are grouped into a
     * single <plurals> element with multiple <item quantity="..."> children.
     */
    fun write(file: File, entries: Map<String, String>) {
        try {
            val doc = newDocument()
            doc.xmlStandalone = true
            val resources = doc.createElement("resources")
            doc.appendChild(resources)

            // Split entries into normal strings and plural items using pattern base[quantity]
            val pluralPattern = Regex("^(.+)\\[(zero|one|two|few|many|other)\\]$")
            val pluralGroups = linkedMapOf<String, MutableMap<String, String>>()
            val normalStrings = linkedMapOf<String, String>()

            entries.toSortedMap().forEach { (name, value) ->
                val match = pluralPattern.find(name)
                if (match != null) {
                    val base = match.groupValues[1]
                    val quantity = match.groupValues[2]
                    val map = pluralGroups.getOrPut(base) { linkedMapOf() }
                    map[quantity] = value
                } else {
                    normalStrings[name] = value
                }
            }

            // Define preferred order of plural quantities
            val quantityOrder = listOf("zero", "one", "two", "few", "many", "other")
            val quantityComparator =
                Comparator<String> { a, b ->
                    val ia = quantityOrder.indexOf(a).let { if (it == -1) Int.MAX_VALUE else it }
                    val ib = quantityOrder.indexOf(b).let { if (it == -1) Int.MAX_VALUE else it }
                    ia.compareTo(ib).takeIf { it != 0 } ?: a.compareTo(b)
                }

            // Emit elements ordered globally by name (strings and plurals mixed)
            val allNames = (normalStrings.keys + pluralGroups.keys).toSortedSet()
            allNames.forEach { name ->
                val normal = normalStrings[name]
                if (normal != null) {
                    val el = doc.createElement("string")
                    el.setAttribute("name", name)
                    el.appendChild(doc.createTextNode(escapeForAndroid(normal)))
                    resources.appendChild(el)
                }
                val qmap = pluralGroups[name]
                if (qmap != null) {
                    val pluralsEl = doc.createElement("plurals")
                    pluralsEl.setAttribute("name", name)
                    qmap.toSortedMap(quantityComparator).forEach { (quantity, value) ->
                        val itemEl = doc.createElement("item")
                        itemEl.setAttribute("quantity", quantity)
                        itemEl.appendChild(doc.createTextNode(escapeForAndroid(value)))
                        pluralsEl.appendChild(itemEl)
                    }
                    resources.appendChild(pluralsEl)
                }
            }

            val transformer =
                TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.INDENT, "yes")
                    setOutputProperty(OutputKeys.METHOD, "xml")
                    setOutputProperty(OutputKeys.ENCODING, "utf-8")
                    setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                    setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "")
                    // Leads to new line after XML declaration
                }
            file.parentFile?.mkdirs()
            transformer.transform(DOMSource(doc), StreamResult(file))
        } catch (e: Exception) {
            logger.error("Failed to write strings.xml at ${file.absolutePath}", e)
            throw GradleException(
                "Failed to write strings.xml: ${file.absolutePath}. ${e.message}",
                e,
            )
        }
    }

    fun maskPlaceholders(input: String): String {
        return input.replace(Regex("%(\\d+)\\$([a-z])")) { matchResult ->
            val number = matchResult.groupValues[1]
            val format = matchResult.groupValues[2]
            "<$format$number/>"
        }
    }

    fun unmaskPlaceholders(input: String): String {
        return input.replace(Regex("<([a-z])(\\d+)/>")) { matchResult ->
            val format = matchResult.groupValues[1]
            val number = matchResult.groupValues[2]
            "%$number\$$format"
        }
    }

    private fun newDocument(): Document {
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return dbf.newDocumentBuilder().newDocument()
    }
}
