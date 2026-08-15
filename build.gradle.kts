import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.6.1"
    id("com.google.devtools.ksp") version "2.3.11"
}

group = "zechs.zplex.sync"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val kotlinCoroutines = "1.11.0"
    val postgresSqlDriver = "42.7.13"
    val okhttpVersion = "5.4.0"
    val retrofitVersion = "3.0.0"
    val moshiVersion = "1.15.2"
    val jedisVersion = "8.0.0"

    // Redis client for Java
    implementation("redis.clients:jedis:$jedisVersion")

    // Networking with OkHttp
    implementation(platform("com.squareup.okhttp3:okhttp-bom:$okhttpVersion"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // Networking dependencies
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")

    // JSON parsing with Moshi
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")

    // Postgres SQL Driver
    implementation("org.postgresql:postgresql:$postgresSqlDriver")

    // Google API Client Libraries
    implementation("com.google.auth:google-auth-library-oauth2-http:1.50.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20260720-2.0.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutines")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
        kotlin.srcDirs("src/main/kotlin")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "zechs.zplex.sync.MainKt"
    }
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("zplex-sync")
    archiveClassifier.set("")
    archiveVersion.set("")
}
