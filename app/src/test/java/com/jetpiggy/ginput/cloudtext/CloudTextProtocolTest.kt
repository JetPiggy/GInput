package com.jetpiggy.ginput.cloudtext

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudTextProtocolTest {

    @Test
    fun requestHasOnlyTheTaskAndTheText() {
        val text = "今日好熱\n佢話：\"開冷氣啦\""
        val json = JSONObject(CloudTextProtocol.request(TextTask.ToEnglish, text))
        assertEquals(setOf("task", "text"), json.keys().asSequence().toSet())
        assertEquals("to-en", json.getString("task"))
        assertEquals(text, json.getString("text"))
    }

    @Test
    fun resultIsReadFromTheReply() {
        assertEquals("It's hot today.", CloudTextProtocol.result("""{"result": "It's hot today."}"""))
    }

    @Test
    fun unusableRepliesHaveNoResult() {
        val replies = listOf("", "busy", "[]", "{}", """{"result": null}""", """{"result": " \n"}""", """{"result": 42}""")
        for (body in replies) assertNull(body, CloudTextProtocol.result(body))
    }

    @Test
    fun taskCodesMatchTheWorker() {
        // 單元測試在 app 資料夾執行。
        val prompts = File("../backend/ai/src/prompts.ts").readText()
        val tasks = prompts.substringAfter("export const TASKS = {").substringBefore("\n};")
        val codes = Regex("""^ {2}"?([a-z-]+)"?:""", RegexOption.MULTILINE).findAll(tasks).map { it.groupValues[1] }
        assertEquals(codes.toSet(), TextTask.entries.map { it.code }.toSet())
    }

    @Test
    fun eachPanelListsItsOwnTasks() {
        assertEquals(
            listOf(TextTask.ToEnglish, TextTask.ToWritten, TextTask.ToColloquial),
            TextTask.of(CloudTool.Translation),
        )
        assertEquals(
            listOf(TextTask.Written, TextTask.Colloquial, TextTask.Polite, TextTask.Casual, TextTask.Fix),
            TextTask.of(CloudTool.Rewrite),
        )
    }
}
