import java.net.URI
import java.net.URISyntaxException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun validatedApiBaseUrl(
    propertyName: String,
    rawValue: String,
    requireHttps: Boolean,
): String {
    val value = rawValue.trim()
    if (value.isEmpty()) {
        throw GradleException("$propertyName must not be empty.")
    }
    val uri = try {
        URI(value)
    } catch (exception: URISyntaxException) {
        throw GradleException("$propertyName must be a valid URI.", exception)
    }
    val scheme = uri.scheme?.lowercase()
    if (
        !uri.isAbsolute ||
        scheme !in setOf("http", "https") ||
        uri.host.isNullOrBlank()
    ) {
        throw GradleException(
            "$propertyName must be an absolute HTTP(S) URL with a host.",
        )
    }
    if (!value.endsWith('/')) {
        throw GradleException("$propertyName must end with '/'.")
    }
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
        throw GradleException(
            "$propertyName must not contain credentials, a query, or a fragment.",
        )
    }
    if (requireHttps && scheme != "https") {
        throw GradleException("$propertyName must use HTTPS for release builds.")
    }
    return value
}

val defaultApiBaseUrl = "https://api.gathra.my.id/"
val developmentApiBaseUrl = providers
    .gradleProperty("GATHRA_API_BASE_URL")
    .orElse(providers.gradleProperty("GATHRA_ROUTE_API_BASE_URL"))
    .orElse(defaultApiBaseUrl)
    .map { value ->
        validatedApiBaseUrl(
            propertyName = "GATHRA_API_BASE_URL",
            rawValue = value,
            requireHttps = false,
        )
    }
val releaseApiBaseUrl = providers
    .gradleProperty("GATHRA_RELEASE_API_BASE_URL")
    .orElse(providers.gradleProperty("GATHRA_RELEASE_ROUTE_API_BASE_URL"))
    .orElse(defaultApiBaseUrl)
    .map { value ->
        validatedApiBaseUrl(
            propertyName = "GATHRA_RELEASE_API_BASE_URL",
            rawValue = value,
            requireHttps = true,
        )
    }

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
            val apiBaseUrl = developmentApiBaseUrl.get()
            buildConfigField(
                "String",
                "API_BASE_URL",
                apiBaseUrl.asBuildConfigString(),
            )
            manifestPlaceholders["usesCleartextTraffic"] =
                URI(apiBaseUrl).scheme.equals("http", ignoreCase = true).toString()
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField(
                "String",
                "API_BASE_URL",
                releaseApiBaseUrl.get().asBuildConfigString(),
            )
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)
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
