import org.gradle.api.tasks.Exec
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("sample-springboot")
}

val frontendDirectory = rootProject.file("frontend")

val frontendInstall by tasks.registering(Exec::class) {
    workingDir(frontendDirectory)
    commandLine("npm", "ci")
}

val frontendBuild by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir(frontendDirectory)
    commandLine("npm", "run", "build")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(frontendBuild)
    from(frontendDirectory.resolve("dist")) {
        into("static")
    }
}

dependencies {
    runtimeOnly(project(":adapter:inbound:controller"))
    runtimeOnly(project(":adapter:outbound:repository"))
    runtimeOnly(project(":infrastructure:h2"))
    runtimeOnly("com.sample.hexagonal.common:actuator")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    testImplementation(testFixtures(project(":application")))
    testImplementation("org.springframework:spring-tx")
}
