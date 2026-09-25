import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Le moteur Stockfish natif est optionnel : il n'est compilé que si les sources ont ete
 * recuperees par `tools/fetch_stockfish.ps1`. Sans lui, l'appli utilise son moteur Kotlin
 * integre (ForgeEngine) et reste parfaitement fonctionnelle.
 */
val stockfishSrcDir = rootProject.file("third_party/stockfish/src")
val hasStockfish = stockfishSrcDir.isDirectory &&
    stockfishSrcDir.listFiles { f -> f.name == "uci.cpp" }?.isNotEmpty() == true

/**
 * Cle de televersement Play. Les secrets vivent dans `keystore.properties`, hors du
 * depot (voir .gitignore) et pointant vers un keystore range ailleurs encore : rien
 * de sensible ne peut partir sur GitHub. Absent, on retombe sur la cle de debug pour
 * que le projet reste compilable par quiconque le clone.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val uploadKeystore = keystoreProperties.getProperty("storeFile")?.let(::File)

/**
 * La cle de televersement n'est utilisee que sur demande explicite :
 *
 *     gradlew bundleRelease -PplayRelease
 *
 * Changer de cle de signature rend l'application non actualisable sur un appareil ou
 * elle est deja installee : Android refuse la mise a jour et il faudrait desinstaller,
 * donc effacer les parties et les puzzles. Les builds ordinaires restent donc signes
 * avec la cle de debug, et seul le bundle destine a Google Play porte la vraie cle.
 */
val signForPlay = providers.gradleProperty("playRelease").isPresent
val hasUploadKey = uploadKeystore?.exists() == true && signForPlay

android {
    namespace = "com.chessforge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chessforge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // arm64 uniquement : c'est l'architecture de tous les telephones vises, et cela
        // divise par deux le temps de compilation de Stockfish comme la taille de l'APK.
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("boolean", "HAS_NATIVE_ENGINE", hasStockfish.toString())
        if (hasStockfish) {
            externalNativeBuild {
                cmake {
                    // c++_static : une seule bibliotheque native, inutile d'embarquer
                    // libc++_shared.so en plus.
                    // android-29 : plateforme minimale exigee par Stockfish. L'appli
                    // reste installable depuis l'API 26, elle se rabattra simplement
                    // sur son moteur integre si la bibliotheque ne se charge pas.
                    arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_PLATFORM=android-29")
                    cppFlags += listOf("-std=c++17")
                }
            }
        }
    }

    if (hasStockfish) {
        ndkVersion = "27.0.12077973"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
        // Repli : cle de debug, suffisante pour installer soi-meme par sideload,
        // jamais acceptee par le Play Store.
        create("sideload") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "sideload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        jniLibs.useLegacyPackaging = false
    }

    androidResources {
        generateLocaleConfig = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
