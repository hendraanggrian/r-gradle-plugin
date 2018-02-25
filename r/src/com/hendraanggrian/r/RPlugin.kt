package com.hendraanggrian.r

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.closureOf
import org.gradle.plugins.ide.idea.model.IdeaModel

/** Generate Android-like R class with this plugin. */
class RPlugin : Plugin<Project> {

    private lateinit var project: Project

    override fun apply(target: Project) {
        project = target
        val extension = project.extensions.create(EXTENSION_NAME, RExtension::class.java, project)
        project.afterEvaluate {
            val generateTask = extension.createGenerateTask()
            val compileTask = generateTask.createCompileTask(extension.getTaskName("compile"))
            val compiledClasses = project.files(compileTask.outputs.files.filter { !it.name.endsWith("dependency-cache") })
            compiledClasses.builtBy(compileTask)

            val sourceSet = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main")
            sourceSet.compileClasspath += compiledClasses
            compiledClasses.forEach { sourceSet.output.dir(it) }

            require(project.plugins.hasPlugin("org.gradle.idea")) { "plugin 'idea' must be applied" }
            val providedConfig = project.configurations.create(extension.getTaskName("provided"))
            providedConfig.dependencies.add(project.dependencies.create(compiledClasses))
            (project.extensions.getByName("idea") as IdeaModel).module.scopes["PROVIDED"]!!["plus"]!! += providedConfig
        }
    }

    private fun RExtension.createGenerateTask(): GenerateRTask = project.task(
        mapOf("type" to GenerateRTask::class.java),
        getTaskName("generate"),
        closureOf<GenerateRTask> {
            group = GROUP_NAME
            config = toConfig()
            outputDirectory = project.buildDir.resolve("$GENERATED_DIRECTORY/$EXTENSION_NAME/src/main")
        }) as GenerateRTask

    private fun GenerateRTask.createCompileTask(name: String): JavaCompile = project.task(
        mapOf("type" to JavaCompile::class.java, "dependsOn" to this),
        name,
        closureOf<JavaCompile> {
            group = GROUP_NAME
            classpath = project.files()
            destinationDir = project.buildDir.resolve("$GENERATED_DIRECTORY/$EXTENSION_NAME/classes/main")
            source(this@createCompileTask.outputDirectory)
        }) as JavaCompile

    companion object {
        internal const val EXTENSION_NAME = "r"
        internal const val GROUP_NAME = "generation"
        internal const val GENERATED_DIRECTORY = "generated"
    }
}