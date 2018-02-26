package unh.edu.cs;

import com.google.common.collect.Maps;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;
import unh.edu.cs.ranklib.KotlinRankLibTrainer;

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
                .choices("page", "section") // Each string is a choice for this param
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

        linkerParser.addArgument("server_location")
                .help("Path to directory where dbpedia spotlight server and model are located. If none exists, " +
                        "linker will automatically download the server and model to the given location.");


        // Graph Builder
        Subparser graphBuilderParser = subparsers.addParser("graph_builder")
                .setDefault("func", new Exec(Main::runGraphBuilder))
                .help("(linker command must be run first) Creates bipartite graph between entities and paragraphs");

        graphBuilderParser.addArgument("index")
                .help("Location of the Lucene index directory");

        // Ranklib Query
        Subparser ranklibQueryParser = subparsers.addParser("ranklib_query")
                .setDefault("func", new Exec(Main::runRanklibQuery))
                .help("Runs queries using ranklib trained methods.");

        ranklibQueryParser.addArgument("method")
                .choices("bm25", "entity_similarity", "average_query", "split_sections", "mixtures", "combined",
                        "lm_mercer", "lm_dirichlet");

        ranklibQueryParser.addArgument("index").help("Location of Lucene index directory.");
        ranklibQueryParser.addArgument("query").help("Location of query file (.cbor)");
        ranklibQueryParser.addArgument("--out")
                .setDefault("query_results.run")
                .help("Specifies the output name of the run file.");
        ranklibQueryParser.addArgument("--graph_database")
                .setDefault("")
                .help("(only used for mixtures method): Location of graph_database.db file.");

        // Ranklib Trainer
        Subparser ranklibTrainerParser = subparsers.addParser("ranklib_trainer")
                .setDefault("func", new Exec(Main::runRanklibTrainer))
                .help("(linker and graph_builder must be run first) " +
                        "Trains according to ranklib");

        ranklibTrainerParser.addArgument("method")
                .choices("entity_similarity", "average_query", "split_sections", "mixtures", "combined",
                "entity_query", "lm_mercer", "lm_dirichlet");
        ranklibTrainerParser.addArgument("index").help("Location of the Lucene index directory");
        ranklibTrainerParser.addArgument("query").help("Location of query file (.cbor)");
        ranklibTrainerParser.addArgument("qrel").help("Locations of matching qrel file.");
        ranklibTrainerParser.addArgument("--out")
                .setDefault("ranklib_features.txt")
                .help("Output name for the RankLib compatible feature file.");
        ranklibTrainerParser.addArgument("--graph_database")
                .setDefault("")
                .help("(only used for mixtures method): Location of graph_database.db file.");


        // Ranklib Trainer
        Subparser statsParser = subparsers.addParser("stats")
                .setDefault("func", new Exec(Main::runStatsParser))
                .help("Gets stats from Lucene index");

        statsParser.addArgument("index").help("Location of the Lucene index directory");

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
        String serverLocation = namespace.getString("server_location");
        KotlinEntityLinker linker =
                new KotlinEntityLinker(indexLocation, serverLocation);
        linker.run();
    }

    private static void runGraphBuilder(Namespace namespace) {
        String indexLocation = namespace.getString("index");
        KotlinGraphBuilder graphBuilder = new KotlinGraphBuilder(indexLocation);
        graphBuilder.run();
    }

    private static void runStatsParser(Namespace namespace) {
        String indexLocation = namespace.getString("index");
        KotlinStatsParser kstat = new KotlinStatsParser(indexLocation);
        kstat.stats();
    }

    private static void runRanklibTrainer(Namespace namespace) {
        String indexLocation = namespace.getString("index");
        String qrelLocation = namespace.getString("qrel");
        String queryLocation = namespace.getString("query");
        String graphLocation = namespace.getString("graph_database");
        String out = namespace.getString("out");
        String method = namespace.getString("method");
        KotlinRankLibTrainer kotTrainer =
                new KotlinRankLibTrainer(indexLocation, queryLocation, qrelLocation, graphLocation);
        kotTrainer.train(method, out);
    }

    private static void runRanklibQuery(Namespace namespace) {
        String indexLocation = namespace.getString("index");
        String queryLocation = namespace.getString("query");
        String graphLocation = namespace.getString("graph_database");
        String method = namespace.getString("method");
        String out = namespace.getString("out");
        KotlinRankLibTrainer kotTrainer =
                new KotlinRankLibTrainer(indexLocation, queryLocation, "", graphLocation);
        kotTrainer.runRanklibQuery(method, out);
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


}
