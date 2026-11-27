package ai.frameflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptPlannerTest {
    @Test
    fun narrationTimelineCreatesOneBeatPerSecond() {
        val script = (1..120).joinToString(" ") { "word$it" }
        val beats = ScriptPlanner.plan(script, 60_000L)
        assertEquals(60, beats.size)
        assertEquals(1, beats.first().second)
        assertEquals(60, beats.last().second)
        assertEquals(60_000L, beats.last().endMs)
        assertTrue(beats.all { it.endMs > it.startMs })
    }

    @Test
    fun everySecondUsesItsCompleteCurrentSentenceNotOnlyTheCaptionFragment() {
        val script = "A girl finds a red bicycle. She rides it through the old town. Rain begins and she reaches home."
        val beats = ScriptPlanner.plan(script, 6_000L)
        assertEquals(6, beats.size)
        assertTrue(beats.first().sourceWords.contains("bicycle"))
        assertTrue(beats.last().sourceWords.contains("home"))
        assertTrue(beats.all { it.sourceWords.split(Regex("\\s+")).size >= 4 })
        assertTrue(beats.all { it.sentenceProgress in 0f..1f })
    }

    @Test
    fun eachPromptCarriesFullStoryAndAdvancesTheCurrentMoment() {
        val script = "A girl finds a red bicycle. She rides it through the old town. Rain begins and she reaches home."
        val beats = ScriptPlanner.plan(script, 5_000L)
        val prompts = beats.mapIndexed { index, _ -> ScriptPlanner.visualPrompt(beats, index) }
        assertEquals(5, prompts.size)
        assertTrue(prompts.all { it.contains("FULL STORY CONTEXT") })
        assertTrue(prompts.all { it.contains("STRICT VISUAL CONTINUITY") })
        assertTrue(prompts.first().contains("OPENING FRAME"))
        assertTrue(prompts.drop(1).all { it.contains("CONTINUATION FRAME") })
        assertNotEquals(prompts[0], prompts[1])
    }

    @Test
    fun lastFrameCanBeShorterThanOneSecond() {
        val beats = ScriptPlanner.plan("one two three four five", 2_350L)
        assertEquals(3, beats.size)
        assertEquals(350L, beats.last().endMs - beats.last().startMs)
    }

    @Test
    fun refusesTimelinesLongerThanThirtyMinutesInsteadOfSilentlyDroppingFrames() {
        val error = runCatching { ScriptPlanner.plan("A story.", 1_801_000L) }.exceptionOrNull()
        assertTrue(error?.message?.contains("30 minutes") == true)
    }
}
