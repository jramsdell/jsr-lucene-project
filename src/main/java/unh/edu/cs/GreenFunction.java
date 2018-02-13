package unh.edu.cs;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jooq.lambda.Seq;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class GreenFunction {
    private GraphAnalyzer graphAnalyzer;
    private final String root;
    private final Double transitionChance;
    private final int maxSteps;
    private final int nPoints;
    private String[] points;
    private Random rand;
    public final ArrayList<HashMap<String, Double>> distributions = new ArrayList<>();
    private HashMap<String, Double> curDist = new HashMap<>();

    GreenFunction(GraphAnalyzer graphAnalyzer,
                  String root, Double transitionChance, int maxSteps, int nPoints) {
        this.graphAnalyzer = graphAnalyzer;
        this.root = root;
        this.transitionChance = transitionChance;
        this.maxSteps = maxSteps;
        this.nPoints = nPoints;
        points = new String[nPoints];
        Arrays.fill(points, root);
        rand = new Random();
    }

    private void simulateStep() {
        HashMap<String, Double> nextDist = new HashMap<>(curDist);
        for (int i = 0; i < points.length; i++) {
            if (rand.nextDouble() <= transitionChance) {
                ImmutablePair<String, String> p = graphAnalyzer.transitionEntity(points[i]);
                points[i] = p.left;
                nextDist.merge(p.right, 1.0, Double::sum);
            }
        }
        distributions.add(nextDist);
        curDist = nextDist;
    }

    private void normalizeDistributions() {
        distributions.forEach( dist -> {
            Double total = Seq.seq(dist.values()).sumDouble(it -> it);
            dist.replaceAll((k,v) -> v / total);
        });
    }

    private Double getTotalVariationDistance(HashMap<String, Double> d1, HashMap<String, Double> d2) {
        HashSet<String> keys = new HashSet<>();
        keys.addAll(d1.keySet());
        keys.addAll(d2.keySet());
        return 0.5 * Seq.seq(keys).sumDouble(
                it -> Math.abs(d1.getOrDefault(it, 0.0) - d2.getOrDefault(it, 0.0)));
    }


    public Double getDistance(GreenFunction gf, Double epsilon) {
        Optional<Integer> modulus = Seq.range(0, distributions.size())
                .findFirst(it -> {
                    Double dist = getTotalVariationDistance(distributions.get(it), gf.distributions.get(it));
                    System.out.println(dist);
                    return dist < epsilon;
                });

        return modulus
                .map(integer -> (double) integer * transitionChance)
                .orElse(Double.NaN);
    }

    public void simulate() {
        for (int i = 0; i < maxSteps; i++) {
            simulateStep();
        }

        normalizeDistributions();
    }
}
