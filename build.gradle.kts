// Versões alinhadas com o que o Code on the Go usa (Gradle 9.x + AGP 9.x + Kotlin 2.x)
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
