import java.io.File
import java.security.MessageDigest
import org.gradle.api.tasks.Copy

/**
 * Dedicated GitHub Actions release APK Gradle build script for Captions Studio v-beta-0.5.
 *
 * Usage (GitHub Actions or local CI):
 *   gradle --init-script github-release.gradle.kts :app:buildGitHubReleaseApk --no-daemon
 *
 * Required environment variables for release signing (consumed by app/build.gradle.kts):
 *   KEYSTORE_PATH   - Path to JKS/PKCS12 keystore containing keyAlias "upload"
 *   STORE_PASSWORD  - Keystore password
 *   KEY_PASSWORD    - Key password for alias "upload"
 */
allprojects {
  if (path == ":app") {
    afterEvaluate {
      val releaseApkName = "Captions-Studio-v-beta-0.5-release.apk"
      val releaseDistDir = rootProject.layout.projectDirectory.dir("release-dist")

      val stageGitHubReleaseApk =
        tasks.register<Copy>("stageGitHubReleaseApk") {
          group = "release"
          description =
            "Assembles the signed release APK and stages $releaseApkName into release-dist/ for GitHub Releases."
          dependsOn("assembleRelease")
          from(layout.buildDirectory.dir("outputs/apk/release")) {
            include("*.apk")
            exclude("*-unsigned.apk")
            rename { releaseApkName }
          }
          into(releaseDistDir)
        }

      tasks.register("buildGitHubReleaseApk") {
        group = "release"
        description =
          "Compiles the signed release APK for Captions Studio v-beta-0.5 and writes its SHA-256 checksum for GitHub Actions."
        dependsOn(stageGitHubReleaseApk)

        val stagedApk = releaseDistDir.file(releaseApkName)
        val checksumFile = releaseDistDir.file("$releaseApkName.sha256")
        inputs.file(stagedApk)
        outputs.file(checksumFile)

        doLast {
          val apkFile: File = stagedApk.asFile
          require(apkFile.exists() && apkFile.length() > 0L) {
            "Signed release APK not found at ${apkFile.absolutePath}. " +
              "Ensure KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD are configured for keyAlias 'upload'."
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
          checksumFile.asFile.writeText("$sha256Hex  ${apkFile.name}\n")

          logger.lifecycle("Staged GitHub Release APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
          logger.lifecycle("SHA-256: $sha256Hex")
        }
      }
    }
  }
}
