package com.markel.flowstate.core.data

/**
 * How the cards of the Flow screen are sized.
 *
 * Lives in `core:data` next to [ThemeMode] because it is persisted as a plain
 * preference and read from more than one module: the Flow grid (which renders
 * it) and Settings → Appearance (which exposes it). It is resolved at render
 * time, so switching it never touches stored data.
 */
enum class FlowCardHeight {

    /**
     * Fixed height shared by every card: rows line up and the text inside
     * fills whatever space is left. Default.
     */
    UNIFORM,

    /**
     * Each card grows with its own content (the original masonry look): short
     * ideas stay short and long ones get taller, so the grid has a ragged
     * bottom edge.
     */
    CONTENT;

    companion object {
        /**
         * Single source of truth for the default, so changing it is one edit and
         * not four (repository fallback, two ViewModels, grid default).
         */
        val DEFAULT = UNIFORM
    }
}
