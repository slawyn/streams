import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = false   // ❗ You are NOT using Compose → disable it
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

/* ---------------------------------------------------------
   🔧 Build number generator
--------------------------------------------------------- */

val generateBuildNumberRes by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/res/buildnumber")
    outputs.dir(outDir)

    doLast {
        val propFile = rootProject.file("gradle.properties")
        val props = Properties()

        if (propFile.exists()) {
            propFile.inputStream().use { props.load(it) }
        }

        val current = props.getProperty("buildNumber")?.toIntOrNull() ?: 0
        val next = current + 1

        props.setProperty("buildNumber", next.toString())
        propFile.outputStream().use { props.store(it, null) }

        val resDir = outDir.get().asFile
        val valuesDir = File(resDir, "values")
        valuesDir.mkdirs()

        File(valuesDir, "build.xml").writeText(
            """
            <resources>
                <string name="version">1.0</string>
                <string name="build">$next</string>
            </resources>
            """.trimIndent()
        )
    }
}

android.sourceSets["main"].res.srcDir(
    layout.buildDirectory.dir("generated/res/buildnumber")
)

tasks.named("preBuild") {
    dependsOn(generateBuildNumberRes)
}

/* ---------------------------------------------------------
   📦 Dependencies
--------------------------------------------------------- */

dependencies {
    implementation("com.google.android.material:material:1.10.0")

    implementation("androidx.media3:media3-exoplayer-hls:1.7.1")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

    implementation("androidx.appcompat:appcompat:1.7.0") // ✔ fixed version

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
