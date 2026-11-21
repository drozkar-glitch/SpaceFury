import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.FileInputStream
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.credentials:credentials:1.3.0")
            implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
    }
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

fun resolveSigningValue(propKey: String, vararg envKeys: String): String? {
    keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }?.let { return it }
    for (env in envKeys) {
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

val resolvedStorePath = resolveSigningValue(
    "storeFile",
    "CM_KEYSTORE_PATH",
    "KEYSTORE_FILE",
    "ANDROID_KEYSTORE_PATH",
    "KEYSTORE_PATH"
)
val resolvedStoreFile = resolvedStorePath?.let { path ->
    val f = file(path)
    if (f.exists()) f else rootProject.file(path)
}?.takeIf { it.exists() }

val resolvedStorePassword = resolveSigningValue(
    "storePassword",
    "CM_KEYSTORE_PASSWORD",
    "KEYSTORE_PASSWORD",
    "STORE_PASSWORD"
)
val resolvedKeyAlias = resolveSigningValue(
    "keyAlias",
    "CM_KEY_ALIAS",
    "KEY_ALIAS",
    "ANDROID_KEY_ALIAS"
)
val resolvedKeyPassword = resolveSigningValue(
    "keyPassword",
    "CM_KEY_PASSWORD",
    "KEY_PASSWORD",
    "ANDROID_KEY_PASSWORD"
) ?: resolvedStorePassword

val ciRunNumber = System.getenv("CM_BUILD_NUMBER")?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: 1
val computedVersionCode = if (ciRunNumber > 100) ciRunNumber else ciRunNumber + 100
val computedVersionName = System.getenv("CM_BUILD_VERSION")?.takeIf { it.isNotBlank() }
    ?: System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "1.0.0"

android {
    namespace = "cz.mares.spacefury"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.spacefury"
        minSdk = 24
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName
    }

    signingConfigs {
        create("release") {
            if (resolvedStoreFile != null && !resolvedStorePassword.isNullOrBlank() && !resolvedKeyAlias.isNullOrBlank()) {
                storeFile = resolvedStoreFile
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (resolvedStoreFile != null && !resolvedStorePassword.isNullOrBlank() && !resolvedKeyAlias.isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val generateIosAppIcon by tasks.registering {
    val iconFile = rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
    outputs.file(iconFile)
    doLast {
        if (!iconFile.exists()) {
            System.setProperty("java.awt.headless", "true")
            iconFile.parentFile.mkdirs()
            val size = 1024
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            g.paint = GradientPaint(0f, 0f, Color(6, 11, 34), 0f, size.toFloat(), Color(2, 4, 14))
            g.fillRect(0, 0, size, size)

            g.paint = GradientPaint(180f, 180f, Color(16, 36, 88), 844f, 844f, Color(8, 16, 44))
            g.fill(RoundRectangle2D.Float(110f, 110f, 804f, 804f, 210f, 210f))

            g.color = Color(0, 229, 255, 55)
            g.stroke = BasicStroke(16f)
            g.drawOval(190, 190, 644, 644)

            val leftPod = Polygon(
                intArrayOf(245, 345, 375, 275),
                intArrayOf(560, 440, 725, 765),
                4
            )
            g.paint = GradientPaint(245f, 440f, Color(255, 56, 100), 375f, 765f, Color(150, 16, 56))
            g.fillPolygon(leftPod)

            val rightPod = Polygon(
                intArrayOf(779, 679, 649, 749),
                intArrayOf(560, 440, 725, 765),
                4
            )
            g.paint = GradientPaint(649f, 440f, Color(255, 56, 100), 779f, 765f, Color(150, 16, 56))
            g.fillPolygon(rightPod)

            val leftWing = Polygon(
                intArrayOf(512, 312, 382, 512),
                intArrayOf(205, 770, 770, 365),
                4
            )
            g.paint = GradientPaint(312f, 205f, Color(0, 245, 255), 512f, 770f, Color(0, 128, 210))
            g.fillPolygon(leftWing)

            val rightWing = Polygon(
                intArrayOf(512, 712, 642, 512),
                intArrayOf(205, 770, 770, 365),
                4
            )
            g.paint = GradientPaint(512f, 205f, Color(140, 255, 255), 712f, 770f, Color(0, 165, 245))
            g.fillPolygon(rightWing)

            g.paint = GradientPaint(375f, 595f, Color(255, 214, 0), 649f, 650f, Color(255, 115, 0))
            g.fillRoundRect(376, 595, 272, 52, 24, 24)

            val flame = Polygon(
                intArrayOf(462, 562, 512),
                intArrayOf(668, 668, 825),
                3
            )
            g.paint = GradientPaint(512f, 668f, Color(255, 245, 120), 512f, 825f, Color(255, 45, 0))
            g.fillPolygon(flame)

            g.dispose()
            ImageIO.write(img, "PNG", iconFile)
        }
    }
}

tasks.matching { it.name.contains("Ios", ignoreCase = true) || it.name.contains("Xcode", ignoreCase = true) }.configureEach {
    dependsOn(generateIosAppIcon)
}
