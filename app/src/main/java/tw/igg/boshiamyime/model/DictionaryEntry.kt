package tw.igg.boshiamyime.model

data class DictionaryEntry(
    val code: String,
    val t9: String,
    val char: String,
    val frequency: Int = 0,
    val phrases: List<String> = emptyList()
)
