import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("idea")
	id("java-library")

	alias(libs.plugins.mdg)
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
//	id ("io.github.goooler.shadow") version "8.1.7"
}

//configurations {
//	create("shade")
//}

repositories {
	maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
	maven("https://thedarkcolour.github.io/KotlinForForge/") {
		name = "Kotlin for Forge"
		content {
			includeGroup("thedarkcolour")
		}
	}
}

dependencies {
	implementation("thedarkcolour:kotlinforforge-neoforge:5.6.0:slim")

	api(kotlin("stdlib"))
	api(kotlin("stdlib-common"))
	api(kotlin("stdlib-jdk8"))
	api("org.jetbrains.kotlinx", "kotlinx-serialization-core", "1.7.3")
	api("org.jetbrains.kotlinx", "kotlinx-serialization-cbor-jvm", "1.7.3")
}

val modId = project.properties["mod_id"] as String
val modVersion = System.getenv("TAG") ?: project.properties["mod_version"] as String
base.archivesName = modId

neoForge {
	version = "21.1.80"
	validateAccessTransformers = true

	parchment {
		mappingsVersion = libs.versions.parchment.asProvider()
		minecraftVersion = libs.versions.parchment.mc
	}

	mods {
		create(modId) {
			sourceSet(sourceSets["main"])
		}
	}

	runs {
		configureEach {
			systemProperty("forge.logging.markers", "REGISTRIES")
			systemProperty("neoforge.enabledGameTestNamespaces", modId)

			logLevel = org.slf4j.event.Level.DEBUG
		}

		create("client") {
			client()

			programArguments.addAll("--username", "Vyrek_", "--quickPlaySingleplayer", "test")
		}

		create("server") {
			server()

			programArgument("--nogui")
		}

		create("data") {
			data()

			programArguments.addAll(
				"--mod",
				modId,
				"--all",
				"--output",
				file("src/generated/resources/").absolutePath,
				"--existing",
				file("src/main/resources/").absolutePath
			)
		}
	}
}

// Plugin
// Cr: https://github.com/kernel-panic-codecave/Archie/blob/1.20.x/plugins/src/main/kotlin/utils/mod-resources.gradle.kts
interface ModResourcesExtension {
	val versions: MapProperty<String, String>
	val properties: MapProperty<String, String>
}

val extension = extensions.create<ModResourcesExtension>("modResources")
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
extension.versions.convention(provider {
	versionCatalog.versionAliases.associate {
		it.replace(".", "_") to versionCatalog.findVersion(it).get().requiredVersion
	}
})
extension.properties.convention(provider {
	project.properties.mapKeys {
		it.key.replace(".", "_")
	}.mapValues { it.value.toString() }
})

tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = JavaVersion.VERSION_21.toString()
		targetCompatibility = JavaVersion.VERSION_21.toString()
		options.release.set(JavaVersion.VERSION_21.toString().toInt())
	}

	withType<KotlinCompile> {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)

			optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
		}
	}

	jar {
		archiveFileName.set("${modId}-${modVersion}.jar")
		from("LICENSE")
	}

	java {
		toolchain {
			languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_21.toString()))
		}

		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21

		withSourcesJar()
	}

	processResources {
		val resourceValues = buildMap {
			put("versions", extension.versions.get())
			putAll(extension.properties.get())
		}
		inputs.properties(resourceValues)

		filesMatching("META-INF/neoforge.mods.toml") {
			expand(resourceValues)
		}
	}
}

sourceSets["main"].resources.srcDir("src/generated/resources")

idea {
	module {
		isDownloadSources = true
		isDownloadJavadoc = true
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			groupId = project.properties["mod_group_id"] as String
			artifactId = modId
			version = modVersion

			from(components["java"])
		}
	}
	repositories {
		maven {
			url = uri("file://${project.projectDir}/repo")
		}
	}
}