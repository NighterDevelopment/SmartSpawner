plugins {
    id("com.gradleup.shadow")
}

val shade: Configuration by configurations.creating
configurations {
    implementation.get().extendsFrom(shade)
}

dependencies {
    api(project(":api"))

    @Suppress("GradleDependency")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    shade("com.zaxxer:HikariCP:7.0.2")
    shade("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    // SQLite JDBC is provided by Paper's runtime classpath - no need to shade
    compileOnly("org.xerial:sqlite-jdbc:3.45.1.0")

    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.2.0") {
        exclude(group = "*")
    }
    compileOnly("com.palmergames.bukkit.towny:towny:0.102.0.6")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2025.2.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("su.nightexpress.coinsengine:CoinsEngine:2.6.0")
    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.9.0")
    compileOnly("world.bentobox:bentobox:3.10.0-SNAPSHOT")
    compileOnly("su.nightexpress.excellentshop:Core:4.22.0")
    compileOnly("io.github.fabiozumbi12.RedProtect:RedProtect-Core:8.1.2") {
        exclude(group = "*")
    }
    compileOnly("io.github.fabiozumbi12.RedProtect:RedProtect-Spigot:8.1.2") {
        exclude(group = "*")
    }
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.9")
    compileOnly("pl.minecodes.plots:plugin-api:4.6.2")
    compileOnly("fr.maxlego08.shop:zshop-api:3.3.2")
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.0.9")

    implementation("com.github.GriefPrevention:GriefPrevention:18.0.0")
    implementation("com.github.IncrediblePlugins:LandsAPI:7.24.1")
    implementation("com.github.Xyness:SimpleClaimSystem:1.12.3.4")
    implementation("com.github.Zrips:Residence:6.0.0.1") {
        exclude(group = "org.bukkit")
    }

    compileOnly("io.lumine:Mythic-Dist:5.11.2")
    compileOnly("com.iridium:IridiumSkyblock:4.1.2")

    implementation(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:-deprecation"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

// Don't use 'jar' task to build plugin jar, use 'shadowJar' task instead
tasks.jar {
    archiveBaseName.set("SmartSpawnerJar")
    archiveVersion.set(version.toString())

    from(project(":api").sourceSets["main"].output)
    from(sourceSets["main"].output)
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

tasks.shadowJar {
    archiveBaseName.set("SmartSpawner")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    from(project(":api").sourceSets["main"].output)

    configurations = listOf(shade)

    relocate("com.zaxxer.hikari", "github.nighter.smartspawner.libs.hikari")
    relocate("org.mariadb.jdbc", "github.nighter.smartspawner.libs.mariadb")

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")

    from(sourceSets["main"].output)

    exclude("org/slf4j/**")

    mergeServiceFiles()

    // destinationDirectory.set(file("C:\\Users\\USER\\Desktop\\TestServer\\plugins"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}

