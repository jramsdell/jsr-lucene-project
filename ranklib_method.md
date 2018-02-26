# Ranklib Query and Ranklib Train Subcommands


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
 
 ### Description of Methods

