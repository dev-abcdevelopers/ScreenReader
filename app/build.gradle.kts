@file:Suppress("LocalVariableName", "FunctionName")

plugins {
    id("com.android.application")
}

fun UploadProperty(NameText: String): String =
    (project.findProperty(NameText) as? String)?.trim().orEmpty()

android {
    namespace = "com.bliss.screenreader"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "21.08.2026|1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "brand"

    productFlavors {
        create("bliss") {
            dimension = "brand"
            applicationId = "com.bliss.screenreader"
            resValue("string", "app_name", "BLISS Reader")
            buildConfigField("boolean", "URL_LICENCE", "true")
            buildConfigField("String", "LICENCE_URL", "\"https://blissmis.com/mregister1\"")
            buildConfigField("String", "LICENCE_ARGS", "\"1!1!1!1!COMBO!1!1\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"+919716720049\"")
            buildConfigField("String", "SUPPORT_PHONE_DISPLAY", "\"+91 97167 20049\"")
            buildConfigField("boolean", "UPLOAD_ENABLED", "true")
            buildConfigField(
                "String",
                "UPLOAD_URL",
                "\"${UploadProperty("blissUploadUrl").ifEmpty { "https://bmaservices.in/agdata/upload.php" }}\""
            )
            buildConfigField(
                "String",
                "UPLOAD_SIGN_PATH",
                "\"${UploadProperty("blissUploadSignPath").ifEmpty { "/upload.php" }}\""
            )
            buildConfigField("String", "UPLOAD_APP_KEY", "\"${UploadProperty("blissUploadAppKey")}\"")
            buildConfigField("String", "UPLOAD_APP_SECRET", "\"${UploadProperty("blissUploadAppSecret")}\"")
            buildConfigField(
                "String",
                "UPDATE_URL",
                "\"${UploadProperty("blissUpdateUrl").ifEmpty { "https://mybliss.in/update/ScreenReaderVersion.json" }}\""
            )
        }
        create("digi") {
            dimension = "brand"
            applicationId = "com.digi.screenreader"
            resValue("string", "app_name", "Digi Reader")
            buildConfigField("boolean", "URL_LICENCE", "false")
            buildConfigField("String", "LICENCE_URL", "\"\"")
            buildConfigField("String", "LICENCE_ARGS", "\"\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"\"")
            buildConfigField("String", "SUPPORT_PHONE_DISPLAY", "\"\"")
            buildConfigField("boolean", "UPLOAD_ENABLED", "false")
            buildConfigField("String", "UPLOAD_URL", "\"\"")
            buildConfigField("String", "UPLOAD_SIGN_PATH", "\"\"")
            buildConfigField("String", "UPLOAD_APP_KEY", "\"\"")
            buildConfigField("String", "UPLOAD_APP_SECRET", "\"\"")
            buildConfigField("String", "UPDATE_URL", "\"${UploadProperty("digiUpdateUrl")}\"")
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "BYPASS_AUTH", "false")
        }
        getByName("release") {
            buildConfigField("boolean", "BYPASS_AUTH", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}


dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0")
    implementation("androidx.activity:activity-ktx:1.13.0")

    // Excel Export (Apache POI)
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // On-device OCR for contact sheets the app hides from accessibility
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Gson for JSON operations
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation("junit:junit:4.13.2")
}
