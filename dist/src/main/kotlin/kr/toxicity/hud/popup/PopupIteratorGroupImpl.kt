package kr.toxicity.hud.popup

import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.popup.PopupIterator
import kr.toxicity.hud.api.popup.PopupIteratorGroup
import kr.toxicity.hud.api.popup.PopupSortType
import java.util.*

class PopupIteratorGroupImpl : PopupIteratorGroup {
    private val sourceSet = TreeSet<PopupIterator>()
    private var lastTotalCount = 0
    
    // 更新所有 iterator 的 total count
    private fun updateTotalCount() {
        val currentTotalCount = sourceSet.size
        println("[DEBUG] PopupIteratorGroupImpl.updateTotalCount: currentSize=$currentTotalCount, lastCount=$lastTotalCount")
        if (lastTotalCount != currentTotalCount) {
            lastTotalCount = currentTotalCount
            // 只對需要動態定位的 PopupIteratorImpl 更新 total count
            var updatedCount = 0
            sourceSet.forEach { iterator ->
                if (iterator is PopupIteratorImpl) {
                    if (iterator.needsDynamicPositioning()) {
                        println("[DEBUG] PopupIteratorGroupImpl.updateTotalCount: Updating total count for iterator ${iterator.name()} (needs dynamic positioning)")
                        iterator.setTotalCount(currentTotalCount)
                        updatedCount++
                    } else {
                        println("[DEBUG] PopupIteratorGroupImpl.updateTotalCount: Skipping iterator ${iterator.name()} (static positioning)")
                    }
                } else {
                    println("[DEBUG] PopupIteratorGroupImpl.updateTotalCount: Skipping non-PopupIteratorImpl: ${iterator.javaClass.simpleName}")
                }
            }
            println("[DEBUG] PopupIteratorGroupImpl.updateTotalCount: Updated $updatedCount out of ${sourceSet.size} iterators")
        }
    }

    override fun addIterator(iterator: PopupIterator) {
        synchronized(sourceSet) {
            println("[DEBUG] PopupIteratorGroupImpl.addIterator: Adding iterator ${iterator.name()}, unique=${iterator.isUnique}")
            if (iterator.isUnique && contains(iterator.name())) {
                println("[DEBUG] PopupIteratorGroupImpl.addIterator: Skipping unique iterator ${iterator.name()}, already exists")
                return
            }
            val map = HashSet<Int>()
            val loop = sourceSet.iterator()
            while (loop.hasNext()) {
                val next = loop.next()
                if (next.markedAsRemoval()) {
                    println("[DEBUG] PopupIteratorGroupImpl.addIterator: Removing marked iterator ${next.name()}")
                    next.remove()
                } else {
                    map += next.index
                }
            }
            val i = if (iterator.index < 0) when (iterator.sortType) {
                PopupSortType.FIRST -> 0
                PopupSortType.LAST -> run {
                    var i = 0
                    while (map.contains(i)) i++
                    i
                }
            } else iterator.index
            iterator.index = i
            println("[DEBUG] PopupIteratorGroupImpl.addIterator: Assigned index $i to iterator ${iterator.name()}")
            
            if (map.contains(i)) {
                println("[DEBUG] PopupIteratorGroupImpl.addIterator: Index $i already exists, shifting existing iterators")
                var t = 0
                var biggest = i
                val more = sourceSet.filter {
                    it.index >= i
                }
                val newValue = ArrayList<PopupIterator>(more.size)
                while (t < more.size && more[t].index >= biggest) {
                    val get = more[t++]
                    if (sourceSet.remove(get)) {
                        val oldIndex = get.index
                        biggest = ++get.index
                        println("[DEBUG] PopupIteratorGroupImpl.addIterator: Shifted iterator ${get.name()} from index $oldIndex to ${get.index}")
                        newValue += get
                    }
                }
                if (newValue.isNotEmpty()) sourceSet.addAll(newValue)
            }
            if (iterator.index >= iterator.parent().maxStack && iterator.push()) {
                val minus = iterator.index - iterator.parent().maxStack + 1
                println("[DEBUG] PopupIteratorGroupImpl.addIterator: Index ${iterator.index} exceeds maxStack ${iterator.parent().maxStack}, pushing down by $minus")
                sourceSet.removeIf {
                    if (it.priority < 0) {
                        val oldIndex = it.index
                        it.index -= minus
                        println("[DEBUG] PopupIteratorGroupImpl.addIterator: Pushed iterator ${it.name()} from $oldIndex to ${it.index}")
                        it.index < 0
                    } else false
                }
                iterator.index -= minus
                println("[DEBUG] PopupIteratorGroupImpl.addIterator: Final index for ${iterator.name()}: ${iterator.index}")
            }
            sourceSet += iterator
            println("[DEBUG] PopupIteratorGroupImpl.addIterator: Total iterators after add: ${sourceSet.size}")
            
            // 更新 total count
            updateTotalCount()
        }
    }

