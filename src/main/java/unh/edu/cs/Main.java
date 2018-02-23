package unh.edu.cs;

import com.google.common.collect.Maps;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;

import javax.naming.Name;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.Consumer;

public class Main {

    // Used as a wrapper around a static method: will call method and pass argument parser's parameters to it
    private static class Exec {
        private Consumer<Namespace> func;
        Exec(Consumer<Namespace> funcArg) { func = funcArg; }
        void run(Namespace params) { func.accept(params);}
    }

    public static ArgumentParser createArgParser() {
        ArgumentParser parser = ArgumentParsers.newFor("program").build();
        Subparsers subparsers = parser.addSubparsers(); // Subparsers is used to create subcommands

        // Add subcommand for running index program
        Subparser indexParser = subparsers.addParser("index")                  // index is the name of the subcommand
                .setDefault("func", new Exec(Main::runIndexer))
                .help("Indexes paragraph corpus using Lucene.");
        indexParser.addArgument("corpus")
                .required(true)
                .help("Location to paragraph corpus file (.cbor)");
        indexParser.addArgument("--out")
                .setDefault("index")
                .help("Directory name to create for Lucene index (default: index)");

        // Example of adding a second subcommand (query)
        Subparser queryParser = subparsers.addParser("query")
                .setDefault("func", new Exec(Main::runQuery))                   // Pass method reference to Exec to
                .help("Queries Lucene database.");                                 // run method when it is called.

        // This is an example of adding a position argument (query_type) that has multiple choices
        queryParser.addArgument("query_type")
                .choices("bm25") // Each string is a choice for this param
                .help("The type of query method to use.");

        // Another positional argument
        queryParser.addArgument("index")
                .required(true)
                .help("Location of the Lucene index directory.");  // This gets printed when -h or --help is called
        queryParser.addArgument("query_file")
                .required(true)
                .help("(required) Location of the query (.cbor) file.");

        // This is an example of an optional argument
        queryParser.addArgument("--out") // -- means it's not positional
                .setDefault("query_results.txt") // If no --out is supplied, defaults to query_results.txt
                .help("The name of the query results file to write. (default: query_results.txt)");

        // You can add more subcommands below by calling subparsers.addparser and following the examples above


        // Entity Linker
        Subparser linkerParser = subparsers.addParser("linker")
                .setDefault("func", new Exec(Main::runLinker))
                .help("Annotates existing Lucene index directory with" +
                        " entities linked using Spotlight.");

        linkerParser.addArgument("index")
                .help("Location of the Lucene index directory");

        return parser;
    }


//    private static void runIndexer(String sType, String corpusFile, String indexOutLocation) throws IOException {
    private static void runIndexer(Namespace namespace)  {
        String corpusFile = namespace.getString("corpus");
        String indexOutLocation = namespace.getString("out");

        LuceneIndexBuilder indexBuilder = new LuceneIndexBuilder(corpusFile, indexOutLocation);

        try {
            indexBuilder.initializeWriter();
            indexBuilder.run();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Runs query as per command arguments
//    private static void runQuery(String command, String qType, String indexLocation, String queryLocation,
//                                        String rankingOutputLocation) throws IOException {
        private static void runQuery(Namespace namespace)  {
        String indexLocation = namespace.getString("index");
        String queryType = namespace.getString("query_type");
        String queryLocation = namespace.getString("query_file");
        String out = namespace.getString("out");

            LuceneQueryBuilder qbuilder = null;
            try {
                qbuilder = new LuceneQueryBuilder(queryType, indexLocation);
                qbuilder.writeRankings(queryLocation, out);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }


    private static void runLinker(Namespace namespace) {
        String indexLocation = namespace.getString("index");
        KotlinEntityLinker linker =
                new KotlinEntityLinker(indexLocation);

//        try {
//            Thread.sleep(8000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        linker.retrieveEntities("this is a test");
        linker.run();
    }


    public static void main(String[] args) throws IOException {
        ArgumentParser parser = createArgParser();
        try {
            Namespace params = parser.parseArgs(args);
            ((Exec)params.get("func")).run(params);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

    }


//    public static void main2(String[] args) throws IOException {
//        String mode = "";
//        try {
//            mode = args[0];
//        } catch (ArrayIndexOutOfBoundsException e) {
//            printUsages();
//            System.exit(1);
//        }
//
//        switch (mode) {
//            // Runs indexer command
//            case "index":
//                try {
//                    final String indexType = args[1].toUpperCase();
//                    final String corpusFile = args[2];
//                    final String indexOutLocation = args[3];
//                    runIndexer(indexType, corpusFile, indexOutLocation);
//                } catch (IndexOutOfBoundsException e) {
//                    printIndexerUsage();
//                }
//                break;
//
//            // Runs query command (query_entity and query_bigram are sub-commands)
//            case "query":
//            case "query_entity":
//            case "query_bigram":
//            case "query_special":
//            case "query_kld":
//            case "query_random":
//                try {
//                    String command = args[0];
//                    String queryType = args[1].toUpperCase();
//                    String indexLocation = args[2];
//                    String queryLocation = args[3];
//                    String rankingOutputLocation = args[4];
//                } catch (ArrayIndexOutOfBoundsException e) {
//                    printQueryUsage();
//                } finally {
//                    String command = args[0];
//                    String queryType = args[1].toUpperCase();
//                    String indexLocation = args[2];
//                    String queryLocation = args[3];
//                    String rankingOutputLocation = args[4];
//                    runQuery(command, queryType, indexLocation, queryLocation, rankingOutputLocation);
//                }
//                break;
//            case "make_db":
//                try {
//                    GraphAnalyzer.makeDB(args);
//                } catch (ArrayIndexOutOfBoundsException e) {
//                    printQueryVectorUsage();
//                }
//                break;
//            case "train":
//                try {
//                    String command = args[0];
//                    String indexLocation = args[1];
//                    String queryLocation = args[2];
//                    String qrelLocation = args[3];
//                    KotlinTrainer trainer = new KotlinTrainer(indexLocation, queryLocation, qrelLocation);
//                    HashMap<String,Double> weights = trainer.train();
//                    trainer.writeWeights(weights);
//                } catch (ArrayIndexOutOfBoundsException e) {
//                    printTrainUsage();
//                }
//                break;
//            case "write_entities":
//                try {
//                    String command = args[0];
//                    String indexLocation = args[1];
//                    String queryLocation = args[2];
//                    String qrelLocation = args[3];
//                    KotlinTrainer trainer = new KotlinTrainer(indexLocation, queryLocation, qrelLocation);
//                    trainer.writeEntityModels();
//                } catch (ArrayIndexOutOfBoundsException e) {
//                    e.printStackTrace();
//                    printTrainUsage();
//                }
//                break;
//            case "reg":
//                try {
//                    String command = args[0];
//                    String indexLocation = args[1];
//                    String queryLocation = args[2];
//                    String weightLocation = args[3];
//                    String alpha = args[4];
//                    KotlinRegularizer regularizer = new KotlinRegularizer(indexLocation, queryLocation, weightLocation,
//                            alpha);
//                    regularizer.rerankQueries();
//                } catch (ArrayIndexOutOfBoundsException e) {
//                    printRegUsage();
//                }
//                break;
//            default:
//                printUsages();
//                break;
//        }
//
//    }
}
