package app.foamforandroid.core

import kotlin.test.assertEquals
import kotlin.test.Test

class TemplateTest {
    @Test fun dropsBlankLinesLeftByTheFoamTemplateBlock() {
        val t = Template.parse("---\nfoam_template:\n  filepath: '/dev-notes/\$FOAM_SLUG.md'\n---\n\n# \$FOAM_TITLE\n")
        assertEquals("/dev-notes/\$FOAM_SLUG.md", t.filepath)
        assertEquals("# \$FOAM_TITLE\n", t.body)
    }

    @Test fun keepsOtherFrontMatterAndItsSpacing() {
        val t = Template.parse("---\nfoam_template:\n  filepath: x.md\ntags: daily\n---\n\n# Title\n")
        assertEquals("---\ntags: daily\n---\n\n# Title\n", t.body)
    }
}
