package com.example.medicalaiguidance

import com.example.medicalaiguidance.demo.MockDemoScript
import com.example.medicalaiguidance.network.RecommendationItemDto
import com.example.medicalaiguidance.network.TtsRequest
import com.example.medicalaiguidance.network.VoiceTtsResponseDto
import com.example.medicalaiguidance.screen.DoctorCardSpeech
import com.example.medicalaiguidance.screen.spokenIntroduction
import com.example.medicalaiguidance.util.FixedTriageAudioResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DoctorCardSpeechUnitTest {
    private fun demoItem(doctor: String, date: String, rank: Int) = RecommendationItemDto(
        recommendationId = "rec_$rank", parentDept = "外科系", childDept = "一般骨科", doctor = doctor,
        date = date, session = "上午", slot = "3202", score = 1.0, rank = rank,
        isBestMatch = rank == 1, sessionTime = "08:30-12:00"
    )

    @Test
    fun demoRecommendationsSpeakExactlyThePreRecordedSentences() {
        val cards = listOf(
            demoItem("蘇宇平", "2026-10-06", 1), demoItem("蘇宇平", "2026-10-13", 2),
            demoItem("蘇宇平", "2026-10-16", 3), demoItem("邱方遙", "2026-10-16", 4),
            demoItem("蘇宇平", "2026-10-20", 5)
        )
        assertEquals(MockDemoScript.doctorIntroductions, cards.map { it.spokenIntroduction() })
        assertEquals(
            listOf(R.raw.mockdemo_doctor_01_zh, R.raw.mockdemo_doctor_02_zh, R.raw.mockdemo_doctor_03_zh,
                R.raw.mockdemo_doctor_04_zh, R.raw.mockdemo_doctor_05_zh),
            MockDemoScript.doctorIntroductions.map { FixedTriageAudioResolver.resolve(it, "chinese") }
        )
        assertEquals(
            listOf(R.raw.mockdemo_doctor_01_taigi, R.raw.mockdemo_doctor_02_taigi, R.raw.mockdemo_doctor_03_taigi,
                R.raw.mockdemo_doctor_04_taigi, R.raw.mockdemo_doctor_05_taigi),
            MockDemoScript.doctorIntroductions.map { FixedTriageAudioResolver.resolve(it, "taiwanese") }
        )
    }

    @Test
    fun preRecordedAudioIsPlayedWithoutBackendTtsAndOthersFallBackToBackend() = runTest {
        val remoteCalls = mutableListOf<TtsRequest>()
        val localPlays = mutableListOf<Int>()
        val remotePlays = mutableListOf<String>()
        var finishLocal: () -> Unit = {}
        val speech = DoctorCardSpeech(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            requestSpeech = { request -> remoteCalls += request; VoiceTtsResponseDto(audioBase64 = "remote") },
            playSpeech = { result, _, _ -> remotePlays += result.audioBase64 },
            stopSpeech = {},
            resolveLocal = FixedTriageAudioResolver::resolve,
            playLocal = { id, done, _ -> localPlays += id; finishLocal = done }
        )
        try {
            speech.toggle("rec_1", MockDemoScript.doctorIntroductions[0], "台語")
            runCurrent()
            assertEquals(listOf(R.raw.mockdemo_doctor_01_taigi), localPlays)
            assertTrue(remoteCalls.isEmpty())
            assertEquals("rec_1", speech.activeId)
            finishLocal()
            assertNull(speech.activeId)

            speech.toggle("rec_x", "王醫師。科別為一般內科。", "國語")
            runCurrent()
            assertEquals(listOf(TtsRequest(text = "王醫師。科別為一般內科。", lang = "chinese")), remoteCalls)
            assertEquals(listOf("remote"), remotePlays)
        } finally {
            speech.close()
        }
    }
}
