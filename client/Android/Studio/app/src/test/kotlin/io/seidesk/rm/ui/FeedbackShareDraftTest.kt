package io.seidesk.rm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeedbackShareDraftTest {
    @Test
    fun shareDraftContainsOnlyBrandCategoryAndUserText() {
        val draft = feedbackShareDraft("Feature idea", "Please add a touch mode.")
        assertEquals(
            "SeiDesk RM — Seijixinghe888\n" +
                "Category: Feature idea\n\n" +
                "Please add a touch mode.",
            draft,
        )
        assertFalse(draft.contains("password"))
        assertFalse(draft.contains("clipboard"))
        assertFalse(draft.contains("host"))
    }
}
