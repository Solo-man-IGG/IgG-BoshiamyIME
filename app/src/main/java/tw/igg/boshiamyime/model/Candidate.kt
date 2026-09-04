package tw.igg.boshiamyime.model

data class Candidate(
    val code: String,
    val char: String,
    val frequency: Int = 0,
    val isExactMatch: Boolean = false
)
