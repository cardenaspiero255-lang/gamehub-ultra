package com.cardenaspiero255.gamehubultra.ai

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

data class UltraMathSolution(
    val resultText: String,
    val explanation: String,
    val requiresInternet: Boolean = false
)

object UltraMathEngine {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun solve(transcript: String): UltraMathSolution? {
        val clean = normalize(transcript)
        if (clean.isBlank()) return null

        solveTriangle(clean)?.let { return it }
        solveLinearEquation(clean)?.let { return it }
        solvePercentage(clean)?.let { return it }
        solveUnitConversion(clean)?.let { return it }
        solveArithmetic(clean)?.let { return it }
        return null
    }

    private fun solveTriangle(clean: String): UltraMathSolution? {
        if (!clean.contains("triangulo") && !clean.contains("triangle")) {
            return null
        }

        val explicitAngles = Regex(
            """(-?\d+(?:[.,]\d+)?)\s*(?:grados?|degrees?|°)"""
        ).findAll(clean)
            .mapNotNull { match -> match.groupValues[1].toDecimalOrNull() }
            .take(2)
            .toList()

        val angles = if (explicitAngles.size >= 2) {
            explicitAngles
        } else if (clean.contains("angulo") || clean.contains("angle")) {
            Regex("""-?\d+(?:[.,]\d+)?""")
                .findAll(clean)
                .mapNotNull { match -> match.value.toDecimalOrNull() }
                .take(2)
                .toList()
        } else {
            explicitAngles
        }

        if (angles.size < 2) return null
        val third = BigDecimal("180")
            .subtract(angles[0], mathContext)
            .subtract(angles[1], mathContext)
        if (third <= BigDecimal.ZERO) return null

        val formatted = formatNumber(third)
        return UltraMathSolution(
            resultText = "$formatted°",
            explanation = "Los ángulos de un triángulo suman 180°. 180 - ${formatNumber(angles[0])} - ${formatNumber(angles[1])} = $formatted."
        )
    }

    private fun solveLinearEquation(clean: String): UltraMathSolution? {
        val match = Regex(
            """(-?\d+(?:[.,]\d+)?)\s*x\s*([+-])\s*(\d+(?:[.,]\d+)?)\s*=\s*(-?\d+(?:[.,]\d+)?)"""
        ).find(clean) ?: return null

        val coefficient = match.groupValues[1].toDecimalOrNull() ?: return null
        if (coefficient.compareTo(BigDecimal.ZERO) == 0) return null
        val operator = match.groupValues[2]
        val constant = match.groupValues[3].toDecimalOrNull() ?: return null
        val right = match.groupValues[4].toDecimalOrNull() ?: return null

        val isolated = if (operator == "+") {
            right.subtract(constant, mathContext)
        } else {
            right.add(constant, mathContext)
        }
        val x = isolated.divide(coefficient, mathContext)
        val formatted = formatNumber(x)
        return UltraMathSolution(
            resultText = "x = $formatted",
            explanation = "${formatNumber(coefficient)}x $operator ${formatNumber(constant)} = ${formatNumber(right)}; al despejar, x = $formatted."
        )
    }

