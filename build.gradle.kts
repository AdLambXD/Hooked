// ==============================================================
// Hooked - 木筏求生漂浮物收集系统
// Gradle 构建脚本
// ==============================================================

plugins {
    id("java")
}

// 项目基本信息
group = "top.adlamb"
version = "1.0-SNAPSHOT"

// Java 编译版本
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 仓库地址
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")     // Paper/Folia API
    maven("https://repo.dmulloy2.net/repository/public/")         // ProtocolLib
}

// 项目依赖
dependencies {
    // Folia API（编译时，不打包）
    compileOnly("dev.folia:folia-api:1.21.8-R0.1-SNAPSHOT")
    // ProtocolLib（编译时，不打包）
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")

    // 测试框架
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 测试配置
tasks.test {
    useJUnitPlatform()
}

// 编译编码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}