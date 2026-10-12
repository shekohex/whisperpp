package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutoLearnAiReviewerTest {

    private val name = AutoLearnCandidate("id-0", "I talked to john smith yesterday", "I talked to John Smyth yesterday", 0L)
    private val brand = AutoLearnCandidate("id-1", "send it to vois ink now", "send it to VoiceInk now", 0L)
    private val batch = listOf(name, brand)

    private val validJson = """
        [{"candidateID":0,"learningAction":"addReplacementAndVocabulary","incorrectTextToReplace":"john smith","correctedVocabularyTerm":"John Smyth"},
         {"candidateID":1,"learningAction":"addReplacementOnly","incorrectTextToReplace":"vois ink","correctedVocabularyTerm":"VoiceInk"}]
    """.trimIndent()

    private fun parse(response: String, candidates: List<AutoLearnCandidate> = batch) =
        AutoLearnAiReviewer.parseAndValidate(response, candidates)

    @Test
    fun prompt_isVoiceInkPromptVerbatim() {
        val prompt = File("src/main/res/raw/autolearn_review_prompt.txt").readText()
        assertTrue(prompt.startsWith("Review speech-to-text corrections. Each candidate has originalText"))
        assertTrue(prompt.endsWith("Repeat an ID only for independent accepted corrections.\n"))
        assertTrue(prompt.contains("\"Prakash Joshi Pages\" to \"Prakash Joshi Pax\""))
        assertTrue(prompt.lines().none { it.startsWith(" ") })
    }

    @Test
    fun payload_usesBatchIndicesAsCandidateIds() {
        val items = JSONObject(AutoLearnAiReviewer.buildPayload(batch)).getJSONArray("candidatesForReview")
        assertEquals(2, items.length())
        assertEquals(1, items.getJSONObject(1).getInt("candidateID"))
        assertEquals("send it to VoiceInk now", items.getJSONObject(1).getString("correctedText"))
    }

    @Test
    fun validResponse_mapsDecisionsToQueueIds() {
        val result = parse(validJson).getOrThrow()
        assertEquals(
            listOf(
                AutoLearnDecision("id-0", LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "john smith", "John Smyth"),
                AutoLearnDecision("id-1", LearningAction.ADD_REPLACEMENT_ONLY, "vois ink", "VoiceInk"),
            ),
            result.decisions,
        )
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun reasoningAndCodeFence_areStripped() {
        val wrapped = "<think>The user said john… [not json]</think>\n```json\n$validJson\n```"
        assertEquals(2, parse(wrapped).getOrThrow().decisions.size)
        assertEquals(2, parse("<thinking>x</thinking>$validJson").getOrThrow().decisions.size)
    }

    @Test
    fun malformedResponses_fail() {
        listOf(
            "not json",
            """{"reviewDecisions":[]}""",
            """[{"candidateID":0,"learningAction":"rejectCorrection","incorrectTextToReplace":null}]""",
            """[{"candidateID":0,"learningAction":"maybe","incorrectTextToReplace":null,"correctedVocabularyTerm":null}]""",
            """[{"candidateID":"0","learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null}]""",
            """[{"candidateID":0.5,"learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null}]""",
            """[{"candidateID":0,"learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null,"extra":1}]""",
        ).forEach { response ->
            val result = parse(response)
            assertTrue(response, result.exceptionOrNull() is NetworkError.InvalidResponse)
        }
    }

    @Test
    fun conflictingAcceptAndReject_isUnresolved() {
        val response = """
            [{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"john smith","correctedVocabularyTerm":"John Smyth"},
             {"candidateID":0,"learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null},
             {"candidateID":1,"learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null}]
        """.trimIndent()
        val result = parse(response).getOrThrow()
        assertEquals(listOf(AutoLearnDecision("id-1", LearningAction.REJECT, null, null)), result.decisions)
        assertEquals(
            listOf(AutoLearnUnresolvedReview("id-0", AutoLearnUnresolvedReason.CONFLICTING_DECISIONS)),
            result.unresolved,
        )
    }

    @Test
    fun unknownIdsIgnored_missingIdsUnresolved() {
        val response = """
            [{"candidateID":7,"learningAction":"addVocabularyOnly","incorrectTextToReplace":null,"correctedVocabularyTerm":"VoiceInk"},
             {"candidateID":1,"learningAction":"addVocabularyOnly","incorrectTextToReplace":null,"correctedVocabularyTerm":"VoiceInk"}]
        """.trimIndent()
        val result = parse(response).getOrThrow()
        assertEquals(listOf(AutoLearnDecision("id-1", LearningAction.ADD_VOCABULARY_ONLY, null, "VoiceInk")), result.decisions)
        assertEquals(listOf(AutoLearnUnresolvedReview("id-0", AutoLearnUnresolvedReason.MISSING_DECISION)), result.unresolved)
    }

    @Test
    fun incorrectText_mustBeSubstringOfOriginal_caseInsensitive() {
        val accepted = """[{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"JOHN SMITH","correctedVocabularyTerm":"John Smyth"}]"""
        assertEquals("JOHN SMITH", parse(accepted, listOf(name)).getOrThrow().decisions.single().incorrectTextToReplace)

        val invented = """[{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"jon smith","correctedVocabularyTerm":"John Smyth"}]"""
        assertEquals(
            AutoLearnUnresolvedReason.INVALID_REQUIRED_ACTION_VALUES,
            parse(invented, listOf(name)).getOrThrow().unresolved.single().reason,
        )
    }

    @Test
    fun correctedTerm_mustComeFromACorrectedText() {
        val ungrounded = """[{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"john smith","correctedVocabularyTerm":"Jon Smythe"}]"""
        assertEquals(
            AutoLearnUnresolvedReason.INVALID_REQUIRED_ACTION_VALUES,
            parse(ungrounded, listOf(name)).getOrThrow().unresolved.single().reason,
        )
        // Batch canonicalization may borrow the term from another candidate.
        val canonical = """
            [{"candidateID":0,"learningAction":"addVocabularyOnly","incorrectTextToReplace":null,"correctedVocabularyTerm":"VoiceInk"},
             {"candidateID":1,"learningAction":"rejectCorrection","incorrectTextToReplace":null,"correctedVocabularyTerm":null}]
        """.trimIndent()
        assertTrue(parse(canonical).getOrThrow().unresolved.isEmpty())
    }

    @Test
    fun missingValues_andCaseOnly() {
        val missing = """[{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":null,"correctedVocabularyTerm":"John Smyth"}]"""
        assertEquals(
            AutoLearnUnresolvedReason.MISSING_REQUIRED_ACTION_VALUES,
            parse(missing, listOf(name)).getOrThrow().unresolved.single().reason,
        )
        val caseOnly = AutoLearnCandidate("c", "call john now", "call John now", 0L)
        val response = """[{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"john","correctedVocabularyTerm":"John"}]"""
        assertEquals(LearningAction.REJECT, parse(response, listOf(caseOnly)).getOrThrow().decisions.single().learningAction)
    }

    @Test
    fun overlappingSplitDecisions_areConflicting() {
        val candidate = AutoLearnCandidate("c", "ask anna bell", "ask Anna Belle", 0L)
        val response = """
            [{"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"anna bell","correctedVocabularyTerm":"Anna Belle"},
             {"candidateID":0,"learningAction":"addReplacementOnly","incorrectTextToReplace":"bell","correctedVocabularyTerm":"Belle"}]
        """.trimIndent()
        assertEquals(
            AutoLearnUnresolvedReason.CONFLICTING_DECISIONS,
            parse(response, listOf(candidate)).getOrThrow().unresolved.single().reason,
        )
    }

    @Test
    fun review_sendsPromptAndPayload_throughProvider() = runBlocking {
        val provider = FakeEnhancementProvider(listOf(Result.success(validJson)))
        val reviewer = AutoLearnAiReviewer(prompt = { "PROMPT" }, resolveModel = { Result.success(AutoLearnReviewModel(provider)) })

        val result = reviewer.review(batch).getOrThrow()

        assertEquals(2, result.accepted.size)
        val request = provider.requests.single()
        assertEquals("PROMPT", request.systemPrompt)
        assertEquals(AutoLearnAiReviewer.buildPayload(batch), request.userText)
    }

    @Test
    fun review_propagatesProviderAndModelFailures() = runBlocking {
        val offline = AutoLearnAiReviewer(
            prompt = { "P" },
            resolveModel = { Result.success(AutoLearnReviewModel(FakeEnhancementProvider(listOf(Result.failure(NetworkError.Offline()))))) },
        )
        assertTrue(offline.review(batch).exceptionOrNull() is NetworkError.Offline)

        val noModel = AutoLearnAiReviewer(
            prompt = { "P" },
            resolveModel = { Result.failure(ProviderConfigException(ProviderConfigException.Problem.MISSING_MODEL)) },
        )
        assertTrue(noModel.review(batch).exceptionOrNull() is ProviderConfigException)
    }
}
