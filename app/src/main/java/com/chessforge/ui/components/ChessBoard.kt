package com.chessforge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.Square
import com.chessforge.ui.theme.BoardPalette

/** Fleche tracee sur le plateau (coup recommande, menace, idee). */
data class BoardArrow(val from: Int, val to: Int, val color: Color, val label: String? = null)

private val WHITE_FILL = Color(0xFFF7F4EC)
private val WHITE_EDGE = Color(0xFF3A3A38)
private val BLACK_FILL = Color(0xFF26262B)
private val BLACK_EDGE = Color(0xFFD9D9D4)

/**
 * Echiquier interactif.
 *
 * Les pieces sont dessinees geometriquement, sans police ni image : le rendu est
 * identique sur tous les appareils et l'APK reste leger.
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    palette: BoardPalette = BoardPalette.Forest,
    flipped: Boolean = false,
    showCoordinates: Boolean = true,
    lastMove: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    interactive: Boolean = false,
    onMove: (from: Int, to: Int, promo: Int) -> Unit = { _, _, _ -> },
) {
    val measurer = rememberTextMeasurer()

    // Ces etats sont manipules depuis les gestionnaires de gestes, qui ne sont relances
    // que si la cle de `pointerInput` change. On les lit donc via l'objet State lui-meme
    // (dont l'identite est stable) et non via une variable capturee, sinon les gestes
    // travailleraient sur une photo perimee de l'etat.
    val selected = remember(position.key) { mutableStateOf<Int?>(null) }
    val dragFrom = remember(position.key) { mutableStateOf<Int?>(null) }
    val dragPoint = remember(position.key) { mutableStateOf<Offset?>(null) }
    val pendingPromotion = remember(position.key) { mutableStateOf<Pair<Int, Int>?>(null) }

    val legalMoves = remember(position.key) { position.legalMoves() }
    fun targetsFrom(from: Int?): Map<Int, Int> {
        if (from == null) return emptyMap()
        return legalMoves.filter { Move.from(it) == from }.associateBy { Move.to(it) }
    }

    fun squareAt(offset: Offset, side: Float): Int? {
        val cell = side / 8f
        val col = (offset.x / cell).toInt()
        val row = (offset.y / cell).toInt()
        if (col !in 0..7 || row !in 0..7) return null
        val file = if (flipped) 7 - col else col
        val rank = if (flipped) row else 7 - row
        return Square.of(file, rank)
    }

    fun tryMove(from: Int, to: Int) {
        val candidates = legalMoves.filter { Move.from(it) == from && Move.to(it) == to }
        if (candidates.isEmpty()) return
        if (candidates.size > 1 && candidates.any { Move.promo(it) != 0 }) {
            pendingPromotion.value = from to to
        } else {
            onMove(from, to, Move.promo(candidates.first()))
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (!interactive) Modifier else Modifier
                        .pointerInput(position.key) {
                            detectTapGestures { offset ->
                                val square = squareAt(offset, size.width.toFloat()) ?: return@detectTapGestures
                                val current = selected.value
                                when {
                                    current != null && targetsFrom(current).containsKey(square) -> {
                                        tryMove(current, square)
                                        selected.value = null
                                    }
                                    position.colorAt(square) == position.side -> selected.value = square
                                    else -> selected.value = null
                                }
                            }
                        }
                        .pointerInput(position.key) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val square = squareAt(offset, size.width.toFloat())
                                    if (square != null && position.colorAt(square) == position.side) {
                                        dragFrom.value = square
                                        selected.value = square
                                        dragPoint.value = offset
                                    }
                                },
                                onDragEnd = {
                                    val from = dragFrom.value
                                    val point = dragPoint.value
                                    if (from != null && point != null) {
                                        squareAt(point, size.width.toFloat())?.let { to ->
                                            if (to != from) {
                                                tryMove(from, to)
                                                selected.value = null
                                            }
                                        }
                                    }
                                    dragFrom.value = null
                                    dragPoint.value = null
                                },
                                onDragCancel = {
                                    dragFrom.value = null
                                    dragPoint.value = null
                                },
                            ) { change, _ ->
                                dragPoint.value = change.position
                            }
                        }
                )
        ) {
            val side = size.minDimension
            val cell = side / 8f

            fun cellTopLeft(square: Int): Offset {
                val file = Square.file(square)
                val rank = Square.rank(square)
                val col = if (flipped) 7 - file else file
                val row = if (flipped) rank else 7 - rank
                return Offset(col * cell, row * cell)
            }

            // Cases
            for (square in 0..63) {
                val isLight = (Square.file(square) + Square.rank(square)) % 2 == 1
                drawRect(
                    color = if (isLight) palette.light else palette.dark,
                    topLeft = cellTopLeft(square),
                    size = Size(cell, cell),
                )
            }

            // Dernier coup joue
            lastMove?.let { move ->
                for (square in listOf(Move.from(move), Move.to(move))) {
                    drawRect(palette.lastMove, topLeft = cellTopLeft(square), size = Size(cell, cell))
                }
            }

            // Roi en echec
            if (position.isInCheck()) {
                val king = position.kingSquare[position.side]
                if (king != Square.NONE) {
                    drawCircle(
                        palette.check,
                        radius = cell * 0.48f,
                        center = cellTopLeft(king) + Offset(cell / 2f, cell / 2f),
                        style = Stroke(width = cell * 0.08f),
                    )
                }
            }

            // Selection et cases accessibles
            val selectedSquare = selected.value
            selectedSquare?.let { square ->
                drawRect(palette.highlight, topLeft = cellTopLeft(square), size = Size(cell, cell))
            }
            for (target in targetsFrom(selectedSquare).keys) {
                val center = cellTopLeft(target) + Offset(cell / 2f, cell / 2f)
                if (position.board[target] == Piece.NONE) {
                    drawCircle(palette.highlight, radius = cell * 0.16f, center = center)
                } else {
                    drawCircle(
                        palette.highlight,
                        radius = cell * 0.44f,
                        center = center,
                        style = Stroke(width = cell * 0.09f),
                    )
                }
            }

            // Pieces
            for (square in 0..63) {
                val piece = position.board[square]
                if (piece == Piece.NONE) continue
                if (square == dragFrom.value && dragPoint.value != null) continue
                drawPiece(piece, cellTopLeft(square), cell)
            }

            if (showCoordinates) {
                for (i in 0..7) {
                    val file = if (flipped) 7 - i else i
                    val rank = if (flipped) i else 7 - i
                    val fileLabel = ('a' + file).toString()
                    val rankLabel = (rank + 1).toString()
                    val lightBottom = (file + 0) % 2 == 0
                    drawBoardLabel(
                        measurer, fileLabel,
                        Offset(i * cell + cell * 0.08f, side - cell * 0.30f),
                        if (lightBottom) palette.dark else palette.light, cell,
                    )
                    val lightLeft = (rank + 1) % 2 == 0
                    drawBoardLabel(
                        measurer, rankLabel,
                        Offset(cell * 0.08f, i * cell + cell * 0.06f),
                        if (lightLeft) palette.dark else palette.light, cell,
                    )
                }
            }

            // Fleches (tracees au-dessus des pieces pour rester lisibles)
            for (arrow in arrows) {
                drawArrow(
                    cellTopLeft(arrow.from) + Offset(cell / 2f, cell / 2f),
                    cellTopLeft(arrow.to) + Offset(cell / 2f, cell / 2f),
                    arrow.color, cell,
                )
            }

            // Piece en cours de deplacement, dessinee a l'endroit du doigt
            val from = dragFrom.value
            val point = dragPoint.value
            if (from != null && point != null) {
                val piece = position.board[from]
                if (piece != Piece.NONE) {
                    val scaled = cell * 1.12f
                    drawPiece(piece, Offset(point.x - scaled / 2f, point.y - scaled / 2f), scaled)
                }
            }
        }

        pendingPromotion.value?.let { (from, to) ->
            PromotionDialog(
                color = position.side,
                onPick = { promo ->
                    pendingPromotion.value = null
                    onMove(from, to, promo)
                },
                onDismiss = { pendingPromotion.value = null },
            )
        }
    }
}

@Composable
private fun PromotionDialog(color: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promotion") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (type in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(4.dp)
                                .pointerInput(type) { detectTapGestures { onPick(type) } },
                        ) {
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                drawPiece(Piece.of(color, type), Offset.Zero, size.minDimension)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/** Petite pastille montrant une piece, utilisee dans les listes et les legendes. */
@Composable
fun PieceChip(piece: Int, size: androidx.compose.ui.unit.Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawPiece(piece, Offset.Zero, this.size.minDimension) }
}

