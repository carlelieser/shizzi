package dev.shizzi

import org.json.JSONArray
import org.json.JSONObject

enum class ProbeOutcome { PASS, FAIL, SKIPPED }

data class ProbeResult(
    val id: String,
    val question: String,
    val outcome: ProbeOutcome,
    val detail: String,
)

class ProbeReportBuilder {

    private val results = mutableListOf<ProbeResult>()
    private val hiddenApiFindings = mutableListOf<Resolution>()

    fun record(
        id: String,
        question: String,
        outcome: ProbeOutcome,
        detail: String,
    ) {
        results += ProbeResult(id, question, outcome, detail)
    }

    fun recordPass(id: String, question: String, detail: String) =
        record(id, question, ProbeOutcome.PASS, detail)

    fun recordFail(id: String, question: String, detail: String) =
        record(id, question, ProbeOutcome.FAIL, detail)

    fun recordSkip(id: String, question: String, reason: String) =
        record(id, question, ProbeOutcome.SKIPPED, reason)

    fun recordHiddenApiResolutions(resolutions: List<Resolution>) {
        hiddenApiFindings += resolutions
    }

    fun recordReleaseProblems(problems: List<String>) {
        record(
            id = "T-2",
            question = "Does the run release its TUN, fd, and test network?",
            outcome = if (problems.isEmpty()) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = when {
                problems.isEmpty() -> "released cleanly"
                else -> problems.joinToString("; ")
            },
        )
    }

    val hasFailure: Boolean get() = results.any { it.outcome == ProbeOutcome.FAIL }

    fun build(environment: JSONObject): String {
        val root = JSONObject()
        root.put("environment", environment)
        root.put("verdict", if (hasFailure) "NOT_VIABLE" else "VIABLE")
        root.put("probes", probesArray())
        root.put("hiddenApis", hiddenApiArray())
        return root.toString(2)
    }

    private fun probesArray(): JSONArray {
        val array = JSONArray()
        results.forEach { result ->
            val entry = JSONObject()
            entry.put("id", result.id)
            entry.put("question", result.question)
            entry.put("outcome", result.outcome.name)
            entry.put("detail", result.detail)
            array.put(entry)
        }
        return array
    }

    private fun hiddenApiArray(): JSONArray {
        val array = JSONArray()
        hiddenApiFindings.forEach { resolution ->
            array.put(hiddenApiEntry(resolution))
        }
        return array
    }

    private fun hiddenApiEntry(resolution: Resolution): JSONObject {
        val entry = JSONObject()
        when (resolution) {
            is Resolution.Found -> {
                entry.put("id", resolution.path.id)
                entry.put("resolved", true)
                entry.put("since", resolution.path.since)
                entry.put("notes", resolution.path.notes)
            }

            is Resolution.Missing -> {
                entry.put("id", resolution.path.id)
                entry.put("resolved", false)
                entry.put("since", resolution.path.since)
                entry.put("reason", resolution.reason)
                entry.put("notes", resolution.path.notes)
            }
        }
        return entry
    }
}
