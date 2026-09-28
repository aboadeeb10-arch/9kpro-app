import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "tv.ninekpro"
version = "1.0.1"   // desktop line: MSI/DMG need major >= 1

kotlin { jvmToolchain(17) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation("com.google.zxing:core:3.5.3")
}

compose.desktop {
    application {
        mainClass = "tv.ninekpro.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g", "-Dfile.encoding=UTF-8")
        buildTypes.release.proguard { isEnabled.set(false) }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "9KProTV"
            packageVersion = project.version.toString()
            description = "9K Pro TV"
            vendor = "9K Pro TV"
            copyright = "9K Pro TV"
            modules("java.naming", "java.net.http", "jdk.unsupported", "java.management", "java.sql", "jdk.crypto.ec")
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            windows {
                menu = true; shortcut = true; perUserInstall = true; dirChooser = false
                upgradeUuid = "6f2c1b7e-3d3a-4a2e-9c4e-5b7a1f0e2d11"
                iconFile.set(project.file("icon.ico"))
            }
            macOS {
                bundleID = "tv.ninekpro.desktop"; dockName = "9K Pro TV"
                iconFile.set(project.file("icon.icns"))
            }
        }
    }
}
