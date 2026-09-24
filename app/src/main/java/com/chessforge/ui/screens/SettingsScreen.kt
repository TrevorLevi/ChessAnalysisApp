package com.chessforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.BuildConfig
import com.chessforge.analysis.GameAnalyzer
import com.chessforge.data.prefs.EnginePreference
import com.chessforge.data.prefs.SettingsData
import com.chessforge.data.prefs.ThemeMode
import com.chessforge.di.AppContainer
import com.chessforge.ui.Format
import com.chessforge.ui.theme.BoardPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.state
    val nativeEngineAvailable = container.engines.nativeAvailable

    private val _verification = MutableStateFlow<String?>(null)
    val verification: StateFlow<String?> = _verification.asStateFlow()

    fun update(block: (SettingsData) -> SettingsData) = container.settings.update(block)

    fun verifyUsername(username: String) {
        _verification.value = "Verification..."
        viewModelScope.launch {
            val result = container.repository.verifyUsername(username)
            _verification.value = result.fold(
                onSuccess = { resolved ->
                    update { it.copy(username = resolved) }
                    "Compte trouve : $resolved"
                },
                onFailure = { "Introuvable sur chess.com. Verifiez l'orthographe du pseudo." },
            )
        }
    }

    fun resetData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.repository.resetEverything() }
            _verification.value = "Donnees locales effacees."
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val verification by viewModel.verification.collectAsStateWithLifecycle()
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup("Compte chess.com") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("Pseudo chess.com") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.verifyUsername(username) }, enabled = username.isNotBlank()) {
                    Text("Verifier et enregistrer")
                }
            }
            verification?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (settings.lastSyncAt > 0) {
                Text(
                    "Dernier import : ${Format.relative(settings.lastSyncAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Seules les donnees publiques de ce pseudo sont lues, en lecture seule, sans mot de passe.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup("Import") {
            SliderRow(
                label = "Mois d'historique a importer",
                value = settings.syncMonths.toFloat(),
                range = 1f..24f,
                steps = 22,
                display = "${settings.syncMonths} mois",
            ) { viewModel.update { s -> s.copy(syncMonths = it.toInt()) } }

            SwitchRow(
                label = "Inclure les parties non classees",
                checked = settings.includeUnrated,
            ) { viewModel.update { s -> s.copy(includeUnrated = it) } }

            SwitchRow(
                label = "Analyser automatiquement apres l'import",
                checked = settings.autoAnalyzeAfterSync,
            ) { viewModel.update { s -> s.copy(autoAnalyzeAfterSync = it) } }
        }

        SettingsGroup("Analyse") {
            Text("Profondeur d'analyse", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAnalyzer.PRESETS.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = settings.analysisPresetIndex == index,
                        onClick = { viewModel.update { s -> s.copy(analysisPresetIndex = index) } },
                        label = { Text(preset.label) },
                    )
                }
            }
            Text(
                GameAnalyzer.PRESETS[settings.analysisPresetIndex.coerceIn(0, 2)].hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Text("Moteur", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preference in EnginePreference.entries) {
                    val enabled = preference != EnginePreference.STOCKFISH || viewModel.nativeEngineAvailable
                    FilterChip(
                        selected = settings.enginePreference == preference,
                        onClick = { viewModel.update { s -> s.copy(enginePreference = preference) } },
                        enabled = enabled,
                        label = {
                            Text(
                                when (preference) {
                                    EnginePreference.AUTO -> "Auto"
                                    EnginePreference.FORGE -> "Integre"
                                    EnginePreference.STOCKFISH -> "Stockfish"
                                }
                            )
                        },
                    )
                }
            }
            Text(
                if (viewModel.nativeEngineAvailable) {
                    "Stockfish natif detecte : l'analyse est plus rapide et plus fiable."
                } else if (BuildConfig.HAS_NATIVE_ENGINE) {
                    "Stockfish a ete compile mais la bibliotheque n'a pas pu etre chargee."
                } else {
                    "Stockfish natif absent : le moteur Kotlin integre est utilise. " +
                        "Pour l'ajouter, lancez tools/fetch_stockfish.ps1 puis recompilez."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SliderRow(
                label = "Puzzles maximum par partie",
                value = settings.maxPuzzlesPerGame.toFloat(),
                range = 1f..8f,
                steps = 6,
                display = "${settings.maxPuzzlesPerGame}",
            ) { viewModel.update { s -> s.copy(maxPuzzlesPerGame = it.toInt()) } }
        }

        SettingsGroup("Entrainement") {
            SliderRow(
                label = "Objectif quotidien de puzzles",
                value = settings.dailyPuzzleGoal.toFloat(),
                range = 3f..40f,
                steps = 36,
                display = "${settings.dailyPuzzleGoal} par jour",
            ) { viewModel.update { s -> s.copy(dailyPuzzleGoal = it.toInt()) } }
        }

        SettingsGroup("Affichage") {
            Text("Plateau", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (palette in BoardPalette.all) {
                    FilterChip(
                        selected = settings.boardTheme == palette.key,
                        onClick = { viewModel.update { s -> s.copy(boardTheme = palette.key) } },
                        label = { Text(palette.label) },
                    )
                }
            }
            SwitchRow("Fleche du meilleur coup en revue", settings.showBestMoveArrow) {
                viewModel.update { s -> s.copy(showBestMoveArrow = it) }
            }

            Spacer(Modifier.height(4.dp))
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.entries) {
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.update { s -> s.copy(themeMode = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
            SwitchRow("Couleurs dynamiques du systeme", settings.dynamicColor) {
                viewModel.update { s -> s.copy(dynamicColor = it) }
            }
        }

        SettingsGroup("Donnees") {
            Text(
                "Tout est stocke sur ce telephone : parties, analyses et puzzles. Rien n'est envoye " +
                    "ailleurs que vers l'API publique de chess.com pour telecharger vos parties.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmReset = true }) { Text("Effacer les donnees locales") }
        }

        Text(
            "ChessForge ${BuildConfig.VERSION_NAME} · analyse locale de parties chess.com",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Effacer les donnees ?") },
            text = {
                Text(
                    "Les parties, analyses et puzzles seront supprimes de ce telephone. " +
                        "Vos parties restent disponibles sur chess.com et pourront etre reimportees.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetData()
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
