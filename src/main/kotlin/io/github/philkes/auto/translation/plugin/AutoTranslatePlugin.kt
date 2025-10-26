package io.github.philkes.auto.translation.plugin

import io.github.philkes.auto.translation.plugin.config.AutoTranslateExtension
import io.github.philkes.auto.translation.plugin.task.AutoTranslateTask
import io.github.philkes.auto.translation.plugin.task.ListSupportedLanguagesTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class AutoTranslatePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension =
            project.extensions.create(AUTO_TRANSLATE_TASK, AutoTranslateExtension::class.java)

        project.tasks.register(
            AUTO_TRANSLATE_TASK,
            AutoTranslateTask::class.java,
            object : Action<AutoTranslateTask> {
                override fun execute(task: AutoTranslateTask) {
                    extension.sourceLanguage.orNull?.let { task.sourceLanguage.set(it) }
                    extension.targetLanguages.orNull?.let { task.targetLanguages.set(it) }
                    extension.excludeLanguages.orNull?.let { task.excludeLanguages.set(it) }
                    extension.provider.orNull?.let { task.provider.set(it) }
                    extension.translateStringsXml.orNull?.let { task.translateStringsXml.set(it) }
                    extension.translateFastlane.orNull?.let { task.translateFastlane.set(it) }
                }
            },
        )

        project.tasks.register(
            LIST_LANGUAGES_TASK,
            ListSupportedLanguagesTask::class.java,
            object : Action<ListSupportedLanguagesTask> {
                override fun execute(task: ListSupportedLanguagesTask) {
                    extension.provider.orNull?.let { task.provider.set(it) }
                }
            },
        )
    }

    companion object {
        const val AUTO_TRANSLATE_TASK = "autoTranslate"
        const val LIST_LANGUAGES_TASK = "autoTranslateSupportedLanguages"
    }
}
