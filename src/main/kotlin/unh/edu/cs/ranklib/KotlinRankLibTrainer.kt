@file:JvmName("KotRankLibTrainer")
package unh.edu.cs.ranklib

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import unh.edu.cs.*
import java.io.StringReader
import java.lang.Double.sum
import java.nio.file.Paths
import java.util.*
import kotlin.coroutines.experimental.buildSequence
import info.debatty.java.stringsimilarity.*
import info.debatty.java.stringsimilarity.interfaces.MetricStringDistance
import info.debatty.java.stringsimilarity.interfaces.StringDistance

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
    val queries = queryRetriever.getSectionQueries(queryPath)
    val ranklibFormatter = KotlinRanklibFormatter(queries, qrelPath, indexSearcher)
    val analyzer = StandardAnalyzer()

//    fun createQuery(query: String): BooleanQuery {
//        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }
//
//        val streamSeq = buildSequence<String> {
//            while (tokenStream.incrementToken()) {
//                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
//            }
//            tokenStream.end()
//            tokenStream.close()
//        }
//
//        return streamSeq
//            .map { token -> TermQuery(Term("spotlight", token))}
//            .fold(BooleanQuery.Builder(), { acc, termQuery ->
//                acc.add(termQuery, BooleanClause.Occur.SHOULD)
//            })
//            .build()
//    }

    fun addStringDistanceFunction(query: String, tops: TopDocs, dist: StringDistance): List<Double> {
        val replaceNumbers = """(%\d+|[_-])""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, " ")
            .split(" ")


        return tops.scoreDocs
            .map { scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
                if (entities.isEmpty()) 0.0 else
                termQueries.flatMap { q -> entities.map { e -> dist.distance(q, e)  } }.average()
            }
            .toList()
    }

    fun addAverageQueryScore(query: String, tops: TopDocs): List<Double> {
        val replaceNumbers = """(%\d+|[_-])""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, " ")
            .replace("/", " ")
            .split(" ")
            .map { TermQuery(Term("text", it))}
            .map { BooleanQuery.Builder().add(it, BooleanClause.Occur.SHOULD).build()}
            .onEach { println(it) }

        return tops.scoreDocs
            .map { scoreDoc ->
                termQueries.map { indexSearcher.explain(it, scoreDoc.doc).value.toDouble() }
                    .average()
            }
            .toList()
    }

    fun sectionSplit(query: String, tops: TopDocs, secIndex: Int): List<Double> {
        val replaceNumbers = """(%\d+|[_-])""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, " ")
            .split("/")
        if (termQueries.size < secIndex + 1) {
            return (0 until tops.scoreDocs.size).map { 0.0 }
        }

        val termQuery = TermQuery(Term("text", termQueries[secIndex]!!))
        val boolQuery = BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()

//            .map { TermQuery(Term("text", it))}
//            .map { BooleanQuery.Builder().add(it, BooleanClause.Occur.SHOULD).build()}

        return tops.scoreDocs
            .map { scoreDoc ->
                indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble()
            }
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
//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, NormalizedLevenshtein() )})
//
//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, SorensenDice() )})

//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, JaroWinkler() )})
//
//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, Jaccard() )})

//        ranklibFormatter.addFeature({query, tops ->
//            sectionSplit(query, tops, 0 )})
//        ranklibFormatter.addFeature({query, tops ->
//            sectionSplit(query, tops, 1 )})
//        ranklibFormatter.addFeature({query, tops ->
//            sectionSplit(query, tops, 2 )})
//        ranklibFormatter.addFeature({query, tops ->
//            sectionSplit(query, tops, 3 )})
        ranklibFormatter.addFeature(this::addAverageQueryScore)
//        ranklibFormatter.addFeature(this::addScoreMixtureSims)
        ranklibFormatter.writeToRankLibFile("mytestlib.txt")
        queryRetriever.writeQueriesToFile(queries)
    }
}
