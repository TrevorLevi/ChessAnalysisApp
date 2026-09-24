package com.chessforge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.Square
import com.chessforge.ui.theme.BoardPalette

/** Fleche tracee sur le plateau (coup recommande, menace, idee). */
data class BoardArrow(val from: Int, val to: Int, val color: Color, val label: String? = null)

/** Pastille de verdict posee sur une case, comme apres un coup de puzzle. */
data class BoardBadge(val square: Int, val kind: Kind) {
    enum class Kind { WRONG, CORRECT, BEST }
}

// Pieces plates a fort contraste : blanc franc cerne de gris sombre, noir chaud cerne
// plus sombre encore. C'est ce qui rend les deux camps lisibles aussi bien sur les
// cases claires que sur les cases foncees.
private val WHITE_FILL = Color(0xFFFFFFFF)
private val WHITE_EDGE = Color(0xFF4A4A48)
private val BLACK_FILL = Color(0xFF3B3A38)
private val BLACK_EDGE = Color(0xFF181715)

/**
 * Echiquier interactif.
 *
 * Les pieces sont dessinees geometriquement au standard Staunton, sans police ni
 * image : le rendu est identique sur tous les appareils et l'APK reste leger.
 *
 * @param badges verdicts a afficher sur des cases (bon coup, erreur)
 * @param overlay contenu libre superpose au plateau, par exemple un bouton "Reessayer"
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    palette: BoardPalette = BoardPalette.Green,
    flipped: Boolean = false,
    lastMove: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    badges: List<BoardBadge> = emptyList(),
    interactive: Boolean = false,
    onMove: (from: Int, to: Int, promo: Int) -> Unit = { _, _, _ -> },
    overlay: (@Composable BoxScope.() -> Unit)? = null,
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

            // --- Cases -------------------------------------------------------
            for (square in 0..63) {
                val isLight = (Square.file(square) + Square.rank(square)) % 2 == 1
                drawRect(
                    color = if (isLight) palette.light else palette.dark,
                    topLeft = cellTopLeft(square),
                    size = Size(cell, cell),
                )
            }

            // --- Surbrillances sous les pieces -------------------------------
            lastMove?.let { move ->
                for (square in listOf(Move.from(move), Move.to(move))) {
                    drawRect(palette.lastMove, topLeft = cellTopLeft(square), size = Size(cell, cell))
                }
            }
            selected.value?.let { square ->
                drawRect(palette.highlight, topLeft = cellTopLeft(square), size = Size(cell, cell))
            }
            if (position.isInCheck()) {
                val king = position.kingSquare[position.side]
                if (king != Square.NONE) {
                    drawCircle(
                        palette.check,
                        radius = cell * 0.46f,
                        center = cellTopLeft(king) + Offset(cell / 2f, cell / 2f),
                        style = Stroke(width = cell * 0.09f),
                    )
                }
            }

            // --- Coordonnees, toujours affichees -----------------------------
            // Convention des plateaux en ligne : chiffres en haut a gauche de la
            // colonne de gauche, lettres en bas a droite de la rangee du bas, dans
            // la couleur de la case opposee.
            for (i in 0..7) {
                val file = if (flipped) 7 - i else i
                val rank = if (flipped) i else 7 - i

                val bottomSquare = Square.of(file, if (flipped) 7 else 0)
                val bottomIsLight = (Square.file(bottomSquare) + Square.rank(bottomSquare)) % 2 == 1
                drawEdgeLabel(
                    measurer,
                    ('a' + file).toString(),
                    cell,
                    if (bottomIsLight) palette.dark else palette.light,
                    anchor = Offset(i * cell + cell, 8 * cell),
                    alignRight = true,
                )

                val leftSquare = Square.of(if (flipped) 7 else 0, rank)
                val leftIsLight = (Square.file(leftSquare) + Square.rank(leftSquare)) % 2 == 1
                drawEdgeLabel(
                    measurer,
                    (rank + 1).toString(),
                    cell,
                    if (leftIsLight) palette.dark else palette.light,
                    anchor = Offset(0f, i * cell),
                    alignRight = false,
                )
            }

            // --- Destinations legales ----------------------------------------
            for (target in targetsFrom(selected.value).keys) {
                val center = cellTopLeft(target) + Offset(cell / 2f, cell / 2f)
                if (position.board[target] == Piece.NONE) {
                    drawCircle(palette.hint, radius = cell * 0.17f, center = center)
                } else {
                    // Prise : anneau epais autour de la piece convoitee.
                    drawCircle(
                        palette.hint,
                        radius = cell * 0.42f,
                        center = center,
                        style = Stroke(width = cell * 0.10f),
                    )
                }
            }

            // --- Pieces ------------------------------------------------------
            for (square in 0..63) {
                val piece = position.board[square]
                if (piece == Piece.NONE) continue
                if (square == dragFrom.value && dragPoint.value != null) continue
                drawPiece(piece, cellTopLeft(square), cell)
            }

            // --- Fleches et pastilles, au-dessus des pieces -------------------
            for (arrow in arrows) {
                drawArrow(
                    cellTopLeft(arrow.from) + Offset(cell / 2f, cell / 2f),
                    cellTopLeft(arrow.to) + Offset(cell / 2f, cell / 2f),
                    arrow.color, cell,
                )
            }
            for (badge in badges) {
                drawBadge(badge.kind, cellTopLeft(badge.square), cell)
            }

            // --- Piece en cours de deplacement, a l'endroit du doigt ----------
            val from = dragFrom.value
            val point = dragPoint.value
            if (from != null && point != null) {
                val piece = position.board[from]
                if (piece != Piece.NONE) {
                    val scaled = cell * 1.15f
                    drawPiece(piece, Offset(point.x - scaled / 2f, point.y - scaled / 2f), scaled)
                }
            }
        }

        overlay?.invoke(this)

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

private fun DrawScope.drawEdgeLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    cell: Float,
    color: Color,
    anchor: Offset,
    alignRight: Boolean,
) {
    val result = measurer.measure(
        AnnotatedString(text),
        style = TextStyle(
            fontSize = (cell * 0.22f).toSp(),
            color = color,
            fontWeight = FontWeight.Bold,
        ),
    )
    val margin = cell * 0.06f
    val x = if (alignRight) anchor.x - result.size.width - margin else anchor.x + margin
    val y = if (alignRight) anchor.y - result.size.height - margin else anchor.y + margin
    drawText(result, topLeft = Offset(x, y))
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

/** Pastille de verdict au coin superieur droit de la case, comme sur les puzzles en ligne. */
private fun DrawScope.drawBadge(kind: BoardBadge.Kind, topLeft: Offset, cell: Float) {
    val radius = cell * 0.20f
    val center = topLeft + Offset(cell * 0.84f, cell * 0.16f)
    val color = when (kind) {
        BoardBadge.Kind.WRONG -> Color(0xFFE02B2B)
        BoardBadge.Kind.CORRECT -> Color(0xFF7FA650)
        BoardBadge.Kind.BEST -> Color(0xFF3B95C9)
    }
    drawCircle(Color.White, radius = radius + cell * 0.025f, center = center)
    drawCircle(color, radius = radius, center = center)

    val arm = radius * 0.46f
    val stroke = cell * 0.055f
    when (kind) {
        BoardBadge.Kind.WRONG -> {
            drawLine(
                Color.White, center + Offset(-arm, -arm), center + Offset(arm, arm),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
            drawLine(
                Color.White, center + Offset(arm, -arm), center + Offset(-arm, arm),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
        }
        BoardBadge.Kind.CORRECT, BoardBadge.Kind.BEST -> {
            drawLine(
                Color.White, center + Offset(-arm, 0f), center + Offset(-arm * 0.2f, arm * 0.8f),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
            drawLine(
                Color.White, center + Offset(-arm * 0.2f, arm * 0.8f), center + Offset(arm, -arm * 0.7f),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
        }
    }
}

// --- Dessin des pieces ------------------------------------------------------

/**
 * Chaque piece est decrite dans un carre normalise (0..1) puis mise a l'echelle de la
 * case, au profil Staunton : socle etage, collerette, et tete caracteristique.
 */
private fun DrawScope.drawPiece(piece: Int, topLeft: Offset, cell: Float) {
    val white = Piece.colorOf(piece) == Piece.WHITE
    val fill = if (white) WHITE_FILL else BLACK_FILL
    val edge = if (white) WHITE_EDGE else BLACK_EDGE
    val s = cell
    val stroke = Stroke(width = s * 0.028f)

    translate(topLeft.x, topLeft.y) {

        fun shape(build: Path.() -> Unit) {
            val path = Path().apply(build)
            drawPath(path, fill)
            drawPath(path, edge, style = stroke)
        }

        fun roundedBar(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
            val size = Size((right - left) * s, (bottom - top) * s)
            val corner = CornerRadius(radius * s)
            drawRoundRect(fill, topLeft = Offset(left * s, top * s), size = size, cornerRadius = corner)
            drawRoundRect(
                edge, topLeft = Offset(left * s, top * s), size = size,
                cornerRadius = corner, style = stroke,
            )
        }

        fun disc(cx: Float, cy: Float, r: Float) {
            drawCircle(fill, radius = r * s, center = Offset(cx * s, cy * s))
            drawCircle(edge, radius = r * s, center = Offset(cx * s, cy * s), style = stroke)
        }

        /** Socle etage commun a toutes les pieces sauf le pion. */
        fun base() {
            roundedBar(0.275f, 0.735f, 0.725f, 0.795f, 0.018f)
            roundedBar(0.205f, 0.795f, 0.795f, 0.885f, 0.030f)
        }

        /** Corps evase reliant la collerette au socle. */
        fun body(topY: Float, halfWidth: Float) {
            shape {
                moveTo(0.335f * s, 0.740f * s)
                cubicTo(
                    0.385f * s, 0.660f * s,
                    (0.5f - halfWidth * 0.75f) * s, 0.600f * s,
                    (0.5f - halfWidth) * s, topY * s,
                )
                lineTo((0.5f + halfWidth) * s, topY * s)
                cubicTo(
                    (0.5f + halfWidth * 0.75f) * s, 0.600f * s,
                    0.615f * s, 0.660f * s,
                    0.665f * s, 0.740f * s,
                )
                close()
            }
        }

        when (Piece.typeOf(piece)) {
            Piece.PAWN -> {
                shape {
                    moveTo(0.360f * s, 0.735f * s)
                    cubicTo(0.400f * s, 0.655f * s, 0.428f * s, 0.560f * s, 0.432f * s, 0.470f * s)
                    lineTo(0.568f * s, 0.470f * s)
                    cubicTo(0.572f * s, 0.560f * s, 0.600f * s, 0.655f * s, 0.640f * s, 0.735f * s)
                    close()
                }
                roundedBar(0.385f, 0.425f, 0.615f, 0.480f, 0.022f)
                disc(0.5f, 0.300f, 0.128f)
                roundedBar(0.300f, 0.735f, 0.700f, 0.790f, 0.018f)
                roundedBar(0.240f, 0.790f, 0.760f, 0.880f, 0.030f)
            }

            Piece.ROOK -> {
                // Creneaux : trois merlons separes par deux embrasures.
                for (x in listOf(0.240f, 0.435f, 0.630f)) roundedBar(x, 0.155f, x + 0.130f, 0.275f, 0.012f)
                roundedBar(0.240f, 0.255f, 0.760f, 0.340f, 0.014f)
                shape {
                    moveTo(0.315f * s, 0.680f * s)
                    lineTo(0.345f * s, 0.340f * s)
                    lineTo(0.655f * s, 0.340f * s)
                    lineTo(0.685f * s, 0.680f * s)
                    close()
                }
                roundedBar(0.280f, 0.670f, 0.720f, 0.740f, 0.016f)
                base()
            }

            Piece.KNIGHT -> {
                // Profil de tete de cheval tourne vers la gauche, comme sur la
                // plupart des jeux Staunton.
                shape {
                    moveTo(0.735f * s, 0.735f * s)
                    // Nuque et encolure, du poitrail vers les oreilles.
                    cubicTo(0.762f * s, 0.585f * s, 0.738f * s, 0.430f * s, 0.652f * s, 0.330f * s)
                    cubicTo(0.620f * s, 0.292f * s, 0.598f * s, 0.252f * s, 0.600f * s, 0.205f * s)
                    lineTo(0.652f * s, 0.108f * s)
                    lineTo(0.548f * s, 0.180f * s)
                    lineTo(0.487f * s, 0.098f * s)
                    lineTo(0.452f * s, 0.205f * s)
                    // Chanfrein, puis naseau et levre.
                    cubicTo(0.398f * s, 0.262f * s, 0.318f * s, 0.330f * s, 0.258f * s, 0.408f * s)
                    cubicTo(0.216f * s, 0.462f * s, 0.196f * s, 0.502f * s, 0.212f * s, 0.522f * s)
                    cubicTo(0.232f * s, 0.545f * s, 0.276f * s, 0.536f * s, 0.302f * s, 0.518f * s)
                    lineTo(0.372f * s, 0.492f * s)
                    // Gorge et bas de l'encolure.
                    cubicTo(0.418f * s, 0.518f * s, 0.436f * s, 0.560f * s, 0.444f * s, 0.612f * s)
                    cubicTo(0.454f * s, 0.668f * s, 0.436f * s, 0.708f * s, 0.414f * s, 0.735f * s)
                    close()
                }
                drawCircle(edge, radius = 0.026f * s, center = Offset(0.432f * s, 0.318f * s))
                roundedBar(0.300f, 0.730f, 0.700f, 0.790f, 0.016f)
                roundedBar(0.205f, 0.790f, 0.795f, 0.885f, 0.030f)
            }

            Piece.BISHOP -> {
                shape {
                    moveTo(0.500f * s, 0.180f * s)
                    cubicTo(0.658f * s, 0.262f * s, 0.668f * s, 0.404f * s, 0.578f * s, 0.502f * s)
                    lineTo(0.422f * s, 0.502f * s)
                    cubicTo(0.332f * s, 0.404f * s, 0.342f * s, 0.262f * s, 0.500f * s, 0.180f * s)
                    close()
                }
                // Fente caracteristique de la mitre.
                drawLine(
                    edge,
                    Offset(0.540f * s, 0.246f * s),
                    Offset(0.600f * s, 0.336f * s),
                    strokeWidth = s * 0.030f,
                    cap = StrokeCap.Round,
                )
                disc(0.5f, 0.138f, 0.046f)
                body(topY = 0.555f, halfWidth = 0.085f)
                base()
                roundedBar(0.360f, 0.495f, 0.640f, 0.555f, 0.018f)
            }

            Piece.QUEEN -> {
                shape {
                    moveTo(0.212f * s, 0.262f * s)
                    lineTo(0.312f * s, 0.505f * s)
                    lineTo(0.688f * s, 0.505f * s)
                    lineTo(0.788f * s, 0.262f * s)
                    lineTo(0.645f * s, 0.372f * s)
                    lineTo(0.500f * s, 0.218f * s)
                    lineTo(0.355f * s, 0.372f * s)
                    close()
                }
                for (point in listOf(0.212f to 0.248f, 0.355f to 0.188f, 0.500f to 0.166f, 0.645f to 0.188f, 0.788f to 0.248f)) {
                    disc(point.first, point.second, 0.052f)
                }
                body(topY = 0.562f, halfWidth = 0.105f)
                base()
                roundedBar(0.295f, 0.498f, 0.705f, 0.562f, 0.018f)
            }

            Piece.KING -> {
                roundedBar(0.462f, 0.068f, 0.538f, 0.250f, 0.014f)
                roundedBar(0.392f, 0.124f, 0.608f, 0.194f, 0.014f)
                shape {
                    moveTo(0.292f * s, 0.352f * s)
                    cubicTo(0.340f * s, 0.284f * s, 0.432f * s, 0.268f * s, 0.500f * s, 0.306f * s)
                    cubicTo(0.568f * s, 0.268f * s, 0.660f * s, 0.284f * s, 0.708f * s, 0.352f * s)
                    lineTo(0.682f * s, 0.505f * s)
                    lineTo(0.318f * s, 0.505f * s)
                    close()
                }
                body(topY = 0.562f, halfWidth = 0.105f)
                base()
                roundedBar(0.295f, 0.498f, 0.705f, 0.562f, 0.018f)
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
