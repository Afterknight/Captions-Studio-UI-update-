import java.io.File
import java.security.MessageDigest
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Copy

/**
 * Dedicated GitHub Actions release APK Gradle init script for Caption Studio v-beta-0.9.
 *
 * Keeps app/build.gradle.kts signingConfigs untouched for AI Studio compatibility while ensuring
 * CI release builds are always validly signed and installable on real Android devices:
 *   - Uses KEYSTORE_FILE (or KEYSTORE_PATH), KEYSTORE_PASSWORD (or STORE_PASSWORD),
 *     KEY_ALIAS (default "upload"), and KEY_PASSWORD when provided.
 *   - Automatically falls back to Android's built-in `signingConfigs.getByName("debug")` when
 *     release keystore environment variables are not set.
 *
 * Usage:
 *   gradle --init-script github-release.gradle.kts assembleRelease --no-daemon
 */
gradle.beforeProject {
  if (path == ":app") {
    afterEvaluate {
      val androidExt = extensions.findByName("android")
      if (androidExt != null) {
        val signingConfigs =
          androidExt.javaClass.getMethod("getSigningConfigs").invoke(androidExt)
            as NamedDomainObjectContainer<*>
        val buildTypes =
          androidExt.javaClass.getMethod("getBuildTypes").invoke(androidExt)
            as NamedDomainObjectContainer<*>
        val releaseBuildType = buildTypes.getByName("release")

        val keystoreFileEnv =
          System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            ?: System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        val keystorePasswordEnv =
          System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
            ?: System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        val keyAliasEnv = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
        val keyPasswordEnv = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }

        val resolvedKeystoreFile = keystoreFileEnv?.let { file(it) }
        val hasValidReleaseKeystore =
          resolvedKeystoreFile != null &&
            resolvedKeystoreFile.isFile &&
            keystorePasswordEnv != null &&
            keyPasswordEnv != null

        val setSigningConfigMethod =
          releaseBuildType.javaClass.methods.firstOrNull { method ->
            method.name == "setSigningConfig" && method.parameterCount == 1
          }

        if (hasValidReleaseKeystore) {
          val releaseSigning = signingConfigs.getByName("release")
          releaseSigning.javaClass
            .methods
            .firstOrNull { it.name == "setStoreFile" && it.parameterCount == 1 }
            ?.invoke(releaseSigning, resolvedKeystoreFile)
          releaseSigning.javaClass
            .methods
            .firstOrNull { it.name == "setStorePassword" && it.parameterCount == 1 }
            ?.invoke(releaseSigning, keystorePasswordEnv)
          if (keyAliasEnv != null) {
            releaseSigning.javaClass
              .methods
              .firstOrNull { it.name == "setKeyAlias" && it.parameterCount == 1 }
              ?.invoke(releaseSigning, keyAliasEnv)
          }
          releaseSigning.javaClass
            .methods
            .firstOrNull { it.name == "setKeyPassword" && it.parameterCount == 1 }
            ?.invoke(releaseSigning, keyPasswordEnv)
          setSigningConfigMethod?.invoke(releaseBuildType, releaseSigning)
          logger.lifecycle("Release APK signing: using configured release keystore (${resolvedKeystoreFile.name}).")
        } else {
          val debugSigning = signingConfigs.getByName("debug")
          setSigningConfigMethod?.invoke(releaseBuildType, debugSigning)
          logger.lifecycle(
            "Release keystore environment variables not set; falling back to signingConfigs.getByName(\"debug\") so the release APK is installable on real Android devices."
          )
        }
      }

      val releaseApkName = "CaptionStudio-v-beta-0.9.apk"
      val releaseOutputDir = layout.buildDirectory.dir("outputs/apk/release")

      val stageGitHubReleaseApk =
        tasks.register<Copy>("stageGitHubReleaseApk") {
          group = "release"
          description =
            "Assembles the signed release APK and copies it to app/build/outputs/apk/release/$releaseApkName."
          dependsOn("assembleRelease")
          from(releaseOutputDir) {
            include("app-release.apk")
            rename { releaseApkName }
          }
          into(releaseOutputDir)
        }

      tasks.register("buildGitHubReleaseApk") {
        group = "release"
        description =
          "Compiles the signed release APK ($releaseApkName) and writes its SHA-256 checksum for GitHub Releases."
        dependsOn(stageGitHubReleaseApk)

        val stagedApk = releaseOutputDir.map { it.file(releaseApkName) }
        val checksumFile = releaseOutputDir.map { it.file("$releaseApkName.sha256") }
        inputs.file(stagedApk)
        outputs.file(checksumFile)

        doLast {
          val apkFile: File = stagedApk.get().asFile
          require(apkFile.exists() && apkFile.length() > 0L) {
            "Signed release APK not found at ${apkFile.absolutePath}."
          }

          val digest = MessageDigest.getInstance("SHA-256")
          apkFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
              val read = input.read(buffer)
              if (read <= 0) break
              digest.update(buffer, 0, read)
            }
          }
          val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
          checksumFile.get().asFile.writeText("$sha256Hex  ${apkFile.name}\n")

          logger.lifecycle("Staged GitHub Release APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
          logger.lifecycle("SHA-256: $sha256Hex")
        }
      }
    }
  }
}
