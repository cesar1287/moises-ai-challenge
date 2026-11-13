plugins {
  id("jacoco")
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.example.moisessongs"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.example.moisessongs"
    minSdk = 26
    targetSdk = 36
    testInstrumentationRunner = "com.example.moisessongs.SongsTestRunner"
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    debug {
      enableUnitTestCoverage = true
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }
  testOptions {
    // Isolate application state and media-service bindings between device tests.
    execution = "ANDROIDX_TEST_ORCHESTRATOR"
  }
  androidResources {
    generateLocaleConfig = true
    localeFilters += listOf("en", "pt-rBR")
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

composeCompiler {
  reportsDestination = layout.buildDirectory.dir("reports/compose")
  metricsDestination = layout.buildDirectory.dir("reports/compose")
}

jacoco { toolVersion = "0.8.14" }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.kotlinx.coroutines.guava)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.coil.compose)
  implementation(libs.coil.network)
  implementation(libs.androidx.splashscreen)
  testImplementation(libs.mockwebserver)
  androidTestImplementation(libs.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.compose.ui.test.accessibility)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestUtil(libs.androidx.test.orchestrator)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
