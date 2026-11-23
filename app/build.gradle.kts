plugins {
    jacoco
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "cl.induztek.protocol"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "cl.induztek.protocol"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.3.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val releaseKeystore = providers.environmentVariable("INDUZTEK_KEYSTORE").orNull
    if (releaseKeystore != null) {
        signingConfigs.create("distribucion") {
            storeFile = file(releaseKeystore)
            storePassword = providers.environmentVariable("INDUZTEK_STORE_PASSWORD").get()
            keyAlias = "induztek"
            keyPassword = providers.environmentVariable("INDUZTEK_STORE_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribucion")
    }
    buildTypes.getByName("debug").enableUnitTestCoverage = true
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.exclude("**/SincronizacionHttpTest*")
            test.extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            test.systemProperty("user.home", testHome.absolutePath)
            listOf("induztek.identidad", "induztek.protocolos").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
            test.doFirst { testHome.mkdirs() }
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}


tasks.register<Test>("integracionHttp") {
    val pruebas = tasks.named<Test>("testDebugUnitTest")
    dependsOn(pruebas)
    testClassesDirs = pruebas.get().testClassesDirs
    classpath = pruebas.get().classpath
    systemProperties(pruebas.get().systemProperties)
    workingDir = pruebas.get().workingDir
    maxHeapSize = "1024m"
    include("**/SincronizacionHttpTest*")
}


jacoco { toolVersion = "0.8.11" }

tasks.register<JacocoReport>("coberturaLogicaDebug") {
    dependsOn("testDebugUnitTest")
    executionData(layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"))
    val paquete = "cl/induztek/protocol"
    classDirectories.setFrom(layout.buildDirectory.dir("tmp/kotlin-classes/debug").map { dir ->
        dir.asFileTree.matching {
            include(
                "$paquete/domain/**",
                "$paquete/data/InduztekRepository*.class",
                "$paquete/data/Calendario*.class",
                "$paquete/viewmodel/**",
            )
            exclude("**/*_Impl*.class")
        }
    })
    sourceDirectories.setFrom("src/main/java")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}