package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.StratagemStore
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedKVAndGameFallbackTest {

    @Test
    fun `parseSelectedStratagemIDs trims blanks and deduplicates`() {
        assertEquals(emptySet(), SharedKVManager.parseSelectedStratagemIDs(null))
        assertEquals(emptySet(), SharedKVManager.parseSelectedStratagemIDs(""))
        assertEquals(
            setOf("eagle_1", "sw_6", "ms_13"),
            SharedKVManager.parseSelectedStratagemIDs(" eagle_1 , sw_6,, sw_6, ms_13 "),
        )
    }

    @Test
    fun `buildStratagemPool falls back to full catalog when selected ids match nothing`() {
        val catalog = StratagemStore.getAllStratagems()
        val pool = GameViewModel.buildStratagemPool(
            catalog = catalog,
            isSelectMode = true,
            selectedIDs = setOf("sw_34_removed_id"),
        )
        assertTrue(catalog.isNotEmpty())
        assertEquals(catalog.size, pool.size)
        assertEquals(catalog.map { it.id }, pool.map { it.id })
    }

    @Test
    fun `buildStratagemPool pads single selection to 10 items`() {
        val catalog = StratagemStore.getAllStratagems()
        val pool = GameViewModel.buildStratagemPool(
            catalog = catalog,
            isSelectMode = true,
            selectedIDs = setOf("eagle_2"),
        )
        assertEquals(10, pool.size)
        assertTrue(pool.all { it.id == "eagle_2" })
    }
}
