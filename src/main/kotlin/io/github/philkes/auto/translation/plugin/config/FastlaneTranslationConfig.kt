package io.github.philkes.auto.translation.plugin.config

import io.github.philkes.auto.translation.plugin.task.AutoTranslateTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

/** Configuration for translating Fastlane metadata files. */
data class FastlaneTranslationConfig
@JvmOverloads
constructor(
    /**
     * Whether to translate Fastlane metadata text files.
     *
     * Defaults to `false`.
     */
    @get:Input var enabled: Boolean,
    /**
     * Path to Fastlane metadata root directory (contains locale subfolders like `en-US`).
     *
     * Defaults to `${projectDir}/fastlane/metadata/android`.
     */
    @get:InputDirectory @get:Optional var metadataDirectory: Directory,

    /**
     * Language ISO-Code of the source fastlane files (folder name under metadataDirectory).
     *
     * Defaults to the task's sourceLanguage ([AutoTranslateTask.sourceLanguage])
     */
    @get:Input @get:Optional var sourceLanguage: String,

    /**
     * Language ISO-Codes (from '[metadataDirectory]/{targetLanguage}' folder names) to translate.
     *
     * Defaults to task's targetLanguages ([AutoTranslateTask.targetLanguages])
     */
    @get:Input @get:Optional var targetLanguages: Set<String>,
) {

    companion object {

        internal fun default(
            project: Project,
            rootSourceLanguage: String,
            rootTargetLanguages: Set<String>,
        ): FastlaneTranslationConfig {
            return FastlaneTranslationConfig(
                false,
                project.rootProject.layout.projectDirectory.dir("fastlane/metadata/android"),
                rootSourceLanguage,
                rootTargetLanguages,
            )
        }
    }
}
