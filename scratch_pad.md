## Installation Instructions
A precompiled jar file can be found in bin/program.jar

You may also compile the source code by entering the following command while in the project directory:

```bash
mvn clean compile assembly:single
```

This will create an executable jar file in the target/ directory.

## Program Commands
The program is divided into the following subcommands:

#### Indexer (index)
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db


```bash
program.jar index corpus [--spotlight_folder ""] [--out "index"]
```
Where:

**corpus**: Is the paragraphCorpus.cbor to create the Lucene index from.

**--out**: Is the name of the directory that the Lucene index will be created in. Default: "index"

**--spotlight_folder**: Is the directory where a runnable DBPedia Spotlight Jar and model are located. If the folder does not contain the required files, the contents are automatically downloaded and unpacked to the folder. If no folder is specified, then entity-linking with Spotlight is skipped during indexing. 


#### Graph Builder (graph_builder)
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db. **This command may be skipped if you are using the pre-existing graph_database.db on the server.**


```bash
program.jar graph_builder index
```

Where **index** is the directory of the Lucene index.

#### Query Heading (query_heading)
Contains methods for querying based on headings and word embedding.

```bash
program.jar query_heading query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 - **just_the_page**: Section path query using only page name
 - **lowest_heading**: Section path query using only the lowest heading of the query.
 - **interior_heading**: Section path query using only the interior heading of the query.
 - **word_embedding**: Word embedding of the query headers.
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index.
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 
 
