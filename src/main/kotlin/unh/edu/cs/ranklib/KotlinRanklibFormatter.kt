package unh.edu.cs.ranklib

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import unh.edu.cs.PID
import java.io.File
import java.util.*

data class ParagraphContainer(val pid: String, val qid: Int,
                     val isRelevant: Boolean, val features: ArrayList<Double>) {

    override fun toString(): String =
            "$isRelevant qid:$qid " +
                    (1..features.size).zip(features)
                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" }

}

data class QueryContainer(val query: String, val tops: TopDocs, val paragraphs: List<ParagraphContainer>)

class KotlinRanklibFormatter(val queries: List<Pair<String, TopDocs>>,
                             qrelFileLocation: String, val indexSearcher: IndexSearcher) {

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

    fun addFeature(f: (String, TopDocs) -> List<Double>) {
        queryContainers.forEach { (query, tops, paragraphs) ->
            f(query, tops).zip(paragraphs).forEach { (score, paragraph) -> paragraph.features += score }
        }
    }

    fun writeToRankLibFile(outName: String) {
        queryContainers
                .flatMap { queryContainer -> queryContainer.paragraphs  }
                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
                .let { File(outName).writeText(it) }
    }
}