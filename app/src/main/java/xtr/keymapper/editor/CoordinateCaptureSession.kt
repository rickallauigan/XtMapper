package xtr.keymapper.editor

/** Explicit foreground gestures only; no raw device reader or injected touches. */
class CoordinateCaptureSession(val width: Float, val height: Float) {
    init { require(width.isFinite() && height.isFinite() && width >= 1 && height >= 1) }
    data class Selection(val point: Pair<Float, Float>?, val trace: List<Pair<Float, Float>>)
    var selection = Selection(null, emptyList())
        private set
    var capturing = false
        private set
    var tracing = false
    private val history = ArrayDeque<Selection>()
    private var gestureStart: Selection? = null
    val point get() = selection.point
    fun start() { capturing = true }
    fun stop() { cancelGesture(); capturing = false }
    fun begin(x: Float, y: Float) {
        if (!capturing) return
        cancelGesture(); gestureStart = selection
        val p = bounded(x, y)
        selection = Selection(p, if (tracing) listOf(p) else emptyList())
    }
    fun move(x: Float, y: Float) {
        if (!capturing || gestureStart == null) return
        val p = bounded(x, y)
        val path = if (tracing) (selection.trace + p).takeLast(512) else emptyList()
        selection = Selection(p, path)
    }
    fun end() {
        gestureStart?.let { if (it != selection) remember(it) }
        gestureStart = null
    }
    fun cancelGesture() { gestureStart?.let { selection = it }; gestureStart = null }
    fun select(x: Float, y: Float) {
        cancelGesture(); val next = Selection(bounded(x, y), emptyList())
        if (next != selection) { remember(selection); selection = next }
    }
    fun nudge(dx: Int, dy: Int) { point?.let { select(it.first + dx, it.second + dy) } }
    fun undo() { cancelGesture(); if (history.isNotEmpty()) selection = history.removeLast() }
    fun reset() { cancelGesture(); if (selection.point != null) { remember(selection); selection = Selection(null, emptyList()) } }
    private fun bounded(x: Float, y: Float): Pair<Float, Float> {
        require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" }
        return x.coerceIn(0f, width - 1) to y.coerceIn(0f, height - 1)
    }
    private fun remember(value: Selection) { if (history.size == 64) history.removeFirst(); history.addLast(value) }
}
