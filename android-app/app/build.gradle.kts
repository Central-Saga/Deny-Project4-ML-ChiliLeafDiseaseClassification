import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.example.chilileafdisease"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.chilileafdisease"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists() && keystoreProperties.containsKey("storeFile")) {
                val storePath = keystoreProperties.getProperty("storeFile")
                val storeCandidateRoot = rootProject.file(storePath)
                val storeCandidateApp = file(storePath)
                val resolvedStoreFile = when {
                    File(storePath).isAbsolute -> File(storePath)
                    storeCandidateRoot.exists() -> storeCandidateRoot
                    storeCandidateApp.exists() -> storeCandidateApp
                    else -> storeCandidateRoot
                }.absoluteFile
                storeFile = resolvedStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.litert)
}

val syncModelArtifacts = tasks.register("syncModelArtifacts") {
    description = "Syncs single-source ML model artifact from ml/models into assets with SHA-256 verification"
    val sourceModel = rootProject.file("../ml/models/mobilenetv2_chili_float32.tflite")
    val targetDir = file("src/main/assets")
    val targetModel = File(targetDir, "mobilenetv2_chili_float32.tflite")
    val expectedSha256 = "989F6CE8AF959779C524BA7E85254E6DE957D4145AD51ECE341A3A3FDEA47159"

    inputs.file(sourceModel)
    outputs.file(targetModel)

    doLast {
        if (!sourceModel.exists()) {
            throw GradleException("Single-source model artifact not found at ${sourceModel.absolutePath}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        sourceModel.inputStream().use { input ->
            val buf = ByteArray(65536)
            var r: Int
            while (input.read(buf).also { r = it } > 0) {
                digest.update(buf, 0, r)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { b ->
            String.format(Locale.US, "%02X", b)
        }
        if (actualSha256 != expectedSha256) {
            throw GradleException("Checksum mismatch for ${sourceModel.name}! Expected $expectedSha256 but got $actualSha256")
        }
        targetDir.mkdirs()
        if (!targetModel.exists() || targetModel.length() != sourceModel.length()) {
            sourceModel.copyTo(targetModel, overwrite = true)
            logger.lifecycle("Synchronized single-source ${sourceModel.name} to assets (SHA-256: $actualSha256)")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncModelArtifacts)
}
