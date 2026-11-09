import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.thunderbascraft.satranc"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.thunderbascraft.satranc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/hayhak1-hub/satranc\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Stockfish is shipped as an executable named libstockfish.so; it must be
    // extracted to nativeLibraryDir so that it can be started as a process.
    packaging { jniLibs { useLegacyPackaging = true } }
}

/** Compiles the bundled Stockfish sources with the NDK for every ABI. */
abstract class BuildStockfishTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val script: RegularFileProperty
    @get:Internal abstract val ndkDir: DirectoryProperty
    @get:Input abstract val abis: ListProperty<String>
    @get:Input abstract val minSdk: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction fun build() {
        val out = outputDir.get().asFile
        out.deleteRecursively(); out.mkdirs()
        exec.exec {
            commandLine("bash", script.get().asFile.absolutePath, ndkDir.get().asFile.absolutePath,
                out.absolutePath, minSdk.get().toString())
            environment("ABIS", abis.get().joinToString(" "))
        }
    }
}

val buildStockfish = tasks.register<BuildStockfishTask>("buildStockfish") {
    sourceDir.set(rootProject.layout.projectDirectory.dir("engine/stockfish"))
    script.set(rootProject.layout.projectDirectory.file("engine/build-stockfish.sh"))
    ndkDir.set(androidComponents.sdkComponents.ndkDirectory)
    abis.set(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
    minSdk.set(26)
    outputDir.set(layout.buildDirectory.dir("generated/stockfish/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildStockfish, BuildStockfishTask::outputDir)
    }
}

dependencies {
    implementation(project(":chess-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // Optional mirror for the Android jars Robolectric downloads at test time.
    System.getenv("ROBOLECTRIC_REPO")?.let { systemProperty("robolectric.dependency.repo.url", it) }
    System.getenv("SCREENSHOT_DIR")?.let { environment("SCREENSHOT_DIR", it) }
}
