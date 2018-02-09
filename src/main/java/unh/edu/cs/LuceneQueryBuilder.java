package unh.edu.cs;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jooq.lambda.Seq;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Given a query file, will build queries to query indexed Lucene database with.
 * Implements variations of querying and ranking.
 *
 * command: Command used to run program. Used to distinguish between variations of normal methods when querying.
 * gloveReader: Used for word vector variation: compares query and documents via cosine sim
 */
class LuceneQueryBuilder {
    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private QueryType queryType;
    private GloveReader gloveReader;
    private final String command;

    LuceneQueryBuilder(String com, QueryType qType, Analyzer ana,
                       Similarity sim, String indexPath) throws IOException {
        analyzer = ana;
        command = com;
        queryType = qType;
        createIndexSearcher(indexPath);
        indexSearcher.setSimilarity(sim);
    }

    // Used by word vector variation: creates a reader from 50D GloVE word vector file.
    public void setVectorLocation(String vectorLocation) throws IOException {
        gloveReader = new GloveReader(vectorLocation);
    }

    // Supplier that wraps around a tokenstream and provides its tokens when called.
    private class TokenGenerator implements Supplier<String> {
        final TokenStream tokenStream;

        TokenGenerator(TokenStream ts) throws IOException {
            tokenStream = ts;
            ts.reset();
        }

        @Override
        public String get() {
            try {
                if (!tokenStream.incrementToken()) {
                    tokenStream.end();
                    tokenStream.close();
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
            return tokenStream.getAttribute(CharTermAttribute.class).toString();
        }
    }

    // Returns TermQueries for the QueryBuilder to use
    // Additional terms are added depending variation used
    private Seq<TermQuery> getQueries(String token) {
        ArrayList<TermQuery> list = new ArrayList<>();
        list.add(new TermQuery(new Term("text", token)));

        // Entity-linking variation: also compare against entities and spotlight entities
        if (command.equals("query_entity")) {
            list.add(new TermQuery(new Term("entities", token)));
            list.add(new TermQuery(new Term("spotlight", token)));
        }
        return Seq.seq(list);
    }

    // Builds queries to be used in search
    private BooleanQuery createQuery(String query) throws IOException {
        TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(query));
        TokenGenerator tg = new TokenGenerator(tokenStream);
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        Seq.generate(tg).limitWhile(Objects::nonNull)
                .flatMap(this::getQueries)
                .forEach(termQuery -> queryBuilder.add(termQuery, BooleanClause.Occur.SHOULD));

        if (command.equals("query_bigram")) {
            BigramAnalyzer bg = new BigramAnalyzer(query);
            List<String> bigrams = bg.run();
            for (String bigram : bigrams) {
                TermQuery tq = new TermQuery(new Term("bigram", bigram));
                queryBuilder.add(tq, BooleanClause.Occur.SHOULD);
            }
        }

        return queryBuilder.build();
    }

    // Formats query string (to be tokenized)
    private static String createQueryString(Data.Page page, List<Data.Section> sectionPath) {
        return page.getPageName() +
                sectionPath.stream()
                        .map(section -> " " + section.getHeading() )
                        .collect(Collectors.joining(" "));
    }

    // Writes top-scores to a given document
    void writeRankings(String queryLocation, String rankingsOutput) throws IOException {
        final BufferedWriter out = new BufferedWriter(new FileWriter(rankingsOutput));
        final FileInputStream inputStream = new FileInputStream(new File(queryLocation));

        if (queryType == QueryType.PAGE) {
            writePageRankings(inputStream, out);
        } else if (queryType == QueryType.SECTION) {
            writeSectionRankings(inputStream, out);
        }

        out.flush();
        out.close();
    }

    void writeRankingsToFile(ScoreDoc[] scoreDoc, String queryId, BufferedWriter out, HashSet<String> ids) throws IOException {
        for (int i = 0; i < scoreDoc.length; i++) {
            ScoreDoc score = scoreDoc[i];
            final Document doc = indexSearcher.doc(score.doc);
            final String paragraphid = doc.getField("paragraphid").stringValue();
            final float searchScore = score.score;
            final int searchRank = i + 1;

            out.write(queryId + " Q0 " + paragraphid + " "
                    + searchRank + " " + searchScore + " Lucene-BM25" + "\n");
        }
    }


    void writePageRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {
        HashSet<String> ids = new HashSet<>();

        for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
            final String queryId = page.getPageId();

            String queryStr = createQueryString(page, Collections.<Data.Section>emptyList());
            TopDocs tops = indexSearcher.search(createQuery(queryStr), 100);

            // if Word Vector variant, rerank according to cosine sim from query to document terms
            if (command.equals("query_vector")) {
                rerankByCosineSim(tops, queryStr);
            }
            ScoreDoc[] scoreDoc = tops.scoreDocs;
            writeRankingsToFile(scoreDoc, queryId, out, ids);
        }
    }

    void writeSectionRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {
        HashSet<String> ids = new HashSet<>();
        for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
            for (List<Data.Section> sectionPath : page.flatSectionPaths()) {
                final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);
                String queryStr = createQueryString(page, sectionPath);
                if (!ids.add(queryId)) {
                    continue;
                }

                TopDocs tops = indexSearcher.search(createQuery(queryStr), 100);

                // if Word Vector variant, rerank according to cosine sim from query to document terms
                if (command.equals("query_vector")) {
                    rerankByCosineSim(tops, queryStr);
                }
                ScoreDoc[] scoreDoc = tops.scoreDocs;
                writeRankingsToFile(scoreDoc, queryId, out, ids);
            }
        }

    }

    // Initializes index searcher that will be used to query indexed Lucene database
    private void createIndexSearcher(String iPath) throws IOException {
        Path indexPath = Paths.get(iPath);
        Directory indexDir = FSDirectory.open(indexPath);
        IndexReader indexReader = DirectoryReader.open(indexDir);
        indexSearcher = new IndexSearcher(indexReader);
    }

    // Tokenizes a string
    private List<String> getVectorWordTokens(String text) throws IOException {
        TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(text));
        TokenGenerator tg = new TokenGenerator(tokenStream);
        return Seq.generate(tg).limitWhile(Objects::nonNull)
                .map(String::toLowerCase)
                .toList();
    }

    // Takes contents of a document, tokenizes it, searches for corresponding word vectors in GloVe,
    // takes the average of these vectors, and runs cosine similarity against query word vector.
    private Double getDocumentVectorScore(INDArray query,  ScoreDoc sd) {
        try {
            Document doc = indexSearcher.doc(sd.doc);
            List<String> tokens = getVectorWordTokens(doc.getField("text").stringValue());
            INDArray docArray = gloveReader.getWordVector(tokens);
            Double score = gloveReader.getCosineSim(query, docArray);
            sd.score = score.floatValue();
            return score;
        } catch (IOException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    // Reranks according to cosine similarity
    private void rerankByCosineSim(TopDocs tops, String query) throws IOException {
        List<String> queryTokens = getVectorWordTokens(query);
        INDArray queryVector = gloveReader.getWordVector(queryTokens);
        Seq.seq(Arrays.stream(tops.scoreDocs))
                .map(sd -> new ImmutablePair<ScoreDoc, Double>(sd, getDocumentVectorScore(queryVector, sd)))
                .sorted(ImmutablePair::getRight)
                .reverse()
                .map(ImmutablePair::getLeft)
                .zip(Seq.range(0, tops.scoreDocs.length))
                .forEach(it -> tops.scoreDocs[it.v2] = it.v1);
    }
}
