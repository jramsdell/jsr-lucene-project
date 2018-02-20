@file:JvmName("KotTrain")
package unh.edu.cs

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.QueryBuilder
import java.io.File
import java.io.StringReader
import java.nio.file.Paths
import java.util.*
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence

data class Topic(val name: String) {
    val relevantDocs = ArrayList<String>()
    val irrelevantDocs = ArrayList<String>()
    val relevantParagraphIds = HashSet<String>()
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

    fun test() {
        topics.forEach(::println)
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
