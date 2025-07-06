package kr.toxicity.hud.equation

import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.location.LocationGroup
import kr.toxicity.hud.location.GuiLocation

class EquationPairLocation(
    private val duration: Int,
    private val gui: EquationPair,
    private val pixel: EquationTriple
) {
    companion object {
        val zero = EquationPairLocation(1, EquationPair.zero, EquationTriple.zero)
    }
    
    // 提供 duration 的 getter
    fun getDuration(): Int = duration
    
    // 檢查是否使用了 total 變數
    val needsDynamicPositioning: Boolean by lazy {
        gui.x.usesTotalVariable() || 
        gui.y.usesTotalVariable() || 
        pixel.x.usesTotalVariable() || 
        pixel.y.usesTotalVariable() || 
        pixel.opacity.usesTotalVariable()
    }
    
    // 保留原始的預計算位置以維持向後兼容性
    val locations = (1..duration).map {
        val d = it.toDouble()
        val eval1 = gui evaluate d
        val eval2 = pixel evaluate d
        LocationGroup(
            GuiLocation(eval1.first, eval1.second),
            PixelLocation(eval2.first.toInt(), eval2.second.toInt(), eval2.third)
        )
    }
    
    // 新增動態計算位置的方法，支援 total count
    fun getLocationAt(timeIndex: Int, totalCount: Int = 0): LocationGroup {
        val d = timeIndex.toDouble()
        val additionalVariables = if (totalCount > 0) mapOf("total" to totalCount.toDouble()) else emptyMap()
        val eval1 = gui.evaluate(d, additionalVariables)
        val eval2 = pixel.evaluate(d, additionalVariables)
        return LocationGroup(
            GuiLocation(eval1.first, eval1.second),
            PixelLocation(eval2.first.toInt(), eval2.second.toInt(), eval2.third)
        )
    }
    
    // 動態生成所有位置（基於 total count）
    fun getLocationsWithTotal(totalCount: Int): List<LocationGroup> {
        println("[DEBUG] EquationPairLocation.getLocationsWithTotal called with totalCount=$totalCount, duration=$duration")
        return (1..duration).map { timeIndex ->
            val location = getLocationAt(timeIndex, totalCount)
            println("[DEBUG] EquationPairLocation.getLocationsWithTotal: timeIndex=$timeIndex -> gui=(${location.gui.x}, ${location.gui.y}), pixel=(${location.pixel.x}, ${location.pixel.y}, ${location.pixel.opacity})")
            location
        }.also {
            println("[DEBUG] EquationPairLocation.getLocationsWithTotal: Generated ${it.size} locations")
        }
    }

    constructor(section: YamlObject): this(
        section.getAsInt("duration", 1).coerceAtLeast(1),
        section["gui"]?.asObject()?.let {
            EquationPair(it)
        } ?: EquationPair.zero,
        section["pixel"]?.asObject()?.let {
            EquationTriple(it)
        } ?: EquationTriple.zero
    )
}