package kr.toxicity.hud.popup

import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.api.popup.Popup.FrameType.*
import kr.toxicity.hud.api.popup.PopupIterator
import kr.toxicity.hud.api.popup.PopupSortType
import kr.toxicity.hud.api.update.PopupUpdateEvent
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.util.EMPTY_WIDTH_COMPONENT
import kr.toxicity.hud.util.Runner
import kr.toxicity.hud.util.runByTick
import java.util.*

class PopupIteratorImpl(
    private val reason: UpdateEvent,
    private val player: HudPlayer,
    private val layouts: List<PopupLayout>,
    private val parent: Popup,
    private val unique: Boolean,
    private val maxIndex: Int,
    private val key: Any,
    private val sortType: PopupSortType,
    private val name: String,
    private val save: Boolean,
    private val push: Boolean,
    private val alwaysCheckCondition: Boolean,
    private var value: Int,
    private val condition: () -> Boolean,
    private val removeTask: () -> Unit,
) : PopupIterator {
    private var tick = 0L
    private var i = -1
    private var removal = false
    private val id = UUID.randomUUID()
    
    private var lastTotalCount = -1
    private var useDynamicPositioning = false

    private val valueMap = run {
        val newReason = PopupUpdateEvent(reason, this)
        layouts.map {
            it.getComponent(newReason) {
                tick
            }
        }
    }
    
    private var dynamicValueMap: List<(HudPlayer, Int) -> Runner<WidthComponent>>? = null

    override fun parent(): Popup = parent

    override fun getMaxIndex(): Int = maxIndex
    override fun getUUID(): UUID = id

    override fun getSortType(): PopupSortType {
        return sortType
    }

    override fun isUnique(): Boolean = unique

    override fun push(): Boolean = push

    override fun canSave(): Boolean = save
    override fun alwaysCheckCondition(): Boolean = alwaysCheckCondition
    override fun getIndex(): Int = i
    override fun setIndex(index: Int) {
        i = index
    }
    override fun available() = condition()
    override fun getKey(): Any = key

    private var _i = -1
    private var _totalCount = -1
    private var _mapper = emptyList<Runner<WidthComponent>>()
    override fun next(): List<WidthComponent> {
        if (_i != i) {
            _i = i
            println("[DEBUG] PopupIteratorImpl.next: Index changed for $name: $_i -> $i, refreshing mapper")
            refreshMapper()
        }
        println("[DEBUG] PopupIteratorImpl.next: Executing ${_mapper.size} mappers for $name at index=$i")
        val r = _mapper.mapIndexed { index, mapper ->
            try {
                val result = mapper()
                println("[DEBUG] PopupIteratorImpl.next: Mapper $index for $name returned component with width=${result.width}")
                result
            } catch (e: Exception) {
                println("[DEBUG] PopupIteratorImpl.next: ERROR in mapper $index for $name: ${e.message}")
                e.printStackTrace()
                EMPTY_WIDTH_COMPONENT
            }
        }
        tick++
        println("[DEBUG] PopupIteratorImpl.next: Returning ${r.size} components for $name")
        return r
    }

    override fun markedAsRemoval(): Boolean = removal
    override fun remove() {
        removal = true
        removeTask()
    }

    override fun getPriority(): Int = value
    override fun setPriority(priority: Int) {
        value = priority
        if (value >= 0) refreshMapper()
    }
    override fun name(): String = name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupIteratorImpl

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun compareTo(other: PopupIterator): Int {
        return i.compareTo(other.index)
    }

    fun needsDynamicPositioning(): Boolean {
        return (parent as? PopupImpl)?.move?.needsDynamicPositioning ?: false
    }

    fun setTotalCount(totalCount: Int) {
        println("[DEBUG] PopupIteratorImpl.setTotalCount called: name=$name, currentTotal=$_totalCount, newTotal=$totalCount")
        if (_totalCount != totalCount) {
            _totalCount = totalCount
            lastTotalCount = totalCount
            useDynamicPositioning = true
            
            println("[DEBUG] PopupIteratorImpl.setTotalCount: Regenerating dynamic value map for $name with total=$totalCount")
            
            val newReason = PopupUpdateEvent(reason, this)
            dynamicValueMap = layouts.map {
                println("[DEBUG] PopupIteratorImpl.setTotalCount: Creating dynamic component for layout")
                it.getComponentWithTotal(newReason, totalCount) {
                    tick
                }
            }
            
            if (_i != -1) {
                println("[DEBUG] PopupIteratorImpl.setTotalCount: Refreshing mapper immediately for $name with index=$_i")
                refreshMapper()
            }
        }
    }

    private fun refreshMapper() {
        val currentValueMap = if (useDynamicPositioning && dynamicValueMap != null) {
            println("[DEBUG] PopupIteratorImpl.refreshMapper: Using DYNAMIC value map for $name (total=$_totalCount)")
            dynamicValueMap!!
        } else {
            println("[DEBUG] PopupIteratorImpl.refreshMapper: Using STATIC value map for $name (useDynamic=$useDynamicPositioning, hasMap=${dynamicValueMap != null})")
            valueMap
        }
        
        println("[DEBUG] PopupIteratorImpl.refreshMapper: Creating mapper for $name with index=${if (value >= 0) value else _i}")
        
        _mapper = currentValueMap.map {
            runByTick(parent.tick(), when (parent.frameType()) {
                GLOBAL -> { { player.tick } }
                LOCAL -> { { tick } }
            }, it(player, if (value >= 0) value else _i))
        }
        
        println("[DEBUG] PopupIteratorImpl.refreshMapper: Created ${_mapper.size} mappers for $name")
    }
}