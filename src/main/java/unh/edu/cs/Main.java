package unh.edu.cs;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;

import java.io.IOException;
import java.util.HashMap;

public class Main {

    private static void printIndexerUsage() {
        System.out.println("Indexer Usage: index indexType corpusCBOR IndexDirectory\n" +
                "Where:\n\tindexType is one of: \n" +
                "\t\tnormal (indexes text in paragraphs)\n" +
                "\t\tspotlight (also indexes entities using local spotlight server)\n" +
                "\tcorpusCBOR: the paragraph corpus file to index\n" +
                "\tIndexDirectory: the name of the index directory to create after indexing.\n"
        );
    }

    private static void printQueryUsage() {
        System.out.println("Query Usage: query queryType indexLoc queryCbor rankOutput\n" +
                "Where:\n\tqueryType is one of: \n" +
                "\t\tpage (retrieves query results for each page)\n" +
                "\t\tsection (retrieves query results for each section)\n" +
                "\tindexLoc: the directory of the lucene index.\n" +
                "\tqueryCbor: the cbor file to be used as a query with the index.\n" +
                "\trankOutput: is the output location of the rankings (used for trec-eval)\n"
        );
    }

    private static void printQueryVectorUsage() {
        System.out.println("Query Usage: query_vector queryType indexLoc vectorLoc queryCbor rankOutput\n" +
                "Where:\n\tqueryType is one of: \n" +
                "\t\tpage (retrieves query results for each page)\n" +
                "\t\tsection (retrieves query results for each section)\n" +
                "\tindexLoc: the directory of the lucene index.\n" +
                "\tvectorLoc: the word vector file.\n" +
                "\tqueryCbor: the cbor file to be used as a query with the index.\n" +
                "\trankOutput: is the output location of the rankings (used for trec-eval)\n"
        );
    }

    private static void printTrainUsage() {
        System.out.println("Usage: train queryType indexLoc queryLoc qrelLoc\n");
    }

    private static void printRegUsage() {
        System.out.println("Usage: reg indexLoc queryLoc weightDB\n");
    }

    private static void runIndexer(String sType, String corpusFile, String indexOutLocation) throws IOException {
        // Index Enum
        IndexType indexType = null;
        try {
            indexType = IndexType.valueOf(sType);
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown index type!");
            printIndexerUsage();
            System.exit(1);
        }

        LuceneIndexBuilder indexBuilder = new LuceneIndexBuilder(indexType, corpusFile, indexOutLocation);

        indexBuilder.initializeWriter();
        indexBuilder.run();

    }

    // Runs query as per command arguments
    private static void runQuery(String command, String qType, String indexLocation, String queryLocation,
                                        String rankingOutputLocation) throws IOException {

        LuceneQueryBuilder qbuilder = getQueryBuilder(command, qType, indexLocation,
                queryLocation, rankingOutputLocation);
        qbuilder.writeRankings(queryLocation, rankingOutputLocation);
    }

    // Variant of runQuery that also supplied location to word vectors file (used for word vector reranking)
    private static void runQuery(String command, String qType, String indexLocation, String queryLocation,
                                 String rankingOutputLocation, String vectorLoc) throws IOException {

        LuceneQueryBuilder qbuilder = getQueryBuilder(command, qType, indexLocation,
                queryLocation, rankingOutputLocation);
        qbuilder.setVectorLocation(vectorLoc);
        qbuilder.writeRankings(queryLocation, rankingOutputLocation);
    }

    private static LuceneQueryBuilder getQueryBuilder(String command, String qType, String indexLocation,
                                                      String queryLocation,
                                                      String rankingOutputLocation) throws IOException {
        QueryType queryType = null;
        try {
            queryType = QueryType.valueOf(qType);

        } catch (IllegalArgumentException e) {
            System.out.println("Unknown query type!");
            printQueryUsage();
            System.exit(1);
        }

        return new LuceneQueryBuilder(command, queryType, new StandardAnalyzer(), new BM25Similarity(), indexLocation);
    }



    private static void printUsages() {
        printIndexerUsage();
        System.out.println();
        printQueryUsage();

    }


    public static void main(String[] args) throws IOException {
        String mode = "";
        try {
            mode = args[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            printUsages();
            System.exit(1);
        }

        switch (mode) {
            // Runs indexer command
            case "index":
                try {
                    final String indexType = args[1].toUpperCase();
                    final String corpusFile = args[2];
                    final String indexOutLocation = args[3];
                    runIndexer(indexType, corpusFile, indexOutLocation);
                } catch (IndexOutOfBoundsException e) {
                    printIndexerUsage();
                }
                break;

            // Runs query command (query_entity and query_bigram are sub-commands)
            case "query":
            case "query_entity":
            case "query_bigram":
            case "query_special":
            case "query_kld":
            case "query_random":
                try {
                    String command = args[0];
                    String queryType = args[1].toUpperCase();
                    String indexLocation = args[2];
                    String queryLocation = args[3];
                    String rankingOutputLocation = args[4];
                } catch (ArrayIndexOutOfBoundsException e) {
                    printQueryUsage();
                } finally {
                    String command = args[0];
                    String queryType = args[1].toUpperCase();
                    String indexLocation = args[2];
                    String queryLocation = args[3];
                    String rankingOutputLocation = args[4];
                    runQuery(command, queryType, indexLocation, queryLocation, rankingOutputLocation);
                }
                break;
            case "make_db":
                try {
                    GraphAnalyzer.makeDB(args);
                } catch (ArrayIndexOutOfBoundsException e) {
                    printQueryVectorUsage();
                }
                break;
            case "train":
                try {
                    String command = args[0];
                    String indexLocation = args[1];
                    String queryLocation = args[2];
                    String qrelLocation = args[3];
                    KotlinTrainer trainer = new KotlinTrainer(indexLocation, queryLocation, qrelLocation);
                    HashMap<String,Double> weights = trainer.train();
                    trainer.writeWeights(weights);
                } catch (ArrayIndexOutOfBoundsException e) {
                    printTrainUsage();
                }
                break;
            case "reg":
                try {
                    String command = args[0];
                    String indexLocation = args[1];
                    String queryLocation = args[2];
                    String weightLocation = args[3];
                    KotlinRegularizer regularizer = new KotlinRegularizer(indexLocation, queryLocation, weightLocation);
                    regularizer.rerankQueries();
                } catch (ArrayIndexOutOfBoundsException e) {
                    printRegUsage();
                }
                break;
            default:
                printUsages();
                break;
        }

    }
}
