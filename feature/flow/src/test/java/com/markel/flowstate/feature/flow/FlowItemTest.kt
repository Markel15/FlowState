package com.markel.flowstate.feature.flow

import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the flat item model that backs the Flow screen
 * ([FlowItem], [FlowUiState.Success.items], [stableKey] and [withItems]).
 *
 * These cover pure mapping logic only — no coroutines, no mocks, no Android
 * dependencies — so they run on the local JVM.
 */
class FlowItemTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun task(id: Int) = Task(id = id, title = "Task $id", isDone = false)

    /**
     * `createdAt` is pinned on purpose: it defaults to `System.currentTimeMillis()`,
     * so two separate calls to `idea(3)` would produce two *unequal* ideas and
     * `items.indexOf(FlowItem.IdeaCard(idea(3)))` would return -1.
     */
    private fun idea(id: Int) = Idea(
        id = id, title = "Idea $id", content = "", createdAt = 0L, color = 0L
    )

    private fun checkList(id: Int) = CheckList(id = id, title = "List $id", color = 0L)

    private fun state(
        tasks: List<Task> = emptyList(),
        ideas: List<Idea> = emptyList(),
        checkLists: List<CheckList> = emptyList(),
    ) = FlowUiState.Success(tasks = tasks, ideas = ideas, checkLists = checkLists)

    // ── items: construcción ────────────────────────────────────────────────────

    @Test
    fun rows_withAllSections_emitsHeadersThenItems_inOrder() {
        val items = state(
            tasks = listOf(task(1), task(2)),
            checkLists = listOf(checkList(1)),
            ideas = listOf(idea(1)),
        ).items

        assertEquals(
            listOf(
                FlowItem.TasksHeader,
                FlowItem.TaskRow(task(1)),
                FlowItem.TaskRow(task(2)),
                FlowItem.CheckListsHeader,
                FlowItem.CheckListCard(checkList(1)),
                FlowItem.IdeasHeader,
                FlowItem.IdeaCard(idea(1)),
                FlowItem.BottomSpacer,
            ),
            items
        )
    }

    @Test
    fun rows_withEmptySections_omitsTheirHeaders() {
        assertEquals(emptyList<FlowItem>(), state().items)

        assertEquals(
            listOf(
                FlowItem.TasksHeader,
                FlowItem.TaskRow(task(1)),
                FlowItem.BottomSpacer,
            ),
            state(tasks = listOf(task(1))).items
        )

        assertEquals(
            listOf(FlowItem.IdeasHeader, FlowItem.IdeaCard(idea(1)), FlowItem.BottomSpacer),
            state(ideas = listOf(idea(1))).items
        )
    }

    /**
     * `items` lives in the class body, so `copy()` runs the constructor again and
     * recomputes it. If it were ever promoted to a constructor parameter this
     * test would fail — which is exactly why it exists.
     */
    @Test
    fun rows_isRecomputedAfterCopy() {
        val original = state(tasks = listOf(task(1)), ideas = listOf(idea(1)))
        assertEquals(5, original.items.size)

        val withoutIdeas = original.copy(ideas = emptyList())
        assertEquals(3, withoutIdeas.items.size)

        val withCheckLists = withoutIdeas.copy(checkLists = listOf(checkList(1)))
        assertEquals(
            listOf(
                FlowItem.TasksHeader,
                FlowItem.TaskRow(task(1)),
                FlowItem.CheckListsHeader,
                FlowItem.CheckListCard(checkList(1)),
                FlowItem.BottomSpacer,
            ),
            withCheckLists.items
        )
    }

    // ── stableKey ─────────────────────────────────────────────────────────────

    @Test
    fun stableKey_isUniqueAcrossAllRowVariants() {
        val items = listOf(
            FlowItem.TasksHeader,
            FlowItem.TaskRow(task(1)),
            FlowItem.CheckListsHeader,
            FlowItem.CheckListCard(checkList(1)),
            FlowItem.IdeasHeader,
            FlowItem.IdeaCard(idea(1)),
            FlowItem.BottomSpacer,
        )

        assertEquals(items.size, items.map { it.stableKey() }.toSet().size)
    }

    /** Tasks and ideas share an id space, so their keys need an explicit prefix. */
    @Test
    fun stableKey_taskAndIdeaWithSameId_doNotCollide() {
        assertNotEquals(
            FlowItem.TaskRow(task(1)).stableKey(),
            FlowItem.IdeaCard(idea(1)).stableKey()
        )
        assertNotEquals(
            FlowItem.CheckListCard(checkList(1)).stableKey(),
            FlowItem.IdeaCard(idea(1)).stableKey()
        )
    }

    @Test
    fun stableKey_forTask_includesDeleteVersion() {
        val item = FlowItem.TaskRow(task(7))

        assertEquals(Pair(7, 0), item.stableKey())
        assertEquals(Pair(7, 3), item.stableKey(mapOf(7 to 3)))
        // Ids distintos siguen siendo distintos con el mismo contador
        assertNotEquals(
            FlowItem.TaskRow(task(7)).stableKey(mapOf(7 to 1)),
            FlowItem.TaskRow(task(8)).stableKey(mapOf(7 to 1))
        )
    }

    @Test
    fun stableKey_forIdeasAndCheckLists_ignoresDeleteVersions() {
        val versions = mapOf(1 to 5)

        assertEquals("idea_1", FlowItem.IdeaCard(idea(1)).stableKey(versions))
        assertEquals("cl_1", FlowItem.CheckListCard(checkList(1)).stableKey(versions))
    }

    // ── withItems ──────────────────────────────────────────────────────────────

    @Test
    fun withRows_projectsRowsBackOntoTypedLists() {
        val original = state(
            tasks = listOf(task(1), task(2)),
            checkLists = listOf(checkList(1)),
            ideas = listOf(idea(1), idea(2)),
        )

        val rebuilt = original.withItems(original.items)

        assertEquals(original.tasks, rebuilt.tasks)
        assertEquals(original.checkLists, rebuilt.checkLists)
        assertEquals(original.ideas, rebuilt.ideas)
        assertEquals(original.items, rebuilt.items)
    }

    @Test
    fun withRows_preservesANewOrderWithinASection() {
        val original = state(ideas = listOf(idea(1), idea(2), idea(3)))
        val items = original.items.toMutableList()

        val from = items.indexOf(FlowItem.IdeaCard(idea(3)))
        val to = items.indexOf(FlowItem.IdeaCard(idea(1)))
        items.add(to, items.removeAt(from))

        val rebuilt = original.withItems(items)

        assertEquals(listOf(idea(3), idea(1), idea(2)), rebuilt.ideas)
        // Las secciones que no se han tocado se conservan
        assertEquals(original.tasks, rebuilt.tasks)
        assertEquals(original.checkLists, rebuilt.checkLists)
    }

    @Test
    fun withRows_dropChromeRowsAndReEmitsThemInPlace() {
        val original = state(tasks = listOf(task(1)), ideas = listOf(idea(1)))
        val shuffled = listOf(
            FlowItem.IdeasHeader,
            FlowItem.IdeaCard(idea(1)),
            FlowItem.TasksHeader,
            FlowItem.TaskRow(task(1)),
        )

        val rebuilt = original.withItems(shuffled)

        assertEquals(listOf(task(1)), rebuilt.tasks)
        assertEquals(listOf(idea(1)), rebuilt.ideas)
        assertEquals(original.items, rebuilt.items)
    }

    // ── section / spanAllColumns ───────────────────────────────────────────────────

    @Test
    fun section_isNullForChrome_andSetForCards() {
        assertNull(FlowItem.TasksHeader.section)
        assertNull(FlowItem.CheckListsHeader.section)
        assertNull(FlowItem.IdeasHeader.section)
        assertNull(FlowItem.BottomSpacer.section)

        assertEquals(FlowItem.Section.TASKS, FlowItem.TaskRow(task(1)).section)
        assertEquals(FlowItem.Section.CHECKLISTS, FlowItem.CheckListCard(checkList(1)).section)
        assertEquals(FlowItem.Section.IDEAS, FlowItem.IdeaCard(idea(1)).section)
    }

    @Test
    fun fullWidth_isTrueForChromeAndTasks_falseForGridCards() {
        assertTrue(FlowItem.TasksHeader.spanAllColumns)
        assertTrue(FlowItem.CheckListsHeader.spanAllColumns)
        assertTrue(FlowItem.IdeasHeader.spanAllColumns)
        assertTrue(FlowItem.TaskRow(task(1)).spanAllColumns)
        assertTrue(FlowItem.BottomSpacer.spanAllColumns)

        assertFalse(FlowItem.CheckListCard(checkList(1)).spanAllColumns)
        assertFalse(FlowItem.IdeaCard(idea(1)).spanAllColumns)
    }
}
