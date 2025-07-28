// 文件路径: build.gradle.kts
// 版本 3 (完美版): 明确导入Spring Boot的BOM(物料清单)，以确保所有依赖版本正确无误。

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"


springBoot {
	mainClass.set("com.aifactory.chat.DA_19_CHAT_Application")
}

java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	maven { url = uri("https://maven.aliyun.com/repository/public") }
	mavenCentral()
}

dependencyManagement {
	imports {
		// 关键修正：明确导入Spring Boot的依赖版本清单 (BOM)
		mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
		mavenBom("org.springframework.statemachine:spring-statemachine-bom:3.2.1")
	}
}

dependencies {
	// --- Spring Boot 核心依赖 ---
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	implementation("org.springframework.statemachine:spring-statemachine-starter:3.2.1")

	// --- ⭐ 新增：Web 和 API 功能依赖 ---
	implementation("org.springframework.boot:spring-boot-starter-web")

	// --- ⭐ 新增：数据库与持久化 (JPA) 依赖 ---
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("com.h2database:h2") // 新增：H2内存数据库，用于本地开发和测试

	// --- ⭐ 修正：为Kotlin协程依赖明确指定版本 ---
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")


	// --- 测试依赖 ---
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	reports {
		junitXml.required.set(true)
		html.required.set(true)
	}
}

