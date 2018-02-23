@file:JvmName("KotGraphBuilder")
package unh.edu.cs

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.MultiFields
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence


class KotlinGraphBuilder(indexLocation: String) {

    // Start up an index searcher using supplied path to Lucene index directory
    val indexSearcher = kotlin.run {
        val indexPath = Paths.get (indexLocation)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    // Open up database where we will be storing graphs
    val db = DBMaker.fileDB("graph_database.db")
            .fileMmapEnable()
            .closeOnJvmShutdown()
            .make()

    // Initialize the graph maps
    val entityMap = db.hashMap("entity_map", Serializer.STRING, Serializer.STRING).createOrOpen()
    val parMap = db.hashMap("par_map", Serializer.STRING, Serializer.STRING).createOrOpen()

    fun buildParagraphGraph() {
        println("Adding edges from paragraphs to entities")
        val maxDoc = indexSearcher.indexReader.maxDoc()

        val bar = ProgressBar("Paragraphs Added", maxDoc.toLong(),
                ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        // Iterate over each paragraph, storing its entities in a map
        (0 until maxDoc).forEachParallel { docId ->
            val doc = indexSearcher.doc(docId)
            val paragraphid = doc.get(PID)
            val entities = doc.getValues("spotlight")
            entityMap[paragraphid] = entities.joinToString()
            lock.withLock { bar.step() }
        }
        bar.stop()
    }

    fun addEntitiesToGraph(entities: List<String>) {
        entities.forEach { entity ->
            val termQuery = TermQuery(Term("spotlight", entity))
            val topDocs = indexSearcher.search(termQuery, 10000)

            val parEdges = topDocs.scoreDocs.joinToString { scoreDoc ->
                indexSearcher.doc(scoreDoc.doc).get(PID) }
            parMap[entity] = parEdges
        }
    }

    fun buildEntityGraph() {
        println("Adding edges from entities to paragraphs")
        val fields = MultiFields.getFields(indexSearcher.indexReader)
        val spotLightTerms = fields.terms("spotlight")
        val numTerms = spotLightTerms.docCount
        val termIterator = spotLightTerms.iterator()

        val termSeq = buildSequence<String> {
            while (true) {
                val bytesRef = termIterator.next()
                if (bytesRef == null) {
                    break
                }
                yield(bytesRef.utf8ToString())
            }
        }

        val bar = ProgressBar("Entities Added", numTerms.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        termSeq.chunked(1000)
                .forEachParallel { chunk ->
                    addEntitiesToGraph(chunk)
                    lock.withLock { bar.stepBy(1000) }
                }

        bar.stop()
    }


    fun run() {
        buildParagraphGraph()
        buildEntityGraph()
        println("Graphs complete!")
    }
}

