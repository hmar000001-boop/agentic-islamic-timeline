package app.timeline.agentique.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.DeviceLocation
import app.timeline.agentique.core.Geo
import app.timeline.agentique.core.LocationResolver
import app.timeline.agentique.core.clock
import app.timeline.agentique.core.parseNum
import app.timeline.agentique.prayer.JamStatus
import app.timeline.agentique.prayer.PrayerCalc
import app.timeline.agentique.prayer.QasrStatus
import app.timeline.agentique.prayer.TravelRules
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun TravelScreen(onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ctx.container.settings
    val s by store.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    var dist by rememberSaveable { mutableStateOf("") }
    var stay by rememberSaveable { mutableStateOf("") }
    var autoFailed by remember { mutableStateOf(false) }
    val at = LocationResolver.current(ctx, s)

    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.travel_mode))
            Switch(checked = s.travelMode, onCheckedChange = { v -> scope.launch { store.update { it.copy(travelMode = v) } } })
        }

        val m = s.madhhab
        if (m == null) {
            Text(stringResource(R.string.travel_need_madhhab))
            Button(onClick = { onNavigate(Routes.SETTINGS) }) { Text(stringResource(R.string.open)) }
        } else {
            NumField(stringResource(R.string.travel_distance), dist, { dist = it })
            NumField(stringResource(R.string.travel_stay), stay, { stay = it })
            OutlinedButton(onClick = {
                val dev = DeviceLocation.lastKnown(ctx)
                val home = s.home
                if (dev != null && home != null) {
                    dist = Geo.distanceKm(home, dev).roundToInt().toString()
                    autoFailed = false
                } else autoFailed = true
            }) { Text(stringResource(R.string.travel_auto_distance)) }
            if (autoFailed) Note(stringResource(R.string.travel_auto_none))

            parseNum(dist)?.let { d ->
                val r = TravelRules.evaluate(m, d, s.travelThresholdKm, parseNum(stay)?.toInt() ?: 0)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                when (r.qasr) {
                                    QasrStatus.NOT_TRAVELLER -> R.string.travel_qasr_none
                                    QasrStatus.OBLIGATORY -> R.string.travel_qasr_obligatory
                                    QasrStatus.PERMISSIBLE -> R.string.travel_qasr_permissible
                                },
                            ),
                        )
                        Text(
                            stringResource(
                                when (r.jam) {
                                    JamStatus.NOT_TRAVELLER -> R.string.travel_jam_none
                                    JamStatus.NOT_PERMITTED -> R.string.travel_jam_not_permitted
                                    JamStatus.PERMITTED_WITH_CONDITIONS -> R.string.travel_jam_permitted
                                },
                            ),
                        )
                        if (r.stayExceeded) Text(stringResource(R.string.travel_stay_exceeded, r.maxStayDays))
                    }
                }
            }
            Note(stringResource(R.string.travel_threshold_note, s.travelThresholdKm.roundToInt()))
            Note(stringResource(R.string.source_user_choice))
            Note(stringResource(R.string.travel_disclaimer))
        }

        SectionTitle(stringResource(R.string.travel_times_title))
        if (at == null) {
            Note(stringResource(R.string.travel_no_location))
        } else {
            val times = remember(at, s.method, s.madhhab) {
                PrayerCalc.times(LocalDate.now(), at, s, ZoneId.systemDefault())
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.forEach { pt ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(pt.prayer.label))
                            Text(pt.time.clock())
                        }
                    }
                }
            }
            Note(stringResource(R.string.travel_qibla, PrayerCalc.qiblaDegrees(at).roundToInt()))
        }
    }
}
