@file:JvmName("KotEntityLinker")
package unh.edu.cs

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class KotlinEntityLinker(indexLoc: String) {
    val url = "http://localhost:9310/jsr-spotlight/annotate"

    val indexSearcher = kotlin.run {
        val  indexPath = Paths.get (indexLoc)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    val server = KotlinSpotlightRunner()

    // Retrieves list of entities linked using Spotlight
    fun retrieveEntities(content: String): List<String> {

        // Retrieve html file from the Spotlight server
        val jsoupDoc = Jsoup.connect(url)
                .data("text", content)
                .post()

        // Parse urls, returning only the last word of the url (after the last /)
        val links = jsoupDoc.select("a[href]")
        return links.map { element ->
            val title = element.attr("title")
            title.substring(title.lastIndexOf("/") + 1)
        }.toList()
    }


    // Iterates over each paragraph in the corpus and annotates with linked entities
    fun run() {
        val totalDocs = indexSearcher.indexReader.maxDoc()
        println(totalDocs)
        val bar = ProgressBar("Documents Linked", totalDocs.toLong(),
                ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        (0 until totalDocs).forEachParallel { docId ->
            val doc = indexSearcher.doc(docId)
//            if (doc.getValues("spotlight").isEmpty()) {
//                val entities = retrieveEntities(doc.get("text"))
//                entities.forEach { entity ->
//                    doc.add(StringField("spotlight", entity, Field.Store.YES))
//                }
//            }

            // Update progress bar (have to make sure it's thread-safe)
            lock.withLock { bar.step() }
        }
    }

}

fun main(args: Array<String>) {
    val entityLinker = KotlinEntityLinker("wee")
    Thread.sleep(10000)
    entityLinker.retrieveEntities(("this is a test to see if it's working"))

}