private fun DrawScope.drawBoardLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
    cell: Float,
) {
    val result = measurer.measure(
        AnnotatedString(text),
        style = TextStyle(
            fontSize = (cell * 0.20f).toSp(),
            color = color.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
        ),
    )
    drawText(result, topLeft = at)
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, cell: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val head = cell * 0.36f
    val shaftEnd = Offset(to.x - ux * head, to.y - uy * head)
    val start = Offset(from.x + ux * cell * 0.22f, from.y + uy * cell * 0.22f)
    drawLine(color, start, shaftEnd, strokeWidth = cell * 0.14f, cap = StrokeCap.Round)
    val wing = cell * 0.19f
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(shaftEnd.x - uy * wing, shaftEnd.y + ux * wing)
        lineTo(shaftEnd.x + uy * wing, shaftEnd.y - ux * wing)
        close()
    }
    drawPath(path, color)
}

// --- Dessin des pieces ------------------------------------------------------

/**
 * Chaque piece est decrite dans un carre normalise (0..1) puis mise a l'echelle de la
 * case. Remplissage clair ou sombre selon la couleur, avec un contour de la teinte
 * opposee : les deux camps restent lisibles sur les cases claires comme sombres.
 */
private fun DrawScope.drawPiece(piece: Int, topLeft: Offset, cell: Float) {
    val white = Piece.colorOf(piece) == Piece.WHITE
    val fill = if (white) WHITE_FILL else BLACK_FILL
    val edge = if (white) WHITE_EDGE else BLACK_EDGE
    val stroke = Stroke(width = cell * 0.035f)

    translate(topLeft.x, topLeft.y) {
        val s = cell
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun path(block: Path.() -> Unit): Path = Path().apply(block)

        fun base() {
            drawRoundRect(
                fill, topLeft = p(0.22f, 0.79f), size = Size(0.56f * s, 0.10f * s),
                cornerRadius = CornerRadius(0.03f * s),
            )
            drawRoundRect(
                edge, topLeft = p(0.22f, 0.79f), size = Size(0.56f * s, 0.10f * s),
                cornerRadius = CornerRadius(0.03f * s), style = stroke,
            )
            drawRoundRect(
                fill, topLeft = p(0.29f, 0.70f), size = Size(0.42f * s, 0.10f * s),
                cornerRadius = CornerRadius(0.02f * s),
            )
            drawRoundRect(
                edge, topLeft = p(0.29f, 0.70f), size = Size(0.42f * s, 0.10f * s),
                cornerRadius = CornerRadius(0.02f * s), style = stroke,
            )
        }

        fun fillAndEdge(shape: Path) {
            drawPath(shape, fill)
            drawPath(shape, edge, style = stroke)
        }

        when (Piece.typeOf(piece)) {
            Piece.PAWN -> {
                val body = path {
                    moveTo(0.33f * s, 0.72f * s)
                    lineTo(0.38f * s, 0.46f * s)
                    lineTo(0.62f * s, 0.46f * s)
                    lineTo(0.67f * s, 0.72f * s)
                    close()
                }
                fillAndEdge(body)
                drawCircle(fill, radius = 0.155f * s, center = p(0.5f, 0.33f))
                drawCircle(edge, radius = 0.155f * s, center = p(0.5f, 0.33f), style = stroke)
                drawRoundRect(
                    fill, topLeft = p(0.26f, 0.70f), size = Size(0.48f * s, 0.11f * s),
                    cornerRadius = CornerRadius(0.03f * s),
                )
                drawRoundRect(
                    edge, topLeft = p(0.26f, 0.70f), size = Size(0.48f * s, 0.11f * s),
                    cornerRadius = CornerRadius(0.03f * s), style = stroke,
                )
                drawRoundRect(
                    fill, topLeft = p(0.22f, 0.79f), size = Size(0.56f * s, 0.10f * s),
                    cornerRadius = CornerRadius(0.03f * s),
                )
                drawRoundRect(
                    edge, topLeft = p(0.22f, 0.79f), size = Size(0.56f * s, 0.10f * s),
                    cornerRadius = CornerRadius(0.03f * s), style = stroke,
                )
            }

            Piece.ROOK -> {
                val body = path {
                    moveTo(0.30f * s, 0.70f * s)
                    lineTo(0.33f * s, 0.36f * s)
                    lineTo(0.67f * s, 0.36f * s)
                    lineTo(0.70f * s, 0.70f * s)
                    close()
                }
                fillAndEdge(body)
                for (x in listOf(0.24f, 0.39f, 0.54f, 0.69f)) {
                    drawRect(fill, topLeft = p(x, 0.16f), size = Size(0.07f * s, 0.14f * s))
                    drawRect(edge, topLeft = p(x, 0.16f), size = Size(0.07f * s, 0.14f * s), style = stroke)
                }
                drawRect(fill, topLeft = p(0.24f, 0.26f), size = Size(0.52f * s, 0.11f * s))
                drawRect(edge, topLeft = p(0.24f, 0.26f), size = Size(0.52f * s, 0.11f * s), style = stroke)
                base()
            }

            Piece.KNIGHT -> {
                val head = path {
                    moveTo(0.32f * s, 0.74f * s)
                    lineTo(0.33f * s, 0.56f * s)
                    lineTo(0.38f * s, 0.46f * s)
                    lineTo(0.30f * s, 0.41f * s)
                    lineTo(0.26f * s, 0.32f * s)
                    lineTo(0.34f * s, 0.25f * s)
                    lineTo(0.41f * s, 0.22f * s)
                    lineTo(0.44f * s, 0.13f * s)
                    lineTo(0.52f * s, 0.20f * s)
                    lineTo(0.62f * s, 0.22f * s)
                    lineTo(0.71f * s, 0.32f * s)
                    lineTo(0.73f * s, 0.48f * s)
                    lineTo(0.70f * s, 0.62f * s)
                    lineTo(0.71f * s, 0.74f * s)
                    close()
                }
                fillAndEdge(head)
                drawCircle(edge, radius = 0.028f * s, center = p(0.46f, 0.30f))
                base()
            }

            Piece.BISHOP -> {
                val mitre = path {
                    moveTo(0.50f * s, 0.14f * s)
                    quadraticTo(0.72f * s, 0.34f * s, 0.62f * s, 0.56f * s)
                    lineTo(0.38f * s, 0.56f * s)
                    quadraticTo(0.28f * s, 0.34f * s, 0.50f * s, 0.14f * s)
                    close()
                }
                fillAndEdge(mitre)
                drawLine(edge, p(0.50f, 0.24f), p(0.58f, 0.36f), strokeWidth = cell * 0.035f)
                drawCircle(fill, radius = 0.055f * s, center = p(0.50f, 0.12f))
                drawCircle(edge, radius = 0.055f * s, center = p(0.50f, 0.12f), style = stroke)
                drawRoundRect(
                    fill, topLeft = p(0.34f, 0.56f), size = Size(0.32f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s),
                )
                drawRoundRect(
                    edge, topLeft = p(0.34f, 0.56f), size = Size(0.32f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s), style = stroke,
                )
                base()
            }

            Piece.QUEEN -> {
                val crown = path {
                    moveTo(0.20f * s, 0.30f * s)
                    lineTo(0.30f * s, 0.62f * s)
                    lineTo(0.70f * s, 0.62f * s)
                    lineTo(0.80f * s, 0.30f * s)
                    lineTo(0.66f * s, 0.42f * s)
                    lineTo(0.58f * s, 0.24f * s)
                    lineTo(0.50f * s, 0.40f * s)
                    lineTo(0.42f * s, 0.24f * s)
                    lineTo(0.34f * s, 0.42f * s)
                    close()
                }
                fillAndEdge(crown)
                for (point in listOf(0.20f to 0.28f, 0.42f to 0.21f, 0.58f to 0.21f, 0.80f to 0.28f)) {
                    drawCircle(fill, radius = 0.055f * s, center = p(point.first, point.second))
                    drawCircle(edge, radius = 0.055f * s, center = p(point.first, point.second), style = stroke)
                }
                drawCircle(fill, radius = 0.06f * s, center = p(0.50f, 0.16f))
                drawCircle(edge, radius = 0.06f * s, center = p(0.50f, 0.16f), style = stroke)
                drawRoundRect(
                    fill, topLeft = p(0.29f, 0.62f), size = Size(0.42f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s),
                )
                drawRoundRect(
                    edge, topLeft = p(0.29f, 0.62f), size = Size(0.42f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s), style = stroke,
                )
                base()
            }

            Piece.KING -> {
                val crown = path {
                    moveTo(0.28f * s, 0.36f * s)
                    lineTo(0.32f * s, 0.64f * s)
                    lineTo(0.68f * s, 0.64f * s)
                    lineTo(0.72f * s, 0.36f * s)
                    quadraticTo(0.60f * s, 0.30f * s, 0.50f * s, 0.34f * s)
                    quadraticTo(0.40f * s, 0.30f * s, 0.28f * s, 0.36f * s)
                    close()
                }
                fillAndEdge(crown)
                drawRect(fill, topLeft = p(0.455f, 0.07f), size = Size(0.09f * s, 0.22f * s))
                drawRect(edge, topLeft = p(0.455f, 0.07f), size = Size(0.09f * s, 0.22f * s), style = stroke)
                drawRect(fill, topLeft = p(0.37f, 0.13f), size = Size(0.26f * s, 0.08f * s))
                drawRect(edge, topLeft = p(0.37f, 0.13f), size = Size(0.26f * s, 0.08f * s), style = stroke)
                drawRoundRect(
                    fill, topLeft = p(0.29f, 0.64f), size = Size(0.42f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s),
                )
                drawRoundRect(
                    edge, topLeft = p(0.29f, 0.64f), size = Size(0.42f * s, 0.09f * s),
                    cornerRadius = CornerRadius(0.02f * s), style = stroke,
                )
                base()
            }
        }
    }
}

/**
 * Barre d'evaluation verticale : part blanche proportionnelle a la probabilite de gain
 * des blancs. Toujours accompagnee du score chiffre a cote, jamais seule.
 */
@Composable
fun EvalBar(
    whiteWinPercent: Double,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
) {
    val fraction = (whiteWinPercent / 100.0).coerceIn(0.02, 0.98).toFloat()
    Canvas(modifier) {
        val radius = CornerRadius(3.dp.toPx())
        drawRoundRect(BLACK_FILL, size = size, cornerRadius = radius)
        val whiteHeight = size.height * fraction
        val y = if (flipped) 0f else size.height - whiteHeight
        clipRoundedRect(radius) {
            drawRect(WHITE_FILL, topLeft = Offset(0f, y), size = Size(size.width, whiteHeight))
        }
    }
}

private inline fun DrawScope.clipRoundedRect(radius: CornerRadius, block: DrawScope.() -> Unit) {
    val path = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, radius))
    }
    clipPath(path) { block() }
}
