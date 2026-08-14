import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.21"
}

paperweight {
    filterPatches = false

    // The upstream name must match ASP's fork name ("aspaper" in aspaper-server/build.gradle.kts): paperweight-core
    // resolves every non-active fork's root to upstreams/<fork name>, which is where this upstream checkout lands.
    upstreams.register("aspaper") {
        repo = github("InfernalSuite", "AdvancedSlimePaper")
        ref = providers.gradleProperty("aspRef")

        patchFile {
            path = "aspaper-server/build.gradle.kts"
            outputFile = file("scissors-server/build.gradle.kts")
            patchFile = file("scissors-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "aspaper-api/build.gradle.kts"
            outputFile = file("scissors-api/build.gradle.kts")
            patchFile = file("scissors-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("scissors-api/paper-patches")
            outputDir = file("paper-api")
        }
        // ASP's aspaper modules depend on its slime `api` and `core` projects. The paperweight patch config below
        // materializes both upstream-owned projects without renaming them to Scissors. Their build scripts are
        // patched to stand alone because the upstream versions rely on ASP's buildSrc convention plugins.
        patchFile {
            path = "api/build.gradle.kts"
            outputFile = file("api/build.gradle.kts")
            patchFile = file("api/build.gradle.kts.patch")
        }
        patchDir("aspApi") {
            upstreamPath = "api"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("scissors-api/asp-api-patches")
            outputDir = file("asp-api")
        }
        patchFile {
            path = "core/build.gradle.kts"
            outputFile = file("core/build.gradle.kts")
            patchFile = file("core/build.gradle.kts.patch")
        }
        patchDir("aspServer") {
            upstreamPath = "aspaper-server/src"
            patchesDir = file("scissors-server/asp-server-patches")
            outputDir = file("asp-server")
        }
        patchDir("aspCore") {
            upstreamPath = "core"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("scissors-server/asp-core-patches")
            outputDir = file("asp-core")
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://artifactory.papermc.io/artifactory/releases/") {
                name = "paperReleases"
                credentials(PasswordCredentials::class)
            }
        }
    }
}
