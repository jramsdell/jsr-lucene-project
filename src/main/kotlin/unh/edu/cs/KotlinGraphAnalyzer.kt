@file:JvmName("KotGraph")
package unh.edu.cs

import jdk.nashorn.internal.ir.Splittable
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.util.*
import java.util.concurrent.ConcurrentMap
import kotlinx.coroutines.experimental.*
import java.lang.Double.sum
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import java.util.concurrent.ThreadLocalRandom

fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

fun <K,V>HashMap<K,V>.removeAll(f: (key:K,value:V) -> Boolean) = {
    this.entries
            .filter{(key,value) -> f(key,value)}
            .forEach { (key,_) -> remove(key) }
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

    fun getParagraphMixture(docInfo: Pair<Int, Float>): ParagraphMixture {
        val pm = ParagraphMixture()
        pm.docId = docInfo.first
        val doc = indexSearcher.doc(pm.docId)
        val paragraphId = doc.get("paragraphid")
        pm.mixture = doWalkModel(paragraphId)
        pm.score = docInfo.second.toDouble()
        return pm
    }

    fun doWalkModel(pid: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 400
        val nSteps = 4

        (0 until nWalks).forEach { _ ->
            var volume = 1.0
            var curPar = pid
            var first = 0

            (0 until nSteps).forEach { _ ->
//                val entities = parMap[curPar]!!
//                val entity = entities.split(" ").let { it[rand.nextInt(it.size)] }

                val entities = storedEntities.computeIfAbsent(curPar,
                        { key -> parMap[key]!!.split(" ") })
                val entity = entities[ThreadLocalRandom.current().nextInt(entities.size)]

//                val paragraphs = entityMap[entity]!!
//                curPar = paragraphs.split(" ").let { it[rand.nextInt(it.size)] }
                val paragraphs = storedParagraphs.computeIfAbsent(entity,
                        { key -> entityMap[key]!!.split(" ") })
                curPar = paragraphs[ThreadLocalRandom.current().nextInt(paragraphs.size)]

                if (first != 0) {
                    first = 1
                } else {
                    volume *= 1/(ln(entities.size.toDouble()) + ln(paragraphs.size.toDouble()))
                }

                counts.merge(entity, volume, ::sum)


            }
        }

        counts.removeAll { key, value -> value < 0.01 }
        counts.values.sum().let { total ->
            counts.replaceAll({k,v -> v/total})
        }

        return counts
    }

    fun rerankTopDocs(tops: TopDocs, command: String) {
        val mixtures =
                tops.scoreDocs.map { it.doc to it.score }
                        .pmap({ getParagraphMixture(it) })
                        .toList()
        val sinks = HashMap<String, Double>()

        mixtures.forEach { pm ->
            pm.mixture.forEach { (k,v) ->
                sinks.merge(k, v * pm.score, ::sum)
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