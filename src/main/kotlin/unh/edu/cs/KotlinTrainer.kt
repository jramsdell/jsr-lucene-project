@file:JvmName("KotTrain")
package unh.edu.cs

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp

data class Topic(val name: String) {
    val relevantDocs = ArrayList<ParagraphMixture>()
    val irrelevantDocs = ArrayList<ParagraphMixture>()
    val relevantParagraphIds = HashSet<String>()

    fun addMixtures(mixtures: List<ParagraphMixture>) {
        mixtures.forEach { pm ->
            if (pm.mixture.isNotEmpty()) {
                if (pm.paragraphId in relevantParagraphIds) {
                    relevantDocs += pm
                } else {
                    irrelevantDocs += pm
                }
            }
        }
    }

    fun getRelevancyRatio(weights: HashMap<String, Double>): Double {
        val doSum = { docs: List<ParagraphMixture> ->
            docs.sumByDouble { pm ->
                pm.mixture.entries.sumByDouble { (k, v) -> v * weights.getOrDefault(k, 1.0) }
            }
        }

        val getValues = { docs: List<ParagraphMixture> ->
            docs.map { pm ->
                pm.mixture.entries.sumByDouble { (k, v) -> v * weights.getOrDefault(k, 1.0) }
            }
        }

//        val relSum = doSum(relevantDocs)
//        val irrelSum = doSum(irrelevantDocs)
        val relSum = getValues(relevantDocs).min() ?: 0.0
        val irrelSum = getValues(irrelevantDocs).min() ?: 0.0
//        if (relSum == Double.NaN) {
//            println("Bad relSum: $entity, $relSum")
//        }
//        if (irrelSum == Double.NaN) {
//            println("Bad irrelSum: $entity, $irrelSum")
//        }
//        return if (relSum == 0.0) 0.0 else ln(relSum / (relSum + irrelSum))
        return relSum - irrelSum
//        return relSum / (relSum + irrelSum)
    }
}


class KotlinRegularizer(indexPath: String, queryPath: String, weightLocation: String,
                        alpha: String) {
    val indexSearcher = kotlin.run {
        val  indexPath = Paths.get (indexPath)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    val alpha = alpha.toDouble()
    val db = KotlinDatabase("graph_database.db")
    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher, db)
    val queryRetriever = QueryRetriever(indexSearcher)
    val queries = queryRetriever.getPageQueries(queryPath)


    fun rerankTops(tops: TopDocs) {
        val mixtures = graphAnalyzer.getMixtures(tops)
        mixtures.forEach { pm ->
            pm.mixture
                    .map { (k,v) -> k to v * pm.score * db.weightMap.getOrDefault(k, 1.0) }
                    .sumByDouble { it.second }
                    .let { pm.score = it * (1 - alpha) + alpha * pm.score }
        }

        mixtures.sortedByDescending { it.score }
                .zip(0 until tops.scoreDocs.size)
                .forEach { (pm, index) ->
                    tops.scoreDocs[index].doc = pm.docId
                    tops.scoreDocs[index].score = pm.score.toFloat()
                }
    }

    fun writeRankingsToFile(tops: TopDocs, queryId: String, writer: BufferedWriter) {
        (0 until tops.scoreDocs.size).forEach { index ->
            val sd = tops.scoreDocs[index]
            val doc = indexSearcher.doc(sd.doc)
            val paragraphid = doc.get(PID)
            val score = sd.score
            val searchRank = index + 1

            writer.write("$queryId Q0 $paragraphid $searchRank $score Lucene-BM25\n")
        }
    }

    fun rerankQueries() {
        val writer = File("mytest.txt").bufferedWriter()
        queries.forEach { (queryId, tops) ->
            rerankTops(tops)
            writeRankingsToFile(tops, queryId, writer)
        }
        writer.flush()
        writer.close()
    }

}

class KotlinTrainer(indexPath: String, queryPath: String, qrelPath: String) {
    val indexSearcher = kotlin.run {
        val  indexPath = Paths.get (indexPath)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    val db = KotlinDatabase("graph_database.db")
    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher, db)
    val queryRetriever = QueryRetriever(indexSearcher)
    val topics = readRelevancy(qrelPath)
    val queries = queryRetriever.getPageQueries(queryPath)

    fun aggFun(key: String, acc: Topic?, element:Pair<String, String>, first: Boolean): Topic {
        val topic = acc ?: Topic(element.first)
        topic.relevantParagraphIds += element.second
        return topic
    }

    fun readRelevancy(filename: String): Map<String, Topic> {
        return File(filename)
                .bufferedReader()
                .readLines()
                .map { it.split(" ").let { it[0] to it[2] } }
                .groupingBy { (name, _) -> name }
                .aggregate(this::aggFun)
    }

