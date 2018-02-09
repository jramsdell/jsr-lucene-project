# lucene-jsr-variant


##### Running from Bin

The may run the precompiled jar file located in bin/jsr-lucene.jar

##### Compiling Source Code
Alternatively, you may use maven to compile the source code and run the target jar.
With maven installed, run the following in the same directory as pom.xml:

mvn clean compile assembly:single

And then run the snapshot jar found in target/

##### Usage

This program has five modes: index, query, query_vector, query_bigram, and query_entity.
Index is used to index a paragraph corpus file, while query is used to search an indexed database using a cbor file as a query. The query_vector, query_bigram, and query_entity modes are variants of the query mode (see instructions below).

---

### Index Mode
To run the index mode, do:

java -jar jsr_lucene.jsr index indexType corpusCBOR IndexDirectory

Where:

**indexType** is one of:
 - **paragraph**: indexes the text of each paragraphs, entitites linked to each paragraph, and frequent bigrams in each paragraph.
 - **spotlight**: As **paragraph**, except also retrieved entities from DBPedia Spotlight server (see Spotlight section below).

**corpusCBOR**: the cbor file that will be used to build a Lucene index directory.

**indexDirectory**: path to the directory that the Lucene index will be created in.


#### Example
java -jar jsr_lucene.jar index paragraph dedup.articles-paragraphs.cbor myindex_directory/

#### DBPedia Spotlight
By specifying the **spotlight** indexType, additional entities will be linked to each paragraph using a DBPedia server that is hosted locally on the same machine. To run the server, do the following:

```bash
wget http://downloads.dbpedia-spotlight.org/spotlight/dbpedia-spotlight-0.7.1.jar
wget http://downloads.dbpedia-spotlight.org/2016-04/en/model/en.tar.gz
tar xzf en.tar.gz
java -jar dbpedia-spotlight-latest.jar en_2+2/ http://localhost:9310/jsr-spotlight
```

### Query Mode
java -jar jsr_lucene.jar query queryType indexDirectory queryCbor rankOutput

Where:
**queryType** is one of:
 - **page** (retrieves query results for each page)
 - **section** (retrieves query results for each section)
    
**indexDirectory**: path to the Lucene index directory to be used in the search.

**queryCbor**: path to the cbor file to be used for querying the indexed database.

**rankOutput**: name of the file to create to store the results of the query (used for trec-eval).

#### Example
java -jar jsr_lucene.jar query page myindex_directory/ train.pages.cbor-outlines.cbor results.tops

### Query Bigram Mode
As query, except this also makes use of the (10%) most frequent bigrams in each document.
The query's bigrams are compared to the most frequent bigrams of each document.

java -jar jsr_lucene.jar query_bigram queryType indexDirectory queryCbor rankOutput

Where:
**queryType** is one of:
 - **page** (retrieves query results for each page)
 - **section** (retrieves query results for each section)
    
**indexDirectory**: path to the Lucene index directory to be used in the search.

**queryCbor**: path to the cbor file to be used for querying the indexed database.

**rankOutput**: name of the file to create to store the results of the query (used for trec-eval).

#### Example
java -jar jsr_lucene.jar query_bigram page myindex_directory/ train.pages.cbor-outlines.cbor results.tops

### Query Entity Mode
As query, except this also compares the query's terms to the linked entities in each paragraph.

java -jar jsr_lucene.jar query_entity queryType indexDirectory queryCbor rankOutput

Where:
**queryType** is one of:
 - **page** (retrieves query results for each page)
 - **section** (retrieves query results for each section)
    
**indexDirectory**: path to the Lucene index directory to be used in the search.

**queryCbor**: path to the cbor file to be used for querying the indexed database.

**rankOutput**: name of the file to create to store the results of the query (used for trec-eval).

#### Example
java -jar jsr_lucene.jar query_entity page myindex_directory/ train.pages.cbor-outlines.cbor results.tops

### Query Vector Mode
After querying the top 100 documents, word vectors are used to rerank and rescore the results according to cosine similarity. The vectors used are GloVe's precomputed 50-dimensional word vectors. 
They can be found here: http://nlp.stanford.edu/data/glove.6B.zip

java -jar jsr_lucene.jar query_vector queryType indexDirectory wordVector queryCbor rankOutput

Where:
**queryType** is one of:
 - **page** (retrieves query results for each page)
 - **section** (retrieves query results for each section)
    
**indexDirectory**: path to the Lucene index directory to be used in the search.

**wordVectors**: path to the text file containing GloVe's 50D word vectors.

**queryCbor**: path to the cbor file to be used for querying the indexed database.

**rankOutput**: name of the file to create to store the results of the query (used for trec-eval).

#### Example
java -jar jsr_lucene.jar query_entity page myindex_directory/ glove.6B.50d.txt train.pages.cbor-outlines.cbor results.tops

