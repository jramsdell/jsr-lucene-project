package unh.edu.cs

class KotlinStatsParser(indexLocation: String) {
    val indexSearcher = getIndexSearcher(indexLocation)

    fun stats() {
        val ir = indexSearcher.indexReader
        val nDocs = ir.maxDoc()
        println(nDocs)
    }
}