#!/bin/bash

# 设置颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}开始修复项目依赖...${NC}"

# 修复 app/build.gradle
echo -e "${YELLOW}修复 app 模块依赖...${NC}"
cat > app/build.gradle << 'EOF'
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.fongmi.android.tv'
    compileSdk 33

    defaultConfig {
        applicationId "com.fongmi.android.tv"
        minSdk 21
        targetSdk 33
        versionCode 1
        versionName "1.0"
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        coreLibraryDesugaringEnabled true
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    
    buildFeatures {
        viewBinding true
    }
    
    flavorDimensions "mode", "abi"
    
    productFlavors {
        mobile {
            dimension "mode"
            applicationIdSuffix ".mobile"
        }
        
        leanback {
            dimension "mode"
            applicationIdSuffix ".leanback"
        }
        
        all32 {
            dimension "abi"
            ndk {
                abiFilters 'armeabi-v7a'
            }
        }
        
        all64 {
            dimension "abi"
            ndk {
                abiFilters 'arm64-v8a'
            }
        }
    }
    
    lintOptions {
        checkReleaseBuilds false
        abortOnError false
    }
}

dependencies {
    implementation fileTree(dir: "libs", include: ["*.aar"])
    implementation project(':catvod')
    implementation project(':quickjs')
    implementation project(':forcetech')
    implementation project(':hook')
    implementation project(':jianpian')
    implementation project(':thunder')
    implementation project(':tvbus')
    implementation project(':zlive')
    
    // AndroidX
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core:1.10.1'
    implementation 'androidx.preference:preference:1.2.0'
    implementation 'androidx.lifecycle:lifecycle-extensions:2.2.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.0'
    
    // Google
    implementation 'com.google.android.material:material:1.7.0'
    
    // OkHttp
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    
    // Room
    implementation 'androidx.room:room-runtime:2.5.2'
    annotationProcessor 'androidx.room:room-compiler:2.5.2'
    
    // Jsoup
    implementation 'org.jsoup:jsoup:1.15.4'
    
    // Coil
    implementation 'io.coil-kt:coil:2.2.2'
    
    // Other
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:1.1.5'
}
EOF

# 修复 catvod/build.gradle
echo -e "${YELLOW}修复 catvod 模块依赖...${NC}"
cat > catvod/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.github.catvod'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    
    // OkHttp
    api 'com.squareup.okhttp3:okhttp:4.11.0'
    api 'com.squareup.okhttp3:okhttp-dnsoverhttps:4.11.0'
    api 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    
    // Gson
    api 'com.google.code.gson:gson:2.10.1'
    
    // Guava
    api 'com.google.guava:guava:31.1-android'
    
    // Logger
    api 'com.orhanobut:logger:2.2.0'
    
    // JSoup
    api 'org.jsoup:jsoup:1.15.4'
    
    // Other
    api 'com.googlecode.juniversalchardet:juniversalchardet:1.0.3'
}
EOF

# 修复 quickjs/build.gradle
echo -e "${YELLOW}修复 quickjs 模块依赖...${NC}"
cat > quickjs/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.fongmi.quickjs'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
    
    // QuickJS
    implementation 'io.github.taoweiji.quickjs:quickjs-android:0.9.0'
    implementation 'com.github.whl1729:quickjs-android:3.2.0'
    
    // Concurrent
    implementation 'net.sourceforge.streamsupport:streamsupport:1.7.4'
    implementation 'net.sourceforge.streamsupport:android-retrofuture:1.7.4'
}
EOF

# 修复 thunder/build.gradle
echo -e "${YELLOW}修复 thunder 模块依赖...${NC}"
cat > thunder/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.xunlei.downloadlib'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
}
EOF

# 修复 forcetech/build.gradle
echo -e "${YELLOW}修复 forcetech 模块依赖...${NC}"
cat > forcetech/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.forcetech'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
}
EOF

# 修复 hook/build.gradle
echo -e "${YELLOW}修复 hook 模块依赖...${NC}"
cat > hook/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.fongmi.hook'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
EOF

# 修复 jianpian/build.gradle
echo -e "${YELLOW}修复 jianpian 模块依赖...${NC}"
cat > jianpian/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.p2p.jianpian'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
}
EOF

# 修复 tvbus/build.gradle
echo -e "${YELLOW}修复 tvbus 模块依赖...${NC}"
cat > tvbus/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.tvbus'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
}
EOF

# 修复 zlive/build.gradle
echo -e "${YELLOW}修复 zlive 模块依赖...${NC}"
cat > zlive/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.zlive'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation project(':catvod')
}
EOF

# 修改settings.gradle添加jitpack仓库
echo -e "${YELLOW}修改 settings.gradle 添加 jitpack 仓库...${NC}"
cat > settings.gradle << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url "https://jitpack.io" }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url "https://jitpack.io" }
    }
}

include ':app'
include ':catvod'
include ':quickjs'
include ':forcetech'
include ':hook'
include ':jianpian'
include ':thunder'
include ':tvbus'
include ':zlive'

rootProject.name = "XMBOX"
EOF

echo -e "${GREEN}依赖修复完成！${NC}"
echo -e "${YELLOW}现在您可以尝试构建项目：./gradlew clean${NC}" 