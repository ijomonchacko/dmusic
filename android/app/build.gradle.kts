plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.dmusic.android.dmusic"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.dmusic.android.dmusic"
        minSdk = 24
        targetSdk = 35
        versionCode = 37
        versionName = "3.0"

        // Configure native builds for BASS-only audio engine
        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-Wall",
                    "-Wextra",
                    "-O2",
                    "-DANDROID",
                    "-DBASS_ONLY_ENGINE=1"
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_VERBOSE_MAKEFILE=ON"
                )
                // Focus on primary architectures for optimal performance
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }

        ndk {
            // Match ABI filters with CMake configuration
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
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
            
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            
            // Optimize native libraries for production
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-DNDEBUG", "-O3", "-flto")
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DANDROID_STRIP_MODE=--strip-all"
                    )
                }
            }
        }
        
        debug {
            isDebuggable = true
            isJniDebuggable = false // Disable for performance during development
            
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    // BASS-only build - no prefab needed
    buildFeatures {
        prefab = false
    }
    
    // Configure native build system
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packagingOptions {
        // Handle native library conflicts
        pickFirst("**/libc++_shared.so")
        pickFirst("**/libbass.so")
        pickFirst("**/libbassflac.so")
        pickFirst("**/libbass_ssl.so")
        
        // Exclude unnecessary files
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.MF")
        exclude("**/libc++_static.a")
        exclude("**/*.a")
        exclude("**/gdb.setup")
        exclude("**/gdbserver")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    
    // Media session and audio focus management
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    
    // Kotlin coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Additional audio support
    implementation("com.google.android.play:core:1.10.3")
}

// Configuration to exclude x86 variants as per Flutter deprecation warning
configurations.all {
    exclude(group = "io.flutter", module = "x86_profile")
    exclude(group = "io.flutter", module = "x86_64_profile")
}