    fun train(): HashMap<String, Double> {
        var counter = 0
        val entityWeights = HashMap<String, Double>()

        // For each query, get paragraph mixtures and add them to topics model
//        queries.pmap { (queryId, tops) ->
//            println(counter.incrementAndGet())
//            queryId to graphAnalyzer.getMixtures(tops)
//        }
//                .forEach { (queryId, mixtures) ->
//                    mixtures.forEach { pm ->
//                        entityWeights += pm.mixture.keys.map { it to 1.0 }
//                    }
//                    topics[queryId]!!.addMixtures(mixtures)
//                }
        queries.forEach { (queryId, tops) ->
            val mixtures = graphAnalyzer.getMixtures(tops)
            mixtures.forEach { pm ->
                entityWeights += pm.mixture.keys.map { it to 1.0 }
            }
            println(counter++)
            topics[queryId]!!.addMixtures(mixtures)
        }


        return trainWeights(entityWeights)
    }

    fun calculateRelevancyGradient(weights: HashMap<String, Double>): Double =
            topics.values
            .map { topic -> topic.getRelevancyRatio(weights) }
                .average()

    fun softMax(hmap: HashMap<String, Double>, temperature: Double = 1.0) {
        val zExp = hmap.entries.map { it.key to exp(it.value)/temperature }.toMap() as HashMap
        val total = zExp.values.sum()
        println("SUM!: $total")
        hmap.replaceAll {k,v -> zExp[k]!! / total}
    }

    fun writeEntityModels() {
//        val db = DBMaker.fileDB("entity_dists.db")
//                .fileMmapEnable()
//                .closeOnJvmShutdown()
//                .make()

//        val entityDistMap = db.hashMap("entity_dist", Serializer.STRING, Serializer.STRING).createOrOpen()

//        val counter = AtomicInteger(0)
//        graphAnalyzer.entityMap.keys.forEachParallel { entity ->
//            val model = graphAnalyzer.doWalkModelEntity(entity)
//            counter.incrementAndGet().let {
//                if (it % 1000 == 0) {
//                    println(it)
//                }
//            }
//
//            val dist = model.map { (k,v) -> "$k:$v" }.joinToString(" ")
//            entityDistMap[entity] = dist
//        }
    }

    fun writeWeights(entityWeights: HashMap<String, Double>) {

//        val weightMap = db.hashMap("weight_map", Serializer.STRING, Serializer.DOUBLE).createOrOpen()

        entityWeights.forEach { k, v -> db.weightMap[k] = v }
    }

    fun trainWeights(entityWeights: HashMap<String, Double>): HashMap<String, Double> {

        println("Size: ${entityWeights.size}")
//        val keySet = entityWeights.keys.take(50).toHashSet()
//        entityWeights.removeAll { key, value -> key !in keySet }

        val baseline = calculateRelevancyGradient(HashMap())

        val magnitudes = HashMap<String, Double>()
        var counter = AtomicInteger(0)

        // Todo: remove take 500
        val results = entityWeights.keys.pmap { entity->
            counter.incrementAndGet().let {
                if (it % 1000 == 0) {
                    println(it)
                }
            }

            val lowRatio = calculateRelevancyGradient(hashMapOf(entity to 0.05))
            val highRatio = calculateRelevancyGradient(hashMapOf(entity to 20.0))
            listOf(lowRatio - baseline to 0.05, highRatio - baseline to 20.0)
                    .maxBy { it.first }!!
                    .run { Triple(entity, first, second) }
        }

//        var lowest = 9999999999.0
//        results.forEach { (_, mag, _) ->
//            if (mag > 0.0 && mag < lowest) { lowest = mag }
//        }
        results.forEach {(entity, mag, weight) ->
            magnitudes[entity] = mag / 0.00001
            entityWeights[entity] = weight
        }

//        magnitudes.forEach(::println)
//        softMax(magnitudes, 1.0)
//        magnitudes.forEach(::println)
        val total = magnitudes.values.sum()
        entityWeights.removeAll { key, value -> magnitudes.getOrDefault(key, 1.0) <= 0  }
        magnitudes.removeAll { key, value -> value <= 0.0  }
//        softMax(magnitudes)
//        magnitudes.replaceAll { k,v -> v / total }

        val best = magnitudes.values.max()!!
        entityWeights.replaceAll { k, v ->
            if (v >= 1.0) v * magnitudes.getOrDefault(k, 1.0) / best
            else v / (20 * magnitudes.getOrDefault(k, 1.0) / best)
        }
//
//        magnitudes.forEach { k, v ->
//            println("$k: $v, ${entityWeights.getOrDefault(k, 0.0)}")
//        }
//        val newBaseline = calculateRelevancyGradient(entityWeights)
//        println("Before: $baseline\nAfter: $newBaseline")
        return entityWeights

    }

}

fun main(args: Array<String>) {
//    val trainer = KotlinTrainer()
//    val rels = trainer.readRelevancy("train.pages.cbor-article.qrels")
//    rels.forEach { k, v -> println("$k: ${v.relevantParagraphIds.size}") }
//    val queryRetriever = QueryRetriever("hi")
//    val page = Data.Page("hi", "hah", null, null, null)
//    val queryStr = queryRetriever.createQueryString(page, emptyList())
//    val result = queryRetriever.createQuery("hi how are you doing are you doing okay? Haha")
//    println(result.clauses())
}
