@file:JvmName("KotRankLibTrainer")
package unh.edu.cs.ranklib

import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import unh.edu.cs.*
import java.lang.Double.sum
import java.util.*
import info.debatty.java.stringsimilarity.*
import info.debatty.java.stringsimilarity.interfaces.StringDistance

class KotlinRankLibTrainer(val indexSearcher: IndexSearcher, queryPath: String, qrelPath: String) {
    constructor(indexPath: String, queryPath: String, qrelPath: String)
            : this(getIndexSearcher(indexPath), queryPath, qrelPath)

    val db = KotlinDatabase("graph_database.db")
    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher, db)
    val queryRetriever = QueryRetriever(indexSearcher)
    val queries = queryRetriever.getSectionQueries(queryPath)
    val ranklibFormatter = KotlinRanklibFormatter(queries, qrelPath, indexSearcher)


    fun addStringDistanceFunction(query: String, tops: TopDocs, dist: StringDistance): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
//        val termQueries = query
//            .replace(replaceNumbers, " ")
//            .split(" ")
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { queryRetriever.createTokenSequence(this) }
            .toList()


        return tops.scoreDocs
            .map { scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
                if (entities.isEmpty()) 0.0 else
                termQueries.flatMap { q -> entities.map { e -> dist.distance(q, e)  } }.average()
            }
    }

    fun addAverageQueryScore(query: String, tops: TopDocs): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { queryRetriever.createTokenSequence(this) }
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

        return tops.scoreDocs.map { scoreDoc ->
                termQueries.map { booleanQuery ->
                    indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
                    .average()
            }
    }

    fun sectionSplit(query: String, tops: TopDocs, secIndex: Int): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { queryRetriever.createTokenSequence(this) }
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

        if (termQueries.size < secIndex + 1) {
            return (0 until tops.scoreDocs.size).map { 0.0 }
        }

//        val termQuery = TermQuery(Term(CONTENT, termQueries[secIndex]!!))
        val boolQuery = termQueries[secIndex]!!

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


    fun sanitizeDouble(d: Double): Double {
        return if (d.isInfinite() || d.isNaN()) 0.0 else d
    }


    fun rescore() {
        ranklibFormatter.addFeature(this::bm25, weight = 0.72954024466881)
        ranklibFormatter.addFeature({query, tops ->
            addStringDistanceFunction(query, tops, JaroWinkler() )}, weight = 0.04743997)

        ranklibFormatter.addFeature({query, tops ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0.023103116067)

        ranklibFormatter.addFeature(this::addAverageQueryScore, weight = -0.19821227474)
        ranklibFormatter.addFeature(this::addScoreMixtureSims, weight = 0.0014976)
        ranklibFormatter.queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs.map { it.score = it.features.sumByDouble(this::sanitizeDouble); it }
                .sortedByDescending { it.score }
                .forEachIndexed { index, par ->
                    queryContainer.tops.scoreDocs[index].doc = par.docId
                    queryContainer.tops.scoreDocs[index].score = par.score.toFloat()
                }
        }

        queryRetriever.writeQueriesToFile(queries)

    }

    fun doTrain() {
        ranklibFormatter.addFeature(this::bm25)
        ranklibFormatter.addFeature({query, tops ->
            addStringDistanceFunction(query, tops, JaroWinkler())})

        ranklibFormatter.addFeature({query, tops ->
            addStringDistanceFunction(query, tops, Jaccard() )})
        ranklibFormatter.addFeature(this::addAverageQueryScore)
//        ranklibFormatter.addFeature(this::addScoreMixtureSims, name = "mixtures")

        ranklibFormatter.addFeature({query, tops -> sectionSplit(query, tops, 0) })
        ranklibFormatter.addFeature({query, tops -> sectionSplit(query, tops, 1) })
        ranklibFormatter.addFeature({query, tops -> sectionSplit(query, tops, 2) })
        ranklibFormatter.addFeature({query, tops -> sectionSplit(query, tops, 3) })
        ranklibFormatter.writeToRankLibFile("mytestlib.txt")

    }

    fun train() {
//        rescore()
        doTrain()
    }
}
