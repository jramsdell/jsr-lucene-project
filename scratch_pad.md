## Results and Report
The newest results for prototype 2 can be found in the results_prototype2 directory. The results are as follows

**results_prototype2/report.pdf**: The group report for the current prototype.

**results_prototype2/jordan/**: Runfiles and trec eval stats for each of Jordan's methods

**results_prototype2/kevin/**: Trec eval stats and run files for Kevin's methods.

**results_prototype2/bindu/**: Trec eval stats and run files for Bindu's methods.
 
 **results_prototype2/public_test/** Compressed run files for runs on benchmark_kY1_public_query test data.

___
## Installation Instructions
A precompiled jar file can be found in bin/program.jar

You may also compile the source code by entering the following command or by running ./compile.sh while in the project directory:

```bash
mvn clean compile assembly:single
```

This will create an executable jar file in the target/ directory.
___
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

___
#### Graph Builder (graph_builder)
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.  
The graph is stored in the MapDB database: graph_database.db. **This command may be skipped if you are using the pre-existing graph_database.db on the server.**  


```bash
program.jar graph_builder index
```

Where **index** is the directory of the Lucene index.
___
#### Heading Weights Variation:  There are mainly 3 type of heading weight variation.
BM25 Query of just the page name.  
BM25 Query of just the lowest heading.  
BM25 Query of the interior headings.   
Contains methods for querying based on headings.  

```bash
program.jar query_heading query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 - **just_the_page**: using only page name as query id.
 - **lowest_heading** : using only the lowest heading of the query.
 - **interior_heading**: using only the interior heading of the query just like  interior section path.
 - **word_embedding**: Using baseline index and BM25 section path query to retrieve a candidate set (top 100) and then reranking the      candidate set as follows.
    * Defined query vector as the average of word vectors of all query terms
    * Define document vector as average of word vectors of all document terms
    * Ranking by cosine similarity of of query and document vector.
  
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index.
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 ___
#### Query Expansion Variation:
BM25 Query of the page name with expanded query.  
BM25 Query of the sections path with expanded query.  

```bash
program.jar query_expansion query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index.
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 ___
#### Frequent Bigram Variation:
Combine BM25 page name query against content field and the Bigram query against bigram field.  
Combine BM25 section path query against content field and the Bigram query against bigram field.  

```bash
program.jar frequent_bigram query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index.
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 ___
 ##### Ranklib Query (ranklib_quer)

This command runs a query using linear combinations of features obtained by methods described in the methodology section further down.
The weights of the features have been trained using RankLib. When run, ranklib_query will output a trec_eval compatible run file (default is to "query_results.run")

```bash
ranklib_query [-h] [--out OUT] [--hyperlink_database HYPERLINK_DATABASE] [--abstract_index ABSTRACT_INDEX] [--gram_index GRAM_INDEX] method index query
```

Where:
average_abstract,combined,abstract_sdm,sdm_components,hyperlink,sdm,section_component

**method**: Is the type of method to use when querying. The choices are:
 - **abstract_sdm**: Query using trained abstract SDM model (see full description later)
 - **sdm**: Query using trained SDM model (see full description later)
 - **section_component**: Query using a trained version of BM25 (reweighted according to section paths)
 - **average_abstract**: Query using trained average abstract model (see full description later)
 - **hyperlink**: Query using trained hyperlink model (see full description later)
 - **combined**: Query using weighted combination of methods (see full description later)
 
 
 **index**: Is the location of the Lucene index directory. Should be /trec_data/team_1/myindex if you do not want to generate a new Lucene index from scratch.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **--out**: Is the name of the runfile to create after querying. Default: query_results.run
 
 **--hyperlink_database**: Points to location of hyperlink database (see hyperlink method). This defaults to the database located on the server at: /trec_data/team_1/entity_mentions.db
 
 **--abstract_index**: Location of the Lucene index for the entity abstracts. This defaults to the following location on the server: /trec_data/team_1/abstract/
 
 **--gram_index**: Location of gram index (stores -gram models for SDM). This defaults to the following location on the server: /trec_data/team_1/gram
___ 
 ##### Ranklib Trainer (ranklib_trainer)
 
 The trainer creates a RankLib compatible file by taking the top100 BM25 results and scoring the documents according to the methods below. 
 
 ```bash
program.jar ranklib_trainer [--out OUT] [--hyperlink_database HYPERLINK_DATABASE] [--abstract_index ABSTRACT_INDEX] [--gram_index GRAM_INDEX] method index query qrel
```
 
 Where:

**method**: Is one of the following methods:
 (Primary Methods)
 - **abstract_sdm**: Training for abstract SDM model (see full description later)
 - **sdm**: Training for SDM model (see full description later)
 - **string_similarities**: Training for string_similarity model (see full description later)
 - **average_abstract**: Training for average_abstract model (see full description later)
 - **hyperlink**: Training for hyperlink model (see full description later)
 - **combined**: All methods are combined in this training method, and the FeatureSelection class is used to do subset selection on the features to determine which are the best features to be combined.
 
 (Secondary Methods)
  - **sdm_alpha**: Generates SDM at various alpha values (used to determine best alpha parameter)
  - **abstract_alpha**: Generates abstract SDM at various alpha values (used to determine best alpha paramter)
  - **similarity_section**: Weighted section version of the string_similarities method
  - **section_path**: Learns weights for section paths (BM25): used to create a single section path feature.
  - **abstract_sdm_components**: Learns abstract SDM weights for unigram/bigram/windowed bigram scores
  - **sdm_components**: Learns SDM weights for unigram/bigram/windowed bigram scores
 
 
 **index**: Is the location of the Lucene index directory. Should be /trec_data/team_1/myindex if you do not want to generate a new Lucene index from scratch.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **qrel**: Is the relevancy file (.qrel) used to determine whether or not documents are revant.
 
 **--out**: Is the name of the runfile to create after querying. Default: ranklib_features.txt
 
 **--hyperlink_database**: Points to location of hyperlink database (see hyperlink method). This defaults to the database located on the server at: /trec_data/team_1/entity_mentions.db
 
 **--abstract_index**: Location of the Lucene index for the entity abstracts. This defaults to the following location on the server: /trec_data/team_1/abstract/
 
 **--gram_index**: Location of gram index (stores -gram models for SDM). This defaults to the following location on the server: /trec_data/team_1/gram
 ___
 
 ## Description of RanklibTrainer / RanklibQuery Methods
 Each of these methods score the Top 100 documents obtained by running BM25 on the concatenated section path against the index.
 For all methods, the score from BM25 is added as an additional feature (in addition to those created by the methods) and the weights are trained using RankLib. **The features (including BM25) were normalized by Z-score.**

#### entity_similarity
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
This method combines the following previous methods as separate features: BM25, LMDirichletSimilarity (mu 2000), entity_similarity (only using Jaccard string similarity), and first and second heading scores (i.e. pagename/header1/header2)/
