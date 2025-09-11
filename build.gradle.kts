plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.9"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.banco.fidc"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
	}
}

dependencies {
	// Spring Cloud Gateway
	implementation("org.springframework.cloud:spring-cloud-starter-gateway")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	
	// Redis Reactive
	implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
	
	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
	
	// Monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	
	// Logging JSON
	implementation("net.logstash.logback:logstash-logback-encoder:8.0")
	
	// Circuit Breaker
	implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
	implementation("io.github.resilience4j:resilience4j-reactor:2.1.0")
	
	// Kotlin
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	
	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.projectreactor:reactor-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
