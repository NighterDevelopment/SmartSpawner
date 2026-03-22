import java.net.URI
import java.time.LocalDate

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
    shade("org.mariadb.jdbc:mariadb-java-client:3.5.7")
    compileOnly("org.xerial:sqlite-jdbc:3.51.3.0")

    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.2.0") {
        exclude(group = "*")
    }
    compileOnly("com.palmergames.bukkit.towny:towny:0.102.0.12")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2025.2.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("su.nightexpress.coinsengine:CoinsEngine:2.6.0")
    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.9.0")
    compileOnly("world.bentobox:bentobox:3.11.3-b115-SNAPSHOT")
    compileOnly("su.nightexpress.excellentshop:Core:4.22.0")
    compileOnly("io.github.fabiozumbi12.RedProtect:RedProtect-Core:8.1.2") {
        exclude(group = "*")
    }
    compileOnly("io.github.fabiozumbi12.RedProtect:RedProtect-Spigot:8.1.2") {
        exclude(group = "*")
    }
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.9")
    compileOnly("pl.minecodes.plots:plugin-api:4.6.2")
    compileOnly("fr.maxlego08.shop:zshop-api:3.3.3")
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.2")

    implementation("com.github.GriefPrevention:GriefPrevention:18.0.0")
    implementation("com.github.IncrediblePlugins:LandsAPI:7.24.1")
    implementation("com.github.Xyness:SimpleClaimSystem:1.12.3.4")
    implementation("com.github.Zrips:Residence:6.0.0.1") {
        exclude(group = "org.bukkit")
    }

    compileOnly("io.lumine:Mythic-Dist:5.11.2")
    compileOnly("com.iridium:IridiumSkyblock:4.1.2")

    implementation(platform("com.intellectualsites.bom:bom-newest:1.56"))
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core")

    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")

    implementation("org.bstats:bstats-bukkit:3.2.1")
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

// ── Language Changelog generation ────────────────────────────────────────────
//
//  Run this task manually before releasing a new version:
//
//    ./gradlew generateLanguageChangelog
//
//  It compares the current project version against the latest published GitHub
//  release.  If the build is newer it prepends a new skeleton entry to
//  core/src/main/resources/language/CHANGELOG.txt – the human-readable file
//  that is extracted to plugins/SmartSpawner/language/CHANGELOG.txt on every
//  server start so admins know which language keys changed.
//
//  The task is skipped gracefully when GitHub is unreachable (offline / CI
//  without network access).
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("generateLanguageChangelog") {
    group       = "documentation"
    description = "Prepends a new skeleton entry to language/CHANGELOG.txt when the build version exceeds the latest GitHub release."

    val changelogFile = project.file("src/main/resources/language/CHANGELOG.txt")

    inputs.property("projectVersion", project.version.toString())
    outputs.file(changelogFile)

    doLast {
        val currentVersion = project.version.toString()

        // ── 1. Fetch latest GitHub release tag ───────────────────────────────
        val githubVersion: String = try {
            val conn = URI.create(
                "https://api.github.com/repos/NighterDevelopment/SmartSpawner/releases/latest"
            ).toURL().openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "SmartSpawner-Changelog-Bot/1.0")
            conn.connectTimeout = 6_000
            conn.readTimeout    = 6_000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Regex(""""tag_name"\s*:\s*"v?([^"]+)"""").find(body)
                    ?.groupValues?.get(1) ?: "0.0.0"
            } else {
                println("[changelog] GitHub API returned HTTP ${conn.responseCode} – skipping.")
                return@doLast
            }
        } catch (e: Exception) {
            println("[changelog] ⚠ Cannot reach GitHub API (${e.message}) – skipping changelog update.")
            return@doLast
        }

        // ── 2. Compare versions ──────────────────────────────────────────────
        fun parseVer(v: String): List<Int> =
            v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        val cur = parseVer(currentVersion)
        val gh  = parseVer(githubVersion)
        var isNewer = false
        for (i in 0 until maxOf(cur.size, gh.size)) {
            val a = cur.getOrElse(i) { 0 }
            val b = gh.getOrElse(i) { 0 }
            if (a > b) { isNewer = true; break }
            if (a < b) break
        }

        if (!isNewer) {
            println("[changelog] Up-to-date (build=$currentVersion, github=$githubVersion) – nothing to add.")
            return@doLast
        }

        // ── 3. Guard against duplicates ──────────────────────────────────────
        val existing = if (changelogFile.exists()) changelogFile.readText() else ""
        if (existing.contains("── v$currentVersion")) {
            println("[changelog] Version $currentVersion already present – skipping.")
            return@doLast
        }

        // ── 4. Build plain-text entry (matches CHANGELOG.txt style) ──────────
        val today = LocalDate.now().toString()
        val separator = "─".repeat(80 - "── v$currentVersion ($today) ".length)
        val newEntry = buildString {
            appendLine("── v$currentVersion ($today) $separator")
            appendLine()
            appendLine("  Summary: Version $currentVersion released – fill in details here.")
            appendLine("           Compare: https://github.com/NighterDevelopment/SmartSpawner/compare/v$githubVersion...v$currentVersion")
            appendLine()
            appendLine("  ADDED:")
            appendLine("    (none)")
            appendLine()
            appendLine("  CHANGED:")
            appendLine("    (none)")
            appendLine()
            appendLine("  REMOVED:")
            appendLine("    (none)")
            appendLine()
        }

        // ── 5. Insert before the first existing version entry ────────────────
        val marker = "\n──"
        val updated = if (existing.contains(marker)) {
            existing.replaceFirst(marker, "\n$newEntry──")
        } else {
            "$existing\n$newEntry"
        }

        changelogFile.writeText(updated)
        println("[changelog] ✓ Prepended entry for v$currentVersion into language/CHANGELOG.txt")
    }
}

