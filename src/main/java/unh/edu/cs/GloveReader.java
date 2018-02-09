package unh.edu.cs;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jooq.lambda.Seq;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses and stores 50D GloVe word vectors. Used in word vector variation.
 */
public class GloveReader {
    final Map<String, INDArray> index;

    private static ImmutablePair<String, INDArray> getEntry(String line) {
        String[] elements = line.split(" ");
        double[] result = Arrays.stream(elements)
                            .skip(1)
                            .mapToDouble(Double::parseDouble)
                            .toArray();

        INDArray array = Nd4j.create(result);
        return new ImmutablePair<String, INDArray>(elements[0], array);
    }

    GloveReader(String path) throws IOException {
        index = Seq.seq(Files.lines(Paths.get(path)))
                .map(GloveReader::getEntry)
                .toMap(ImmutablePair::getLeft, ImmutablePair::getRight);
    }

    // Takes tokens, finds corresponding word vectors, and takes the average of these vectors
    public INDArray getWordVector(List<String> words) {
        Double count = 0.0;
        INDArray array = Nd4j.zeros(50);

        for (String word : words) {
            INDArray result = index.get(word);
            if (result != null) {
                count += 1.0;
                array.addi(result);
            }
        }
        array.divi(count);
        return array;
    }

    // Returns cosine similarity between two vectors
    public Double getCosineSim(INDArray a1, INDArray a2) {
        return Transforms.cosineSim(a1, a2);
    }

    public static void main(String[] args) throws IOException {
        GloveReader gr = new GloveReader("glove.6B.50d.txt");
        ArrayList<String> ls1 = new ArrayList<>();
        ls1.add("hi");
        ls1.add("which");
        ls1.add("were");

        ArrayList<String> ls2 = new ArrayList<>();
        ls2.add("hi");
        ls2.add("which");
        ls2.add("whatever");
        ls2.add("industry");

        INDArray id1 = gr.getWordVector(ls1);
        INDArray id2 = gr.getWordVector(ls2);
        Double result = Transforms.cosineSim(id1, id2);
        System.out.println(result);
    }
}

