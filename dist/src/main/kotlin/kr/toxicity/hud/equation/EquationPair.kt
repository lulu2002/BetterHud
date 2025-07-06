package kr.toxicity.hud.equation

import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.util.ifNull
import kr.toxicity.hud.util.toEquation

class EquationPair(val x: TEquation, val y: TEquation) {
    companion object {
        val zero = EquationPair(TEquation.zero, TEquation.zero)
    }

    infix fun evaluate(d: Double) = evaluate(d, emptyMap())
    
    fun evaluate(d: Double, additionalVariables: Map<String, Double>) = 
        x.evaluate(d, additionalVariables) to y.evaluate(d, additionalVariables)

    constructor(section: YamlObject): this(
        section["x-equation"]?.asString().ifNull { "x-equation value not set." }.toEquation(),
        section["y-equation"]?.asString().ifNull { "y-equation value not set." }.toEquation()
    )
}