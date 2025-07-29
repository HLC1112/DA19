// 文件路径: build.gradle.kts
// 描述: 修正了Spring Statemachine的版本冲突问题。

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"


springBoot {
	mainClass.set("com.aifactory.chat.DA_19_CHAT_Application")
}

java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
	mavenCentral()
}

dependencyManagement {
	imports {
		// 关键修正：明确导入Spring Boot的依赖版本清单 (BOM)
		mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
		// 使用与Spring Boot 3.5.x 兼容的最新稳定版
		mavenBom("org.springframework.statemachine:spring-statemachine-bom:3.2.1")
	}
}

dependencies {
	// --- Spring Boot 核心依赖 ---
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// ⭐ 关键修正：移除了此处的版本号。
	// 版本将由上面 dependencyManagement 中的 statemachine-bom 统一管理。
	implementation("org.springframework.statemachine:spring-statemachine-starter")

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

	testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
	// In your build.gradle.kts file, add this to the dependencies block
	implementation("org.springframework.boot:spring-boot-starter-websocket")
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
