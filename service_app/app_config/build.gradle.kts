plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.voiptest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voiptest"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ NDK ABI 필터 추가 (필수!)
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    buildFeatures {
        viewBinding = true
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }

        // ✅ JNI 라이브러리 충돌 해결 (필수!)
        jniLibs {
            pickFirsts.addAll(listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/libfbjni.so",
                "lib/arm64-v8a/libtorch.so",
                "lib/arm64-v8a/libtorch_cpu.so"
            ))
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets")  // ✅ assets 명시
        }
    }
}

dependencies {
    // AndroidX 기본 라이브러리
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // 테스트 라이브러리
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // BOM으로 Google Cloud 라이브러리 버전 관리
    implementation(platform("com.google.cloud:libraries-bom:26.68.0"))

    // Google Cloud Speech
    implementation("com.google.cloud:google-cloud-speech:4.0.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }

    // gRPC (버전 통일 - 1.65.0)
    implementation("io.grpc:grpc-okhttp:1.65.0")
    implementation("io.grpc:grpc-protobuf:1.65.0")
    implementation("io.grpc:grpc-stub:1.65.0")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    // Annotations
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ✅ PyTorch Mobile 2.1.0 (최신 안정 버전)
    implementation("org.pytorch:pytorch_android_lite:2.1.0")
    implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")

    // TensorFlow Lite (보이스피싱 탐지용)
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")  // LSTM 지원용
 }