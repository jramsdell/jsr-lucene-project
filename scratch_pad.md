
#### Indexer
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db


```bash
program index corpus [--spotlight_folder ""] [--out "index"]
```


Where:

**corpus**: Is the paragraphCorpus.cbor to create the Lucene index from.

**--out**: Is the name of the directory that the Lucene index will be created in. Default: "index"

**--spotlight_folder**: Is the directory where a runnable DBPedia Spotlight Jar and model are located. If the folder does not contain the required files, the contents are automatically downloaded and unpacked to the folder. If no folder is specified, then entity-linking with Spotlight is skipped during indexing. 


#### Graph Builder
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db


```bash
program graph_builder index
```

Where **index** is the directory of the Lucene index.


