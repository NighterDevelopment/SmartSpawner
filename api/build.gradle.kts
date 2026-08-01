dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "github.nighter"
            artifactId = "smartspawner-api"
            from(components["java"])

            pom {
                name.set("SmartSpawner API")
                description.set("API for SmartSpawner plugin - allows other plugins to create and manage spawners")
                url.set("https://github.com/OpenVdra/SmartSpawner")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("nighter")
                        name.set("Nighter")
                        email.set("notnighter@gmail.com")
                    }
                }
            }
        }
    }
}
