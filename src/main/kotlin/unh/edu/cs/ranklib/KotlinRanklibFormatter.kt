package unh.edu.cs.ranklib

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import unh.edu.cs.PID
import unh.edu.cs.pmap
import java.io.File
import java.util.*
import java.util.concurrent.locks.ReentrantLock

data class ParagraphContainer(val pid: String, val qid: Int,
                     val isRelevant: Boolean, val features: ArrayList<Double>) {

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
                        features = arrayListOf(sc.score.toDouble()))
            }
            QueryContainer(query = query, tops = tops, paragraphs = containers)
        }.toList()


    fun addFeature(f: (String, TopDocs) -> List<Double>) =
            queryContainers.pmap { (query, tops, paragraphs) ->
                f(query, tops)          // Apply the scoring function given to us
                    .zip(paragraphs)    // Annotate paragraph containers with this score
//                    .forEach { (score, paragraph) -> paragraph.features += score }
            }.toList().forEach { results ->
                results
                    .forEach { (score, paragraph) -> paragraph.features += score }
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