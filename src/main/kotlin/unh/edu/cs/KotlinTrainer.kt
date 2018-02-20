@file:JvmName("KotTrain")
package unh.edu.cs

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.StringReader
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.exp
import kotlin.math.ln

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

        val relSum = doSum(relevantDocs)
        val irrelSum = doSum(irrelevantDocs)
//        if (relSum == Double.NaN) {
//            println("Bad relSum: $entity, $relSum")
//        }
//        if (irrelSum == Double.NaN) {
//            println("Bad irrelSum: $entity, $irrelSum")
//        }
//        return if (relSum == 0.0) 0.0 else ln(relSum / (relSum + irrelSum))
        return relSum / (relSum + irrelSum)
    }
}

class QueryRetriever(val indexSearcher: IndexSearcher) {

//     Initialize index searcher
//    val indexSearcher = kotlin.run {
//        val  indexPath = Paths.get (path)
//        val indexDir = FSDirectory.open(indexPath)
//        val indexReader = DirectoryReader.open(indexDir)
//        IndexSearcher(indexReader)
//    }

    val analyzer = StandardAnalyzer()

    fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }


    fun createQuery(query: String): BooleanQuery {
        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }

        val streamSeq = buildSequence<String> {
            while (tokenStream.incrementToken()) {
                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
            }
            tokenStream.end()
            tokenStream.close()
        }

        return streamSeq
                .map { token -> TermQuery(Term("text", token))}
                .fold(BooleanQuery.Builder(), { acc, termQuery ->
                    acc.add(termQuery, BooleanClause.Occur.SHOULD)
                })
                .build()
    }

    fun getQueries(queryLocation: String): List<Pair<String, TopDocs>> =
        DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
                .map { page ->
                    val queryId = page.pageId
                    val queryStr = createQueryString(page, emptyList())
                    queryId to indexSearcher.search(createQuery(queryStr), 100)
                }.toList()
}

class KotlinTrainer(indexPath: String, queryPath: String, qrelPath: String) {
    val indexSearcher = kotlin.run {
        val  indexPath = Paths.get (indexPath)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher)
    val queryRetriever = QueryRetriever(indexSearcher)
    val topics = readRelevancy(qrelPath)
    val queries = queryRetriever.getQueries(queryPath)

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

    fun train() {
        var counter = 0
        val entityWeights = HashMap<String, Double>()

        // For each query, get paragraph mixtures and add them to topics model
        queries.forEach { (queryId, tops) ->
            val mixtures = graphAnalyzer.getMixtures(tops)
            mixtures.forEach { pm ->
                entityWeights += pm.mixture.keys.map { it to 1.0 }
            }
            println(counter++)
            topics[queryId]!!.addMixtures(mixtures)
        }


        trainWeights(entityWeights)
    }

    fun calculateRelevancyGradient(weights: HashMap<String, Double>): Double =
            topics.values
            .map { topic -> topic.getRelevancyRatio(weights) }
                .average()

//    fun softMax(hmap: HashMap<String, Double>, temperature: Double = 1.0) {
//        val zExp = hmap.entries.map { it.key to exp(it.value)/temperature }.toMap() as HashMap
//        val total = zExp.values.sum()
//        println("SUM!: $total")
//        hmap.replaceAll {k,v -> zExp[k]!! / total}
//    }

    fun trainWeights(entityWeights: HashMap<String, Double>) {

        println("Size: ${entityWeights.size}")
//        val keySet = entityWeights.keys.take(50).toHashSet()
//        entityWeights.removeAll { key, value -> key !in keySet }

        val baseline = calculateRelevancyGradient(HashMap())
        println("Baseline: $baseline")

        val magnitudes = HashMap<String, Double>()
        var counter = AtomicInteger(0)

        // Todo: remove take 100
        val results = entityWeights.keys.take(100).pmap { entity->
            println(counter.incrementAndGet())
            val lowRatio = calculateRelevancyGradient(hashMapOf(entity to 0.005))
            val highRatio = calculateRelevancyGradient(hashMapOf(entity to 200.0))
            listOf(lowRatio - baseline to 0.005, highRatio - baseline to 200.0)
                    .maxBy { it.first }!!
                    .run { Triple(entity, first, second) }
        }

        var lowest = 9999999999.0
        results.forEach { (entity, mag, weight) ->
            if (mag >= 0.0 && mag < lowest) { lowest = mag }
        }
        results.forEach {(entity, mag, weight) ->
            magnitudes[entity] = mag / lowest
            entityWeights[entity] = weight
        }

//        magnitudes.forEach(::println)
//        softMax(magnitudes, 1.0)
//        magnitudes.forEach(::println)
        val total = magnitudes.values.sum()
        entityWeights.removeAll { key, value -> magnitudes.getOrDefault(key, 1.0) <= 0  }
        magnitudes.removeAll { key, value -> value <= 0.0  }
        magnitudes.replaceAll { k,v -> v / total }

        val best = magnitudes.values.max()!!
        entityWeights.replaceAll { k, v ->
            if (v >= 1.0) v * magnitudes.getOrDefault(k, 1.0) / best
            else v / (200 * magnitudes.getOrDefault(k, 1.0) / best)
        }
//
        magnitudes.forEach { k, v ->
            println("$k: $v, ${entityWeights.getOrDefault(k, 0.0)}")
        }
        val newBaseline = calculateRelevancyGradient(entityWeights)
        println("Before: $baseline\nAfter: $newBaseline")

    }

    fun test() {
        queries.forEach(::println)
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
