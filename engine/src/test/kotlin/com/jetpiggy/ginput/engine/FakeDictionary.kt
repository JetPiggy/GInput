package com.jetpiggy.ginput.engine

class FakeDictionary(
    private val characters: Map<Pair<Scheme, String>, List<CharacterEntry>> = emptyMap(),
    private val words: Map<String, List<WordEntry>> = emptyMap(),
    /** 英文詞和它的詞頻，數值越小越常用（和英文詞庫的 frequency 欄一樣）。 */
    private val english: Map<String, Int> = emptyMap(),
    private val jyutping: List<JyutpingEntry> = emptyList(),
) : Dictionary {
    override fun characters(scheme: Scheme, code: String): List<CharacterEntry> =
        characters[scheme to code].orEmpty()

    override fun wordsAfter(head: String): List<WordEntry> = words[head].orEmpty()

    override fun englishWords(prefix: String, limit: Int): List<String> =
        english.entries
            .filter { it.key.lowercase().replace("'", "").startsWith(prefix) }
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }.thenBy { it.key })
            .map { it.key }
            .take(limit)

    override fun jyutpingSyllables(): Set<String> =
        jyutping.flatMapTo(LinkedHashSet()) { it.syllables.split(' ') }

    override fun jyutping(syllables: String): List<JyutpingEntry> = jyutping.filter { it.syllables == syllables }

    override fun jyutpingStartingWith(syllables: String): List<JyutpingEntry> =
        jyutping.filter { it.syllables == syllables || it.syllables.startsWith("$syllables ") }
}
