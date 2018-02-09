package unh.edu.cs;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

class LuceneIndexBuilder {
    private IndexWriter indexWriter;
    private final String corpusFile;
    private final String indexOutLocation;
    private final IndexType indexType;
    private AtomicInteger commitCount = new AtomicInteger(0);

    LuceneIndexBuilder(IndexType iType, String cFile, String iOut) {
        indexType = iType;
        corpusFile = cFile;
        indexOutLocation = iOut;
    }

    void initializeWriter() throws IOException {
        Path indexPath = Paths.get(indexOutLocation);
        Directory indexOutDirectory = FSDirectory.open(indexPath);
        IndexWriterConfig indexConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriter = new IndexWriter(indexOutDirectory, indexConfig);
    }


    private void addDocument(Document doc)  {
        try {
            indexWriter.addDocument(doc);
            if (commitCount.getAndIncrement() % 10000 == 0) {
                indexWriter.commit();
                System.out.print(".");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void run() throws IOException {
        final FileInputStream fStream = new FileInputStream(new File(corpusFile));
        Iterable<Data.Paragraph> ip = DeserializeData.iterableParagraphs(fStream);

        // Parallelized stream for adding documents to indexed database
        StreamSupport.stream(ip.spliterator(), true)
                .parallel()
                .map(this::createDocument)
                .forEach(this::addDocument);
        indexWriter.close();
    }


    private Document createDocument(Data.Paragraph p) {
        final Document doc = new Document();
        final String content = p.getTextOnly();
        doc.add(new TextField("text", content, Field.Store.YES));
        doc.add(new StringField("paragraphid", p.getParaId(), Field.Store.YES));

        // index stored entities
        for (String entity : p.getEntitiesOnly()) {
            doc.add(new StringField("entity", entity, Field.Store.YES));
        }

        // index DBpedia entities
        if (indexType == IndexType.SPOTLIGHT) {
            // Query local spotlight server using EntityLinker
            EntityLinker entityLinker = new EntityLinker(content);
            for (String entity : entityLinker.run()) {
                doc.add(new StringField("spotlight", entity, Field.Store.YES));
            }
        }

        // index top 10% bigrams
        BigramAnalyzer bigramAnalyzer = new BigramAnalyzer(content);
        try {
            for (String bigram : bigramAnalyzer.run()) {
                doc.add(new StringField("bigram", bigram, Field.Store.YES));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc;
    }

}

