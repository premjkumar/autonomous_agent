plugins {  
    id("com.android.application")  
    id("org.jetbrains.kotlin.android")  
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"  
}  

android {  
    namespace = "com.example.roboqwen"  
    compileSdk = 34  

    defaultConfig {  
        applicationId = "com.example.roboqwen"  
        minSdk = 29  
        targetSdk = 34  
        versionCode = 3  
        versionName = "3.0"  

        vectorDrawables {  
            useSupportLibrary = true  
        }  
    }  

    buildTypes {  
        release {  
            isMinifyEnabled = true  
            isShrinkResources = true  
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
        compose = true  
    }  
    composeOptions {  
        kotlinCompilerExtensionVersion = "1.5.1"  
    }  
}  

dependencies {  
    implementation("androidx.core:core-ktx:1.12.0")  
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")  
    implementation("androidx.activity:activity-compose:1.8.2")  
      
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))  
    implementation("androidx.compose.ui:ui")  
    implementation("androidx.compose.ui:ui-graphics")  
    implementation("androidx.compose.ui:ui-tooling-preview")  
    implementation("androidx.compose.material3:material3")  
      
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")  
    implementation("androidx.media3:media3-exoplayer:1.3.1")  

    val roomVersion = "2.6.1"  
    implementation("androidx.room:room-runtime:$roomVersion")  
    implementation("androidx.room:room-ktx:$roomVersion")  
    ksp("androidx.room:room-compiler:$roomVersion")  
}
