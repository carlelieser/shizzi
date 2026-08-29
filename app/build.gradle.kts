import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.compose")
}

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

        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("int", "SERVICE_BUILD_ID", "${sourceFingerprint(projectDir)}")

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

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

tasks.named("preBuild") { dependsOn(gomobileBind) }

dependencies {

    implementation(files(layout.buildDirectory.file("gomobile/datapath.aar")))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
}
