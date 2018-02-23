@file:JvmName("KotSpotlightRunner")
package unh.edu.cs

import org.apache.commons.io.FileUtils
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import org.codehaus.plexus.logging.console.ConsoleLoggerManager
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

//        run { if (condition()) block() else this}


class KotlinSpotlightRunner() {
    val process: Process

    init {
        beginDownloads()

        // run server
        process = Runtime.getRuntime().exec("java -jar spotlight_server/spotlight.jar " +
                "spotlight_server/en_2+2/ http://localhost:9310/jsr-spotlight")

        // Ensure process is destroyed when we terminate the JVM
        Runtime.getRuntime().addShutdownHook(Thread {
            process.destroy()
        })

    }


    fun downloadFromUrl(url: String, out: String) {
        println("Downloading from $url to $out")
        val site = URL(url)
        val destination = File(out)
        FileUtils.copyURLToFile(site, destination)
    }


    fun beginDownloads() {
        val serverLoc = File("spotlight_server")
                .applyIf({!exists()}) { mkdir() } // create directory if it doesn't already exist

        // Get Spotlight Jar file (download it from server if it doesn't exist in spotlight directory)
        val spotLoc = "spotlight_server/spotlight.jar"
        val spotlightJar = File(spotLoc).applyIf({!exists()}) {
            downloadFromUrl("http://downloads.dbpedia-spotlight.org/spotlight/dbpedia-spotlight-0.7.1.jar", spotLoc)
        }

        // Make sure the model data is also downloaded
        val model_loc = "spotlight_server/en_2+2"
        val compressed_loc = "spotlight_server/en.tar.gz"
        val modelFile = File(model_loc)

        if (!modelFile.exists() || modelFile.list().size == 0) {
            modelFile.mkdir()
            val archive = File(compressed_loc).applyIf({!exists()}) {
                downloadFromUrl("http://downloads.dbpedia-spotlight.org/2016-04/en/model/en.tar.gz", compressed_loc)
            }

//            downloadFromUrl("http://downloads.dbpedia-spotlight.org/2016-04/en/model/en.tar.gz", compressed_loc)
//            val unarchiver = TarGZipUnArchiver(archive).apply { destDirectory = modelFile }
            val manager = ConsoleLoggerManager()
            manager.initialize()
            val unarchiver = TarGZipUnArchiver().apply {
                sourceFile = archive
                destDirectory = File("spotlight_server/")
                enableLogging(manager.getLoggerForComponent("compress"))
            }
            unarchiver.extract()
        }
    }
}

fun main(args: Array<String>) {
    val runner = KotlinSpotlightRunner()
//    runner.beginDownloads()
//    runner.downloadFromUrl("http://downloads.dbpedia-spotlight.org/spotlight/dbpedia-spotlight-0.7.1.jar", "spotlight.jar")
}

// 24642

