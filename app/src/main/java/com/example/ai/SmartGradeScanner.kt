package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.local.StudentEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

private const val TAG = "SmartGradeScanner"

enum class ExtractionConfidence(val labelAr: String) {
    HIGH("دقة عالية"),
    MEDIUM("يحتاج مراجعة"),
    LOW("غير واضح")
}

data class ExtractedGradeRow(
    val rowId: String,
    val detectedName: String,
    val matchedStudentId: Long?,
    val scoreText: String,
    val confidence: ExtractionConfidence,
    val issueNote: String,
    val isSelected: Boolean = true
) {
    fun parsedScore(maxScore: Double = 20.0): Double? {
        val normalized = SmartGradeScanner.normalizeDigits(scoreText).replace(',', '.').trim()
        val parsed = normalized.toDoubleOrNull() ?: return null
        return if (parsed in 0.0..maxScore) parsed else null
    }

    fun hasWarning(maxScore: Double = 20.0): Boolean {
        return matchedStudentId == null ||
            parsedScore(maxScore) == null ||
            confidence != ExtractionConfidence.HIGH ||
            issueNote.isNotBlank()
    }
}

sealed interface ScanOutcome {
    data class Success(
        val rows: List<ExtractedGradeRow>,
        val summaryNote: String,
        val usedAiOnline: Boolean
    ) : ScanOutcome

    data class Error(val message: String) : ScanOutcome
}

