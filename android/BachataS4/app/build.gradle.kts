import java.util.Properties
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bachatas4.android"
    compileSdk = 37

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            localProperties.load(it)
        }
    }

    val versionProperties = Properties()
    val versionPropertiesFile = rootProject.file("version.properties")
    if (versionPropertiesFile.exists()) {
        versionPropertiesFile.inputStream().use {
            versionProperties.load(it)
        }
    }

    val releaseKeystoreName = localProperties.getProperty("signing.storeFile")
    val releaseKeystoreFile =
        if (releaseKeystoreName != null) rootProject.file(releaseKeystoreName) else null
    val hasReleaseKeystore = releaseKeystoreFile != null && releaseKeystoreFile.exists()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = localProperties.getProperty("signing.storePassword")
                keyAlias = localProperties.getProperty("signing.keyAlias")
                keyPassword = localProperties.getProperty("signing.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.bachatas4.android"
        minSdk = 31
        targetSdk = 37
        // Priority: CLI -P only > version.properties > date-based dev defaults
        // (do not read VERSION_* from gradle.properties — that fights AutoUpdate)
        val cliVersionCode = gradle.startParameter.projectProperties["VERSION_CODE"]
        val cliVersionName = gradle.startParameter.projectProperties["VERSION_NAME"]
        versionCode = cliVersionCode?.toIntOrNull()
            ?: versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
            ?: SimpleDateFormat("yyMMddHH").format(Date()).toInt()
        versionName = cliVersionName
            ?: versionProperties.getProperty("VERSION_NAME")
            ?: ("0.1.0-dev-" + SimpleDateFormat("yyyyMMdd-HHmm").format(Date()))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Only attach signing when a local release keystore is configured.
            // F-Droid builds strip signing config and must remain unsigned here.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "DOWNLOAD_RUNTIME", "false")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("Boolean", "DOWNLOAD_RUNTIME", "true")
        }
    }
    androidResources {
        noCompress += listOf("zip", "json")
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        val startParameterTasks = gradle.startParameter.taskNames
        val hasPlaystoreExplicitly = startParameterTasks.any { it.contains("playstore", ignoreCase = true) }
        val hasGenericAssemble = startParameterTasks.any { 
            it.endsWith("assemble") || 
            it.endsWith("assembleDebug") || 
            it.endsWith("assembleRelease") || 
            it.endsWith("build") 
        }
        
        if (variantBuilder.flavorName == "playstore" && hasGenericAssemble && !hasPlaystoreExplicitly) {
            variantBuilder.enable = false
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:runtime"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:library"))
    implementation(project(":feature:session"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:drivers"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
