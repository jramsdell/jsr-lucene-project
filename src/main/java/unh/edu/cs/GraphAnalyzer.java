package unh.edu.cs;
import edu.unh.cs.treccar_v2.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

//TODO: instead weight edges by BM25 score

public class GraphAnalyzer {
    private IndexSearcher indexSearcher;
    private IndexWriter indexWriter;
    private DB db;
    private ConcurrentMap<String, String> cmap;
    Random rand = new Random();
//    ConcurrentHashMap<String, TopDocs> storedQueries = new ConcurrentHashMap<>();
//    ConcurrentHashMap<String, String[]> storedEntities = new ConcurrentHashMap<>();
//    ConcurrentHashMap<Integer, Document> storedDocuments = new ConcurrentHashMap<>();
//    HashMap<String, HashMap<String, Double>> parModel = new HashMap<>();
//    HashMap<String, HashMap<String, Double>> entityModel = new HashMap<>();
    ConcurrentHashMap<String, HashMap<String, Double>> storedTerms = new ConcurrentHashMap<>();

    GraphAnalyzer(IndexSearcher id) throws IOException {
//        DB.HashMapMaker<?, ?> something = DBMaker.memoryShardedHashMap(30);
        indexSearcher = id;
//        db = DBMaker.fileDB("entity_db.db").fileLockDisable().fileMmapEnable().make();
//        cmap = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen();
//        db.close();
        db = DBMaker.fileDB("entity_db.db")
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .concurrencyScale(600).make();
        cmap = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen();
    }


    class Model {
        int docId = 0;
        String pid = "";
        Double score = 0.0;
        public final HashMap<String, Double> parModel = new HashMap<>();
        public final HashMap<String, Double> entityModel = new HashMap<>();
    }

    class ParagraphMixture {
        int docId = 0;
        Double score = 0.0;
        Double finalScore = 0.0;
        public HashMap<String, Double> entityMixture = new HashMap<>();
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


        // Switch back to concurrent
        List<ParagraphMixture> mixtures =
                ids.parallelStream()
                .map(this::getParagraphMixture)
                .collect(Collectors.toList());
        mixtures.forEach(pm -> pm.score = (double)tops.scoreDocs[indexMappings.get(pm.docId)].score);

        mixtures.forEach(pm -> pm.entityMixture.forEach((k, v) -> sinks.merge(k, v * pm.score, Double::sum)));
        mixtures.forEach(pm -> {
            if (!pm.entityMixture.isEmpty()) {
                pm.score = 0.0;
            }
            pm.entityMixture.forEach((k, v) -> pm.score += sinks.get(k) * v);
//            pm.score = Math.max(pm.score, pm.finalScore);
//            System.out.println(pm.score);

        });


        Seq.seq(mixtures)
                .sorted(m -> m.score)
                .reverse()
                .zip(Seq.range(0, tops.scoreDocs.length))
                .forEach(m -> {
                    tops.scoreDocs[m.v2].score = m.v1.score.floatValue();
                    tops.scoreDocs[m.v2].doc = m.v1.docId;
                });
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

    public void writeTermMap(HashMap<String, Double> termMap, String entity) throws IOException {
        String out = Seq.seq(termMap.entrySet())
                .map(entry -> entry.getKey() + " " + entry.getValue().toString())
                .toString("$");
        cmap.putIfAbsent(entity, out);

//        Document doc = new Document();
//        doc.add(new StringField("term", entity, Field.Store.YES));
//        termMap.forEach((k,v) -> {
//            doc.add(new StringField("distribution", k + " " + v.toString(), Field.Store.NO));
//        });
//        indexWriter.addDocument(doc);
    }

