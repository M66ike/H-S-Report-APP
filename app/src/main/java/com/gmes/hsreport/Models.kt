package com.gmes.hsreport

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class Answer(val label: String, val scoreFactor: Double) {
    YES("Yes", 1.0),
    PARTIALLY("Partially", 0.5),
    NO("No", 0.0),
    NA("N/A", 1.0),
    IN_PART("In Part", 0.0),
    POOR("Poor", 0.0);

    companion object {
        fun fromLabel(value: String?): Answer? = entries.firstOrNull { it.label == value }
    }
}

data class QuestionDef(
    val id: String,
    val text: String,
    val weight: Double? = null,
    val answers: List<Answer> = listOf(Answer.YES, Answer.PARTIALLY, Answer.NO, Answer.NA),
    val photoRecommended: Boolean = false
)

data class SectionDef(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val questions: List<QuestionDef>,
    val scored: Boolean = true
)

object ReportTemplate {
    val sections = listOf(
        SectionDef(
            id = "policy",
            title = "GMES Health & Safety - Policy Documents",
            subtitle = "Dated photographic evidence of signed documents required",
            questions = listOf(
                QuestionDef("1.01", "Correct Project Specific RAMS available, read and signed by all team?", 0.06, photoRecommended = true),
                QuestionDef("1.02", "All documentation being uploaded onto GMES SharePoint?", 0.03, photoRecommended = true),
                QuestionDef("1.03", "Weekly toolbox talks held, signed for by the individual attendees and issued to the client?", 0.06, photoRecommended = true),
                QuestionDef("1.04", "SWP’s available, read and signed by all team?", 0.04, photoRecommended = true),
                QuestionDef("1.05", "GMES H&S policy available, read and signed by all team?", 0.06, photoRecommended = true),
                QuestionDef("1.06", "DRA’s (or client equivalent) readily available for each GMES individual every day they are on site and issued to the client?", 0.06, photoRecommended = true)
            )
        ),
        SectionDef(
            id = "ppe",
            title = "GMES Health & Safety - PPE/Training/Inspections",
            subtitle = "Senior Manager visual & verbal checks required",
            questions = listOf(
                QuestionDef("2.01", "Training records and certificates available for all GMES individuals on site and issued to client?", 0.04),
                QuestionDef("2.02", "Correct GMES PPE (Hard hat, safety gloves, safety glasses, Hi-Viz vest, safety boots, hearing protection and Cal 10 arc flash) available, in good condition and correctly being used by all GMES operatives?", 0.04, photoRecommended = true),
                QuestionDef("2.03", "Spare PPE (Gloves, Glasses & Ear protection) readily available?", 0.02, photoRecommended = true),
                QuestionDef("2.04", "GMES Cal 40 arc flash available, in good condition and been worn by AP’s?", 0.06),
                QuestionDef("2.05", "Daily access equipment checks, ensure appropriate daily inspections by GMES are happening?", 0.04),
                QuestionDef("2.06", "Project muster points and emergency procedures known by all GMES individuals? (we must ask individuals without warning)", 0.03),
                QuestionDef("2.07", "GMES team all received client’s induction?", 0.02),
                QuestionDef("2.08", "Ihasco/Mentor first aid training (and other modules) completed? Available to client if needed?", 0.04, answers = listOf(Answer.YES, Answer.PARTIALLY, Answer.NO))
            )
        ),
        SectionDef(
            id = "equipment",
            title = "GMES Health & Safety - Equipment",
            subtitle = "Checks by Senior Manager Required",
            questions = listOf(
                QuestionDef("3.01", "Test equipment and leads calibration records (must be issued to client) available and kit in good condition?", 0.06),
                QuestionDef("3.02", "LOTO, ensure equipment is available and in good working order?", 0.06),
                QuestionDef("3.03", "Torque wrenches (must be calibrated and issued to client) and GMES supplied hand tools all in good working order?", 0.06)
            )
        ),
        SectionDef(
            id = "operational",
            title = "GMES Operational",
            questions = listOf(
                QuestionDef("4.01", "GMES Input software being used correctly with ALL fields completed, and data recorded?", 0.05),
                QuestionDef("4.02", "GMES Inspection reports and Tracker in use, with clear and concise descriptions used, as a minimum must be issued to client on a weekly basis?", 0.04),
                QuestionDef("4.03", "GMES controlled office area clean, tidy, and organised?", 0.02),
                QuestionDef("4.04", "GMES CPR in use. All fields to be completed and as a minimum must be issued to client on a weekly basis?", 0.03),
                QuestionDef("4.05", "GMES engineers producing good quality red lined drawings/documentation?", 0.02),
                QuestionDef("4.06", "GMES team producing good quality QA/QC reports, including internal and external photographs of first and last visits to each panel and/or DB? Also including picture of cover installed?", 0.02),
                QuestionDef("4.07", "MCCB settings available and understood before Earth Loop impedance testing/verification?", 0.02),
                QuestionDef("4.08", "Mains criteria information recorded and available?", 0.02)
            )
        ),
        SectionDef(
            id = "client",
            title = "GMES Client",
            scored = false,
            questions = listOf(
                QuestionDef("5.01", "Access and Egress to and from project and work face is safe and obstruction free?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA)),
                QuestionDef("5.02", "Toilet facilities clean and tidy?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA)),
                QuestionDef("5.03", "Hot and cold running water?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA)),
                QuestionDef("5.04", "Canteen facilities?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA)),
                QuestionDef("5.05", "Defibrillator available on site?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA)),
                QuestionDef("5.06", "Client driven H&S meetings held and attended by all site-based companies?", answers = listOf(Answer.YES, Answer.NO, Answer.IN_PART, Answer.POOR, Answer.NA))
            )
        )
    )

    fun section(id: String) = sections.first { it.id == id }
    fun question(id: String) = sections.flatMap { it.questions }.first { it.id == id }
}

data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String = "",
    val shortName: String = "",
    val savedSignaturePath: String = ""
)

data class ReportData(
    val id: String = UUID.randomUUID().toString(),
    val client: String = "",
    val visitDate: String = LocalDate.now().toString(),
    val projectName: String = "",
    val projectNo: String = "",
    val personId: String = "",
    val personName: String = "",
    val personRole: String = "",
    val sector: String = "",
    val managerName: String = "",
    val managerRole: String = "",
    val territory: String = "UK",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val answers: Map<String, String> = emptyMap(),
    val comments: Map<String, String> = emptyMap(),
    val photos: Map<String, String> = emptyMap(),
    val signaturePath: String = "",
    val status: String = "Draft",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun sectionEarned(section: SectionDef): Double = section.questions.sumOf { q ->
        val factor = Answer.fromLabel(answers[q.id])?.scoreFactor ?: 0.0
        (q.weight ?: 0.0) * factor
    }

    fun sectionAvailable(section: SectionDef): Double = section.questions.sumOf { it.weight ?: 0.0 }

    fun sectionPercent(section: SectionDef): Int {
        val total = sectionAvailable(section)
        if (total <= 0.0) return 0
        return ((sectionEarned(section) / total) * 100.0).toInt()
    }

    fun overallPercent(): Int {
        val scored = ReportTemplate.sections.filter { it.scored }
        val total = scored.sumOf { sectionAvailable(it) }
        if (total <= 0.0) return 0
        return ((scored.sumOf { sectionEarned(it) } / total) * 100.0).toInt()
    }

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("client", client)
        put("visitDate", visitDate)
        put("projectName", projectName)
        put("projectNo", projectNo)
        put("personId", personId)
        put("personName", personName)
        put("personRole", personRole)
        put("sector", sector)
        put("managerName", managerName)
        put("managerRole", managerRole)
        put("territory", territory)
        put("reviewerId", personId)
        put("reviewerName", personName)
        put("answers", JSONObject(answers))
        put("comments", JSONObject(comments))
        put("photos", JSONObject(photos))
        put("signaturePath", signaturePath)
        put("status", status)
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        private fun JSONObject.stringMap(name: String): Map<String, String> {
            val obj = optJSONObject(name) ?: return emptyMap()
            return obj.keys().asSequence().associateWith { obj.optString(it, "") }
        }

        fun fromJson(json: String): ReportData {
            val o = JSONObject(json)
            return ReportData(
                id = o.optString("id", UUID.randomUUID().toString()),
                client = o.optString("client"),
                visitDate = o.optString("visitDate", LocalDate.now().toString()),
                projectName = o.optString("projectName"),
                projectNo = o.optString("projectNo"),
                personId = o.optString("personId"),
                personName = o.optString("personName"),
                personRole = o.optString("personRole"),
                sector = o.optString("sector"),
                managerName = o.optString("managerName"),
                managerRole = o.optString("managerRole"),
                territory = o.optString("territory", "UK"),
                reviewerId = o.optString("personId"),
                reviewerName = o.optString("personName"),
                answers = o.stringMap("answers"),
                comments = o.stringMap("comments"),
                photos = o.stringMap("photos"),
                signaturePath = o.optString("signaturePath"),
                status = o.optString("status", "Draft"),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

fun personToJson(person: Person): String = JSONObject().apply {
    put("id", person.id)
    put("name", person.name)
    put("role", person.role)
    put("shortName", person.shortName)
    put("savedSignaturePath", person.savedSignaturePath)
}.toString()

fun personFromJson(json: String): Person {
    val o = JSONObject(json)
    return Person(
        id = o.optString("id", UUID.randomUUID().toString()),
        name = o.optString("name"),
        role = o.optString("role"),
        shortName = o.optString("shortName"),
        savedSignaturePath = o.optString("savedSignaturePath")
    )
}
