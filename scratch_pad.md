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
#### Abstract Indexer (abstract_indexer)
Given the location of unprocessAllButBenchmark page corpus, extracts page names (used to represent an entity) and the first three paragraphs (used to represent the abstract of the entity). Unigrams, bigrams, and windowed bigrams are also indexed for each of the entity abstracts.

The resulting Lucene index will be named "abstract" and is created in the current working directory. **Note that an already indexed version of abstract can be found on the server at: /trec_data/team_1/abstract**

```bash
program.jar abstract_indexer corpus
```

Where **corpus** is the location of the allButBenchmark corpus. 
 - A copy of the corpus is located on the server at: /trec_data/unprocessedAllButBenchmark/unprocessedAllButBenchmark.cbor
___
#### Gram Indexer (gram_indexer)
Given location of paragraphCorpus, this indexes stemmed unigrams, bigrams, and windowed bigrams for 33% fo the documents in the corpus (I did not do more due to size constraints on the erver). 

The resulting index is named "gram" and is created in the current working directory. **Note that an already indexed version of abstract can be found on the server at: /trec_data/team_1/gram**

```bash
program.jar gram_indexer corpus
```

Where **corpus** is the location of the paragraphCorpus. 
 - A copy of the corpus is located on the server at: /trec_data/paragraphCorpus/dedup.articles-paragraphs.cbor
 ___
#### Hyperlink Indexer (hyperlink_indexer)
Given location of unprocessedAllButBenchmark page corpus, parses pages for anchor text and links and makes not of the frequencies of entities given entity mentions. The resulting database is stored in the working directory and is named "entity_mentions.db". 

**Note: there is an already indexed version of this database on the server and it is located at: /trec_data/team_1/entity_mentions.db**

```bash
program.jar hyperlink_indexer corpus
```

Where **corpus** is the location of the allButBenchmark corpus. 
 - A copy of the corpus is located on the server at: /trec_data/unprocessedAllButBenchmark/unprocessedAllButBenchmark.cbor
___
#### Feature Selection (feature_selection)
Given a Ranklib-compatible feature file, this tool will either perform subset selection (to find the best features), or report pairwise combinations of features (used in determining alpha parameters for my SDM and Abstract SDM methods). 

**Note that this command requires a path to a RankLib jar file. There is such a file on the server at: /trec_data/team_1/RankLib-2.1-patched.jar**

```bash
program.jar feature_Selection [--features FEATURES] ranklib_jar method
```

Where:

**ranklib_jar**: is the location of a RankLib jar file. One is available at /trec_data/team_1/RankLib-2.1-patched.jar
**method**: Is one of the following:
 - **alpha_selection**: Runs each feature pairwise with feature 1 (assumed to be BM25 feature) and selects the pair with the best MAP. This is used to determine the alpha parameter for SDM and Abstract SDM, where each of the features are just copies of the SDM at different alpha values.
 - **subset_selection**: Attempts to do forward subset selection, where features are added to a set if they raise the MAP score by a significant amount. Prints the final set and the trained model weights.

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
 
 ## Description of Primary RanklibQuery Methods and Training
 Each of these methods score the Top 100 documents obtained by running BM25 on the concatenated section path against the index.
 For all individual methods, the score from BM25 is added as an additional feature (in addition to those created by the methods) and the weights are trained using RankLib. **The features (including BM25) were normalized by Z-score.**

#### sdm
This represents my (hopefully decent) attempt at implementing the SDM model for the paragraphCorpus. Stemmed unigrams, bigrams, and windowed bigrams were indexed for 33% of the corpus (could not do more due to space limitations). Dirichlet smoothing was used for the language models (to do this, I ran RankLib a bunch of times with different versions of alpha (see KotlinFeatureSelector and the **sdm_alpha** method in ranklib_train) to determine what values of alpha work best). The three -gram scores were also waited according to training with RankLib (this is the **sdm_components** method in ranklib_train).

#### abstract_sdm
This SDM model was trained on the abstract index and represents an SDM for entities. The way in which this is used is as follows: using the given query string, the abstract database is queried (BM25) and the top 20 results are considered the "entities relevant to the query". For each of these, the abstracts are used to create -gram models and the entities are scored according to their likelihood given the query. The final score for each document is expressed as the average likelihood score given the relevant entities it was annotated with (using Spotlight).

The -gram weights were trained using the **abstract_sdm_components** method in ranklib_train, and the alpha components were estimated using the **abstract_alpha** method in RanklibTrain.

#### average_abstract
For each query, the "relevant" entities of the query are determined by querying the entity abstract database. The top 20 results are considered "relevant". Each document is scored based on average score of the relevant entities it contains. 


#### section_component
I do not neccessarily consider this a new method, but what was done here is that the query was split into its sections, and the documents were scored according to BM25 (where the section is the new query). A weighted combination of these section scores was learned using ranklib (see **section_path** in ranklib_train) and then this was turned into a new feature that could be combined with other features. It appears to be strictly superior to BM25 in the tests.

#### hyperlink
The likelihood of an entity given a query is approximated using the allButBenchmark page corpus, in which the anchor text and entity links are used to generate a probability of entity given anchor text. This is used to determine relevant entities (given the query) and scores the documents (linked using Spotlight) according to the log likelihood of these entity mentions. (It's basically the hyperlink popularity method)


#### combined

