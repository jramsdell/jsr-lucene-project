package unh.edu.cs

import org.apache.lucene.search.IndexSearcher
import java.io.File
import java.util.*

data class Topic(val name: String) {
    val relevantDocs = ArrayList<String>()
    val irrelevantDocs = ArrayList<String>()
    val relevantParagraphIds = HashSet<String>()
}


class KotlinTrainer() {
//    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher)

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
}

fun main(args: Array<String>) {
    val trainer = KotlinTrainer()
    val rels = trainer.readRelevancy("train.pages.cbor-article.qrels")
}