package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.DOWN
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.LEFT
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.RIGHT
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.UP
import cn.antares.helldiver_trainer.data.wiki.search.WikiDirectionParser
import cn.antares.helldiver_trainer.data.wiki.search.WikiTextNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikiNormalizerAndParserTest {

    @Test
    fun `normalizes fullwidth, quotes, dots, hyphens, and case`() {
        val eagle = WikiTextNormalizer.normalize("“飞鹰”５００ＫＧ炸弹")
        assertEquals("飞鹰500kg炸弹", eagle.text)
        assertEquals("飞鹰500kg炸弹", eagle.compact)

        val wasp = WikiTextNormalizer.normalize("StA-X3 W.A.S.P.发射器")
        assertEquals("stax3 wasp发射器", wasp.text)
        assertEquals("stax3wasp发射器", wasp.compact)

        val sos = WikiTextNormalizer.normalize("S.O.S.求救信标")
        assertEquals("sos求救信标", sos.compact)

        val ar1 = WikiTextNormalizer.normalize("AR-23")
        val ar2 = WikiTextNormalizer.normalize("ar23")
        assertEquals(ar1.compact, ar2.compact)
    }

    @Test
    fun `parses arrows, chinese direction words, and WASD when length is at least 3`() {
        assertEquals(listOf(UP, RIGHT, DOWN, RIGHT), WikiDirectionParser.parseOrNull("↑→↓→"))
        assertEquals(listOf(UP, RIGHT, DOWN, RIGHT), WikiDirectionParser.parseOrNull("w d s d"))
        assertEquals(listOf(UP, RIGHT, DOWN, RIGHT), WikiDirectionParser.parseOrNull("上右下右"))
        assertEquals(
            listOf(LEFT, RIGHT, UP, UP, UP),
            WikiDirectionParser.parseOrNull("LRUUU".let { "adwww" }),
        )
    }

    @Test
    fun `does not misclassify short or non-direction text as directions`() {
        assertNull(WikiDirectionParser.parseOrNull("wasp"))
        assertNull(WikiDirectionParser.parseOrNull("sw"))
        assertNull(WikiDirectionParser.parseOrNull("up"))
        assertNull(WikiDirectionParser.parseOrNull("wd"))
        assertNull(WikiDirectionParser.parseOrNull("ab"))
    }
}
