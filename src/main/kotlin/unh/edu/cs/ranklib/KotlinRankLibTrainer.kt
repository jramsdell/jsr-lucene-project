@file:JvmName("KotRankLibTrainer")
package unh.edu.cs.ranklib

import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import unh.edu.cs.*
import java.lang.Double.sum
import java.util.*
import info.debatty.java.stringsimilarity.*
import info.debatty.java.stringsimilarity.interfaces.StringDistance

class KotlinRankLibTrainer(indexPath: String, queryPath: String, qrelPath: String, graphPath: String) {

    val db = KotlinDatabase(graphPath)
//    val queryRetriever = QueryRetriever(indexSearcher)
//    val queries = queryRetriever.getSectionQueries(queryPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db)


    fun addStringDistanceFunction(query: String, tops: TopDocs, dist: StringDistance): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
//        val termQueries = query
//            .replace(replaceNumbers, " ")
//            .split(" ")
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { formatter.queryRetriever.createTokenSequence(this) }
            .toList()


        return tops.scoreDocs
            .map { scoreDoc ->
                val doc = formatter.indexSearcher.doc(scoreDoc.doc)
                val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
                if (entities.isEmpty()) 0.0 else
                termQueries.flatMap { q -> entities.map { e -> dist.distance(q, e)  } }.average()
            }
    }

    fun addAverageQueryScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { formatter.queryRetriever.createTokenSequence(this) }
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

        return tops.scoreDocs.map { scoreDoc ->
                termQueries.map { booleanQuery ->
                    indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
                    .average()
            }
    }

    fun addEntityQueries(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val entityQuery = query
            .replace(replaceNumbers, "")
            .run { formatter.queryRetriever.createTokenSequence(this) }
            .map { token -> TermQuery(Term("spotlight", token))}
            .fold(BooleanQuery.Builder()) { builder, termQuery ->
                builder.add(termQuery, BooleanClause.Occur.SHOULD) }
            .build()

        return tops.scoreDocs.map { scoreDoc ->
                indexSearcher.explain(entityQuery, scoreDoc.doc).value.toDouble() }
    }

    fun sectionSplit(query: String, tops: TopDocs, indexSearcher: IndexSearcher, secIndex: Int): List<Double> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val termQueries = query
            .replace(replaceNumbers, "")
            .run { formatter.queryRetriever.createTokenSequence(this) }
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


    fun addScoreMixtureSims(query: String, tops:TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val sinks = HashMap<String, Double>()
        val mixtures = graphAnalyzer!!.getMixtures(tops)

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

    fun queryStandard() {

    }

    fun querySimilarity() {
        formatter.addBM25(weight = 0.884669653, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, weight = -0.001055, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0.11427, normType = NormType.ZSCORE)
    }

    private fun queryAverage() {
        formatter.addBM25(weight = 0.5, normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, weight = 0.5, normType = NormType.ZSCORE)
    }

    private fun querySplit() {
    }

    private fun queryMixtures() {
        if (graphAnalyzer == null) {
            println("You must supply a --graph_database location for this method!")
            return
        }
    }

    private fun queryCombined() {
    }

    fun runRanklibQuery(method: String) {
        when (method) {
            "bm25" -> queryStandard()
            "entity_similarity" -> querySimilarity()
            "average_query" -> queryAverage()
            "split_sections" -> querySplit()
            "mixtures" -> queryMixtures()
            "combined" -> queryCombined()
            else -> println("Unknown method!")
        }
        formatter.rerankQueries()
        formatter.queryRetriever.writeQueriesToFile(formatter.queries)
    }



    fun rescore() {
        val weights = listOf(0.075174, 0.24885699, 0.554, 0.1219)
        formatter.addBM25(weight = weights[0], normType = NormType.NONE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler() )}, weight = weights[1], normType = NormType.NONE)

        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = weights[2], normType = NormType.NONE)

        formatter.addFeature(this::addAverageQueryScore, weight = weights[3], normType = NormType.NONE)
//        formatter.addFeature(this::addScoreMixtureSims, weight = weights[4])

//        formatter.addFeature({query, tops -> sectionSplit(query, tops, 0) },
//                weight = weights[4])
//        formatter.addFeature({query, tops -> sectionSplit(query, tops, 1) },
//                weight = weights[5])
//        formatter.addFeature({query, tops -> sectionSplit(query, tops, 2) },
//                weight = weights[6])
//        formatter.addFeature({query, tops -> sectionSplit(query, tops, 3) },
//                weight = weights[7])


        formatter.rerankQueries()
//        formatter.queryContainers.forEach { queryContainer ->
//            queryContainer.paragraphs.map { it.score = it.features.sumByDouble(this::sanitizeDouble); it }
//                .sortedByDescending { it.score }
//                .forEachIndexed { index, par ->
//                    queryContainer.tops.scoreDocs[index].doc = par.docId
//                    queryContainer.tops.scoreDocs[index].score = par.score.toFloat()
//                }
//        }

        formatter.queryRetriever.writeQueriesToFile(formatter.queries)

    }

    private fun trainSimilarity() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.ZSCORE)
    }

    private fun trainSplit() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 0) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 3) }, normType = NormType.ZSCORE)
    }

    private fun trainMixtures() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addScoreMixtureSims, normType = NormType.ZSCORE)
    }

    private fun trainAverageQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, normType = NormType.ZSCORE)
    }

    private fun trainCombined() {
        formatter.addBM25(weight = 1.0, normType = NormType.NONE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.NONE)
        formatter.addFeature(this::addAverageQueryScore, normType = NormType.NONE)
    }

    fun train(method: String) {
        when (method) {
            "entity_similarity" -> trainSimilarity()
            "average_query" -> trainAverageQuery()
            "split_sections" -> trainSplit()
            "mixtures" -> trainMixtures()
            "combined" -> trainCombined()
            else -> println("Unknown method!")
        }
        formatter.writeToRankLibFile("mytestlib.txt")
    }


}
