package unh.edu.cs;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jooq.lambda.Seq;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

//TODO: instead weight edges by BM25 score

public class GraphAnalyzer {
    private IndexSearcher indexSearcher;
    private IndexWriter indexWriter;
    private DB db;
    private ConcurrentMap<String, String> cmap;
    private ConcurrentMap<String, String> entityMap;
    private ConcurrentMap<String, String> parMap;
    Random rand = new Random();
//    ConcurrentHashMap<String, TopDocs> storedQueries = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>>> storedEntities = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>>> storedParagraphs = new ConcurrentHashMap<>();
//    ConcurrentHashMap<String, String[]> storedEntities = new ConcurrentHashMap<>();
//    ConcurrentHashMap<String, String[]> storedParagraphs = new ConcurrentHashMap<>();
//    HashMap<String, HashMap<String, Double>> parModel = new HashMap<>();
//    HashMap<String, HashMap<String, Double>> entityModel = new HashMap<>();
    ConcurrentHashMap<String, HashMap<String, Double>> storedTerms = new ConcurrentHashMap<>();

    GraphAnalyzer(IndexSearcher id) throws IOException {
//        DB.HashMapMaker<?, ?> something = DBMaker.memoryShardedHashMap(30);
        indexSearcher = id;
//        db = DBMaker.fileDB("entity_db.db").fileLockDisable().fileMmapEnable().make();
//        cmap = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen();
//        db.close();
        db = DBMaker.fileDB("entity_db_dedup.db")
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();
        cmap = db.hashMap("dist_map", Serializer.STRING, Serializer.STRING).createOrOpen();
        parMap = db.hashMap("par_map", Serializer.STRING, Serializer.STRING).createOrOpen();
        entityMap = db.hashMap("entity_map", Serializer.STRING, Serializer.STRING).createOrOpen();
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

    public void recordTerms(String entity) throws IOException {
        TermQuery tq = new TermQuery(new Term("spotlight", entity));
        TopDocs td = indexSearcher.search(tq, 10000);
        StringJoiner stringJoiner = new StringJoiner(" ");
        HashSet<String> termSet = new HashSet<>();

        for (ScoreDoc sc : td.scoreDocs) {
            Document doc = indexSearcher.doc(sc.doc);
            String pid = doc.get("paragraphid");
//            stringJoiner.add(pid);
            termSet.add(pid);
//            parMap.merge(pid, entity, (k,v) ->  k + " " + v);
        }
        termSet.iterator().forEachRemaining(stringJoiner::add);
//        entityMap.merge(entity, pid, (k,v) ->  k + " " + v);
        entityMap.put(entity, stringJoiner.toString());
    }

    public void recordParagraphs(int docID) throws IOException {
        Document doc = indexSearcher.doc(docID);
        StringJoiner stringJoiner = new StringJoiner(" ");
        HashSet<String> paragraphSet = new HashSet<>();
        for (String entity : doc.getValues("spotlight")) {
            paragraphSet.add(entity);
        }
        paragraphSet.iterator().forEachRemaining(stringJoiner::add);
        parMap.put(doc.get("paragraphid"), stringJoiner.toString());
    }

    public HashMap<String, Double> getTermMap(String entity) throws IOException {
//        if (storedTerms.containsKey(entity)) {
//            return storedTerms.get(entity);
//        }

        TermQuery tq = new TermQuery(new Term("spotlight", entity));
//        TopDocs td = indexSearcher.search(tq, 1000000);
        TopDocs td = indexSearcher.search(tq, 100);
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


    public static IndexSearcher createIndexSearcher(String iPath) throws IOException {
        Path indexPath = Paths.get(iPath);
        Directory indexDir = FSDirectory.open(indexPath);
        IndexReader indexReader = DirectoryReader.open(indexDir);
        return new IndexSearcher(indexReader);
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

    public ArrayList<ImmutablePair<Integer, Integer>> getJumpPlaces(String text) {
        ArrayList<ImmutablePair<Integer, Integer>> places = new ArrayList<>();
        int cur = -1;
        int next = text.indexOf(" ");
        while (true) {
            if (next < 0) {
                places.add(ImmutablePair.of(cur + 1, text.length()));
                break;
            }
            places.add(ImmutablePair.of(cur + 1, next));
            cur = next;
            next = text.indexOf(" ", next + 1);
        }
        return places;
    }

    public String useJumpPlaces(String text, ArrayList<ImmutablePair<Integer, Integer>> places) {
        ImmutablePair<Integer, Integer> place = places.get(rand.nextInt(places.size()));
        return text.substring(place.left, place.right);
    }

//    public void doJumps(String entity) {
//        HashMap<String, Integer> counts = new HashMap<>();
//        int nWalks = 2000;
//        int nSteps = 5;
//        int counter = 0;
//        for (int walk = 0; walk < nWalks; walk++) {
//            String curEntity = entity;
//
//            for (int step = 0; step < nSteps; step++) {
//                counter++;
//                String parString = entityMap.get(curEntity);
//                ArrayList<ImmutablePair<Integer, Integer>> parJumpPlaces = getJumpPlaces(parString);
//                String nextPar = useJumpPlaces(parString, parJumpPlaces);
//                String entityString = parMap.get(nextPar);
//                ArrayList<ImmutablePair<Integer, Integer>> entityJumpPlaces = getJumpPlaces(entityString);
//                curEntity = useJumpPlaces(entityString, entityJumpPlaces);
//                counts.merge(curEntity, 1, Integer::sum);
//            }
//        }
//        Double total = (double)counter;
//        StringJoiner stringJoiner = new StringJoiner("$");
//        counts.forEach((k,v) -> {
//            stringJoiner.add(k + " " + ((double)v / total));
//        });
//        System.out.println(stringJoiner.toString());
////        counts.replaceAll((k,v) -> v / total);
////        Seq.seq(counts.entrySet())
////                .sorted(Map.Entry::getValue)
////                .reverse()
////                .take(10)
////                .forEach(entry -> System.out.println(entry.getKey() + " " + entry.getValue()));
//    }


    public void doJumps(String pid) {
        HashMap<String, Double> counts = new HashMap<>();
        int nWalks = 8000;
        int nSteps = 5;
        for (int walk = 0; walk < nWalks; walk++) {
            String curPar = pid;
            double volume = 1.0;
            int start = 0;

            for (int step = 0; step < nSteps; step++) {
//                if (!storedEntities.contains(curEntity)) {
//                    storedEntities.putIfAbsent(curEntity, getJumpPlaces(parString));
//                }
                ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>> entityData =
                        storedParagraphs.computeIfAbsent(curPar, (it -> {
                            String entityString = parMap.get(it);
                            ArrayList<ImmutablePair<Integer, Integer>> places = getJumpPlaces(entityString);
                            return ImmutablePair.of(entityString, places);
                        }));

//                ArrayList<ImmutablePair<Integer, Integer>> parPlaces = storedEntities.get(curEntity);
//                ArrayList<ImmutablePair<Integer, Integer>> parPlaces = getJumpPlaces(parString);

                String nextEntity = useJumpPlaces(entityData.left, entityData.right);

                ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>> parData =
                        storedEntities.computeIfAbsent(nextEntity, (it -> {
                            String parString = entityMap.get(it);
                            ArrayList<ImmutablePair<Integer, Integer>> places = getJumpPlaces(parString);
                            return ImmutablePair.of(parString, places);
                        }));

                curPar = useJumpPlaces(parData.left, parData.right);
//                volume *= 0.8;
//                volume *= 1 / (double)parData.right.size();

                if (start == 1) {
                    volume *= 1 / (1 + Math.log((double)parData.right.size()) + Math.log((double)entityData.right.size()));
                    counts.merge(nextEntity, volume, Double::sum);
                } else {
                    start = 1;
                }
//                System.out.println("YAY");
            }
        }

        Seq.seq(counts.entrySet())
                .sorted(Map.Entry::getValue)
                .reverse()
                .take(15)
                .forEach(entry -> System.out.println(entry.getKey() + " " + entry.getValue()));
    }


//    public void doJumps(String entity) {
//        System.out.println("Original: " + entity);
//        HashMap<String, Double> counts = new HashMap<>();
//        int nWalks = 8000;
//        int nSteps = 5;
//        for (int walk = 0; walk < nWalks; walk++) {
//            String curEntity = entity;
//            double volume = 1.0;
//
//            for (int step = 0; step < nSteps; step++) {
////                if (!storedEntities.contains(curEntity)) {
////                    storedEntities.putIfAbsent(curEntity, getJumpPlaces(parString));
////                }
//                ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>> parData =
//                        storedEntities.computeIfAbsent(curEntity, (it -> {
//                            String parString = entityMap.get(it);
//                            ArrayList<ImmutablePair<Integer, Integer>> places = getJumpPlaces(parString);
//                            return ImmutablePair.of(parString, places);
//                        }));
//
////                ArrayList<ImmutablePair<Integer, Integer>> parPlaces = storedEntities.get(curEntity);
////                ArrayList<ImmutablePair<Integer, Integer>> parPlaces = getJumpPlaces(parString);
//
//                String nextPar = useJumpPlaces(parData.left, parData.right);
//
//                ImmutablePair<String, ArrayList<ImmutablePair<Integer, Integer>>> entityData =
//                        storedEntities.computeIfAbsent(nextPar, (it -> {
//                            String entityString = parMap.get(it);
//                            ArrayList<ImmutablePair<Integer, Integer>> places = getJumpPlaces(entityString);
//                            return ImmutablePair.of(entityString, places);
//                        }));
//
//                curEntity = useJumpPlaces(entityData.left, entityData.right);
//                volume *= 1 / (1 + Math.log((double)parData.right.size()) + Math.log((double)entityData.right.size()));
////                volume *= 1 / (double)parData.right.size();
//
//                counts.merge(curEntity, volume, Double::sum);
////                System.out.println("YAY");
//            }
//        }
//
//        Seq.seq(counts.entrySet())
//                .sorted(Map.Entry::getValue)
//                .reverse()
//                .take(15)
//                .forEach(entry -> System.out.println(entry.getKey() + " " + entry.getValue()));
//    }

//    public void doJumps(String entity) {
//        HashMap<String, Integer> counts = new HashMap<>();
//        int nWalks = 800;
//        int nSteps = 5;
//        for (int walk = 0; walk < nWalks; walk++) {
//            String curEntity = entity;
//
//            for (int step = 0; step < nSteps; step++) {
//                String[] pars;
//                if (!storedEntities.contains(curEntity)) {
//                    pars = entityMap.get(curEntity).split(" ");
//                    storedEntities.put(curEntity, pars);
//                } else {
//                    pars = storedEntities.get(curEntity);
//                }
//
//                String nextPar = pars[rand.nextInt(pars.length)];
//                String[] entities;
//                if (!storedParagraphs.contains(nextPar)) {
//                    entities = parMap.get(nextPar).split(" ");
//                    storedParagraphs.put(nextPar, entities);
//                } else {
//                    entities = storedParagraphs.get(nextPar);
//                }
//                curEntity = entities[rand.nextInt(entities.length)];
//                counts.merge(curEntity, 1, Integer::sum);
//            }
//        }
//
//        Seq.seq(counts.entrySet())
//                .sorted(Map.Entry::getValue)
//                .reverse()
//                .take(10)
//                .forEach(entry -> System.out.println(entry.getKey() + " " + entry.getValue()));
//    }

    public void rerankTopDocs(TopDocs tops) {
        if (true) {
            try {
                Document doc = indexSearcher.doc(tops.scoreDocs[0].doc);
                String[] entities = doc.getValues("spotlight");
                if (entities.length > 0) {
                    System.out.println(doc.get("text"));
                    System.out.println("---------");
                    StringJoiner stringJoiner = new StringJoiner(" ");
                    for (String entity : entities) { stringJoiner.add(entity); }
                    System.out.println(stringJoiner.toString());
                    System.out.println("---------");

//                    doJumps(doc.get("paragraphid"));
                    System.out.println("Entity: " + entities[0]);
                    doJumps(entities[0]);
                }
            } catch (IOException e) {

            }
            return;
        }
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
//            if (!pm.entityMixture.isEmpty()) {
//                pm.score = 0.0;
//            }
            pm.entityMixture.forEach((k, v) -> pm.finalScore += sinks.get(k) * v);
            pm.score = Math.max(pm.score, pm.finalScore);
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

    public void writeTermMap(HashMap<String, Double> termMap, String entity) throws IOException {
        String out = Seq.seq(termMap.entrySet())
//                .filter(entry -> entry.getValue() >= 0.01)
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
        int failures = 0;
        while (end == 0) {
            counter++;
            int next = s.indexOf("$", cur + 1);
            if (next < 0 || cur < 0) {
                end = 1;
                next = s.length();
            }

            int space = s.indexOf(" ", cur);
            if (space < 0) {
                cur = next;
                continue;
            }

            try {
                String entity = s.substring(cur + 1, space);
                String value = s.substring(space + 1, next);
                mixture.merge(entity, Double.parseDouble(value), Double::sum);
                cur = next;
            } catch (StringIndexOutOfBoundsException e) {
                cur = next;
                failures++;
                if (failures > 1) {
                    System.out.println("Counter: " + counter);
                    break;
                }
                continue;
            }


        }
        return pairs;
    }


    public HashMap<String, Double> getEntityMixture(String[] entities) throws IOException {
        HashMap<String, Double> mixture = new HashMap<>();
        int counter = 0;
        for (String entity : entities) {
//            TermQuery tq = new TermQuery(new Term("term", entity));
//            String[] distribution;
//            TopDocs td = entitySearcher.search(tq, 1);
//            distribution = entitySearcher.doc(td.scoreDocs[0].doc).getValues("distribution");
            String wee = cmap.getOrDefault(entity, "");
            if (!wee.equals("")) {
                myTokenizer(wee, mixture);
                counter++;
            } else {
                System.out.println(entity);
            }


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
        IndexSearcher is = createIndexSearcher(args[1]);
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
                                ga.recordTerms(entity);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
//                            try {
//                                HashMap<String, Double> termMap = ga.getTermMap(entity);
//                                if (termMap.size() <= 200) {
//                                    ga.writeTermMap(termMap, entity);
//                                }
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
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
                        ga.recordTerms(entity);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    try {
//                        HashMap<String, Double> termMap = ga.getTermMap(entity);
//                        ga.writeTermMap(termMap, entity);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                });


        ArrayList<Integer> docIds = new ArrayList<>();
        int maxDocs = ga.indexSearcher.getIndexReader().maxDoc();
        for (int i = 0; i < maxDocs; i++) {
            docIds.add(i);
            if (docIds.size() >= 1000 || i == maxDocs - 1) {
                docIds.parallelStream()
                        .forEach(paragraph -> {
                                    try {
                                        ga.recordParagraphs(paragraph);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                        );
                docIds.clear();
                System.out.println(i);
            }
        }

        ga.db.close();
    }


    public static void main (String[] args) throws IOException {
        makeDB(args);


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
