package com.pedometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedometer.pulselab.PulseLabExportWorker
import com.pedometer.service.WatchConnectionService
import com.pedometer.ui.theme.PedometerTheme
import com.pedometer.vm.ConnectionStatus
import com.pedometer.vm.WatchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran autonome français. Le protocole Bluetooth est conservé dans WatchRepository.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PedometerApp.repository.hasCredentials) {
            ContextCompat.startForegroundService(this, Intent(this, WatchConnectionService::class.java))
        }
        setContent {
            PedometerTheme {
                val vm: WatchViewModel = viewModel()
                val state by vm.state.collectAsState()
                var showKey by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    val bluetoothOk = if (Build.VERSION.SDK_INT >= 31) {
                        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
                    } else true
                    if (bluetoothOk) {
                        vm.connect()
                        message = "Connexion demandée. Garde la montre à proximité et Mi Fitness arrêté."
                    } else {
                        message = "Autorise les appareils à proximité dans les paramètres Android."
                    }
                }
                val macOk = Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
                    .matches(state.macAddress.trim())
                val keyOk = Regex("(?i)^[0-9a-f]{32}$")
                    .matches(state.authKey.trim().removePrefix("0x").removePrefix("0X"))
                val connected = state.connectionStatus == ConnectionStatus.Connected

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text("Pulse Lab", style = MaterialTheme.typography.headlineLarge)
                        Text("Connecteur Redmi Watch 5 Lite", style = MaterialTheme.typography.titleMedium)
                        Text("Bluetooth direct et sauvegarde automatique locale", style = MaterialTheme.typography.bodySmall)

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("État de la montre", style = MaterialTheme.typography.titleLarge)
                                val status = when (state.connectionStatus) {
                                    ConnectionStatus.Connected -> "Connectée"
                                    ConnectionStatus.Connecting -> "Connexion en cours…"
                                    ConnectionStatus.Authenticating -> "Authentification en cours…"
                                    ConnectionStatus.Disconnected -> "Déconnectée"
                                }
                                Text(status, style = MaterialTheme.typography.titleMedium)
                                Text(if (state.batteryLevel >= 0) "Batterie : " + state.batteryLevel + " %" else "Batterie : non disponible")
                                if (state.model.isNotBlank()) Text("Modèle : " + state.model)
                                if (state.firmware.isNotBlank()) Text("Logiciel de la montre : " + state.firmware)
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Connexion Bluetooth", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    value = state.macAddress,
                                    onValueChange = vm::updateMacAddress,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Adresse Bluetooth (MAC)") },
                                    placeholder = { Text("AA:BB:CC:DD:EE:FF") }
                                )
                                OutlinedTextField(
                                    value = state.authKey,
                                    onValueChange = vm::updateAuthKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    label = { Text("Clé d’authentification (32 caractères)") },
                                    placeholder = { Text("Clé extraite de Mi Fitness") },
                                    supportingText = { Text("Garde cette clé confidentielle.") }
                                )
                                TextButton(onClick = { showKey = !showKey }) {
                                    Text(if (showKey) "Masquer la clé" else "Afficher la clé")
                                }
                                if (!macOk && state.macAddress.isNotBlank()) {
                                    Text("Adresse MAC invalide.", color = MaterialTheme.colorScheme.error)
                                }
                                if (!keyOk && state.authKey.isNotBlank()) {
                                    Text("La clé doit contenir 32 caractères hexadécimaux.", color = MaterialTheme.colorScheme.error)
                                }
                                if (connected) {
                                    OutlinedButton(onClick = {
                                        vm.disconnect()
                                        message = "Déconnexion demandée."
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Déconnecter la montre") }
                                } else {
                                    Button(
                                        enabled = macOk && keyOk,
                                        onClick = {
                                            val permissions = mutableListOf<String>()
                                            if (Build.VERSION.SDK_INT >= 31) {
                                                permissions += Manifest.permission.BLUETOOTH_CONNECT
                                                permissions += Manifest.permission.BLUETOOTH_SCAN
                                            }
                                            permissions += Manifest.permission.ACCESS_FINE_LOCATION
                                            if (Build.VERSION.SDK_INT >= 29) permissions += Manifest.permission.ACTIVITY_RECOGNITION
                                            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                                            permissionLauncher.launch(permissions.toTypedArray())
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Connecter la montre") }
                                }
                                Text(
                                    "Avant l’essai : force l’arrêt de Mi Fitness, sans le désinstaller et sans dissocier la montre.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (message.isNotBlank()) Text(message)
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Synchronisation des séances", style = MaterialTheme.typography.titleLarge)
                                Text("Les nouvelles activités sont demandées à la connexion.")
                                Button(
                                    onClick = {
                                        PedometerApp.repository.refreshFromWatch()
                                        vm.refreshData(alsoFetchFromWatch = false)
                                        PulseLabExportWorker.changed(this@MainActivity)
                                        message = if (connected)
                                            "Synchronisation demandée à la montre."
                                        else
                                            "Montre non connectée : export des données déjà enregistrées lancé."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Synchroniser maintenant") }
                                Text("Dossier : Téléchargements / PulseLab / WatchInbox")
                                Text(
                                    "Une séance = un JSON. Le fichier est mis à jour si le GPS arrive plus tard. Android peut décaler les tâches en arrière-plan.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées : " + state.recentWorkouts.size,
                                    style = MaterialTheme.typography.titleLarge)
                                if (state.recentWorkouts.isEmpty()) {
                                    Text("Aucune séance reçue. Connecte la montre, puis synchronise.")
                                } else {
                                    for (workout in state.recentWorkouts.take(15)) {
                                        HorizontalDivider()
                                        Text(sportEnFrancais(workout.sportName), style = MaterialTheme.typography.titleMedium)
                                        Text(dateFrancais(workout.startTime) + " • " +
                                            (workout.durationSec / 60) + " min • " +
                                            String.format(Locale.FRANCE, "%.2f", workout.distanceM / 1000.0) + " km")
                                        if (workout.hrAvg > 0)
                                            Text("Fréquence cardiaque moyenne : " + workout.hrAvg + " bpm")
                                    }
                                }
                            }
                        }
                        Text(
                            "Application de test indépendante de Pulse Lab. Aucune donnée envoyée à un serveur.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

private fun dateFrancais(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(timestamp))

private fun sportEnFrancais(s: String) = when (s) {
    "Бег" -> "Course à pied"
    "Ходьба" -> "Marche"
    "Беговая дорожка" -> "Tapis de course"
    "Велосипед" -> "Vélo"
    "Велотренажёр" -> "Vélo d’intérieur"
    "Свободная" -> "Entraînement libre"
    "Плавание" -> "Natation"
    "Эллиптический" -> "Elliptique"
    "Гребля" -> "Rameur"
    "Скакалка" -> "Corde à sauter"
    "HIIT" -> "HIIT"
    else -> if (s.startsWith("Тренировка #")) "Entraînement #" + s.substringAfter("#") else s
}
