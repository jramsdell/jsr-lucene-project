package unh.edu.cs;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jooq.lambda.Seq;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tokenizes a given string and returns a list of the top 10% most frequent bigrams (2-letter strings).
 */
public class BigramAnalyzer {
    private final NGramTokenizer tokenizer;

    BigramAnalyzer(String content) {
        tokenizer = new NGramTokenizer(2, 2);
        tokenizer.setReader(new StringReader(content));
    }

    // Returns bigram score (as depicted in the Search and Indexing Variations)
    Double getScore(String bigram, HashMap<String, Double> bigramCounts, HashMap<Character, Double> monogramCounts) {
        Double pBigram = bigramCounts.get(bigram);
        Double p1 = monogramCounts.get(bigram.charAt(0));
        Double p2 = monogramCounts.get(bigram.charAt(1));

        try {
            return (pBigram / (p1 * p2));
        } catch (NullPointerException e) {
            return 0.0;
        }
    }

    // Tokenizes string that BigramAnalyzer was initialized with and returns list of frequent bigrams
    public List<String> run() throws IOException {
        HashMap<String, Double> bigramCounts = new HashMap<>();
        HashMap<Character, Double> monogramCounts = new HashMap<>();
        int totalBigrams = 0;

        NGramTokenFilter filter = new NGramTokenFilter(tokenizer, 2, 2);
        CharTermAttribute charTerm = filter.addAttribute(CharTermAttribute.class);
        filter.reset();

        // Count number of times each bigram and its two terms occurs
        while (filter.incrementToken()) {
            String token = charTerm.toString();
            totalBigrams += 1;

            bigramCounts.put(token, bigramCounts.getOrDefault(token, 0.0) + 1.0);
            monogramCounts.put(token.charAt(0),
                    monogramCounts.getOrDefault(token.charAt(1), 0.0) + 1.0);
        }

        // Convert to probabilities
        final int finalCounts = totalBigrams;

        bigramCounts.entrySet()
                .forEach(entry -> entry.setValue(entry.getValue() / finalCounts));
        monogramCounts.entrySet()
                .forEach(entry -> entry.setValue(entry.getValue() / finalCounts * 2));

        // Create list sorted by bigram scores
        return Seq.seq(bigramCounts.entrySet())
                .map(entry ->
                        new MutablePair<String, Double>(
                                entry.getKey(), getScore(entry.getKey(), bigramCounts, monogramCounts)) {})
                .sorted(MutablePair::getRight)
                .reverse()                          // Sorted by ascending, so need to reverse
                .take(finalCounts / 10)             // Only take top 10% of most frequent bigrams
                .map(MutablePair::getLeft)
                .toList();

    }

    public static void main(String[] args) throws IOException {
        BigramAnalyzer ba = new BigramAnalyzer("This is a test. I wonder if it will work.");
        List<String> field = ba.run();
    }

}