class SmartGradeScanner {

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "summaryNote" to Schema.string(
                description = "Short Arabic summary of sheet quality and any reading issues."
            ),
            "entries" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "detectedName" to Schema.string(
                            description = "Student full name exactly as read from the image."
                        ),
                        "matchedStudentId" to Schema.integer(
                            description = "ID from the provided class roster if matched, or -1 if unmatched.",
                            nullable = true
                        ),
                        "score" to Schema.double(
                            description = "Extracted numeric grade score, or -1 if unreadable.",
                            nullable = true
                        ),
                        "confidence" to Schema.enumeration(
                            values = listOf("HIGH", "MEDIUM", "LOW"),
                            description = "Confidence level of reading name and grade."
                        ),
                        "issueNote" to Schema.string(
                            description = "Arabic note if digits are blurry, ambiguous, out of range, or name unmatched; empty string if clean."
                        )
                    ),
                    optionalProperties = listOf("matchedStudentId", "score", "issueNote")
                ),
                description = "List of student rows extracted from the grade sheet."
            )
        ),
        optionalProperties = listOf("summaryNote")
    )

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    suspend fun scanImageWithGemini(
        bitmap: Bitmap,
        roster: List<StudentEntity>,
        subjectName: String,
        maxScore: Double = 20.0
    ): ScanOutcome {
        return try {
            val rosterText = if (roster.isEmpty()) {
                "القائمة فارغة حالياً."
            } else {
                roster.joinToString("\n") { s ->
                    "ID=${s.id} | اللقب والاسم=${s.lastName} ${s.firstName} | رقم التسجيل=${s.registrationNumber}"
                }
            }

            val promptText = """
                أنت مساعد خبير في قراءة كشوف النقاط المدرسية في الجزائر (مكتوبة بخط اليد أو مطبوعة بالعربية أو الفرنسية).
                المادة المستهدفة: $subjectName (العلامة القصوى: $maxScore).
                قائمة تلاميذ القسم المسجلين:
                $rosterText

                المطلوب:
                1. استخرج كل صف في كشف النقاط المصور (الاسم المقروء والعلامة).
                2. طابق كل اسم مع التلميذ الأنسب في قائمة القسم وأعد معرفه في matchedStudentId، وإذا لم يطابق أي تلميذ أعد -1 وضع ملاحظة "اسم غير مطابق للقائمة".
                3. حوّل الأرقام العربية الهندية أو المكتوبة بفاصلة (مثل 14,5) إلى رقم عشري صالح.
                4. إذا كانت العلامة غير واضحة أو مشطوبة أو خارج المجال [0..$maxScore]، اجعل confidence = LOW أو MEDIUM واكتب السبب بوضوح في issueNote.
                5. لا تختلق أسماء أو علامات غير موجودة في الصورة.
            """.trimIndent()

            val response = withTimeout(120_000L) {
                generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: return ScanOutcome.Error("أرجع نموذج الذكاء الاصطناعي استجابة فارغة، يرجى المحاولة بصورة أوضح.")

            val json = JSONObject(rawJson)
            val summaryNote = json.optString("summaryNote", "")
            val entriesArray = json.optJSONArray("entries")
            val rows = mutableListOf<ExtractedGradeRow>()
            val validStudentIds = roster.map { it.id }.toSet()

            if (entriesArray != null) {
                for (i in 0 until entriesArray.length()) {
                    val item = entriesArray.optJSONObject(i) ?: continue
                    val detectedName = item.optString("detectedName", "").trim()
                    val rawId = if (item.has("matchedStudentId") && !item.isNull("matchedStudentId")) {
                        item.optLong("matchedStudentId", -1L)
                    } else {
                        -1L
                    }
                    val matchedId = when {
                        rawId in validStudentIds -> rawId
                        detectedName.isNotBlank() -> findBestStudentMatch(detectedName, roster)?.id
                        else -> null
                    }

                    val rawScore = if (item.has("score") && !item.isNull("score")) {
                        val v = item.optDouble("score", -1.0)
                        if (v >= 0.0) v else null
                    } else {
                        null
                    }

                    val confStr = item.optString("confidence", "MEDIUM").uppercase()
                    val baseConf = ExtractionConfidence.entries.firstOrNull { it.name == confStr }
                        ?: ExtractionConfidence.MEDIUM

                    var issueNote = item.optString("issueNote", "").trim()
                    if (matchedId == null && issueNote.isBlank()) {
                        issueNote = "اسم غير مطابق لتلاميذ القسم"
                    }
                    if (rawScore == null && issueNote.isBlank()) {
                        issueNote = "الرقم غير واضح"
                    } else if (rawScore != null && rawScore > maxScore && issueNote.isBlank()) {
                        issueNote = "العلامة تتجاوز الحد الأقصى ($maxScore)"
                    }

                    val finalConf = when {
                        rawScore == null || rawScore > maxScore || matchedId == null -> ExtractionConfidence.LOW
                        else -> baseConf
                    }

                    rows.add(
                        ExtractedGradeRow(
                            rowId = "ai_$i",
                            detectedName = detectedName.ifBlank { "صف ${i + 1}" },
                            matchedStudentId = matchedId,
                            scoreText = rawScore?.let { formatExtractedScore(it) } ?: "",
                            confidence = finalConf,
                            issueNote = issueNote,
                            isSelected = matchedId != null && rawScore != null && rawScore <= maxScore
                        )
                    )
                }
            }

            if (rows.isEmpty()) {
                ScanOutcome.Error("لم يتم العثور على أسماء أو علامات واضحة في الصورة. تأكد من إضاءة ووضوح الكشف أو استخدم الوضع الأساسي دون إنترنت.")
            } else {
                ScanOutcome.Success(
                    rows = rows,
                    summaryNote = summaryNote.ifBlank { "تم استخراج ${rows.size} سجل للمراجعة قبل الحفظ." },
                    usedAiOnline = true
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini OCR timed out", e)
            ScanOutcome.Error("انتهت مهلة تحليل الصورة (120 ثانية). تحقق من الاتصال بالإنترنت أو استخدم الوضع الأساسي دون إنترنت.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini OCR failed", e)
            ScanOutcome.Error(
                "تعذر تحليل الصورة عبر الذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير متوقع"}. يمكنك استخدام الوضع الأساسي دون إنترنت."
            )
        }
    }

    /**
     * Offline basic mode parser: parses pasted/dictated/typed grade sheet lines
     * (e.g., "بن علي محمد : 15.5", "202501; 13,25", "سارة بوعلام - 17")
     * and matches them locally against the class roster with fuzzy Arabic matching.
     */
    fun parseOfflineTextToRows(
        rawText: String,
        roster: List<StudentEntity>,
        maxScore: Double = 20.0
    ): ScanOutcome {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ScanOutcome.Error("يرجى إدخال أو لصق أسطر الأسماء والعلامات لتحليلها محلياً.")
        }

        val scoreRegex = Regex("""(\d+(?:[.,]\d+)?)\s*$""")
        val rows = lines.mapIndexed { index, rawLine ->
            val normalizedLine = normalizeDigits(rawLine)
            val scoreMatch = scoreRegex.find(normalizedLine)
            val scoreStr = scoreMatch?.groupValues?.getOrNull(1)?.replace(',', '.') ?: ""
            val scoreVal = scoreStr.toDoubleOrNull()

            val namePart = if (scoreMatch != null) {
                normalizedLine.substring(0, scoreMatch.range.first)
                    .trim(' ', '-', ':', ';', '\t', '|', '/', '،')
            } else {
                normalizedLine
            }

            val matchedStudent = findBestStudentMatch(namePart, roster)
            val issues = mutableListOf<String>()
            if (matchedStudent == null) issues.add("اسم غير مطابق للقائمة")
            if (scoreVal == null) {
                issues.add("رقم غير واضح أو مفقود")
            } else if (scoreVal < 0.0 || scoreVal > maxScore) {
                issues.add("العلامة خارج المجال (0 - ${maxScore.toInt()})")
            }

            val confidence = when {
                issues.isNotEmpty() -> ExtractionConfidence.LOW
                else -> ExtractionConfidence.HIGH
            }

            ExtractedGradeRow(
                rowId = "offline_$index",
                detectedName = namePart.ifBlank { "سطر ${index + 1}" },
                matchedStudentId = matchedStudent?.id,
                scoreText = scoreVal?.let { formatExtractedScore(it) } ?: scoreStr,
                confidence = confidence,
                issueNote = issues.joinToString(" • "),
                isSelected = matchedStudent != null && scoreVal != null && scoreVal in 0.0..maxScore
            )
        }

        return ScanOutcome.Success(
            rows = rows,
            summaryNote = "تم تحليل ${rows.size} سطر محلياً دون إنترنت. راجع النتائج قبل التأكيد.",
            usedAiOnline = false
        )
    }

    /**
     * Generates a pre-filled editable review sheet for all students in the class
     * so teachers working completely offline can read directly from the captured image
     * and enter/confirm marks side-by-side.
     */
    fun buildOfflineRosterTemplate(roster: List<StudentEntity>): List<ExtractedGradeRow> {
        return roster.mapIndexed { index, student ->
            ExtractedGradeRow(
                rowId = "manual_$index",
                detectedName = student.fullName,
                matchedStudentId = student.id,
                scoreText = "",
                confidence = ExtractionConfidence.MEDIUM,
                issueNote = "أدخل العلامة من الصورة الظاهرة",
                isSelected = true
            )
        }
    }

    companion object {
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        suspend fun loadScaledBitmap(
            context: Context,
            uri: Uri,
            maxDimension: Int = 1600
        ): Bitmap? = withContext(Dispatchers.IO) {
            try {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }
                val rawWidth = boundsOptions.outWidth
                val rawHeight = boundsOptions.outHeight
                if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

                var sampleSize = 1
                while ((rawWidth / sampleSize) > maxDimension || (rawHeight / sampleSize) > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode bitmap from URI", e)
                null
            }
        }

        fun normalizeDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                    in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                    '٫' -> sb.append('.')
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun normalizeArabicText(text: String): String {
            return normalizeDigits(text)
                .lowercase()
                .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Strip diacritics
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun findBestStudentMatch(query: String, roster: List<StudentEntity>): StudentEntity? {
            val normQuery = normalizeArabicText(query)
            if (normQuery.isBlank() || roster.isEmpty()) return null

            // 1. Exact registration number match
            roster.firstOrNull { normalizeArabicText(it.registrationNumber) == normQuery }?.let { return it }

            // 2. Fuzzy similarity against "lastName firstName" and "firstName lastName"
            var bestStudent: StudentEntity? = null
            var bestScore = 0.0

            for (student in roster) {
                val formA = normalizeArabicText("${student.lastName} ${student.firstName}")
                val formB = normalizeArabicText("${student.firstName} ${student.lastName}")
                val sim = max(stringSimilarity(normQuery, formA), stringSimilarity(normQuery, formB))
                if (sim > bestScore) {
                    bestScore = sim
                    bestStudent = student
                }
            }
            return if (bestScore >= 0.58) bestStudent else null
        }

        private fun stringSimilarity(a: String, b: String): Double {
            if (a == b) return 1.0
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a.contains(b) || b.contains(a)) return 0.85
            val dist = levenshtein(a, b)
            val maxLen = max(a.length, b.length)
            return 1.0 - (dist.toDouble() / maxLen.toDouble())
        }

        private fun levenshtein(a: String, b: String): Int {
            val prev = IntArray(b.length + 1) { it }
            val curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                curr.copyInto(prev)
            }
            return prev[b.length]
        }

        private fun formatExtractedScore(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", value)
            }
        }
    }
}
