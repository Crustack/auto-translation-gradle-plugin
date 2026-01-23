package io.github.philkes.auto.translation.plugin

import io.github.philkes.auto.translation.plugin.config.DeepLConfig
import io.github.philkes.auto.translation.plugin.task.AutoTranslateTask
import io.github.philkes.auto.translation.plugin.util.setIsUnitTest
import java.io.File
import kotlin.test.assertNotEquals
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AutoTranslateTaskIntegrationTest {

    @Test
    fun `translation with plurals and missing strings xml`(@TempDir projectDir: File) {
        // Base strings.xml
        val baseValues = File(projectDir, "src/main/res/values").apply { mkdirs() }
        val dollar = "\$"
        writeFile(
            File(baseValues, "strings.xml"),
            """
            <resources>
              <string name="hello">Hello</string>
              <string name="bye">Bye %1${dollar}s</string>
              <string name="items">Items title</string>
              <string name="apostrophe_test">It's working today</string>
              <plurals name="items">
                <item quantity="one">item</item>
                <item quantity="other">items</item>
              </plurals>
              <string name="skip_me" translatable="false">DO NOT TRANSLATE</string>
            </resources>
            """
                .trimIndent(),
        )
        // Existing German with one key to test merge behavior
        val deValues = File(projectDir, "src/main/res/values-de").apply { mkdirs() }
        writeFile(
            File(deValues, "strings.xml"),
            """
            <resources>
              <string name="hello">Hallo</string>
            </resources>
            """
                .trimIndent(),
        )
        setIsUnitTest(true)
        // Build a minimal Gradle task via ProjectBuilder
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.create("autoTranslate", AutoTranslateTask::class.java)
        // Set a valid provider config (required by the task) with a dummy auth key
        val config = project.objects.newInstance(DeepLConfig::class.java)
        config.authKey.set("dummy-key")
        task.provider.set(config)
        task.targetLanguages.set(listOf("de", "fr"))
        // Configure strings.xml wrapper
        val stringsCfg =
            project.objects.newInstance(
                io.github.philkes.auto.translation.plugin.config.StringsXmlTranslationConfig::class
                    .java
            )
        stringsCfg.resDirectory.set(project.layout.projectDirectory.dir("src/main/res"))
        task.translateStringsXml.set(stringsCfg)

        // Execute the task
        task.translate()

        // Verify DE file merged: existing 'hello' preserved, 'bye' added by mock provider
        val deStrings = File(deValues, "strings.xml")
        assertTrue(deStrings.exists())
        val deContent = deStrings.readText()
        val deByeIndex = deContent.indexOf("<string name=\"bye\">Bye %1\$s [DE]</string>")
        assertNotEquals(-1, deByeIndex)
        val deHelloIndex = deContent.indexOf("<string name=\"hello\">Hallo</string>")
        assertNotEquals(-1, deHelloIndex)
        val deItemsStringIndex =
            deContent.indexOf("<string name=\"items\">Items title [DE]</string>")
        assertNotEquals(-1, deItemsStringIndex)
        // Plurals should be present and translated for DE
        val dePluralsIndex = deContent.indexOf("<plurals name=\"items\">")
        assertNotEquals(-1, dePluralsIndex)
        val deItemOne = "<item quantity=\"one\">item [DE]</item>"
        val deItemOther = "<item quantity=\"other\">items [DE]</item>"
        val deItemOneIndex = deContent.indexOf(deItemOne)
        val deItemOtherIndex = deContent.indexOf(deItemOther)
        assertNotEquals(-1, deItemOneIndex)
        assertNotEquals(-1, deItemOtherIndex)
        assertTrue(deItemOneIndex < deItemOtherIndex, "DE plurals quantities have wrong order!")
        // Check ordering: bye < hello < items (string) < items (plurals)
        assertTrue(deByeIndex < deHelloIndex, "DE bye should come before hello!")
        assertTrue(deHelloIndex < deItemsStringIndex, "DE items string should come after hello!")
        assertTrue(
            deItemsStringIndex < dePluralsIndex,
            "DE items plurals should come after items string!",
        )
        // Ensure translatable=false key not added
        assertTrue(!deContent.contains("skip_me"))
        // Verify FR file created with both keys translated by mock provider
        val frStrings = File(projectDir, "src/main/res/values-fr/strings.xml")
        assertTrue(frStrings.exists())
        val frContent = frStrings.readText()
        println("[DEBUG_LOG] FR strings.xml content:\n$frContent")
        val frByeIndex = frContent.indexOf("<string name=\"bye\">Bye %1\$s [FR]</string>")
        assertNotEquals(-1, frByeIndex)
        val frHelloIndex = frContent.indexOf("<string name=\"hello\">Hello [FR]</string>")
        assertNotEquals(-1, frHelloIndex)
        val frItemsStringIndex =
            frContent.indexOf("<string name=\"items\">Items title [FR]</string>")
        assertNotEquals(-1, frItemsStringIndex)
        val frPluralsIndex = frContent.indexOf("<plurals name=\"items\">")
        assertNotEquals(-1, frPluralsIndex)
        // Global sort order by name: bye < hello < items (string) < items (plurals)
        assertTrue(
            frByeIndex < frHelloIndex,
            "FR translations have the wrong order between simple strings!",
        )
        assertTrue(frHelloIndex < frItemsStringIndex, "FR items string should come after hello!")
        assertTrue(
            frItemsStringIndex < frPluralsIndex,
            "FR plurals block should come after items string by name!",
        )
        // Check plural quantities and their order
        val frItemOne = "<item quantity=\"one\">item [FR]</item>"
        val frItemOther = "<item quantity=\"other\">items [FR]</item>"
        val frItemOneIndex = frContent.indexOf(frItemOne)
        val frItemOtherIndex = frContent.indexOf(frItemOther)
        println("[DEBUG_LOG] frItemOneIndex=$frItemOneIndex, frItemOtherIndex=$frItemOtherIndex")
        assertNotEquals(-1, frItemOneIndex)
        assertNotEquals(-1, frItemOtherIndex)
        assertTrue(frItemOneIndex < frItemOtherIndex, "FR plurals quantities have wrong order!")
        // Ensure translatable=false key not added
        assertTrue(!frContent.contains("skip_me"))
        // Verify apostrophes are properly escaped for Android
        val frApostropheIndex =
            frContent.indexOf("<string name=\"apostrophe_test\">It\\'s working today [FR]</string>")
        assertNotEquals(
            -1,
            frApostropheIndex,
            "Apostrophe should be escaped in FR translation",
        )
        val deApostropheIndex =
            deContent.indexOf("<string name=\"apostrophe_test\">It\\'s working today [DE]</string>")
        assertNotEquals(
            -1,
            deApostropheIndex,
            "Apostrophe should be escaped in DE translation",
        )
    }

    private fun writeFile(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
