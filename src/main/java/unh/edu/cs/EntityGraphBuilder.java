package unh.edu.cs;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jooq.lambda.Seq;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class EntityGraphBuilder {
    private IndexReader indexReader;
    Random rand = new Random();
    HashMap<String, ArrayList<String>> parToEntity = new HashMap<>();
    HashMap<String, ArrayList<String>> entityToPar = new HashMap<>();
    HashMap<String, HashMap<String, Double>> parModel = new HashMap<>();
    HashMap<String, HashMap<String, Double>> entityModel = new HashMap<>();

    EntityGraphBuilder(String indexLocation) throws IOException {
        createIndexReader(indexLocation);

    }

    private void createIndexReader(String iPath) throws IOException {
        Path indexPath = Paths.get(iPath);
        Directory indexDir = FSDirectory.open(indexPath);
        indexReader = DirectoryReader.open(indexDir);
    }

    private void addToMap(HashMap<String, ArrayList<String>> map, String key, String val) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<String>());
        }
        map.get(key).add(val);
    }


    private void addToGraph(int docID) {
        // Oh my god I hate Java
        try {
            Document doc = indexReader.document(docID);
            String[] entities = doc.getValues("spotlight");
            String id = doc.get("paragraphid");
            for (String entity: entities) {
                addToMap(entityToPar, entity, id);
                addToMap(parToEntity, id, entity);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getRandomElement(HashMap<String, ArrayList<String>> map, String key) {
        ArrayList<String> list = map.getOrDefault(key, null);
        if (list != null) {
            return list.get(rand.nextInt(list.size()));
        } else {
            return "";
        }
    }

    private void doWalks(String id) {
        int nSteps = 8;
        int nWalks = 100;

        HashMap<String, Double> entityCounts = new HashMap<>();
        HashMap<String, Double> parCounts = new HashMap<>();

        for (int i = 0; i < nWalks; i++) {
            String par = id;

            for (int j = 0; j < nSteps; j++) {
                String entity = getRandomElement(parToEntity, par);
                par = getRandomElement(entityToPar, entity);
                entityCounts.merge(entity, 1.0, Double::sum);
                parCounts.merge(par, 1.0, Double::sum);
            }
        }

        Double total = (double) (nSteps * nWalks);
        entityCounts.replaceAll((k,v) -> v / total);
        parCounts.replaceAll((k,v) -> v / total);
        parModel.put(id, parCounts);
        entityModel.put(id, entityCounts);
    }

    public void buildGraph() {
        Seq.range(0, indexReader.maxDoc())
                .forEach(this::addToGraph);
    }

    public void buildModel() {
        Seq.seq(parToEntity.keySet())
                .forEach(this::doWalks);
    }


    public void writeModels() throws IOException {
        File out = new File("models.txt");
        FileWriter writer = new FileWriter(out);

        for (String key : parModel.keySet()) {
            writer.write(key + "\n");
            ArrayList<String> keys = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();

            parModel.forEach((k,v) -> {
                keys.add(k);
                values.add(v.toString());
            });

            writer.write(String.join(" ", keys) + "\n");
            writer.write(String.join(" ", values) + "\n");

            keys.clear();
            values.clear();

            entityModel.forEach((k,v) -> {
                keys.add(k);
                values.add(v.toString());
            });
            writer.write(String.join(" ", keys) + "\n");
            writer.write(String.join(" ", values) + "\n");
        }
    }

    public static void main (String[] args) throws IOException {
        EntityGraphBuilder eb = new EntityGraphBuilder(args[0]);
        eb.buildGraph();
        eb.buildModel();
        eb.writeModels();
    }
}
