package unh.edu.cs;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
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
    private IndexWriter indexWriter;
    Random rand = new Random();
//    ConcurrentHashMap<String, TopDocs> storedQueries = new ConcurrentHashMap<>();
//    ConcurrentHashMap<String, String[]> storedEntities = new ConcurrentHashMap<>();
//    ConcurrentHashMap<Integer, Document> storedDocuments = new ConcurrentHashMap<>();
//    HashMap<String, HashMap<String, Double>> parModel = new HashMap<>();
//    HashMap<String, HashMap<String, Double>> entityModel = new HashMap<>();

    GraphAnalyzer(IndexSearcher id) throws IOException {
        indexSearcher = id;

    }

    class Model {
        int docId = 0;
        String pid = "";
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

    public Document graphTransition(String entity, HashMap<String, TopDocs> storedQueries, HashMap<Integer, Document> storedDocuments) throws IOException {
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
        HashMap<String, TopDocs> storedQueries = new HashMap<>();
        HashMap<String, String[]> storedEntities = new HashMap<>();
        HashMap<Integer, Document> storedDocuments = new HashMap<>();


        final Model model = new Model();

        int nSteps = 5;
        int nWalks = 300;
        Document baseDoc = indexSearcher.doc(docID);
        model.pid = baseDoc.get("paragraphid");
        model.docId = docID;

        for (int walk = 0; walk < nWalks; walk++) {
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
                // TODO: fix this
                if (entities.length <= 0)
                    break;
                String entity = entities[rand.nextInt(entities.length)];
                doc = graphTransition(entity, storedQueries, storedDocuments);
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

    public void rerankTopDocs(TopDocs tops) {
        ArrayList<Integer> ids = new ArrayList<>();
        HashMap<Integer, Integer> indexMappings = new HashMap<>();
        HashMap<String, Double> sinks = new HashMap<>();
        for (int i = 0; i < tops.scoreDocs.length; i++) {
            ids.add(tops.scoreDocs[i].doc);
            indexMappings.put(tops.scoreDocs[i].doc, i);
        }

        ArrayList<Model> models = new ArrayList<>();
        StreamSupport.stream(ids.spliterator(), true)
                .parallel()
                .map(this::gett)
                .forEach(models::add);

        Seq.seq(models)
                .forEach( m -> {
                    Double score = (double)tops.scoreDocs[indexMappings.get(m.docId)].score;
                    m.entityModel.forEach(
                            (k,v) -> sinks.merge(k, v * score, Double::sum));
                });

        Seq.seq(sinks.entrySet())
                .sorted(Map.Entry::getValue)
                .reverse()
                .take(12)
                .forEach(System.out::println);

    }

    void initializeWriter(String indexOutLocation) throws IOException {
        Path indexPath = Paths.get(indexOutLocation);
        Directory indexOutDirectory = FSDirectory.open(indexPath);
        IndexWriterConfig indexConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriter = new IndexWriter(indexOutDirectory, indexConfig);
    }

    public void writeModel(Model model) {
        Document doc = new Document();
        doc.add(new StringField("paragraphid", model.pid, Field.Store.YES));
        model.entityModel.forEach((k,v) -> {
            if (v >= 0.01) {
                doc.add(new StringField("entity_distribution", k + " " + v.toString(), Field.Store.YES));
            }
        });

        model.parModel.forEach((k,v) -> {
            if (v >= 0.01) {
                doc.add(new StringField("paragraph_distribution", k + " " + v.toString(), Field.Store.YES));
            }
        });

        try {
            indexWriter.addDocument(doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main (String[] args) throws IOException {
//        IndexSearcher is = createIndexSearcher("/home/hcgs/Desktop/myindex");
        IndexSearcher is = createIndexSearcher(args[0]);
        GraphAnalyzer ga = new GraphAnalyzer(is);
        ga.initializeWriter(args[1]);
        int maxCount = is.getIndexReader().maxDoc();
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            indices.add(i);
        }

        indices.parallelStream()
                .map(ga::gett)
                .forEach(ga::writeModel);


//        Integer index = Integer.parseInt(args[1]);
//        ArrayList<Integer> indices = new ArrayList<>();
//        for (int i = 2; i < 10; i++) {
//            indices.add(i);
//        }
//        ArrayList<Model> models = new ArrayList<>();
//        StreamSupport.stream(indices.spliterator(), true)
//                .parallel()
//                .map(ga::gett)
//                .forEach(models::add);
//        models.forEach(m ->{
//            Seq.seq(m.parModel.entrySet())
//                    .sorted(Map.Entry::getValue)
//                    .reverse()
//                    .take(8)
//                    .forEach(System.out::println);
//        });
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