    override fun clear() {
        synchronized(sourceSet) {
            sourceSet.forEach {
                it.remove()
            }
            sourceSet.clear()
            
            // 更新 total count
            updateTotalCount()
        }
    }

    private fun checkCondition(iterator: PopupIterator): Boolean {
        if (iterator.markedAsRemoval()) {
            return false
        }
        if (iterator.index < 0 || !iterator.available()) return false
        return true
    }

    override fun next(): List<WidthComponent> {
        synchronized(sourceSet) {
            println("[DEBUG] PopupIteratorGroupImpl.next: Starting with ${sourceSet.size} iterators")
            
            // 主動更新 total count，確保即使沒有新增/移除 iterator 也會檢查
            updateTotalCount()
            
            val copy = sourceSet.toList()
            val result = ArrayList<WidthComponent>()
            var i = 0
            val removedAny = sourceSet.removeIf { next ->
                i++
                val shouldRemove = (if (next.index > next.maxIndex) {
                    val remove = !next.canSave() || (next.alwaysCheckCondition() && !next.available())
                    if (remove) {
                        println("[DEBUG] PopupIteratorGroupImpl.next: Removing iterator ${next.name()} - index ${next.index} > maxIndex ${next.maxIndex}")
                    }
                    remove
                } else if (checkCondition(next)) {
                    println("[DEBUG] PopupIteratorGroupImpl.next: Processing iterator ${next.name()} at index ${next.index}")
                    val components = next.next()
                    println("[DEBUG] PopupIteratorGroupImpl.next: Iterator ${next.name()} returned ${components.size} components")
                    result += components
                    false
                } else {
                    println("[DEBUG] PopupIteratorGroupImpl.next: Removing iterator ${next.name()} - failed condition check")
                    true
                })
                
                if (shouldRemove) {
                    next.remove()
                    println("[DEBUG] PopupIteratorGroupImpl.next: Shifting down indices for remaining iterators")
                    copy.subList(i, copy.size).forEach { remainingIterator ->
                        val oldIndex = remainingIterator.index
                        remainingIterator.index--
                        println("[DEBUG] PopupIteratorGroupImpl.next: Shifted iterator ${remainingIterator.name()} from $oldIndex to ${remainingIterator.index}")
                    }
                }
                shouldRemove
            }
            
            // 如果有 iterator 被移除，再次更新 total count
            if (removedAny) {
                println("[DEBUG] PopupIteratorGroupImpl.next: Some iterators were removed, updating total count again")
                updateTotalCount()
            }
            
            println("[DEBUG] PopupIteratorGroupImpl.next: Returning ${result.size} components from ${sourceSet.size} remaining iterators")
            return result
        }
    }

    override fun contains(name: String): Boolean {
        return synchronized(sourceSet) {
            sourceSet.any {
                it.name() == name
            }
        }
    }
    override fun getIndex(): Int = sourceSet.size
}