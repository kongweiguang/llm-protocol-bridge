# Spring Boot BOM 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目从使用 `spring-boot-starter-parent` 作为 parent POM 改为使用 `spring-boot-dependencies` BOM 方式引入，实现更干净的依赖管理架构。

**Architecture:** 采用标准的 Maven BOM 导入模式，在根 pom.xml 的 `dependencyManagement` 中导入 `spring-boot-dependencies`，同时移除 parent POM 的 Spring Boot 继承关系。插件管理通过 `pluginManagement` 显式配置。

**Tech Stack:** Maven, Spring Boot 3.4.5, Java 17

---

## 文件结构

- **修改:** `pom.xml` (根 POM)
- **修改:** `llm-protocol-bridge-sample-server/pom.xml`
- **验证:** 运行 `mvn clean install` 确保所有模块正常构建

---

### Task 1: 修改根 pom.xml - 移除 parent 并添加 BOM 导入

**Files:**
- Modify: `pom.xml` (根 POM)

- [ ] **Step 1: 移除 Spring Boot parent 声明**

从根 pom.xml 中删除以下代码块：
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version>
    <relativePath/>
</parent>
```

- [ ] **Step 2: 添加 spring-boot.version 属性**

在 `<properties>` 标签内添加：
```xml
<spring-boot.version>3.4.5</spring-boot.version>
```

完整的 properties 部分应为：
```xml
<properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <lombok.version>1.18.34</lombok.version>
    <spring-boot.version>3.4.5</spring-boot.version>
</properties>
```

- [ ] **Step 3: 在 dependencyManagement 中添加 Spring Boot BOM 导入**

在 `<dependencyManagement><dependencies>` 的开头添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>${spring-boot.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

完整的 dependencyManagement 部分应为：
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>io.github.kongweiguang</groupId>
            <artifactId>llm-protocol-bridge-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.kongweiguang</groupId>
            <artifactId>llm-protocol-bridge-http</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.kongweiguang</groupId>
            <artifactId>llm-protocol-bridge-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] **Step 4: 添加 build.pluginManagement 配置**

在 `</dependencyManagement>` 标签之后、`</project>` 标签之前添加：
```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

- [ ] **Step 5: 验证根 pom.xml 语法正确**

Run: `mvn validate -N` (仅验证根 POM)

Expected: BUILD SUCCESS

---

### Task 2: 修改 sample-server 模块添加插件引用

**Files:**
- Modify: `llm-protocol-bridge-sample-server/pom.xml`

- [ ] **Step 1: 确认 sample-server 已有 spring-boot-maven-plugin 配置**

检查 `llm-protocol-bridge-sample-server/pom.xml` 是否已有：
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

如果已有，跳过此任务。如果没有，继续下一步。

- [ ] **Step 2: 添加 spring-boot-maven-plugin 引用**

如果 `<build>` 部分不存在，添加：
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

如果 `<build><plugins>` 已存在，在 `<plugins>` 内添加：
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

- [ ] **Step 3: 验证 sample-server 模块 POM 语法**

Run: `mvn validate -pl llm-protocol-bridge-sample-server`

Expected: BUILD SUCCESS

---

### Task 3: 构建验证

**Files:**
- 无（仅执行构建命令）

- [ ] **Step 1: 执行完整构建**

Run: `mvn clean install -DskipTests`

Expected: BUILD SUCCESS，所有模块构建成功

- [ ] **Step 2: 执行完整测试**

Run: `mvn test`

Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 3: 验证 Spring Boot 插件正常工作**

Run: `mvn spring-boot:run -pl llm-protocol-bridge-sample-server`

Expected: 应用正常启动（看到 Spring Boot 启动日志）

按 Ctrl+C 停止应用

- [ ] **Step 4: 提交变更**

```bash
git add pom.xml llm-protocol-bridge-sample-server/pom.xml
git commit -m "refactor: migrate from spring-boot-starter-parent to spring-boot-dependencies BOM

- Remove spring-boot-starter-parent as parent POM
- Import spring-boot-dependencies BOM in dependencyManagement
- Add explicit pluginManagement configuration
- Maintain version consistency via spring-boot.version property"
```

---

## 变更摘要

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `pom.xml` | 修改 | 移除 parent，添加 BOM 导入和 pluginManagement |
| `llm-protocol-bridge-sample-server/pom.xml` | 修改 | 确保显式引用 spring-boot-maven-plugin |

## 验证清单

- [ ] 所有模块构建成功
- [ ] 所有测试通过
- [ ] sample-server 可以正常启动
- [ ] Lombok 依赖仍然正常工作
- [ ] Spring Boot 依赖版本正确解析
