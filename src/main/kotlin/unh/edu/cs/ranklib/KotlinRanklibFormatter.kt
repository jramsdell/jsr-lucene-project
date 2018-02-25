package unh.edu.cs.ranklib

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import unh.edu.cs.*
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

data class ParagraphContainer(val pid: String, val qid: Int,
                     val isRelevant: Boolean, val features: ArrayList<Double>,
                              val docId: Int, var score:Double = 0.0) {

    override fun toString(): String =
            "${if (isRelevant) 1 else 0} qid:$qid " +
                    (1..features.size).zip(features)
                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" }

}

data class QueryContainer(val query: String, val tops: TopDocs, val paragraphs: List<ParagraphContainer>)

enum class NormType { NONE, SUM, ZSCORE, LINEAR }

class KotlinRanklibFormatter(val queries: List<Pair<String, TopDocs>>,
                             qrelFileLocation: String, val indexSearcher: IndexSearcher) {

    val lock = ReentrantLock()

    val relevancies = File(qrelFileLocation)
            .bufferedReader()
            .readLines()
            .map { it.split(" ").let { it[0] to it[2] } }
            .toSet()

    val queryContainers =
        queries.mapIndexed {index,  (query, tops) ->
            val containers = tops.scoreDocs.map { sc ->
                val pid = indexSearcher.doc(sc.doc).get(PID)
                ParagraphContainer(
                        pid = pid,
                        qid = index + 1,
                        isRelevant = Pair(query, pid) in relevancies,
                        features = arrayListOf(),
                        docId = sc.doc)
            }
            QueryContainer(query = query, tops = tops, paragraphs = containers)
        }.toList()


    fun normSum(values: List<Double>): List<Double> =
            values.sum()
                .let { total -> values.map { value ->  value / total } }

    fun normZscore(values: List<Double>): List<Double> {
        val mean = values.average()
        val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
        return values.map { ((it - mean) / std) }
    }

    fun normLinear(values: List<Double>): List<Double> {
        val minValue = values.min()!!
        val maxValue = values.max()!!
        return values.map { value -> (value - minValue) / (maxValue - minValue) }
    }

    fun normalizeResults(values: List<Double>, normType: NormType): List<Double> {
        return when (normType) {
            NormType.NONE -> values
            NormType.SUM -> normSum(values)
            NormType.ZSCORE -> normZscore(values)
            NormType.LINEAR -> normLinear(values)
        }
    }

    fun addFeature(f: (String, TopDocs) -> List<Double>, weight:Double = 1.0,
                   normType: NormType = NormType.ZSCORE) {
        val counter = AtomicInteger(0)
        queryContainers.pmap { (query, tops, paragraphs) ->
            println(counter.incrementAndGet())
            f(query, tops).run { normalizeResults(this, normType) }
                .zip(paragraphs)    // Annotate paragraph containers with this score
//                    .forEach { (score, paragraph) -> paragraph.features += score }
        }.forEach { results ->
            results
                .forEach { (score, paragraph) -> paragraph.features += score * weight }
        }
    }

    fun sanitizeDouble(d: Double): Double {
        return if (d.isInfinite() || d.isNaN()) 0.0 else d
    }

    private fun bm25(query: String, tops:TopDocs): List<Double> {
        return tops.scoreDocs.map { it.score.toDouble() }
    }

    fun addBM25(weight: Double = 1.0, normType: NormType = NormType.NONE) =
            addFeature(this::bm25, weight = weight, normType = normType)

    fun rerankQueries() =
        queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs.map { it.score = it.features.sumByDouble(this::sanitizeDouble); it }
                .sortedByDescending { it.score }
                .forEachIndexed { index, par ->
                    queryContainer.tops.scoreDocs[index].doc = par.docId
                    queryContainer.tops.scoreDocs[index].score = par.score.toFloat()
                }
        }


    fun writeToRankLibFile(outName: String) {
        queryContainers
                .flatMap { queryContainer -> queryContainer.paragraphs  }
                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
                .let { File(outName).writeText(it) }
    }
}
