import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Required from Kotlin 2.0 onward whenever buildFeatures.compose is enabled.
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Builds the Go datapath into an AAR.
 *
 * The AAR is a build product, not a checked-in binary: leaving a stale one in
 * the tree is how the shell process ends up running a datapath that does not
 * match the source next to it.
 *
 * gomobile is not on Gradle's PATH under Android Studio, so the toolchain is
 * located explicitly and the task fails loudly when it is missing rather than
 * silently producing an APK with no datapath in it.
 */
/**
 * A stable fingerprint of the shell-side sources.
 *
 * The daemon survives APK replacement and will not reload a class it has
 * already loaded, so the app needs a value that changes when the code does —
 * and only then. A timestamp would change on every configuration and defeat
 * Gradle's up-to-date checks; hashing the sources does not.
 */
fun sourceFingerprint(projectDir: File): Int {
    val sources = File(projectDir, "src/main/java")
    if (!sources.isDirectory) return 0

    return sources.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .fold(7) { hash, file -> hash * 31 + file.readText().hashCode() }
}

val gomobileBind by tasks.registering(Exec::class) {
    val goModule = rootProject.layout.projectDirectory.dir("datapath")
    val outputAar = layout.buildDirectory.file("gomobile/datapath.aar")

    inputs.dir(goModule).withPropertyName("goSources")
    outputs.file(outputAar).withPropertyName("aar")

    val goBin = File(System.getProperty("user.home"), "go/bin")
    val ndk = android.ndkDirectory

    workingDir = goModule.asFile
    environment("PATH", "${goBin.absolutePath}:${System.getenv("PATH")}")
    environment("ANDROID_NDK_HOME", ndk.absolutePath)
    environment("ANDROID_HOME", android.sdkDirectory.absolutePath)

    doFirst {
        val gomobile = File(goBin, "gomobile")
        check(gomobile.exists()) {
            "gomobile not found at ${gomobile.absolutePath} — " +
                "run: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init"
        }
        outputAar.get().asFile.parentFile.mkdirs()
    }

    commandLine(
        File(goBin, "gomobile").absolutePath,
        "bind",
        "-target=android/arm64",
        "-androidapi", "24",
        "-o", outputAar.get().asFile.absolutePath,
        ".",
    )
}

android {
    namespace = "dev.shizzi"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.shizzi"
        // TetheringManager.setPreferTestNetworks, the one call the whole
        // approach rests on, was added in API 33. Below it the app installs,
        // launches, and can do nothing but report UNSUPPORTED. Rejecting those
        // devices at install time is the honest failure.
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Identifies the build to the shell-side daemon, which survives APK
        // replacement and will not reload an already-loaded class. Without a
        // per-build value a rebuilt implementation keeps running old code
        // behind an unchanged AIDL surface, silently. Derived from the source
        // rather than the clock so it is stable across rebuilds of unchanged
        // code and does not defeat Gradle's up-to-date checks.
        buildConfigField("int", "SERVICE_BUILD_ID", "${sourceFingerprint(projectDir)}")

        // The datapath AAR ships arm64 only. Filtering here keeps the APK from
        // claiming ABIs whose libgojni.so it does not contain, which would fail
        // at load time on a 32-bit device rather than at install.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Loaded from a gitignored file locally and written by CI from secrets.
    // Absent on a fresh clone, which is why every read below is guarded: an
    // unsigned debug build must still work for someone who has never seen the
    // key.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        create("release") {
            val path = keystoreProperties.getProperty("storeFile")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Only when the key is actually present. Configuring it
            // unconditionally makes every release build fail on a machine
            // without the keystore, including CI runs that only need to check
            // that the project compiles.
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// The Go datapath must exist before Kotlin compiles against it.
tasks.named("preBuild") { dependsOn(gomobileBind) }

dependencies {
    // The gomobile AAR, built from /datapath by the task above.
    implementation(files(layout.buildDirectory.file("gomobile/datapath.aar")))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Settings that survive a restart. The theme choice has to be readable
    // before the first frame, so this is read synchronously once at startup
    // and observed as a flow thereafter.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // The app draws seven-plus glyphs across three screens. Hand-drawing them
    // on Canvas, as the first build did for its single gear, produces icons that
    // drift in stroke weight and optical size against each other. R8 shrinks
    // the unused catalogue out of the release build.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Shizuku: API surface + the provider that publishes the binder to us.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Required to call hidden framework APIs on API 28+ without hitting the
    // greylist/blocklist enforcement. See docs/hidden-api-record.md.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
