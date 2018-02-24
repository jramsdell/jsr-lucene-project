@file:JvmName("KotQueryRetriever")
package unh.edu.cs

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import java.io.BufferedWriter
import java.io.File
import java.io.StringReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.buildSequence

class QueryRetriever(val indexSearcher: IndexSearcher) {
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
            .map { token -> TermQuery(Term("text", token)) }
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

    fun getSectionQueries(queryLocation: String): List<Pair<String, TopDocs>> {
//        val seen = ConcurrentHashMap<String, String>()
        val seen = HashSet<String>()

        return DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
            .flatMap { page ->
                page.flatSectionPaths().map { sectionPath ->
                    val queryId = Data.sectionPathId(page.pageId, sectionPath)
                    val queryStr = createQueryString(page, sectionPath)
                    val result = queryId to indexSearcher.search(createQuery(queryStr), 100)
                    result.takeUnless {!seen.add(queryId)}
                }
            }.filterNotNull()
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

    fun writeQueriesToFile(queries: List<Pair<String, TopDocs>>, out: String = "results.txt") {
        val writer = File(out).bufferedWriter()
        queries.forEach { (query, tops) -> writeRankingsToFile(tops, query, writer)}
        writer.flush()
        writer.close()
    }
}

