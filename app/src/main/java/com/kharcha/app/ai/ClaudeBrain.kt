package com.kharcha.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.kharcha.app.data.Category
import com.kharcha.app.data.Repository
import com.kharcha.app.data.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChatMessage(val fromUser: Boolean, val text: String)

class AiException(message: String) : Exception(message)

/**
 * Claude as the app's brain: answers questions about your money, writes the daily brief and the
 * monthly review, and cleans up transactions the SMS parser couldn't name or categorise.
 *
 * Only summaries and transaction lines are sent; raw SMS go to Claude only during clean-up, with
 * account and reference numbers masked first.
 */
class ClaudeBrain(private val repo: Repository) {

    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun client(): AnthropicClient {
        val key = repo.settings.state.value.anthropicKey.trim()
        if (key.isBlank()) throw AiException("Add your Claude API key in Settings to switch on the AI brain.")
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofMinutes(3))
                .maxRetries(2)
                .build()
            clientKey = key
        }
        return client!!
    }

    private val model get() = repo.settings.state.value.aiModel

    private fun params(maxTokens: Long, effort: OutputConfig.Effort, system: List<TextBlockParam>): MessageCreateParams.Builder =
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .systemOfTextBlockParams(system)
            .apply {
                // If a safety classifier declines, retry on a fallback model instead of failing outright.
                if (model == "claude-opus-5-5" || model == "claude-sonnet-5-5") {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }

    private fun systemBlocks(instructions: String, snapshot: String?): List<TextBlockParam> = buildList {
        add(TextBlockParam.builder().text(instructions).build())
        if (snapshot != null) {
            // The money snapshot is large and repeats across chat turns, so cache it.
            add(
                TextBlockParam.builder()
                    .text("<my_finances>\n$snapshot\n</my_finances>")
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build(),
            )
        }
    }

    private fun Message.textOrThrow(): String {
        if (stopReason().orElse(null) == StopReason.REFUSAL) {
            throw AiException("Claude declined to answer that one. Try rephrasing.")
        }
        return content().flatMap { b -> b.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("").trim()
    }

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: AiException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw AiException("That API key was rejected. Check it in Settings.")
        } catch (e: PermissionDeniedException) {
            throw AiException("This API key can't use ${model}. Pick another model in Settings.")
        } catch (e: RateLimitException) {
            throw AiException("Claude is rate-limited right now. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            throw AiException("Claude error (${e.statusCode()}). Try again shortly.")
        } catch (e: AnthropicIoException) {
            throw AiException("No internet connection to Claude.")
        }
    }

    /** Streams Claude's answer to a question about your money, piece by piece. */
    fun chat(history: List<ChatMessage>, snapshot: String): Flow<String> = flow {
        val builder = params(16000, OutputConfig.Effort.MEDIUM, systemBlocks(CHAT_INSTRUCTIONS, snapshot))
        history.forEach { m -> if (m.fromUser) builder.addUserMessage(m.text) else builder.addAssistantMessage(m.text) }
        val p = builder.build()
        val c = client()
        var refused = false
        try {
            c.messages().createStreaming(p).use { stream ->
                val events = stream.stream().iterator()
                while (events.hasNext()) {
                    val event = events.next()
                    val text = event.contentBlockDelta().flatMap { d -> d.delta().text() }.map { t -> t.text() }.orElse(null)
                    if (text != null) emit(text)
                    if (event.messageDelta().flatMap { d -> d.delta().stopReason() }.orElse(null) == StopReason.REFUSAL) refused = true
                }
            }
        } catch (e: UnauthorizedException) {
            throw AiException("That API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) {
            throw AiException("Claude is rate-limited right now. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            throw AiException("Claude error (${e.statusCode()}). Try again shortly.")
        } catch (e: AnthropicIoException) {
            throw AiException("No internet connection to Claude.")
        }
        if (refused) emit("\n\n_Claude stopped here and couldn't finish this answer._")
    }.flowOn(Dispatchers.IO)

    /** Two or three lines for the home screen: how today looks and the single best next move. */
    suspend fun dailyBrief(snapshot: String): String = call {
        client().messages().create(
            params(4000, OutputConfig.Effort.LOW, systemBlocks(BRIEF_INSTRUCTIONS, snapshot))
                .addUserMessage("Write today's brief.")
                .build(),
        ).textOrThrow()
    }

    /** A personal month review: what went well, where money leaked, a concrete plan for next month. */
    suspend fun monthlyReview(snapshot: String, monthLabel: String): String = call {
        client().messages().create(
            params(16000, OutputConfig.Effort.HIGH, systemBlocks(COACH_INSTRUCTIONS, snapshot))
                .addUserMessage("Review my spending for $monthLabel and give me a plan for next month.")
                .build(),
        ).textOrThrow()
    }

    /** A cheap round-trip to confirm the key and model work. */
    suspend fun test(): String = call {
        client().messages().create(
            params(2000, OutputConfig.Effort.LOW, systemBlocks("Reply with one short friendly sentence.", null))
                .addUserMessage("Say hi to a new Kharcha user in under 12 words.")
                .build(),
        ).textOrThrow()
    }

    data class Review(
        val id: Long,
        val merchant: String?,
        val category: Category?,
        val notTransaction: Boolean,
        val selfTransfer: Boolean,
    )

    /**
     * Sends a batch of parsed transactions (with their masked SMS) and gets back a corrected merchant
     * name and category for each, plus flags for messages that weren't real spends.
     */
    suspend fun review(batch: List<TransactionEntity>): List<Review> = call {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val lines = batch.joinToString("\n") { t ->
            val sms = mask(t.rawText.substringBefore("\n---\n")).take(260).replace('\n', ' ')
            "id=${t.id} | ${fmt.format(Instant.ofEpochMilli(t.timestamp).atZone(zone))} | ${t.type} | ${t.amount} | " +
                "parsed_merchant=${t.merchant} | parsed_category=${t.category.name} | sms=\"$sms\""
        }
        val categories = Category.entries.joinToString { "${it.name} (${it.label})" }
        val text = client().messages().create(
            params(16000, OutputConfig.Effort.LOW, systemBlocks(REVIEW_INSTRUCTIONS.replace("{CATEGORIES}", categories), null))
                .addUserMessage(lines)
                .build(),
        ).textOrThrow()
        parseReview(text)
    }

    private fun parseReview(text: String): List<Review> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) throw AiException("Claude's clean-up answer couldn't be read. Try again.")
        val arr = JSONArray(text.substring(start, end + 1))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Review(
                id = o.optLong("id", -1).takeIf { it > 0 } ?: return@mapNotNull null,
                merchant = o.optString("merchant", "").trim().takeIf { it.isNotBlank() && it.length <= 40 },
                category = runCatching { Category.valueOf(o.optString("category", "")) }.getOrNull(),
                notTransaction = o.optBoolean("not_a_transaction", false),
                selfTransfer = o.optBoolean("self_transfer", false),
            )
        }
    }

    companion object {
        /** Hides account numbers, UPI references and phone numbers before an SMS leaves the phone. */
        fun mask(sms: String): String = sms
            .replace(Regex("(?i)([xX*•]{1,})(\\d{3,6})")) { "${it.groupValues[1]}••••" }
            .replace(Regex("\\d{9,}")) { "#".repeat(6) }

        private const val CHAT_INSTRUCTIONS = """You are Kharcha, a sharp, warm personal finance coach inside an Indian expense-tracking app.
The user's real financial data is in <my_finances>. It comes from their bank SMS and PhonePe/UPI notifications.

How to answer:
- Use only numbers from <my_finances>. Do the arithmetic carefully. If the data doesn't cover something, say so plainly instead of guessing.
- Amounts in rupees with Indian grouping (₹1,23,456).
- Be direct and specific: name merchants, categories, dates and amounts. Give one or two concrete actions, not generic advice.
- Keep it short for a phone screen: a few sentences or a short bulleted list. Use **bold** for key numbers. No tables, no headings.
- "Live balance" is an estimate rolled forward from the bank's last reported balance; mention that when it matters.
- Never ask for passwords, OTPs, PINs or card numbers, and never recommend specific stocks or schemes as guaranteed returns."""

        private const val BRIEF_INSTRUCTIONS = """You are Kharcha, a personal finance coach. Using <my_finances>, write today's brief for the home screen.
Exactly 2 short sentences, max 40 words total, plain text (no markdown, no emoji at the start):
1) How they're doing right now, with the single most important number (today's safe-to-spend, budget pace, or a leak).
2) One specific action for today, naming a merchant or category when useful.
Tone: encouraging but honest, like a smart friend. Use ₹ with Indian grouping."""

        private const val COACH_INSTRUCTIONS = """You are Kharcha, a personal finance coach. Using <my_finances>, write a month review for the user's phone.
Structure (use these exact bold labels, each followed by 2-4 short bullet points starting with "- "):
**Scorecard** — spent vs budget, income, saved and savings rate, discipline score.
**What went well**
**Where money leaked** — specific merchants/categories with amounts and the change vs last month.
**Plan for next month** — 3 concrete rules with rupee targets (e.g. "Cap Swiggy at ₹2,000 — about 5 orders").
**One number to watch**
Only use numbers present in the data; say when something can't be determined. Indian grouping for ₹. Keep it under 250 words."""

        private const val REVIEW_INSTRUCTIONS = """You check transactions that an SMS parser extracted from Indian bank alerts.
For each input line, decide:
- merchant: a clean, human-readable payee/payer name (e.g. "Swiggy", "Zepto", "Rahul Sharma", "Salary - Acme Corp"). Use the SMS. Keep "UPI ••1234"-style names if the payee is only a phone number.
- category: exactly one of these enum names: {CATEGORIES}
  Credits are INCOME or REFUND. Payments to a person's VPA with no business signal are TRANSFER.
- self_transfer: true if it moves money between the user's own accounts, pays their own credit card bill, or books an FD/RD.
- not_a_transaction: true if the SMS is not actually money moving (promo, reminder, OTP, failed/declined, mandate setup, balance enquiry).
Respond with only a JSON array, no prose: [{"id":123,"merchant":"...","category":"FOOD","self_transfer":false,"not_a_transaction":false}, ...]
Include every id from the input exactly once."""
    }
}
