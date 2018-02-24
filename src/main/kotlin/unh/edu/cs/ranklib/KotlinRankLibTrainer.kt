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
import java.lang.Math.pow
import java.lang.Math.sqrt

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

    fun bm25(query: String, tops:TopDocs): List<Double> {
        return tops.scoreDocs.map { it.score.toDouble() }
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

    fun normalizeFeatures() {
        val featureVectors = HashMap<Int, ArrayList<Double>>()
        val means = HashMap<Int, Double>()
        val deviations = HashMap<Int, Double>()

        ranklibFormatter.queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs.forEach { it.features.forEachIndexed { index, d ->
                featureVectors.getOrDefault(index, ArrayList()) += d
            } }
        }

        featureVectors.forEach(::println)
//        featureVectors.forEach { k, v ->
//            val average = v.average()
//            means[k] = average
//            deviations[k] = sqrt(v.sumByDouble { pow(it - average, 2.0) })
//        }
        means.forEach(::println)
        deviations.forEach(::println)

//        ranklibFormatter.queryContainers.forEach { queryContainer ->
//            queryContainer.paragraphs.forEach { it.features.forEachIndexed { index, d ->
//                val newDouble = (d - means[index]!!)/deviations[index]!!
//                println("Was: $d Now: $newDouble")
//                it.features[index] = (d - means[index]!!)/deviations[index]!!
//            } }
//        }


    }


    fun rescore() {
        ranklibFormatter.addFeature(this::bm25, weight = 0.81314951)
        ranklibFormatter.addFeature({query, tops ->
            addStringDistanceFunction(query, tops, JaroWinkler() )}, weight = 0.083779)

//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0)

        ranklibFormatter.addFeature(this::addAverageQueryScore, weight = -0.102719)
        ranklibFormatter.queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs.map { it.features.sum() }
                .zip(queryContainer.tops.scoreDocs)
                .sortedByDescending { it.first }
                .forEachIndexed { index, (score, sd) ->
                    queryContainer.tops.scoreDocs[index].doc = sd.doc
                    queryContainer.tops.scoreDocs[index].score = score.toFloat()
                }
        }

        queryRetriever.writeQueriesToFile(queries)

    }

    fun train() {
        rescore()
////        ranklibFormatter.addFeature({query, tops ->
////            addStringDistanceFunction(query, tops, NormalizedLevenshtein() )})
////
////        ranklibFormatter.addFeature({query, tops ->
////            addStringDistanceFunction(query, tops, SorensenDice() )})
//
//        ranklibFormatter.addFeature(this::bm25)
//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, JaroWinkler() )})
//
//        ranklibFormatter.addFeature({query, tops ->
//            addStringDistanceFunction(query, tops, Jaccard() )})
//
////        ranklibFormatter.addFeature({query, tops ->
////            sectionSplit(query, tops, 0 )})
////        ranklibFormatter.addFeature({query, tops ->
////            sectionSplit(query, tops, 1 )})
////        ranklibFormatter.addFeature({query, tops ->
////            sectionSplit(query, tops, 2 )})
////        ranklibFormatter.addFeature({query, tops ->
////            sectionSplit(query, tops, 3 )})
//        ranklibFormatter.addFeature(this::addAverageQueryScore)
////        normalizeFeatures()
////        ranklibFormatter.addFeature(this::addScoreMixtureSims)
//        ranklibFormatter.writeToRankLibFile("mytestlib.txt")
////        queryRetriever.writeQueriesToFile(queries)
    }
}