    private fun solvePercentage(clean: String): UltraMathSolution? {
        val match = Regex(
            """(-?\d+(?:[.,]\d+)?)\s*(?:%|por\s+ciento|percent)\s*(?:de|of)\s*(-?\d+(?:[.,]\d+)?)"""
        ).find(clean) ?: return null

        val percentage = match.groupValues[1].toDecimalOrNull() ?: return null
        val base = match.groupValues[2].toDecimalOrNull() ?: return null
        val result = base
            .multiply(percentage, mathContext)
            .divide(BigDecimal("100"), mathContext)

        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = "${formatNumber(percentage)}% de ${formatNumber(base)} = ${formatNumber(result)}."
        )
    }

    private fun solveUnitConversion(clean: String): UltraMathSolution? {
        val match = Regex(
            """(-?\d+(?:[.,]\d+)?)\s*(kilometros?|km|metros?|m|centimetros?|cm|milimetros?|mm|kilogramos?|kg|gramos?|g|litros?|l|mililitros?|ml)\s*(?:a|en|to)\s*(kilometros?|km|metros?|m|centimetros?|cm|milimetros?|mm|kilogramos?|kg|gramos?|g|litros?|l|mililitros?|ml)"""
        ).find(clean) ?: return null

        val value = match.groupValues[1].toDecimalOrNull() ?: return null
        val from = canonicalUnit(match.groupValues[2]) ?: return null
        val to = canonicalUnit(match.groupValues[3]) ?: return null
        if (from.dimension != to.dimension) return null

        val baseValue = value.multiply(from.toBaseFactor, mathContext)
        val converted = baseValue.divide(to.toBaseFactor, mathContext)
        return UltraMathSolution(
            resultText = "${formatNumber(converted)} ${to.symbol}",
            explanation = "${formatNumber(value)} ${from.symbol} = ${formatNumber(converted)} ${to.symbol}."
        )
    }

    private data class UnitDefinition(
        val symbol: String,
        val dimension: String,
        val toBaseFactor: BigDecimal
    )

    private fun canonicalUnit(raw: String): UnitDefinition? =
        when (raw) {
            "kilometro", "kilometros", "km" ->
                UnitDefinition("km", "length", BigDecimal("1000"))
            "metro", "metros", "m" ->
                UnitDefinition("m", "length", BigDecimal.ONE)
            "centimetro", "centimetros", "cm" ->
                UnitDefinition("cm", "length", BigDecimal("0.01"))
            "milimetro", "milimetros", "mm" ->
                UnitDefinition("mm", "length", BigDecimal("0.001"))
            "kilogramo", "kilogramos", "kg" ->
                UnitDefinition("kg", "mass", BigDecimal("1000"))
            "gramo", "gramos", "g" ->
                UnitDefinition("g", "mass", BigDecimal.ONE)
            "litro", "litros", "l" ->
                UnitDefinition("l", "volume", BigDecimal("1000"))
            "mililitro", "mililitros", "ml" ->
                UnitDefinition("ml", "volume", BigDecimal.ONE)
            else -> null
        }

    private fun solveArithmetic(clean: String): UltraMathSolution? {
        val patterns = listOf(
            ArithmeticPattern(
                regex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:x|\*|por|times)\s*(-?\d+(?:[.,]\d+)?)"""),
                operation = { a, b -> a.multiply(b, mathContext) },
                symbol = "×"
            ),
            ArithmeticPattern(
                regex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:/|dividido\s+por|divided\s+by)\s*(-?\d+(?:[.,]\d+)?)"""),
                operation = { a, b -> if (b.compareTo(BigDecimal.ZERO) == 0) null else a.divide(b, mathContext) },
                symbol = "÷"
            ),
            ArithmeticPattern(
                regex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:\+|mas|plus)\s*(-?\d+(?:[.,]\d+)?)"""),
                operation = { a, b -> a.add(b, mathContext) },
                symbol = "+"
            ),
            ArithmeticPattern(
                regex = Regex("""(-?\d+(?:[.,]\d+)?)\s*(?:-|menos|minus)\s*(-?\d+(?:[.,]\d+)?)"""),
                operation = { a, b -> a.subtract(b, mathContext) },
                symbol = "−"
            )
        )

        for (pattern in patterns) {
            val match = pattern.regex.find(clean) ?: continue
            val left = match.groupValues[1].toDecimalOrNull() ?: continue
            val right = match.groupValues[2].toDecimalOrNull() ?: continue
            val result = pattern.operation(left, right) ?: continue
            return UltraMathSolution(
                resultText = formatNumber(result),
                explanation = "${formatNumber(left)} ${pattern.symbol} ${formatNumber(right)} = ${formatNumber(result)}."
            )
        }
        return null
    }

    private data class ArithmeticPattern(
        val regex: Regex,
        val operation: (BigDecimal, BigDecimal) -> BigDecimal?,
        val symbol: String
    )

    private fun String.toDecimalOrNull(): BigDecimal? =
        replace(',', '.').toBigDecimalOrNull()

    private fun formatNumber(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('×', 'x')
            .replace('÷', '/')
            .replace(Regex("[^a-z0-9+\\-*/=.,° ]"), " ")
            .replace(Regex("^\\s*(?:gamehub\\s+ultra|gamehub|ultra)\\s*[,;:.-]?\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
