plugins {
    `java-library`
    id("org.openrewrite.java-base")
    id("nebula.javadoc-jar")
    id("nebula.source-jar")
}

dependencies {
    implementation("org.jetbrains:annotations:latest.release")
    compileOnly("com.google.code.findbugs:jsr305:latest.release")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation(platform("org.junit:junit-bom:latest.release"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    testImplementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-common")

    testImplementation("org.assertj:assertj-core:latest.release")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.10")
}
