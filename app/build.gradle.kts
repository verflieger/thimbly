import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "at.mtr.sewfar"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.mtr.sewfar"
        minSdk = 34
        targetSdk = 37
        // Raise versionCode for every APK given to others: Android only installs higher versions as updates.
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    // The release key lives outside the repository. Without it the release APK stays unsigned and can't be
    // installed, so it never ships with a wrong key.
    val releaseKey = File(System.getProperty("user.home"), ".config/sewfar/keystore.properties")
    if (releaseKey.exists()) {
        val props = Properties().apply { releaseKey.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = File(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG switches on destructive Room migrations during development (SPEC 8).
        buildConfig = true
    }

    // The dependency list AGP embeds is encrypted with Google's key; F-Droid can't check it and flags it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Lists the app's languages (English default, German) for Android's per-app language setting (SPEC 1).
    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        // Robolectric needs merged resources on the unit-test classpath.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric touches FileDescriptor internals, which newer JDKs lock down.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * SPEC 1 / 12: the merged manifest must contain neither INTERNET (offline promise) nor CAMERA
 * (TakePicture throws SecurityException if CAMERA is declared but not granted). Runs before every assemble.
 */
abstract class CheckForbiddenPermissions : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()
        val found = listOf("INTERNET", "CAMERA").filter { permission ->
            Regex("""<uses-permission[^>]*android:name="android\.permission\.$permission"""").containsMatchIn(text)
        }
        if (found.isNotEmpty()) {
            throw GradleException("Merged manifest declares forbidden permission(s): $found (see SPEC 1)")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val checkTask = tasks.register<CheckForbiddenPermissions>("check${variantName}Permissions") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(checkTask) }
        tasks.matching { it.name == "test${variantName}UnitTest" }.configureEach { dependsOn(checkTask) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Compose test pulls an Espresso too old for current Android; pin a newer one.
    testImplementation(libs.espresso.core)
}
