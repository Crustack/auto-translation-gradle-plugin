package io.github.philkes.auto.translation.plugin.config

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

/** Configuration for translating Android strings.xml resources. */
data class StringsXmlTranslationConfig
@JvmOverloads
constructor(
    /**
     * Whether to translate strings.xml resources.
     *
     * Defaults to `true`
     */
    @get:Input var enabled: Boolean,

    /**
     * Path to the folder containing the `values/strings.xml` and `values-{targetLanguage}` folders.
     *
     * Defaults to `${projectDir}/src/main/res`.
     */
    @get:InputDirectory @get:Optional var resDirectory: Directory,
) {

    companion object {

        internal fun default(project: Project): StringsXmlTranslationConfig {
            return StringsXmlTranslationConfig(
                true,
                project.layout.projectDirectory.dir("src/main/res"),
            )
        }
    }
}
