package unh.edu.cs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;

/**
 * Used to query local spotlight server with a paragraph's content and retrieve a list of entities.
 */
public class EntityLinker {
    private final String url = "http://localhost:9310/jsr-spotlight/annotate";
    private final String data;

    EntityLinker(String content) {
        data = content;
    }

    // Queries DBpedia and returns a list of entities
    ArrayList<String> run() {
        ArrayList<String> entities = new ArrayList<>();

        try {
            // Connect to database, retrieve entity-linked urls
            Document doc = Jsoup.connect(url)
                    .data("text", data)
                    .post();
            Elements links = doc.select("a[href]");

            // Parse urls, returning only the last word of the url (after the last /)
            for (Element e : links) {
                String title = e.attr("title");
                title = title.substring(title.lastIndexOf("/") + 1);
                entities.add(title);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return entities;
    }

    public static void main(String[] args) throws IOException {
        EntityLinker entityLinker = new EntityLinker("I think computer science is mostly okay.");
        ArrayList<String> entities = entityLinker.run();
        for (String entity : entities) {
            System.out.println(entity);
        }
    }
}
