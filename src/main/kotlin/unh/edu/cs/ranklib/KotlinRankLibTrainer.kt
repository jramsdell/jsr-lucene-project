@file:JvmName("KotRankLibTrainer")
package unh.edu.cs.ranklib

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import unh.edu.cs.*
import java.lang.Double.sum
import java.nio.file.Paths
import java.util.*

class KotlinRankLibTrainer(indexPath: String, queryPath: String, qrelPath: String) {
    val indexSearcher = kotlin.run {
        val indexPath = Paths.get(indexPath)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    val db = KotlinDatabase("graph_database.db")
    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher, db)
    val queryRetriever = QueryRetriever(indexSearcher)
    val queries = queryRetriever.getQueries(queryPath)
    val ranklibFormatter = KotlinRanklibFormatter(queries, qrelPath, indexSearcher)

    fun addSpotlightSims(query: String, tops: TopDocs): List<Double> {
        val replaceNumbers = """(%\d+|[_-])""".toRegex()
        val termQuery = query
//            .replace("_", " ")
//            .replace("-", " ")
            .replace(replaceNumbers, " ")
            .split(" ")
            .map { TermQuery(Term("text", it)) }
            .fold(BooleanQuery.Builder(), { acc, termQuery ->
                                            acc.add(termQuery, BooleanClause.Occur.SHOULD) })
            .build()
        println(termQuery)

        return tops.scoreDocs
            .map { scoreDoc -> indexSearcher.explain(termQuery, scoreDoc.doc).value.toDouble() }
            .onEach (::println)
            .toList()
    }

    fun addScoreMixtureSims(query: String, tops:TopDocs): List<Double> {
        val sinks = HashMap<String, Double>()
        val mixtures = graphAnalyzer.getMixtures(tops)

        mixtures.forEach { pm ->
            pm.mixture.forEach { entity, probability ->
                sinks.merge(entity, probability * pm.score, ::sum)
            }
        }

        val total = sinks.values.sum()
        sinks.replaceAll { k, v -> v / total }

        return mixtures
            .map { pm -> pm.mixture.entries.sumByDouble { (k, v) -> sinks[k]!! * v * pm.score } }
            .toList()
    }

    fun train() {
        ranklibFormatter.addFeature(this::addSpotlightSims)
//        ranklibFormatter.addFeature(this::addScoreMixtureSims)
        ranklibFormatter.writeToRankLibFile("mytestlib.txt")
    }
}
