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
//                        features = arrayListOf(sc.score.toDouble()))
                        features = arrayListOf(),
                        docId = sc.doc)
            }
            QueryContainer(query = query, tops = tops, paragraphs = containers)
        }.toList()

    fun normalizeResults(values: List<Double>): List<Double> {
        val mean = values.average()
        val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
        return values.map { ((it - mean)/std).run{
            if (this == Double.NaN) 0.0 else this
        } }
    }

    fun addFeature(f: (String, TopDocs) -> List<Double>, weight:Double = 1.0, normalize: Boolean = true) {
        val counter = AtomicInteger(0)
        queryContainers.pmap { (query, tops, paragraphs) ->
            println(counter.incrementAndGet())
            f(query, tops).applyIf(normalize, {normalizeResults(this)})
                .zip(paragraphs)    // Annotate paragraph containers with this score
//                    .forEach { (score, paragraph) -> paragraph.features += score }
        }.forEach { results ->
            results
                .forEach { (score, paragraph) -> paragraph.features += score * weight }
        }
    }


//    fun addFeature(f: (String, TopDocs) -> List<Double>) =
//            queryContainers.forEach { (query, tops, paragraphs) ->
//                f(query, tops)          // Apply the scoring function given to us
//                    .zip(paragraphs)    // Annotate paragraph containers with this score
//                    .forEach { (score, paragraph) -> paragraph.features += score }
//            }

    fun writeToRankLibFile(outName: String) {
        queryContainers
                .flatMap { queryContainer -> queryContainer.paragraphs  }
                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
                .let { File(outName).writeText(it) }
    }
}


//fun <A, B, C>Iterable<Pair<A,B>>.map2(f: (A,B) -> C): List<C> {
//    return map({f(it.first, it.second)})
//}
//
//fun <A:Triple<B,C,D>, B, C, D, E>Iterable<A>.map3(f: (B,C,D) -> E): List<E> {
//    return map({f(it.first, it.second, it.third)})
//}


fun main(args: Array<String>) {

}