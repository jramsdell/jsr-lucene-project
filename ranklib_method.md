# Ranklib Query and Ranklib Trainer Subcommands


##### Ranklib Query Command

This command runs a query using linear combinations of features obtained by methods described below.
The weights of the features have been trained using RankLib. When run, ranklib_query will output a trec_eval compatible run file (default is to "query_results.run")

```bash
program ranklib_query method index query [--out "query_results.run"] [--graph_database ""] 
```

Where:

**method**: Is the type of method to use when querying. The choices are:
 - **entity_similarity**
 - **average_query**
 - **split_sections**
 - **mixtures**
 - **lm_mercer**
 - **lm_dirichlet**
 - **combined**
 
 
 **index**: Is the location of the Lucene index directory.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **--out**: Is the name of the runfile to create after querying. Default: query_results.run
 
 **--graph_database**: This option is only used for the mixtures method. It specifies the location of the graph_database.db database.
 
 ##### Ranklib Trainer Command
 
 The trainer creates a RankLib compatible file by annotating the BM25 query results with features obtained by using methods described below. The trainer doesn't quite "train" the features yet: it is required that the outputted file be run with RankLib, and the resulting weights are manually assigned to the methods (these are the weights used by the RankLib Query command)
 
 ```bash
program ranklib_trainer method index query qrel [--out "query_results.run"] [--graph_database ""] 
```
 
 Where:

**method**: Are the same methods described in Ranklib Query
 
 **index**: Is the location of the Lucene index directory.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **qrel**: Is the relevancy file (.qrel) used to determine whether or not documents are revant.
 
 **--out**: Is the name of the runfile to create after querying. Default: query_results.run
 
 **--graph_database**: This option is only used for the mixtures method. It specifies the location of the graph_database.db database.
 
 
 
 ### Description of Methods
 Each of these methods score the Top 100 documents obtained by running BM25 on the concatenated section path against the index.
 For all methods, the score from BM25 is added as an additional feature (in addition to those created by the methods) and the weights are trained using RankLib. **The features (including BM25) were normalized by Z-score.**

#### entity_similary
The query string is first tokenized, and then the score of each paragraph is expressed as the average similiarity score of each query token to each entity in the paragraph. Two similarity metrics are considered in this method: Jaccard and JaroWinkler. The metrics were obtained from the Java library: https://github.com/tdebatty/java-string-similarity

#### average_query
The query string is first tokenized and turned into individual boolean queries. The score of each paragraph is expressed as the average score of these boolean queries (using BM25) against the text of the paragraph.

#### split_sections
The concatenated section query is split into separate sections. These are then scored individually (using BM25) against the text of each paragraph, and each section (when present) is treated as a separate feature.

#### mixtures
Each paragraph is assigned a distribution over entities with respect to a random walk model over a bipartite graph of entities and paragraphs. These distributions are mixed together based on the BM25 score from the query to the paragraph. The proportion of each distribution in the mixture is equal to the proportion of the paragraph's BM25 score over the total score of all paragraphs. The final distribution is used to rescore the paragraphs.

#### lm_mercer
This uses Lucene's LMJelinekMercerSimilarity metric in place of the BM25 similarity metric, and it is used to score each query against the paragraph's text.

#### lm_dirichlet
This uses Lucene's LMDirichletSimilarity metric in place of the BM25 similarity metric, and it is used to score each query against the paragraph's text.

#### combined
This method combines the following previous methods as separate features:



