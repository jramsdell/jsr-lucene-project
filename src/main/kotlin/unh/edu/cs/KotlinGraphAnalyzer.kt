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




data class ParagraphMixture(
        var docId: Int = 0,
        var paragraphId: String = "",
        var score: Double = 0.0,
        var mixture: HashMap<String, Double> = HashMap())

class KotlinGraphAnalyzer(var indexSearcher: IndexSearcher, val db: KotlinDatabase) {
    private val storedParagraphs = ConcurrentHashMap<String, List<String>>()
    private val storedEntities = ConcurrentHashMap<String, List<String>>()

    fun getParagraphMixture(docInfo: Pair<Int, Float>): ParagraphMixture {
        val doc = indexSearcher.doc(docInfo.first)
        val paragraphId = doc.get("paragraphid")
        val pm = ParagraphMixture(
                docId = docInfo.first,
                paragraphId = paragraphId,
                score = docInfo.second.toDouble(),
                mixture = doWalkModel(paragraphId)
                )
        return pm
    }


    fun doWalkModelEntity(entity: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 400
        val nSteps = 4

        (0 until nWalks).forEach { _ ->
            var volume = 1.0
            var curEntity = entity
            var first = 0

            (0 until nSteps).forEach { _ ->
                // Retrieve a random paragrath linked to entity (memoize result)
                val paragraphs = db.entityMap[curEntity]!!.split(" ")
                val paragraph = paragraphs[ThreadLocalRandom.current().nextInt(paragraphs.size)]

                // Retrieve a random entity linked to paragraph (memoize result)
                val entities = db.parMap[paragraph]!!.split(" ")
                curEntity = entities[ThreadLocalRandom.current().nextInt(entities.size)]

                if (first != 0) {
                    first = 1
                } else {
                    volume *= 1/(ln(entities.size.toDouble()) + ln(paragraphs.size.toDouble()))
                }

                counts.merge(curEntity, volume, ::sum)


            }
        }

        val topEntries = counts.entries.sortedByDescending{ it.value }
                .take(20)
                .map { it.key }
                .toHashSet()

        counts.removeAll { key, value -> key !in topEntries }
        counts.values.sum().let { total ->
            counts.replaceAll({k,v -> v/total})
        }

        return counts
    }

    fun doWalkModel(pid: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 100
        val nSteps = 2
        val firstPar = db.parMap[pid]!!.split(" ")

        (0 until nWalks).forEach { _ ->
            var volume = 1.0
            var curPar = pid
            var first = 0

            (0 until nSteps).forEach { _ ->

                // Retrieve a random entity linked to paragraph (memoize result)
                val entities = storedEntities.computeIfAbsent(curPar,
                        { key -> db.parMap[key]!!.split(" ") })
//                val entities = db.parMap[curPar]!!.split(" ")
                val entity = entities[ThreadLocalRandom.current().nextInt(entities.size)]

                // Retrieve a random paragrath linked to entity (memoize result)
                val paragraphs = storedParagraphs.computeIfAbsent(entity,
                        { key -> db.entityMap[key]!!.split(" ") })
//                val paragraphs = db.entityMap[entity]!!.split(" ")
                curPar = paragraphs[ThreadLocalRandom.current().nextInt(paragraphs.size)]

//                if (first != 0) {
//                    first = 1
//                } else {
//                    volume *= 1/(ln(entities.size.toDouble()) + ln(paragraphs.size.toDouble()))
//                }

                counts.merge(entity, volume, ::sum)


            }
        }

        val topEntries = counts.entries.sortedByDescending{ it.value }
                .take(20)
                .map { it.key }
                .toHashSet()

        counts.removeAll { key, value -> key !in topEntries }
        counts.values.sum().let { total ->
            counts.replaceAll({k,v -> v/total})
        }

        return counts
    }

    fun getMixtures(tops: TopDocs): List<ParagraphMixture> =
            tops.scoreDocs
                    .map { it.doc to it.score }
                    .map({ getParagraphMixture(it) })
                    .toList()

}


fun main(args: Array<String>) {
}