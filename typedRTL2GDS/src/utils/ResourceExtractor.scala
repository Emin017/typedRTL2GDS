package rtl2gds.utils

import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Comparator
import scala.jdk.CollectionConverters.*

object ResourceExtractor {
  def ensureScripts(): Path = {
    val resourcePath = "/scripts"
    val url = Option(getClass.getResource(resourcePath)).getOrElse {
      throw new RuntimeException(
        s"Could not find resource: $resourcePath. Make sure 'scripts' folder is in src/main/resources or equivalent."
      )
    }

    url.getProtocol match {
      case "file" =>
        Paths.get(url.toURI)
      case "jar" =>
        val tempDir = Files.createTempDirectory("typedRTL2GDS_scripts")

        // Register shutdown hook to clean up
        Runtime.getRuntime.addShutdownHook(new Thread(() => {
          deleteRecursively(tempDir)
        }))

        val connection = url.openConnection() match {
          case c: JarURLConnection => c
          case other =>
            throw new RuntimeException(
              s"Expected JarURLConnection, but got ${other.getClass.getName}"
            )
        }
        val jarFile = connection.getJarFile

        val entries = jarFile.entries().asScala
        entries.foreach { entry =>
          val name = entry.getName
          // entry names inside JAR usually don't start with /
          if (name.startsWith("scripts/")) {
            val destPath = tempDir.resolve(name)
            if (entry.isDirectory) {
              Files.createDirectories(destPath)
            } else {
              Files.createDirectories(destPath.getParent)
              val is = jarFile.getInputStream(entry)
              try {
                Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING)
              } finally {
                is.close()
              }
            }
          }
        }

        tempDir.resolve("scripts")
      case other =>
        throw new RuntimeException(s"Unsupported protocol: $other")
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach(p => Files.delete(p))
    }
  }
}
