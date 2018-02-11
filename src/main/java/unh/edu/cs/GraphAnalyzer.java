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
import org.apache.lucene.util.BytesRef;
import org.jooq.lambda.Seq;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    ConcurrentHashMap<String, HashMap<String, Double>> storedTerms = new ConcurrentHashMap<>();

    GraphAnalyzer(IndexSearcher id) throws IOException {
        indexSearcher = id;

    }

    class Model {
        int docId = 0;
        String pid = "";
        Double score = 0.0;
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

    public HashMap<String, Double> getTermMap(String entity) throws IOException {
//        if (storedTerms.containsKey(entity)) {
//            return storedTerms.get(entity);
//        }

        TermQuery tq = new TermQuery(new Term("spotlight", entity));
        TopDocs td = indexSearcher.search(tq, 1000000);
        HashMap<String, Double> termCounts = new HashMap<>();
        int counter = 0;
        for (ScoreDoc sc : td.scoreDocs) {
            Document doc = indexSearcher.doc(sc.doc);
            String[] terms = doc.getValues("spotlight");
            for (String term : terms) {
                counter++;
                termCounts.merge(term, 1.0, Double::sum);
            }
        }

        final Double total = (double)counter;
        termCounts.replaceAll((k,v) -> v / total);
//        storedTerms.put(entity, termCounts);
        return termCounts;
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
        String[] entities = baseDoc.getValues("spotlight");
        for (String entity : entities) {
            HashMap<String, Double> termCounts = getTermMap(entity);
            termCounts.forEach((k,v) -> model.entityModel.merge(k, v, Double::sum));
        }
        if (model.entityModel.isEmpty()) {
            return model;
        }
        Double total = Seq.seq(model.entityModel.values()).sum().get();
        model.entityModel.replaceAll((k,v) -> v / total);

//        for (int walk = 0; walk < nWalks; walk++) {
//            Document doc = baseDoc;
//
//            for (int step = 0; step < nSteps; step++) {
//                String pid = doc.get("paragraphid");
//                String[] entities;
//                if (!storedEntities.containsKey(pid)) {
//                    entities = doc.getValues("spotlight");
//                    storedEntities.put(pid, entities);
//                } else {
//                    entities = storedEntities.get(pid);
//                }
//
////                String[] entities = doc.getValues("spotlight");
//                // TODO: fix this
//                if (entities.length <= 0)
//                    break;
//                String entity = entities[rand.nextInt(entities.length)];
//                doc = graphTransition(entity, storedQueries, storedDocuments);
//                model.entityModel.merge(entity, 1.0, Double::sum);
//                model.parModel.merge(doc.get("paragraphid"), 1.0, Double::sum);
//            }
//        }
//
//        Double total = (double)nSteps * nWalks;
//        model.entityModel.replaceAll((k,v) -> v / total);
//        model.parModel.replaceAll((k,v) -> v / total);
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

    public void recordTerms(TopDocs tops) throws IOException {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int i = 0; i < tops.scoreDocs.length; i++) {
            ids.add(i);
        }
        ids.parallelStream()
                .forEach(v -> {
                    try {
                        String[] terms = indexSearcher.doc(tops.scoreDocs[v].doc).getValues("spotlight");
                        for (String term : terms) {
                            getTermMap(term);
                        }
                    } catch (IOException e) {
                    }
                });

        System.out.println(".");
    }

    public void writeTerms() throws IOException {
        initializeWriter("termMaps");
        storedTerms.forEach((k,v) -> {
            Document doc = new Document();
            doc.add(new StringField("term", k, Field.Store.YES));
            v.forEach((tmap, tvalue) -> {
                doc.add(new StringField("distribution", tmap + " " + tvalue.toString(), Field.Store.YES));
            });
            try {
                indexWriter.addDocument(doc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
                    m.score = score;
                    m.entityModel.forEach(
                            (k,v) -> sinks.merge(k, v * score, Double::sum));
                });

//        Seq.seq(sinks.entrySet())
//                .sorted(Map.Entry::getValue)
//                .reverse()
//                .take(12)
//                .forEach(System.out::println);

        Seq.seq(models)
                .forEach( m -> {
                    if (!m.entityModel.isEmpty()) {
                        m.score = 0.0;
                    }
                    m.entityModel.forEach((k,v) -> m.score += sinks.get(k) * v);
                });
        Seq.seq(models)
                .sorted(m -> m.score)
                .reverse()
                .zip(Seq.range(0, tops.scoreDocs.length))
                .forEach(m -> {
                    tops.scoreDocs[m.v2].score = m.v1.score.floatValue();
                    tops.scoreDocs[m.v2].doc = m.v1.docId;
                });
        System.out.print(".");


    }

    void initializeWriter(String indexOutLocation) throws IOException {
        Path indexPath = Paths.get(indexOutLocation);
        Directory indexOutDirectory = FSDirectory.open(indexPath);
        IndexWriterConfig indexConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriter = new IndexWriter(indexOutDirectory, indexConfig);
    }

    public void writeModel(Model model) {
        Document doc = new Document();
        if (model.docId % 100 == 0) {
            System.out.println(model.docId);
        }
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
        Fields fields = MultiFields.getFields(is.getIndexReader());
        TermsEnum terms = fields.terms("spotlight").iterator();
        BytesRef bytesRef = terms.next();
        int counter = 0;
        while (bytesRef != null) {
            String term = bytesRef.utf8ToString();
            counter += 1;
            HashMap<String, Double> termCounts = ga.getTermMap(term);
            if (counter % 100 == 0) {
                Seq.seq(termCounts.entrySet())
                        .sorted(Map.Entry::getValue)
                        .reverse()
                        .take(20)
                        .forEach(System.out::println);
            }
            bytesRef = terms.next();
        }

//        IndexSearcher is = createIndexSearcher(args[0]);



//        GraphAnalyzer ga = new GraphAnalyzer(is);
//        ga.initializeWriter(args[1]);
//        int maxCount = is.getIndexReader().maxDoc();
//        ArrayList<Integer> indices = new ArrayList<>();
//        for (int i = 0; i < maxCount; i++) {
//            indices.add(i);
//        }
//
//        indices.parallelStream()
//                .map(ga::gett)
//                .forEach(ga::writeModel);


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
