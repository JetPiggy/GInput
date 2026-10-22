plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
}

tasks.test {
    // 用 tools/dictgen 建置、App 實際內置的字庫跑測試。
    val dictionary = rootProject.file("app/src/main/assets/dict/ginput-6.db")
    val english = rootProject.file("app/src/main/assets/dict/english-1.db")
    val emojiKeywords = rootProject.file("app/src/main/assets/emoji/keywords.tsv")
    inputs.files(dictionary, english, emojiKeywords)
    systemProperty("ginput.dictionary", dictionary.absolutePath)
    systemProperty("ginput.english", english.absolutePath)
    systemProperty("ginput.emojiKeywords", emojiKeywords.absolutePath)
}
