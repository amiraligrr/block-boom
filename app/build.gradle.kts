plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ir.amiraligrr.blockboom"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    // ndkVersion intentionally omitted - no native code

    defaultConfig {
        applicationId = "ir.amiraligrr.blockboom"
        minSdk = 24
        targetSdk = 35
        // Version history: the first public build was versionCode 1 / versionName
        // 1.0. This build is the player-facing 1.2.0 line, carrying the Tapsell
        // ad SDK, the milestone/perfect-clear loop, and the final polish pass.
        //
        // 25 is the next available code after the live 24 build. The server's
        // app_config.php allows a newer client by default, but production still
        // needs a matching app_versions row before this build is uploaded.
        versionCode = 25
        versionName = "1.2.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BLOCKBOOM_API_URL", "\"https://blockboomapi.amiraligrr.ir\"")
        // NOTE: there is deliberately no secret in the app any more.
        //
        // BLOCKBOOM_HMAC_SECRET used to be compiled into the APK, so anyone
        // who unzipped it could forge scores and ad events — a key that ships
        // to the client is not a secret. Score authenticity now comes from
        // server-signed run tokens (RunSession.kt / start_run.php), whose key
        // never leaves the server. Do not add a shared secret back here.
    }

    /**
     * Two store distributions, one shared source set.
     *
     * Myket keeps the published application id. Cafe Bazaar gets a new id so
     * the two installs cannot overwrite each other and Bazaar reviews do not
     * touch the existing Myket listing.
     */
    flavorDimensions += "store"
    productFlavors {
        create("myket") {
            dimension = "store"
            applicationId = "ir.amiraligrr.blockboom"
            buildConfigField("String", "STORE_ID", "\"myket\"")
            manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
            manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"
        }
        create("bazaar") {
            dimension = "store"
            applicationId = "ir.amiraligrr.blockboom.bazaar"
            buildConfigField("String", "STORE_ID", "\"bazaar\"")
            manifestPlaceholders["marketApplicationId"] = "com.farsitel.bazaar"
            manifestPlaceholders["marketBindAddress"] = "ir.cafebazaar.pardakht.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR"
        }
    }

    signingConfigs {
        create("release") {
            // Myket requires a release keystore. Put your keystore at app/blockboom-release.jks
            // and set env vars BLOCKBOOM_STORE_PASSWORD / KEY_PASSWORD, or use gradle.properties.
            // Fallback to debug for local builds so CI doesn't break.
            val storeFilePath = project.findProperty("BLOCKBOOM_STORE_FILE") as String? ?: "blockboom-release.jks"
            val storeFileObj = file(storeFilePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = project.findProperty("BLOCKBOOM_STORE_PASSWORD") as String? ?: System.getenv("BLOCKBOOM_STORE_PASSWORD") ?: ""
                keyAlias = project.findProperty("BLOCKBOOM_KEY_ALIAS") as String? ?: "blockboom"
                keyPassword = project.findProperty("BLOCKBOOM_KEY_PASSWORD") as String? ?: System.getenv("BLOCKBOOM_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            enableUnitTestCoverage = false
            // Use release keystore only if it exists; otherwise build unsigned for Myket signing later
            val releaseStoreFile = project.findProperty("BLOCKBOOM_STORE_FILE") as String? ?: "blockboom-release.jks"
            if (file(releaseStoreFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-debug"
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged resources and the real AndroidManifest,
        // not the empty stubs `isReturnDefaultValues` fakes. Without this the
        // whole harness silently renders an empty 0x0 window and every assertion
        // passes for the wrong reason.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("robolectric.logging", "stdout")
            // Screenshots and reports land somewhere collectable from the repo
            // root rather than in a build dir nobody looks at.
            it.systemProperty(
                "blockboom.qa.dir",
                rootProject.layout.projectDirectory.dir("build/qa").asFile.absolutePath
            )
            it.maxHeapSize = "2g"
            it.testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The Myket billing SDK pulls play-services-ads-identifier 18.1.0 transitively, but 18.1.0+
// (via play-services-basement 18.4.0) crashes on Android 7 with NoClassDefFoundError. Force the
// safe 18.0.1 that the Tapsell SDK also pins.
configurations.configureEach {
    resolutionStrategy {
        force("com.google.android.gms:play-services-ads-identifier:18.0.1")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tapsell.sdk)
    implementation(libs.play.services.ads.identifier)

    // Store-specific billing SDKs. Keeping these in flavor configurations means
    // the Bazaar APK never packages Myket's AIDL/IabHelper and vice versa.
    "myketImplementation"(libs.myket.billing.client)
    "bazaarImplementation"("com.github.farasource:billing-client:1.4.0")

    debugImplementation(libs.androidx.ui.tooling)

    // Unit tests, for the parts of the simulation that are pure arithmetic.
    //
    // There were none. The bugs this suite now guards — a coin credited twice, a
    // boss countdown off by one, a particle that grew instead of shrinking — are
    // all invisible to the compiler and to the build, and none of them would
    // have been caught by reading the code: they were caught by noticing a
    // number that did not add up. Tests are what stops the next one shipping.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}