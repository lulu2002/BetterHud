package kr.toxicity.hud.equation

import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function
import java.lang.Math.clamp
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TEquation(private val expressionString: String) {
    companion object {
        val t = TEquation("t")
        val one = TEquation("1")
        val zero = TEquation("0")
    }

    private val expression = ExpressionBuilder(expressionString)
        .functions(
            object : Function("min", 2) {
                override fun apply(vararg p0: Double): Double = min(p0[0], p0[1])
            },
            object : Function("max", 2) {
                override fun apply(vararg p0: Double): Double = max(p0[0], p0[1])
            },
            object : Function("clamp", 3) {
                override fun apply(vararg p0: Double): Double = clamp(p0[0], p0[1], p0[2])
            }
        )
        .variables(setOf(
            "t",
            "pi",
            "e",
            "total"
        ))
        .build()

    // 檢查是否使用了 total 變數
    fun usesTotalVariable(): Boolean {
        // 使用更精確的檢測：檢查 total 是否作為獨立的詞出現
        return Regex("\\btotal\\b", RegexOption.IGNORE_CASE).containsMatchIn(expressionString)
    }

    infix fun evaluate(t: Double) = evaluate(t, emptyMap())
    
    fun evaluate(t: Double, additionalVariables: Map<String, Double>) = Expression(expression)
        .setVariables(mapOf(
            "t" to t,
            "pi" to PI,
            "e" to E,
            "total" to 0.0
        ) + additionalVariables)
        .evaluate()

    infix fun evaluateToInt(t: Double) = evaluate(t).roundToInt()
    
    fun evaluateToInt(t: Double, additionalVariables: Map<String, Double>) = evaluate(t, additionalVariables).roundToInt()
    
    override fun toString(): String = expressionString
}