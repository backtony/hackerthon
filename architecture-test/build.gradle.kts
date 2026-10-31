plugins {
    id("sample-kotlin-jvm")
}

dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":application"))
    testImplementation(project(":adapter:inbound:controller"))
    testImplementation(project(":adapter:outbound:repository"))
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
}
