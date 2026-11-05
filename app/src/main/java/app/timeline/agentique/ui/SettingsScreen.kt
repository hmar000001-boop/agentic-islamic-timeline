package app.timeline.agentique.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.GeoPoint
import app.timeline.agentique.core.Madhhab
import app.timeline.agentique.core.MethodOptions
import app.timeline.agentique.core.Proactivity
import app.timeline.agentique.core.parseNum
import app.timeline.agentique.llm.MODEL_WORK_NAME
import app.timeline.agentique.llm.ModelFiles
import app.timeline.agentique.llm.enqueueModelDownload
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp, end = 12.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SaveField(label: String, initial: String, secret: Boolean = false, onSave: (String) -> Unit) {
    var v by remember(initial) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = v, onValueChange = { v = it }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        )
        TextButton(onClick = { onSave(v) }) { Text(stringResource(R.string.save)) }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ctx.container.settings
    val s by store.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    fun update(block: (AppSettings) -> AppSettings) { scope.launch { store.update(block) } }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Page {
        SectionTitle(stringResource(R.string.set_madhhab))
        listOf(
            Madhhab.HANAFI to R.string.madhhab_hanafi, Madhhab.MALIKI to R.string.madhhab_maliki,
            Madhhab.SHAFII to R.string.madhhab_shafii, Madhhab.HANBALI to R.string.madhhab_hanbali,
        ).forEach { (m, label) -> RadioRow(stringResource(label), s.madhhab == m) { update { it.copy(madhhab = m) } } }

        SectionTitle(stringResource(R.string.set_method))
        val labels = stringArrayResource(R.array.method_labels)
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(labels.getOrElse(MethodOptions.names.indexOf(s.method)) { s.method })
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MethodOptions.names.forEachIndexed { i, name ->
                    DropdownMenuItem(text = { Text(labels[i]) }, onClick = { menuOpen = false; update { it.copy(method = name) } })
                }
            }
        }

        SectionTitle(stringResource(R.string.set_location))
        SwitchRow(stringResource(R.string.set_use_device), s.useDeviceLocation) { v -> update { it.copy(useDeviceLocation = v) } }
        OutlinedButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
            Text(stringResource(R.string.set_grant_location))
        }
        Note(stringResource(R.string.set_home_title))
        HomeFields(s.home) { p -> update { it.copy(home = p) } }

        SectionTitle(stringResource(R.string.set_alarms))
        SwitchRow(stringResource(R.string.set_alarms), s.alarms) { v -> update { it.copy(alarms = v) } }
        if (Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.set_grant_notif))
            }
        }
        if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")),
                )
            }) { Text(stringResource(R.string.set_exact_alarm)) }
        }

        SectionTitle(stringResource(R.string.set_proactivity))
        listOf(
            Proactivity.LOW to R.string.pro_low, Proactivity.NORMAL to R.string.pro_normal, Proactivity.HIGH to R.string.pro_high,
        ).forEach { (p, label) -> RadioRow(stringResource(label), s.proactivity == p) { update { it.copy(proactivity = p) } } }

        var minutes by remember(s.hifzMinutes) { mutableFloatStateOf(s.hifzMinutes.toFloat()) }
        Text(stringResource(R.string.set_hifz_minutes, minutes.roundToInt()))
        Slider(
            value = minutes, onValueChange = { minutes = it }, valueRange = 5f..60f, steps = 10,
            onValueChangeFinished = { update { it.copy(hifzMinutes = minutes.roundToInt()) } },
        )

        SaveField(stringResource(R.string.set_travel_km), s.travelThresholdKm.roundToInt().toString()) { v ->
            parseNum(v)?.takeIf { it > 0 }?.let { km -> update { it.copy(travelThresholdKm = km) } }
        }

        SectionTitle(stringResource(R.string.set_model_title))
        ModelSection(s)

        Note(stringResource(R.string.set_privacy))
        Note(stringResource(R.string.set_about))
    }
}

@Composable
private fun HomeFields(home: GeoPoint?, onSave: (GeoPoint) -> Unit) {
    var lat by remember(home) { mutableStateOf(home?.lat?.toString().orEmpty()) }
    var lon by remember(home) { mutableStateOf(home?.lon?.toString().orEmpty()) }
    NumField(stringResource(R.string.set_home_lat), lat, { lat = it }, KeyboardType.Text)
    NumField(stringResource(R.string.set_home_lon), lon, { lon = it }, KeyboardType.Text)
    Button(onClick = {
        val a = parseNum(lat)
        val b = parseNum(lon)
        if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) onSave(GeoPoint(a, b))
    }) { Text(stringResource(R.string.save)) }
}

@Composable
private fun ModelSection(s: AppSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ctx.container.settings
    val infos by WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(MODEL_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val info = infos.firstOrNull()
    val installed = remember(info?.state) { ModelFiles.installed(ctx) }

    Text(
        when {
            info?.state == WorkInfo.State.RUNNING ->
                stringResource(R.string.set_model_progress, info?.progress?.getInt("pct", 0) ?: 0)
            info?.state == WorkInfo.State.FAILED && !installed -> stringResource(R.string.set_model_failed)
            installed -> stringResource(R.string.set_model_installed)
            else -> stringResource(R.string.set_model_missing)
        },
    )
    SaveField(stringResource(R.string.set_model_url), s.modelUrl) { v -> scope.launch { store.update { it.copy(modelUrl = v.trim()) } } }
    SaveField(stringResource(R.string.set_model_token), s.modelToken, secret = true) { v ->
        scope.launch { store.update { it.copy(modelToken = v.trim()) } }
    }
    Button(
        enabled = s.modelUrl.isNotBlank() && info?.state != WorkInfo.State.RUNNING,
        onClick = { enqueueModelDownload(ctx) },
    ) { Text(stringResource(R.string.set_model_download)) }
}
