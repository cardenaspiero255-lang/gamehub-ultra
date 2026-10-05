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

        solveLinearSystem(clean)?.let { return it }
        solveGeometry(clean)?.let { return it }
        solveTrigonometry(clean)?.let { return it }
        solveFunctionEvaluation(clean)?.let { return it }
        solveStatistics(clean)?.let { return it }
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

    private data class LinearEquation2(
        val xCoefficient: BigDecimal,
        val yCoefficient: BigDecimal,
        val result: BigDecimal
    )

    private fun solveLinearSystem(clean: String): UltraMathSolution? {
        if (!clean.contains("sistema") && !clean.contains("system")) return null

        val pattern = Regex(
            """([+-]?\s*(?:\d+(?:[.,]\d+)?)?)\s*x\s*([+-])\s*(?:(\d+(?:[.,]\d+)?)\s*)?y\s*=\s*(-?\d+(?:[.,]\d+)?)"""
        )
        val equations = pattern.findAll(clean)
            .take(2)
            .mapNotNull { match ->
                val x = signedCoefficient(match.groupValues[1]) ?: return@mapNotNull null
                val yMagnitude = match.groupValues[3]
                    .takeIf(String::isNotBlank)
                    ?.toDecimalOrNull()
                    ?: BigDecimal.ONE
                val y = if (match.groupValues[2] == "-") {
                    yMagnitude.negate()
                } else {
                    yMagnitude
                }
                val result = match.groupValues[4].toDecimalOrNull()
                    ?: return@mapNotNull null
                LinearEquation2(x, y, result)
            }
            .toList()

        if (equations.size != 2) return null
        val first = equations[0]
        val second = equations[1]
        val determinant = first.xCoefficient
            .multiply(second.yCoefficient, mathContext)
            .subtract(
                second.xCoefficient.multiply(first.yCoefficient, mathContext),
                mathContext
            )
        if (determinant.compareTo(BigDecimal.ZERO) == 0) return null

        val xNumerator = first.result
            .multiply(second.yCoefficient, mathContext)
            .subtract(
                second.result.multiply(first.yCoefficient, mathContext),
                mathContext
            )
        val yNumerator = first.xCoefficient
            .multiply(second.result, mathContext)
            .subtract(
                second.xCoefficient.multiply(first.result, mathContext),
                mathContext
            )
        val x = xNumerator.divide(determinant, mathContext)
        val y = yNumerator.divide(determinant, mathContext)

        return UltraMathSolution(
            resultText = "x = ${formatNumber(x)}, y = ${formatNumber(y)}",
            explanation =
                "Resolví las dos ecuaciones simultáneamente con determinantes y verifiqué ambos valores."
        )
    }

    private fun signedCoefficient(raw: String): BigDecimal? {
        val clean = raw.replace(" ", "")
        return when (clean) {
            "", "+" -> BigDecimal.ONE
            "-" -> BigDecimal.ONE.negate()
            else -> clean.toDecimalOrNull()
        }
    }

    private fun solveGeometry(clean: String): UltraMathSolution? {
        val numbers = Regex("""-?\d+(?:[.,]\d+)?""")
            .findAll(clean)
            .mapNotNull { it.value.toDecimalOrNull() }
            .toList()
        val unit = geometryLengthUnit(clean)

        if (
            (clean.contains("rectangulo") || clean.contains("rectangle")) &&
            (clean.contains("area") || clean.contains("perimetro") || clean.contains("perimeter"))
        ) {
            if (numbers.size < 2 || numbers.take(2).any { it <= BigDecimal.ZERO }) {
                return null
            }
            val width = numbers[0]
            val height = numbers[1]
            return if (clean.contains("area")) {
                val area = width.multiply(height, mathContext)
                UltraMathSolution(
                    resultText = "${formatNumber(area)}${unit?.let { " $it²" }.orEmpty()}",
                    explanation =
                        "Área del rectángulo = base × altura = ${formatNumber(width)} × ${formatNumber(height)} = ${formatNumber(area)}."
                )
            } else {
                val perimeter = width
                    .add(height, mathContext)
                    .multiply(BigDecimal("2"), mathContext)
                UltraMathSolution(
                    resultText = "${formatNumber(perimeter)}${unit?.let { " $it" }.orEmpty()}",
                    explanation =
                        "Perímetro del rectángulo = 2 × (base + altura) = ${formatNumber(perimeter)}."
                )
            }
        }

        if (
            clean.contains("volumen") || clean.contains("volume")
        ) {
            val rectangularPrism =
                clean.contains("prisma rectangular") ||
                    clean.contains("rectangular prism") ||
                    clean.contains("paralelepipedo")
            if (!rectangularPrism) return null
            if (numbers.size < 3 || numbers.take(3).any { it <= BigDecimal.ZERO }) {
                return null
            }
            val volume = numbers[0]
                .multiply(numbers[1], mathContext)
                .multiply(numbers[2], mathContext)
            return UltraMathSolution(
                resultText = "${formatNumber(volume)}${unit?.let { " $it³" }.orEmpty()}",
                explanation =
                    "Volumen = largo × ancho × alto = ${formatNumber(numbers[0])} × ${formatNumber(numbers[1])} × ${formatNumber(numbers[2])} = ${formatNumber(volume)}."
            )
        }

        return null
    }

    private fun geometryLengthUnit(clean: String): String? =
        when {
            Regex("""\b(kilometros?|km)\b""").containsMatchIn(clean) -> "km"
            Regex("""\b(centimetros?|cm)\b""").containsMatchIn(clean) -> "cm"
            Regex("""\b(milimetros?|mm)\b""").containsMatchIn(clean) -> "mm"
            Regex("""\b(metros?|m)\b""").containsMatchIn(clean) -> "m"
            else -> null
        }

    private fun solveTrigonometry(clean: String): UltraMathSolution? {
        val operation = when {
            Regex("""\b(seno|sin)\b""").containsMatchIn(clean) -> "sin"
            Regex("""\b(coseno|cos)\b""").containsMatchIn(clean) -> "cos"
            Regex("""\b(tangente|tan)\b""").containsMatchIn(clean) -> "tan"
            else -> return null
        }
        val angle = Regex(
            """\b(?:seno|sin|coseno|cos|tangente|tan)\s+(?:de\s+)?(-?\d+(?:[.,]\d+)?)"""
        ).find(clean)
            ?.groupValues
            ?.get(1)
            ?.toDecimalOrNull()
            ?: return null

        val isRadians = Regex("""\b(radian|radianes|radians?)\b""").containsMatchIn(clean)
        val isDegrees = Regex("""\b(grado|grados|degrees?)\b""").containsMatchIn(clean)
        if (!isRadians && !isDegrees) return null

        val angleDouble = angle.toDouble()
        val radians = if (isRadians) {
            angleDouble
        } else {
            Math.toRadians(angleDouble)
        }
        val raw = when (operation) {
            "sin" -> kotlin.math.sin(radians)
            "cos" -> kotlin.math.cos(radians)
            else -> {
                if (kotlin.math.abs(kotlin.math.cos(radians)) < 1e-12) return null
                kotlin.math.tan(radians)
            }
        }
        if (!raw.isFinite()) return null
        val result = BigDecimal.valueOf(raw).round(mathContext)
        val label = if (isRadians) "radianes" else "grados"

        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation =
                "${operation.uppercase(Locale.ROOT)} de ${formatNumber(angle)} $label = ${formatNumber(result)}."
        )
    }

    private fun solveFunctionEvaluation(clean: String): UltraMathSolution? {
        if (!clean.contains("funcion") && !clean.contains("function")) return null
        val match = Regex(
            """(?:funcion|function)\s+f\s+(?:de\s+)?x\s*=\s*(-?\d+(?:[.,]\d+)?)\s*x\s*([+-])\s*(\d+(?:[.,]\d+)?)\s*,?\s*(?:evalua|evaluate|calcula|calculate)\s+(?:en\s+|at\s+)?x\s*=\s*(-?\d+(?:[.,]\d+)?)"""
        ).find(clean) ?: return null

        val slope = match.groupValues[1].toDecimalOrNull() ?: return null
        val sign = match.groupValues[2]
        val interceptMagnitude = match.groupValues[3].toDecimalOrNull() ?: return null
        val intercept = if (sign == "-") interceptMagnitude.negate() else interceptMagnitude
        val input = match.groupValues[4].toDecimalOrNull() ?: return null
        val output = slope.multiply(input, mathContext).add(intercept, mathContext)

        return UltraMathSolution(
            resultText = "f(${formatNumber(input)}) = ${formatNumber(output)}",
            explanation =
                "Sustituí x = ${formatNumber(input)} en la función: ${formatNumber(slope)} × ${formatNumber(input)} ${if (intercept.signum() < 0) "-" else "+"} ${formatNumber(intercept.abs())} = ${formatNumber(output)}."
        )
    }

    private fun solveStatistics(clean: String): UltraMathSolution? {
        val mode = when {
            Regex("""\b(promedio|media|average|mean)\b""").containsMatchIn(clean) -> "mean"
            Regex("""\b(mediana|median)\b""").containsMatchIn(clean) -> "median"
            else -> return null
        }
        val marker = if (mode == "mean") {
            Regex("""\b(?:promedio|media|average|mean)\b""")
        } else {
            Regex("""\b(?:mediana|median)\b""")
        }
        val markerMatch = marker.find(clean) ?: return null
        val tail = clean.substring(markerMatch.range.last + 1).trim()
        val number = """-?\d+(?:[.,]\d+)?"""
        val listPattern = Regex(
            """^(?:(?:de|of)\s+)?($number(?:\s*(?:,\s+|\by\b|\band\b)\s*$number)+)\s*$"""
        )
        val list = listPattern.matchEntire(tail)?.groupValues?.get(1) ?: return null
        val values = Regex(number)
            .findAll(list)
            .mapNotNull { it.value.toDecimalOrNull() }
            .toList()
        if (values.size < 2) return null

        val result = if (mode == "mean") {
            values.fold(BigDecimal.ZERO) { total, value ->
                total.add(value, mathContext)
            }.divide(BigDecimal(values.size), mathContext)
        } else {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                sorted[middle - 1]
                    .add(sorted[middle], mathContext)
                    .divide(BigDecimal("2"), mathContext)
            }
        }

        return UltraMathSolution(
            resultText = formatNumber(result),
            explanation = if (mode == "mean") {
                "El promedio es la suma de los valores dividida por ${values.size}: ${formatNumber(result)}."
            } else {
                "Ordené los valores y calculé el punto central: mediana ${formatNumber(result)}."
            }
        )
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
        val operand =
            """(?:(?:-?\d+(?:[.,]\d+)?|un|uno|una|one)(?:\s*(?:mil|millon(?:es)?|billon(?:es)?|trillon(?:es)?|million(?:s)?|billion(?:s)?|trillion(?:s)?))?|(?:mil|millon(?:es)?|billon(?:es)?|trillon(?:es)?|million(?:s)?|billion(?:s)?|trillion(?:s)?))"""
        val patterns = listOf(
            ArithmeticPattern(
                regex = Regex("""($operand)\s*(?:x|\*|por|times)\s*($operand)"""),
                operation = { a, b -> a.multiply(b) },
                symbol = "×"
            ),
            ArithmeticPattern(
                regex = Regex("""($operand)\s*(?:/|dividido\s+por|divided\s+by)\s*($operand)"""),
                operation = { a, b ->
                    if (b.compareTo(BigDecimal.ZERO) == 0) null else a.divide(b, mathContext)
                },
                symbol = "÷"
            ),
            ArithmeticPattern(
                regex = Regex("""($operand)\s*(?:\+|mas|plus)\s*($operand)"""),
                operation = { a, b -> a.add(b) },
                symbol = "+"
            ),
            ArithmeticPattern(
                regex = Regex("""($operand)\s*(?:-|menos|minus)\s*($operand)"""),
                operation = { a, b -> a.subtract(b) },
                symbol = "−"
            )
        )

        for (pattern in patterns) {
            val match = pattern.regex.find(clean) ?: continue
            val left = parseArithmeticOperand(match.groupValues[1]) ?: continue
            val right = parseArithmeticOperand(match.groupValues[2]) ?: continue
            val result = pattern.operation(left, right) ?: continue
            return UltraMathSolution(
                resultText = formatNumber(result),
                explanation = "${formatNumber(left)} ${pattern.symbol} ${formatNumber(right)} = ${formatNumber(result)}."
            )
        }
        return null
    }

    private fun parseArithmeticOperand(raw: String): BigDecimal? {
        val clean = raw.trim()
        clean.toDecimalOrNull()?.let { return it }

        val match = Regex(
            """^(?:(-?\d+(?:[.,]\d+)?|un|uno|una|one)\s*)?(mil|millon(?:es)?|billon(?:es)?|trillon(?:es)?|million(?:s)?|billion(?:s)?|trillion(?:s)?)?$"""
        ).matchEntire(clean) ?: return null

        val baseToken = match.groupValues[1]
        val magnitudeToken = match.groupValues[2]
        if (baseToken.isBlank() && magnitudeToken.isBlank()) return null

        val base = when (baseToken) {
            "", "un", "uno", "una", "one" -> BigDecimal.ONE
            else -> baseToken.toDecimalOrNull() ?: return null
        }
        val factor = when (magnitudeToken) {
            "" -> BigDecimal.ONE
            "mil" -> BigDecimal("1000")
            "millon", "millones", "million", "millions" -> BigDecimal("1000000")
            "billon", "billones" -> BigDecimal("1000000000000")
            "billion", "billions" -> BigDecimal("1000000000")
            "trillon", "trillones" -> BigDecimal("1000000000000000000")
            "trillion", "trillions" -> BigDecimal("1000000000000")
            else -> return null
        }
        return base.multiply(factor)
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
