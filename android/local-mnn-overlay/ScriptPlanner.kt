package ai.frameflow.app

/**
 * Maps measured narration time to one image per second. Each prompt is grounded
 * in a complete sentence-level story beat; the renderer uses the prior frame
 * as its img2img reference to maintain visual continuity.
 */
object ScriptPlanner {
    data class Beat(
        val second: Int,
        val startMs: Long,
        val endMs: Long,
        val caption: String,
        val sourceWords: String,
        val sentenceIndex: Int,
        val sentenceProgress: Float,
        val momentPosition: String
    )

    private data class StoryBeat(val text: String, val words: List<String>)

    private const val MAX_STORY_SECONDS = 1_800
    private val sentenceBoundary = Regex("(?<=[.!?;:।！？,])\\s+|\\n+")

    fun plan(script: String, durationMs: Long): List<Beat> {
        require(script.isNotBlank()) { "Write a script first." }
        require(durationMs >= 1_000L) { "Narration must be at least one second long." }

        val storyBeats = script.trim()
            .split(sentenceBoundary)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { text -> StoryBeat(text, text.split(Regex("\\s+")).filter(String::isNotBlank)) }
            .filter { it.words.isNotEmpty() }
        require(storyBeats.isNotEmpty()) { "The script does not contain any usable story text." }

        val totalWords = storyBeats.sumOf { it.words.size }
        val frameCount = ((durationMs + 999L) / 1_000L).toInt()
        require(frameCount <= MAX_STORY_SECONDS) {
            "This version supports narration up to 30 minutes (1,800 images). Shorten the script or split it into parts."
        }

        val cumulativeEnds = IntArray(storyBeats.size)
        var running = 0
        storyBeats.forEachIndexed { index, beat ->
            running += beat.words.size
            cumulativeEnds[index] = running
        }
        val allWords = storyBeats.flatMap { it.words }

        return (0 until frameCount).map { index ->
            val startWord = (index.toLong() * totalWords / frameCount)
                .toInt().coerceIn(0, totalWords - 1)
            val endWord = (((index + 1L) * totalWords) / frameCount)
                .toInt().coerceIn(startWord + 1, totalWords)
            val centerWord = ((index + 0.5) * totalWords / frameCount)
                .coerceAtMost(totalWords - 0.0001)
            val sentenceIndex = cumulativeEnds.indexOfFirst { centerWord < it }
                .let { if (it < 0) storyBeats.lastIndex else it }
            val sentenceStart = if (sentenceIndex == 0) 0 else cumulativeEnds[sentenceIndex - 1]
            val sentence = storyBeats[sentenceIndex]
            val progress = ((centerWord - sentenceStart) / sentence.words.size.toDouble())
                .toFloat().coerceIn(0f, 1f)
            val position = when {
                progress < 0.34f -> "opening action of this sentence"
                progress < 0.68f -> "continuing action in this sentence"
                else -> "the next visible beat near the end of this sentence"
            }
            val caption = allWords.subList(startWord, endWord).joinToString(" ")

            Beat(
                second = index + 1,
                startMs = index * 1_000L,
                endMs = minOf((index + 1L) * 1_000L, durationMs),
                caption = caption.ifBlank { sentence.text },
                sourceWords = sentence.text,
                sentenceIndex = sentenceIndex,
                sentenceProgress = progress,
                momentPosition = position
            )
        }
    }

    /**
     * Keeps the complete story and current sentence in every prompt. Only the
     * smallest next action should change; the prior generated image is the
     * reference frame passed to MNN img2img for every frame after the opening.
     */
    fun visualPrompt(beats: List<Beat>, beatIndex: Int): String {
        require(beatIndex in beats.indices) { "Storyboard beat does not exist." }
        val beat = beats[beatIndex]
        val previousSentence = beats.take(beatIndex)
            .lastOrNull { it.sentenceIndex != beat.sentenceIndex }
            ?.sourceWords.orEmpty()
        val nextSentence = beats.drop(beatIndex + 1)
            .firstOrNull { it.sentenceIndex != beat.sentenceIndex }
            ?.sourceWords.orEmpty()
        val fullStory = beats.map { it.sourceWords }.distinctByIndexedSentence()
            .joinToString(" ").replace(Regex("\\s+"), " ").trim().take(2_400)
        val neighboringContext = listOf(previousSentence, beat.sourceWords, nextSentence)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(900)
        val openingInstruction = if (beatIndex == 0) {
            "OPENING FRAME: establish the main character(s), face, appearance, clothing, important objects, location and lighting. Keep these visual identities fixed for the entire film."
        } else {
            "CONTINUATION FRAME: the immediately preceding generated image is supplied as the visual reference. Treat it as ground truth for identity, composition and environment. Continue that exact scene; do not restart or redesign it."
        }

        return "Create exactly one cinematic movie frame for second ${beat.second} of ${beats.size}. " +
            "FULL STORY CONTEXT: $fullStory. " +
            "CURRENT STORY SENTENCE: ${beat.sourceWords}. " +
            "NEARBY STORY CONTEXT: $neighboringContext. " +
            "MOMENT WITHIN THE CURRENT SENTENCE: ${beat.momentPosition} (progress ${(beat.sentenceProgress * 100).toInt()}%). " +
            "$openingInstruction " +
            "STRICT VISUAL CONTINUITY: keep the same character identity, face, apparent age, hairstyle, body proportions, clothing, accessories, objects, architecture, location, lighting direction, weather, color grade, art style, lens and camera position as the preceding frame. " +
            "Advance only the smallest natural action required by this exact sentence, such as a slight pose, gaze, expression, gesture or movement. " +
            "Do not add random people, props, costumes, locations, camera angles, panels, text, subtitles, logos or watermarks. " +
            "Photorealistic cinematic realism, believable anatomy and natural sequential motion."
    }

    private fun List<Beat>.distinctByIndexedSentence(): List<String> {
        val seen = HashSet<Int>()
        return mapNotNull { beat -> if (seen.add(beat.sentenceIndex)) beat.sourceWords else null }
    }
}
