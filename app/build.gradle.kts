plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val developmentRouteApiBaseUrl = providers
    .gradleProperty("GATHRA_ROUTE_API_BASE_URL")
    .orElse("http://10.0.2.2:3000/")
val releaseRouteApiBaseUrl = providers
    .gradleProperty("GATHRA_RELEASE_ROUTE_API_BASE_URL")
    .orElse("https://routes.invalid/")

android {
    namespace = "opsi.sman35jkt.gathra"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "opsi.sman35jkt.gathra"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "ROUTE_API_BASE_URL",
                developmentRouteApiBaseUrl.get().asBuildConfigString(),
            )
            buildConfigField("boolean", "USE_FAKE_ROUTES", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField(
                "String",
                "ROUTE_API_BASE_URL",
                developmentRouteApiBaseUrl.get().asBuildConfigString(),
            )
            buildConfigField("boolean", "USE_FAKE_ROUTES", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            matchingFallbacks += listOf("debug")
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField(
                "String",
                "ROUTE_API_BASE_URL",
                releaseRouteApiBaseUrl.get().asBuildConfigString(),
            )
            buildConfigField("boolean", "USE_FAKE_ROUTES", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
