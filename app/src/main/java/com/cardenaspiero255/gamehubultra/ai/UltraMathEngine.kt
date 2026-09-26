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
        solveFractionExpression(clean)?.let { return it }
        solvePower(clean)?.let { return it }
        solveSquareRoot(clean)?.let { return it }
        solveRuleOfThree(clean)?.let { return it }
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
        if (angles.any { it <= BigDecimal.ZERO }) return null
        val angleSum = angles[0].add(angles[1], mathContext)
        if (angleSum >= BigDecimal("180")) return null
        val third = BigDecimal("180").subtract(angleSum, mathContext)

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

    private fun solveFractionExpression(clean: String): UltraMathSolution? {
        val wordPattern = Regex(
            """(?:suma\s+)?(un|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|\d+)\s+(medio|medios|tercio|tercios|cuarto|cuartos|quinto|quintos|sexto|sextos|septimo|septimos|octavo|octavos|noveno|novenos)\s*(?:\+|mas|y)\s*(un|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|\d+)\s+(medio|medios|tercio|tercios|cuarto|cuartos|quinto|quintos|sexto|sextos|septimo|septimos|octavo|octavos|noveno|novenos)"""
        )
        val wordMatch = wordPattern.find(clean)
        if (wordMatch != null) {
            val left = fractionFromWords(wordMatch.groupValues[1], wordMatch.groupValues[2])
                ?: return null
            val right = fractionFromWords(wordMatch.groupValues[3], wordMatch.groupValues[4])
                ?: return null
            val result = left.add(right, mathContext)
            return UltraMathSolution(
                resultText = formatNumber(result),
                explanation = "La suma de las fracciones es ${formatNumber(result)}."
            )
        }

        val numericMatch = Regex(
            """(-?\d+)\s*/\s*(\d+)\s*(?:\+|mas|plus)\s*(-?\d+)\s*/\s*(\d+)"""
        ).find(clean) ?: return null
        val leftDenominator = numericMatch.groupValues[2].toDecimalOrNull() ?: return null
        val rightDenominator = numericMatch.groupValues[4].toDecimalOrNull() ?: return null
        if (
            leftDenominator.compareTo(BigDecimal.ZERO) == 0 ||
            rightDenominator.compareTo(BigDecimal.ZERO) == 0
        ) return null
        val left = numericMatch.groupValues[1].toDecimalOrNull()
            ?.divide(leftDenominator, mathContext) ?: return null
        val right = numericMatch.groupValues[3].toDecimalOrNull()
            ?.divide(rightDenominator, mathContext) ?: return null
        val result = left.add(right, mathContext)
        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = "La suma de las fracciones es ${formatNumber(result)}."
        )
    }

    private fun fractionFromWords(
        numeratorText: String,
        denominatorText: String
    ): BigDecimal? {
        val numerator = when (numeratorText) {
            "un", "uno" -> BigDecimal.ONE
            "dos" -> BigDecimal("2")
            "tres" -> BigDecimal("3")
            "cuatro" -> BigDecimal("4")
            "cinco" -> BigDecimal("5")
            "seis" -> BigDecimal("6")
            "siete" -> BigDecimal("7")
            "ocho" -> BigDecimal("8")
            "nueve" -> BigDecimal("9")
            else -> numeratorText.toDecimalOrNull()
        } ?: return null
        val denominator = when (denominatorText) {
            "medio", "medios" -> BigDecimal("2")
            "tercio", "tercios" -> BigDecimal("3")
            "cuarto", "cuartos" -> BigDecimal("4")
            "quinto", "quintos" -> BigDecimal("5")
            "sexto", "sextos" -> BigDecimal("6")
            "septimo", "septimos" -> BigDecimal("7")
            "octavo", "octavos" -> BigDecimal("8")
            "noveno", "novenos" -> BigDecimal("9")
            else -> return null
        }
        return numerator.divide(denominator, mathContext)
    }

    private fun solvePower(clean: String): UltraMathSolution? {
        val match = Regex(
            """(-?\d+(?:[.,]\d+)?)\s*(?:\^|elevado\s+a|raised\s+to)\s*(-?\d+)"""
        ).find(clean) ?: return null
        val base = match.groupValues[1].toDecimalOrNull() ?: return null
        val exponent = match.groupValues[2].toIntOrNull() ?: return null
        if (kotlin.math.abs(exponent) > 1000) return null

        val result = if (exponent >= 0) {
            base.pow(exponent, mathContext)
        } else {
            val positive = base.pow(-exponent, mathContext)
            if (positive.compareTo(BigDecimal.ZERO) == 0) return null
            BigDecimal.ONE.divide(positive, mathContext)
        }
        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = "${formatNumber(base)} elevado a $exponent = ${formatNumber(result)}."
        )
    }

    private fun solveSquareRoot(clean: String): UltraMathSolution? {
        val match = Regex(
            """(?:raiz\s+cuadrada\s+de|square\s+root\s+of)\s*(-?\d+(?:[.,]\d+)?)"""
        ).find(clean) ?: return null
        val value = match.groupValues[1].toDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        val root = kotlin.math.sqrt(value.toDouble())
        if (!root.isFinite()) return null
        val result = BigDecimal.valueOf(root).round(mathContext)
        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = "La raíz cuadrada de ${formatNumber(value)} es ${formatNumber(result)}."
        )
    }

    private fun solveRuleOfThree(clean: String): UltraMathSolution? {
        val match = Regex(
            """si\s+(-?\d+(?:[.,]\d+)?)\s+(?:cuestan|valen|son)\s+(-?\d+(?:[.,]\d+)?)\s+cuanto\s+(?:cuestan|valen|son)\s+(-?\d+(?:[.,]\d+)?)"""
        ).find(clean) ?: return null

        val knownQuantity = match.groupValues[1].toDecimalOrNull() ?: return null
        val knownValue = match.groupValues[2].toDecimalOrNull() ?: return null
        val targetQuantity = match.groupValues[3].toDecimalOrNull() ?: return null
        if (knownQuantity.compareTo(BigDecimal.ZERO) == 0) return null

        val result = knownValue
            .multiply(targetQuantity, mathContext)
            .divide(knownQuantity, mathContext)
        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = "${formatNumber(knownValue)} × ${formatNumber(targetQuantity)} ÷ ${formatNumber(knownQuantity)} = ${formatNumber(result)}."
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
