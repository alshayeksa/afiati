import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// إعداد Firebase (إشعارات العائلة): الملف خارج git. بدونه يُبنى التطبيق ويعمل، لكن بلا إشعارات فورية من العائلة.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// توقيع نسخة النشر: من متغيرات البيئة في GitHub، أو من keystore.properties محليًا. الملف والمفتاح خارج git.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(name: String): String? = System.getenv(name) ?: keystoreProperties.getProperty(name)

// رقم البناء: رقم تشغيل GitHub عند البناء هناك، و1 محليًا
val buildNumber = (System.getenv("AFIATI_BUILD_NUMBER") ?: "1").toInt()
// عنوان الخادم (Cloudflare Worker). يمكن تغييره عند البناء بمتغير AFIATI_API_URL.
val apiUrl = System.getenv("AFIATI_API_URL") ?: "https://afiati-api.alshayeksa.workers.dev"

android {
    namespace = "com.alshayeksa.afiati"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.alshayeksa.afiati"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "2.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        // إعدادات التطبيق (ومنها عنوان الخادم) تُقرأ من هنا، فيمكن نقل الخادم بلا تحديث للتطبيق
        buildConfigField("String", "CONFIG_URL", "\"https://afiati.alshaye.ai/v1/app-config\"")
    }

    signingConfigs {
        val keystore = signingValue("AFIATI_KEYSTORE_FILE")
        if (keystore != null) {
            create("release") {
                storeFile = rootProject.file(keystore)
                storePassword = signingValue("AFIATI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("AFIATI_KEY_ALIAS")
                keyPassword = signingValue("AFIATI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }
}

dependencies {
    // اختبارات المنطق على JVM: المعرّفات، الجدولة، وحل التعارض
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    // رمز QR لدعوة العائلة
    implementation("com.google.zxing:core:3.5.3")
    // قياس النبض: الكاميرا الخلفية والفلاش، والتحليل على الجوال فقط
    implementation("androidx.camera:camera-core:1.7.0")
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    // إشعارات العائلة الفورية عبر Firebase Cloud Messaging (مجانية)
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")
    // مكتبات Google تجلب نسخة قديمة من Fragment لا تتوافق مع طلب الأذونات الحديث
    implementation("androidx.fragment:fragment:1.9.1")
}
