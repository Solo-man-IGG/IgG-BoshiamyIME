package tw.igg.boshiamyime.model

enum class KeyboardMode(val displayName: String) {
    T9("T9"),
    QWERTY("QWERTY"),
    ZHUYIN("注音"),
    SYMBOL("符號"),
    EMOJI("Emoji")
}

enum class BoshiamyMode(val displayName: String, val tableFile: String) {
    STANDARD("標準", "standard/dictionary.json"),
    SIMPLIFIED("簡速", "simplified/dictionary.json"),
    ETEN("倚天", "eten/dictionary.json")
}
