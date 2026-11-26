plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.viren"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.hibernate.orm:hibernate-community-dialects")

    // AI: official Anthropic Java SDK (structured outputs for plans, task batches and recaps)
    implementation("com.anthropic:anthropic-java:3.14.0")
    // Push: OAuth2 access tokens for the FCM HTTP v1 API
    implementation("com.google.auth:google-auth-library-oauth2-http:1.58.0")

    runtimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
tasks.withType<Test> { useJUnitPlatform() }

// One fixed-name fat jar for the Docker image; no extra "-plain" jar.
tasks.bootJar { archiveFileName.set("app.jar") }
tasks.jar { enabled = false }