    public ArrayList<ImmutablePair<String,Double>> myTokenizer(String s, HashMap<String, Double> mixture) {
        ArrayList<ImmutablePair<String,Double>> pairs = new ArrayList<>();
        int last = 0;
        int counter = 0;
        int cur = 0;
        int end = 0;
        while (end == 0) {
            int next = s.indexOf("$", cur + 1);
            if (next < 0 || cur < 0) {
                end = 1;
                next = s.length();
            }
            if (counter++ > 100) {
                break;
            }

            int space = s.indexOf(" ", cur);
            if (space < 0) {
                cur = next;
                continue;
            }

            String entity = s.substring(cur + 1, space);
            String value = s.substring(space + 1, next);
//            System.out.println(entity + " " + value);
//            pairs.add(new ImmutablePair<String,Double>(entity, Double.parseDouble(value)));
            mixture.merge(entity, Double.parseDouble(value), Double::sum);

            cur = next;

//            if (space > 0) {
//                String entity = s.substring(last, space);
//                Double value;
//                if (cur < 0) {
//                    value = Double.parseDouble(s.substring(space));
//                } else {
//                    try {
//                        value = Double.parseDouble(s.substring(space, s.indexOf("$", cur)));
//                    } catch (StringIndexOutOfBoundsException e) {
//                        System.out.println("Space: " + space + " cur: " + cur);
//                        value = 0.0;
//                    }
//                }
//                System.out.println("Got here");
//
//                pairs.add(new ImmutablePair<String,Double>(entity, value));
//            }
//
//            if (cur == -1) {
//                break;
//            }
        }
        return pairs;
    }


    public HashMap<String, Double> getEntityMixture(String[] entities) throws IOException {
        HashMap<String, Double> mixture = new HashMap<>();
        int counter = 0;
        int failures = 0;
        for (String entity : entities) {
//            TermQuery tq = new TermQuery(new Term("term", entity));
//            String[] distribution;
//            TopDocs td = entitySearcher.search(tq, 1);
//            distribution = entitySearcher.doc(td.scoreDocs[0].doc).getValues("distribution");
            String wee = cmap.get(entity);
            try {
                myTokenizer(wee, mixture);
            } catch (StringIndexOutOfBoundsException e) {
                failures++;
                continue;
            }
            counter++;


//            String[] distribution = cmap.get(entity).split("w");
//
//            Seq.of(distribution)
//                    .map(m -> {
//                        String[] elements = m.split(" ");
//                        if (elements.length == 2) {
//                            return new Tuple2<String, Double>(elements[0], Double.parseDouble(elements[1]));
//                        } else {
//                            return new Tuple2<String, Double>(elements[0], 0.0);
//                        }
//                    })
//                    .forEach(t -> mixture.merge(t.v1, t.v2, Double::sum));
        }
        System.out.println("Failures: " + failures + ", entities: " + entities.length);

        Double total = 0.0;
        for (Double v: mixture.values()) {
            total += v;
        }
        final Double mytotal = total;
        mixture.replaceAll((k,v) -> v / mytotal);
//        Double total = (double)counter;
//        mixture.replaceAll((k,v) -> v / total);
//        mixture.forEach((k,v) -> System.out.println(k + ": " + v));
//        System.out.println("_____");
        return mixture;
    }

    public ParagraphMixture getParagraphMixture(int docId) {
        ParagraphMixture pm = new ParagraphMixture();
        pm.docId = docId;
        Document doc;
        try {
            doc = indexSearcher.doc(docId);
        } catch (IOException e) {
            e.printStackTrace();
            return pm;
        }

        String[] entities = doc.getValues("spotlight");
        try {
            pm.entityMixture = getEntityMixture(entities);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pm;
    }

    public static void makeDB(String[] args) throws IOException {
//        IndexSearcher is = createIndexSearcher("/home/hcgs/Desktop/myindex");
        IndexSearcher is = createIndexSearcher(args[0]);
        GraphAnalyzer ga = new GraphAnalyzer(is);
        ga.initializeWriter("entity_index");
        Fields fields = MultiFields.getFields(is.getIndexReader());
        TermsEnum terms = fields.terms("spotlight").iterator();
        ArrayList<String> termList = new ArrayList<>();
        BytesRef bytesRef = terms.next();
        int counter = 0;
        while (bytesRef != null) {
            String term = bytesRef.utf8ToString();
            termList.add(term);
            if (termList.size() >= 1000) {
                termList.parallelStream()
                        .forEach(entity -> {
                            try {
                                HashMap<String, Double> termMap = ga.getTermMap(entity);
                                ga.writeTermMap(termMap, entity);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                termList.clear();
            }
            counter += 1;
            if (counter % 100 == 0) {
                System.out.println(counter);
            }
            bytesRef = terms.next();
        }

        termList.parallelStream()
                .forEach(entity -> {
                    try {
                        HashMap<String, Double> termMap = ga.getTermMap(entity);
                        ga.writeTermMap(termMap, entity);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        ga.db.close();
    }


    public static void main (String[] args) throws IOException {


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
