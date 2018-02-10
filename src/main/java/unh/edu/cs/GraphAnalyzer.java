package unh.edu.cs;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jooq.lambda.Seq;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.StreamSupport;

//TODO: instead weight edges by BM25 score

public class GraphAnalyzer {
    private IndexSearcher indexSearcher;
    Random rand = new Random();
    ConcurrentHashMap<String, TopDocs> storedQueries = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, String[]> storedEntities = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, Document> storedDocuments = new ConcurrentHashMap<>();
//    HashMap<String, HashMap<String, Double>> parModel = new HashMap<>();
//    HashMap<String, HashMap<String, Double>> entityModel = new HashMap<>();

    GraphAnalyzer(IndexSearcher id) throws IOException {
        indexSearcher = id;

    }

    class Model {
        public final HashMap<String, Double> parModel = new HashMap<>();
        public final HashMap<String, Double> entityModel = new HashMap<>();
    }



//    public void getQueryDocs() throws IOException {
//        TermQuery tq = new TermQuery(new Term("text", "hello"));
//        TopDocs td = indexSearcher.search(tq, 1000000);
//        System.out.println(td.scoreDocs.length);
//        for (ScoreDoc sc : td.scoreDocs) {
//            System.out.println(indexReader.document(sc.doc).getField("text"));
//        }
//    }

    public Document graphTransition(String entity) throws IOException {
        TopDocs td;
        if (!storedQueries.containsKey(entity)) {
            TermQuery tq = new TermQuery(new Term("spotlight", entity));
            td = indexSearcher.search(tq, 1000000);
            storedQueries.put(entity, td);
        } else {
            td = storedQueries.get(entity);
        }
        ScoreDoc sc = td.scoreDocs[rand.nextInt(td.scoreDocs.length)];
        Document doc;
        if (!storedDocuments.containsKey(sc.doc)) {
            doc = indexSearcher.doc(sc.doc);
            storedDocuments.put(sc.doc, doc);
        } else {
            doc = storedDocuments.get(sc.doc);
        }
        return doc;
    }

    public Model getModel(int docID) throws IOException {
        final Model model = new Model();

        int nSteps = 5;
        int nWalks = 800;
        Document baseDoc = indexSearcher.doc(docID);

        for (int walk = 0; walk < nWalks; walk++) {
            System.out.println(walk);
            Document doc = baseDoc;

            for (int step = 0; step < nSteps; step++) {
                String pid = doc.get("paragraphid");
                String[] entities;
                if (!storedEntities.containsKey(pid)) {
                    entities = doc.getValues("spotlight");
                    storedEntities.put(pid, entities);
                } else {
                    entities = storedEntities.get(pid);
                }

//                String[] entities = doc.getValues("spotlight");
                String entity = entities[rand.nextInt(entities.length)];
                doc = graphTransition(entity);
                model.entityModel.merge(entity, 1.0, Double::sum);
                model.parModel.merge(doc.get("paragraphid"), 1.0, Double::sum);
            }
        }

        Double total = (double)nSteps * nWalks;
        model.entityModel.replaceAll((k,v) -> v / total);
        model.parModel.replaceAll((k,v) -> v / total);
        return model;
    }

    public static IndexSearcher createIndexSearcher(String iPath) throws IOException {
        Path indexPath = Paths.get(iPath);
        Directory indexDir = FSDirectory.open(indexPath);
        IndexReader indexReader = DirectoryReader.open(indexDir);
        return new IndexSearcher(indexReader);
    }

    public Model gett(Integer i) {
        try {
            return getModel(i);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main (String[] args) throws IOException {
//        IndexSearcher is = createIndexSearcher("/home/hcgs/Desktop/myindex");
        IndexSearcher is = createIndexSearcher(args[0]);
        GraphAnalyzer ga = new GraphAnalyzer(is);
        Integer index = Integer.parseInt(args[1]);
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            indices.add(i);
        }
        ArrayList<Model> models = new ArrayList<>();
        StreamSupport.stream(indices.spliterator(), true)
                .parallel()
                .map(ga::gett)
                .forEach(models::add);
//        List<Model> models = Seq.range(2, 10)
//                .parallel()
//                .map(ga::gett)
//                .toList();
//        Model m = ga.getModel(index);
//        System.out.println(m.parModel);
//        System.out.println(is.doc(index).get("text"));
//        Seq.seq(m.entityModel.entrySet())
//                .sorted(Map.Entry::getValue)
//                .reverse()
//                .take(60)
//                .forEach(System.out::println);
//        EntityGraphBuilder eb = new EntityGraphBuilder(args[0]);
//        GraphAnalyzer eb = new GraphAnalyzer("/home/hcgs/Desktop/myindex");
//        eb.getQueryDocs();
//        eb.buildGraph();
//        eb.buildModel();
//        eb.writeModels();
    }
}
