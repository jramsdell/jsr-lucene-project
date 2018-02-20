@file:JvmName("KotGraph")
package unh.edu.cs

import edu.unh.cs.treccar_v2.Data
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.util.*
import java.util.concurrent.ConcurrentMap
import java.util.stream.StreamSupport
import kotlinx.coroutines.experimental.*
import org.apache.lucene.document.Document
import java.lang.Double.sum
import java.util.concurrent.ConcurrentHashMap

fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

data class ParagraphMixture(
        var docId: Int = 0,
        var score: Double = 0.0,
        var mixture: HashMap<String, Double> = HashMap())

class KotlinGraphAnalyzer(var indexSearcher: IndexSearcher) {
    private val db: DB
    private val cmap: ConcurrentMap<String, String>
    private val entityMap: ConcurrentMap<String, String>
    private val parMap: ConcurrentMap<String, String>
    private val storedParagraphs = ConcurrentHashMap<String, List<String>>()
    private val storedEntities = ConcurrentHashMap<String, List<String>>()
    val rand = Random()


    init {
        db = DBMaker.fileDB("entity_db_3.db")
                .readOnly()
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make()
        cmap = db.hashMap("dist_map", Serializer.STRING, Serializer.STRING).createOrOpen();
        parMap = db.hashMap("par_map", Serializer.STRING, Serializer.STRING).createOrOpen();
        entityMap = db.hashMap("entity_map", Serializer.STRING, Serializer.STRING).createOrOpen();
    }

    fun getParagraphMixture(docId: Int): ParagraphMixture {
        val pm = ParagraphMixture()
        pm.docId = docId
        val doc = indexSearcher.doc(docId)
        val paragraphId = doc.get("paragraphid")
        pm.mixture = doWalkModel(paragraphId)
        // Todo: Add doJumps
        return pm
    }

    fun doWalkModel(pid: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 50
        val nSteps = 3

        (0 until nWalks).forEach { _ ->
            var volume = 1.0
            var curPar = pid

            (0 until nSteps).forEach { _ ->
//                val entities = parMap[curPar]!!
//                val entity = entities.split(" ").let { it[rand.nextInt(it.size)] }

                val entity = storedEntities.computeIfAbsent(curPar, { key ->
                    parMap[key]!!.split(" ")
                }).let { it[rand.nextInt(it.size)] }

                counts.merge(entity, 1.0, ::sum)
//                val paragraphs = entityMap[entity]!!
//                curPar = paragraphs.split(" ").let { it[rand.nextInt(it.size)] }
                curPar = storedParagraphs.computeIfAbsent(entity, { key ->
                    entityMap[key]!!.split(" ")
                }).let { it[rand.nextInt(it.size)] }
            }
        }
        return counts
    }

    fun rerankTopDocs(tops: TopDocs, command: String) {
        val mixtures =
                (0 until tops.scoreDocs.size)
                        .map({ getParagraphMixture(it) })
                        .toList()
        val sinks = HashMap<String, Double>()

        mixtures.forEach { pm ->
            pm.mixture.forEach { (k,v) ->
                sinks.merge(k, v, ::sum)
            }
        }

        if (command.equals("query_special")) {
            mixtures.forEach { pm ->
                if (!pm.mixture.isEmpty()) {
                    pm.score = 0.0
                }
                pm.mixture.forEach { k, v -> pm.score += sinks[k]!! * v  }
            }
        }

        mixtures.sortedByDescending(ParagraphMixture::score)
                .zip(0 until tops.scoreDocs.size)
                .forEach { (mixture,index) -> tops.scoreDocs[index].run {
                    score = mixture.score.toFloat()
                    doc = mixture.docId
                }}
    }

}


fun main(args: Array<String>) {
}