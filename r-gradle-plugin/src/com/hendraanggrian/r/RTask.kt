@file:Suppress("NOTHING_TO_INLINE")

package com.hendraanggrian.r

import com.hendraanggrian.javapoet.TypeSpecBuilder
import com.hendraanggrian.javapoet.buildJavaFile
import com.hendraanggrian.r.adapters.BaseAdapter
import com.hendraanggrian.r.adapters.CssAdapter
import com.hendraanggrian.r.adapters.JsonAdapter
import com.hendraanggrian.r.adapters.PathAdapter
import com.hendraanggrian.r.adapters.PropertiesAdapter
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.invoke
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ofPattern
import javax.lang.model.element.Modifier

/** A task that writes `R` class. */
open class RTask : DefaultTask() {

    /**
     * Package name of which `R` class will be generated to, cannot be empty.
     * If left null, project group will be assigned as value.
     */
    @Input var packageName: String? = null

    /**
     * Generated class name, cannot be empty.
     * Default value is `R`.
     */
    @Input var className: String = "R"

    /**
     * When activated, automatically make all field names uppercase.
     * It is disabled by default.
     */
    var isUppercaseField: Boolean = false @Input get

    /**
     * When activated, automatically make all class names lowercase.
     * It is enabled by default.
     */
    var isLowercaseClass: Boolean = false @Input get

    /**
     * Main resources directory.
     * Default is resources folder in main module.
     */
    @InputDirectory lateinit var resourcesDir: File

    /** Convenient method to set resources directory relative to project directory. */
    var resourcesDirectory: String
        @Input get() = resourcesDir.absolutePath
        set(value) {
            resourcesDir = project.projectDir.resolve(value)
        }

    /**
     * Collection of files (or directories) that are ignored from this task.
     * Default is empty.
     */
    @InputFiles var exclusions: Iterable<File> = emptyList()

    /** Convenient method to set exclusions relative to project directory. */
    fun exclude(vararg exclusions: String) {
        this.exclusions = exclusions.map { project.projectDir.resolve(it) }
    }

    /**
     * Directory of which `R` class will be generated to.
     * Default is `build/generated` relative to project directory.
     */
    @OutputDirectory lateinit var outputDir: File

    /** Convenient method to set output directory relative to project directory. */
    var outputDirectory: String
        @Input get() = outputDir.absolutePath
        set(value) {
            outputDir = project.projectDir.resolve(value)
        }

    private var cssSettings: CssSettings? = null
    private var propertiesSettings: PropertiesSettings? = null
    private var jsonSettings: JsonSettings? = null

    init {
        outputs.upToDateWhen { false } // always consider this task out of date
    }

    /** Enable CSS files support with default configuration. */
    fun configureCss() {
        var settings = cssSettings
        if (settings == null) {
            settings = CssSettings()
            cssSettings = settings
        }
    }

    /** Enable CSS files support with customized [configuration]. */
    fun configureCss(configuration: Action<CssSettings>) {
        configureCss()
        configuration(cssSettings!!)
    }

    /** Enable CSS files support with customized [configuration] in Kotlin DSL. */
    inline fun css(noinline configuration: CssSettings.() -> Unit): Unit =
        configureCss(configuration)

    /** Enable properties files support with default configuration. */
    fun configureProperties() {
        var settings = propertiesSettings
        if (settings == null) {
            settings = PropertiesSettings()
            propertiesSettings = settings
        }
    }

    /** Enable properties files support with customized [configuration]. */
    fun configureProperties(configuration: Action<PropertiesSettings>) {
        configureProperties()
        configuration(propertiesSettings!!)
    }

    /** Enable properties files support with customized [configuration] in Kotlin DSL. */
    inline fun properties(noinline configuration: PropertiesSettings.() -> Unit): Unit =
        configureProperties(configuration)

    /** Enable json files support with default configuration. */
    fun configureJson() {
        var settings = jsonSettings
        if (settings == null) {
            settings = JsonSettings()
            jsonSettings = settings
        }
    }

    /** Enable json files support with customized [configuration]. */
    fun configureJson(configuration: Action<JsonSettings>) {
        configureJson()
        configuration(jsonSettings!!)
    }

    /** Enable json files support with customized [configuration] in Kotlin DSL. */
    inline fun json(noinline configuration: JsonSettings.() -> Unit): Unit =
        configureJson(configuration)

    /** Generate R class given provided options. */
    @TaskAction fun generate() {
        logger.info("Generating R:")

        require(packageName!!.isNotBlank()) { "Package name cannot be empty" }
        require(className.isNotBlank()) { "Class name cannot be empty" }
        require(resourcesDir.exists() && resourcesDir.isDirectory) { "Resources folder not found" }

        if (outputDir.exists()) {
            logger.info("  Existing source deleted")
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()

        val javaFile = buildJavaFile(packageName!!) {
            comment = "Generated at ${LocalDateTime.now().format(ofPattern("MM-dd-yyyy 'at' h.mm.ss a"))}"
            addClass(className) {
                addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                methods.addConstructor { addModifiers(Modifier.PRIVATE) }
                processDir(
                    listOfNotNull(
                        cssSettings?.let { CssAdapter(it, isUppercaseField, logger) },
                        jsonSettings?.let { JsonAdapter(it, isUppercaseField, logger) },
                        propertiesSettings?.let { PropertiesAdapter(it, isLowercaseClass, isUppercaseField, logger) }
                    ),
                    PathAdapter(resourcesDir.path, isUppercaseField, logger),
                    resourcesDir
                )
            }
        }

        javaFile.writeTo(outputDir)
        logger.info("  Source generated")
    }

    private fun TypeSpecBuilder.processDir(
        adapters: Iterable<BaseAdapter>,
        pathAdapter: PathAdapter,
        resourcesDir: File
    ) {
        val exclusionPaths = exclusions.map { it.path }
        resourcesDir.listFiles()!!
            .filter { file -> !file.isHidden && file.path !in exclusionPaths }
            .forEach { file ->
                when {
                    file.isDirectory -> {
                        var innerClassName = file.name.toJavaNameOrNull()
                        if (innerClassName != null) {
                            if (isLowercaseClass) {
                                innerClassName = innerClassName.toLowerCase()
                            }
                            types.addClass(innerClassName) {
                                addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                methods.addConstructor { addModifiers(Modifier.PRIVATE) }
                                processDir(adapters, pathAdapter, file)
                            }
                        }
                    }
                    file.isFile -> {
                        pathAdapter.isUnderscorePrefix = adapters.any { it.process(this, file) }
                        pathAdapter.process(this, file)
                    }
                }
            }
    }
}
