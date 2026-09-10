plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.basculaserial"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.basculaserial"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
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
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Iconos Material extendidos (Scale, Usb, UsbOff, etc.)
  implementation("androidx.compose.material:material-icons-extended")

  // USB Serial para comunicación con báscula
  implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

  // Cámara USB UVC (Rapoo C500) — libausbc v3.2.7
  // Excluir dependencias transitivas no disponibles en repos estándar
  implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.2.7") {
    exclude(group = "com.gyf.immersionbar")        // status bar lib (innecesario)
    exclude(group = "com.zlc.glide")               // webp decoder (innecesario)
    exclude(group = "com.github.bumptech.glide")   // glide (usamos Coil)
    exclude(group = "com.tencent", module = "mmkv") // mmkv prefs (innecesario)
  }
  // libuvc es runtime dep de libausbc pero necesitamos USBMonitor en compile classpath
  implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvc:3.2.7")
  implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvccommon:3.2.7")
  // Coil — carga de imágenes en Compose (thumbnails de fotos)
  implementation("com.google.zxing:core:3.5.3")
  implementation("io.coil-kt:coil-compose:2.7.0")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
