package app.timeline.islamic.feature.domainhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.timeline.islamic.core.domain.DomainPayloadCodec
import app.timeline.islamic.core.domain.DomainWorkspaceRecord
import app.timeline.islamic.core.domain.DomainWorkspaceRepository
import app.timeline.islamic.feature.prayer.HighLatitudeRule
import app.timeline.islamic.feature.prayer.PrayerCalculationProfile
import app.timeline.islamic.feature.prayer.PrayerCalculator
import app.timeline.islamic.feature.hifz.HifzReviewPlanner
import app.timeline.islamic.feature.hifz.HifzReviewState
import app.timeline.islamic.feature.hifz.QuranPassageReference
import app.timeline.islamic.feature.travel.QiblaCalculator
import app.timeline.islamic.feature.travel.TravelStop
import app.timeline.islamic.feature.travel.TravelSegment
import app.timeline.islamic.feature.halal.CertificationEvidence
import app.timeline.islamic.feature.halal.HalalEvidenceAssessment
import app.timeline.islamic.feature.halal.HalalEvidenceEngine
import app.timeline.islamic.feature.halal.IngredientEvidenceState
import app.timeline.islamic.feature.halal.IngredientFinding
import app.timeline.islamic.feature.zakat.*
import app.timeline.islamic.feature.inheritance.*
import app.timeline.islamic.feature.community.local.CommunitySnapshotImporter
import app.timeline.islamic.feature.community.local.CommunityEntry
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DomainSection(val id: String) {
    PRAYER("prayer"), HIFZ("hifz"), TRAVEL("travel"), HALAL("halal"), ZAKAT("zakat"), INHERITANCE("inheritance"), COMMUNITY("community-local")
}

@Composable
private fun localized(en: String, ar: String): String =
    if (LocalConfiguration.current.locales.get(0)?.language == "ar") ar else en

@Composable
private fun DomainField(labelEn: String, labelAr: String, value: String, onValueChange: (String) -> Unit, multiline: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(localized(labelEn, labelAr)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = if (multiline) 120.dp else 56.dp),
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 8 else 1
    )
}

@Composable
private fun ConfirmRow(labelEn: String, labelAr: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(localized(labelEn, labelAr), modifier = Modifier.padding(top = 12.dp))
    }
}

/** Phase 6 domain workspace. All state is local; religious outputs remain evidence/ruleset-gated. */
@Composable
fun DomainHubFeature(repository: DomainWorkspaceRepository, onBack: () -> Unit) {
    var section by remember { mutableStateOf<DomainSection?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }
    var savedRecords by remember { mutableStateOf<List<DomainWorkspaceRecord>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun refreshRecords(domain: DomainSection) {
        val records = withContext(Dispatchers.IO) { repository.list(domain.id, 100) }
        savedRecords = records
        savedCount = records.size
    }

    LaunchedEffect(section) {
        val selected = section
        if (selected != null) {
            try {
                refreshRecords(selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = "FAILED:${failure::class.simpleName}"
                savedRecords = emptyList()
                savedCount = 0
            }
        } else {
            savedRecords = emptyList()
            savedCount = 0
        }
    }

    fun persist(domain: DomainSection, status: String, fields: Map<String, String>, rawPayload: ByteArray? = null) {
        scope.launch {
            try {
                val payload = rawPayload ?: DomainPayloadCodec.encode(fields)
                withContext(Dispatchers.IO) {
                    repository.create(UUID.randomUUID().toString(), domain.id, 1, status, payload, System.currentTimeMillis())
                }
                refreshRecords(domain)
                message = "SAVED"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = "FAILED:${failure::class.simpleName}"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(localized("Islamic domain tools", "الأدوات الإسلامية"), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { if (section == null) onBack() else { section = null; message = null } }) {
                Text(localized("Back", "رجوع"))
            }
        }
        if (section == null) {
            Text(localized(
                "Local-first tools. Calculations are deterministic; religious conclusions require reviewed evidence and explicit settings.",
                "أدوات تعمل محلياً. الحسابات حتمية؛ أما الأحكام الشرعية فتتطلب أدلة مراجعة وإعدادات صريحة."
            ))
            DomainSection.entries.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { section = item; message = null }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(sectionTitle(item), style = MaterialTheme.typography.titleMedium)
                        Text(sectionDescription(item), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Text(sectionTitle(section!!), style = MaterialTheme.typography.titleLarge)
            Text(localized("Saved local drafts: $savedCount", "المسودات المحلية المحفوظة: $savedCount"), style = MaterialTheme.typography.bodySmall)
            savedRecords.forEach { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${record.status} · r${record.revision}", style = MaterialTheme.typography.titleSmall)
                            Text("${record.id} · ${Instant.ofEpochMilli(record.updatedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            val selectedDomain = section
                            scope.launch {
                                val removed = try {
                                    withContext(Dispatchers.IO) { repository.delete(record.id, record.revision) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    false
                                }
                                selectedDomain?.let { target ->
                                    try { refreshRecords(target) } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { savedRecords = emptyList(); savedCount = 0 }
                                }
                                message = if (removed) "DELETED" else "FAILED:Draft changed or storage unavailable; refresh and retry"
                            }
                        }) { Text(localized("Delete", "حذف")) }
                    }
                }
            }
            when (section!!) {
                DomainSection.PRAYER -> PrayerPanel { status, fields -> persist(DomainSection.PRAYER, status, fields) }
                DomainSection.HIFZ -> HifzPanel { status, fields -> persist(DomainSection.HIFZ, status, fields) }
                DomainSection.TRAVEL -> TravelPanel { status, fields -> persist(DomainSection.TRAVEL, status, fields) }
                DomainSection.HALAL -> HalalPanel { status, fields -> persist(DomainSection.HALAL, status, fields) }
                DomainSection.ZAKAT -> ZakatPanel { status, fields -> persist(DomainSection.ZAKAT, status, fields) }
                DomainSection.INHERITANCE -> InheritancePanel { status, fields -> persist(DomainSection.INHERITANCE, status, fields) }
                DomainSection.COMMUNITY -> CommunityPanel { status, bytes -> persist(DomainSection.COMMUNITY, status, emptyMap(), bytes) }
            }
        }
        message?.let { status ->
            when {
                status == "SAVED" -> Text(localized("Saved locally in encrypted storage.", "تم الحفظ محلياً في التخزين المشفر."), color = MaterialTheme.colorScheme.primary)
                status == "DELETED" -> Text(localized("Draft deleted.", "تم حذف المسودة."), color = MaterialTheme.colorScheme.primary)
                status.startsWith("FAILED:") -> Text(localized("Operation failed", "فشلت العملية") + ": " + status.removePrefix("FAILED:"), color = MaterialTheme.colorScheme.error)
                else -> Text(status, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            localized("This is a local workspace, not a fatwa, legal opinion or live information service.",
                "Cet espace est local ; il ne remplace ni une fatwa, ni un avis juridique, ni un service d'information en direct."),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun sectionTitle(section: DomainSection): String = when (section) {
    DomainSection.PRAYER -> localized("Prayer times", "مواقيت الصلاة")
    DomainSection.HIFZ -> localized("Hifz and review", "الحفظ والمراجعة")
    DomainSection.TRAVEL -> localized("Travel and Qibla", "السفر والقبلة")
    DomainSection.HALAL -> localized("Halal evidence review", "مراجعة أدلة الحلال")
    DomainSection.ZAKAT -> localized("Zakat calculator", "حاسبة الزكاة")
    DomainSection.INHERITANCE -> localized("Inheritance case", "ملف المواريث")
    DomainSection.COMMUNITY -> localized("Local community snapshots", "بيانات المجتمع المحلية")
}

@Composable
private fun sectionDescription(section: DomainSection): String = when (section) {
    DomainSection.PRAYER -> localized("Explicit location, timezone, twilight angles and Asr setting.", "موقع ومنطقة زمنية وزوايا الشفق وإعداد العصر بشكل صريح.")
    DomainSection.HIFZ -> localized("Exact verse references and user-rated spaced review; no AI recitation grading.", "مراجع آيات دقيقة ومراجعة متباعدة بتقييم المستخدم؛ دون تقييم آلي للتلاوة.")
    DomainSection.TRAVEL -> localized("Imported/manual itinerary, local times and on-device Qibla bearing.", "مسار مستورد أو يدوي، أوقات محلية واتجاه القبلة على الجهاز.")
    DomainSection.HALAL -> localized("Ingredient uncertainty and certification provenance; no automatic verdict.", "عدم اليقين في المكونات ومصدر الشهادة؛ دون حكم تلقائي.")
    DomainSection.ZAKAT -> localized("Explainable arithmetic preview using user-entered assumptions.", "معاينة حسابية قابلة للتفسير وفق افتراضات يدخلها المستخدم.")
    DomainSection.INHERITANCE -> localized("Confirmed facts and estate arithmetic; distribution awaits reviewed rules.", "وقائع مؤكدة وحساب التركة؛ التوزيع ينتظر قواعد مراجعة.")
    DomainSection.COMMUNITY -> localized("Versioned imported events only; no chat or live directory.", "فعاليات مستوردة بإصدارات فقط؛ دون محادثة أو دليل مباشر.")
}

@Composable
private fun PrayerPanel(save: (String, Map<String, String>) -> Unit) {
    var name by remember { mutableStateOf("") }; var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }; var fajr by remember { mutableStateOf("") }; var isha by remember { mutableStateOf("") }
    var asr by remember { mutableStateOf("") }; var highLat by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }
    DomainField("Profile name", "اسم الإعداد", name, { name = it })
    DomainField("Latitude", "خط العرض", latitude, { latitude = it })
    DomainField("Longitude", "خط الطول", longitude, { longitude = it })
    DomainField("IANA time zone (e.g. America/Toronto)", "المنطقة الزمنية", zone, { zone = it })
    DomainField("Fajr depression angle (degrees)", "زاوية الفجر بالدرجات", fajr, { fajr = it })
    DomainField("Isha depression angle (degrees)", "زاوية العشاء بالدرجات", isha, { isha = it })
    DomainField("Asr shadow factor (1 or 2)", "معامل ظل العصر (١ أو ٢)", asr, { asr = it })
    Text(localized("High-latitude rule (choose explicitly)", "قاعدة خطوط العرض العليا (اختر صراحةً)"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("NONE", "MIDDLE_OF_NIGHT", "ONE_SEVENTH", "ANGLE_BASED").forEach { value ->
            Button(onClick = { highLat = value }) { Text(if (highLat == value) "✓ $value" else value) }
        }
    }
    Button(onClick = {
        runCatching {
            val profile = PrayerCalculationProfile("user-profile", name, latitude.toDouble(), longitude.toDouble(), zone,
                fajr.toDouble(), isha.toDouble(), asr.toDouble(), HighLatitudeRule.valueOf(highLat))
            val date = LocalDate.now(ZoneId.of(zone))
            val schedule = PrayerCalculator.calculate(date, profile)
            val ordered = listOf("Fajr" to schedule.fajr, "Sunrise" to schedule.sunrise, "Dhuhr" to schedule.dhuhr,
                "Asr" to schedule.asr, "Maghrib" to schedule.maghrib, "Isha" to schedule.isha)
            result = ordered.joinToString("\n") { (label, time) -> "$label: ${time ?: "Unavailable"}" } +
                if (schedule.warnings.isEmpty()) "" else "\n${schedule.warnings.joinToString()}"
            save("CALCULATED_CUSTOM_PROFILE", mapOf("name" to name, "latitude" to latitude, "longitude" to longitude,
                "zoneId" to zone, "fajrAngle" to fajr, "ishaAngle" to isha, "asrFactor" to asr, "highLatitudeRule" to highLat,
                "calculationDate" to date.toString(), "schedule" to result))
        }.onFailure { result = "Invalid profile or unavailable event: ${it.message}" }
    }, enabled = listOf(name, latitude, longitude, zone, fajr, isha, asr, highLat).all { it.isNotBlank() }) {
        Text(localized("Calculate and save profile", "احسب واحفظ الإعداد"))
    }
    if (result.isNotBlank()) Text(result)
}

@Composable
private fun HifzPanel(save: (String, Map<String, String>) -> Unit) {
    var edition by remember { mutableStateOf("") }; var stableKey by remember { mutableStateOf("") }
    var surah by remember { mutableStateOf("") }; var first by remember { mutableStateOf("") }; var last by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }; var interval by remember { mutableStateOf("0") }; var repetitions by remember { mutableStateOf("0") }
    var ease by remember { mutableStateOf("2.5") }; var result by remember { mutableStateOf("") }
    DomainField("Mushaf edition identifier (must match an installed verified edition)", "معرّف طبعة المصحف الموثقة", edition, { edition = it })
    DomainField("Exact stable Quran evidence key", "المفتاح الدقيق لدليل الآية", stableKey, { stableKey = it })
    DomainField("Surah number", "رقم السورة", surah, { surah = it })
    DomainField("First ayah", "أول آية", first, { first = it })
    DomainField("Last ayah", "آخر آية", last, { last = it })
    DomainField("Your review quality (0–5)", "تقييمك للمراجعة (٠–٥)", quality, { quality = it })
    DomainField("Previous repetitions", "عدد المراجعات السابقة", repetitions, { repetitions = it })
    DomainField("Previous interval days", "فترة المراجعة السابقة بالأيام", interval, { interval = it })
    DomainField("Previous ease factor", "معامل السهولة السابق", ease, { ease = it })
    Button(onClick = {
        runCatching {
            val passage = QuranPassageReference(edition, stableKey, surah.toInt(), first.toInt(), last.toInt())
            val scheduled = HifzReviewPlanner.recordReview(passage,
                HifzReviewState(repetitions.toInt(), interval.toInt(), ease.toDouble()), quality.toInt(), Instant.now())
            result = "Next review: ${scheduled.nextDueAt}\nInterval: ${scheduled.next.intervalDays} days\nAlgorithm: ${scheduled.algorithmId}\nThe verse reference has not been resolved against an installed, verified Mushaf by this screen."
            save("REVIEW_SCHEDULED_REFERENCE_UNVERIFIED", mapOf("edition" to edition, "stableKey" to stableKey,
                "surah" to surah, "firstAyah" to first, "lastAyah" to last, "quality" to quality,
                "nextIntervalDays" to scheduled.next.intervalDays.toString(), "nextDueAt" to scheduled.nextDueAt.toString(),
                "repetitions" to scheduled.next.repetitions.toString(), "easeFactor" to scheduled.next.easeFactor.toString()))
        }.onFailure { result = "Cannot schedule review: ${it.message}" }
    }, enabled = listOf(edition, stableKey, surah, first, last, quality).all { it.isNotBlank() }) {
        Text(localized("Schedule review", "جدولة المراجعة"))
    }
    if (result.isNotBlank()) Text(result)
    Text(localized("No voice-recognition or tajwid evaluation is performed.", "لا يتم إجراء تعرف صوتي أو تقييم للتجويد."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TravelPanel(save: (String, Map<String, String>) -> Unit) {
    var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }; var zone by remember { mutableStateOf("") }
    var originLabel by remember { mutableStateOf("") }; var destinationLabel by remember { mutableStateOf("") }
    var originZone by remember { mutableStateOf("") }; var destinationZone by remember { mutableStateOf("") }
    var departureLocal by remember { mutableStateOf("") }; var departureOffset by remember { mutableStateOf("") }
    var arrivalLocal by remember { mutableStateOf("") }; var arrivalOffset by remember { mutableStateOf("") }
    var sourceReference by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }
    Text(localized("Current location for Qibla", "الموقع الحالي لحساب القبلة"), style = MaterialTheme.typography.titleMedium)
    DomainField("Latitude", "خط العرض", latitude, { latitude = it })
    DomainField("Longitude", "خط الطول", longitude, { longitude = it })
    DomainField("IANA time zone", "المنطقة الزمنية", zone, { zone = it })
    Button(onClick = {
        runCatching {
            val bearing = QiblaCalculator.calculate(latitude.toDouble(), longitude.toDouble())
            ZoneId.of(zone)
            result = "Qibla: ${String.format(Locale.ROOT, "%.2f", bearing.degreesFromTrueNorthClockwise)}° from true north\nTime zone: $zone"
            save("LOCAL_QIBLA_AND_TRAVEL_CONTEXT", mapOf("latitude" to latitude, "longitude" to longitude,
                "zoneId" to zone, "qiblaBearingDegrees" to bearing.degreesFromTrueNorthClockwise.toString()))
        }.onFailure { result = "Invalid coordinates or time zone: ${it.message}" }
    }, enabled = latitude.isNotBlank() && longitude.isNotBlank() && zone.isNotBlank()) {
        Text(localized("Calculate Qibla and save", "احسب القبلة واحفظ"))
    }
    if (result.isNotBlank()) Text(result)

    Text(localized("One imported/manual route segment", "مقطع مسار مستورد أو يدوي"), style = MaterialTheme.typography.titleMedium)
    DomainField("Origin label", "نقطة الانطلاق", originLabel, { originLabel = it })
    DomainField("Destination label", "الوجهة", destinationLabel, { destinationLabel = it })
    DomainField("Origin IANA time zone", "منطقة زمن نقطة الانطلاق", originZone, { originZone = it })
    DomainField("Destination IANA time zone", "منطقة زمن الوجهة", destinationZone, { destinationZone = it })
    DomainField("Departure local time (YYYY-MM-DDTHH:MM)", "وقت المغادرة المحلي", departureLocal, { departureLocal = it })
    DomainField("Departure UTC offset if time is ambiguous (e.g. -04:00)", "إزاحة UTC للمغادرة عند التوقيت الملتبس", departureOffset, { departureOffset = it })
    DomainField("Arrival local time (YYYY-MM-DDTHH:MM)", "وقت الوصول المحلي", arrivalLocal, { arrivalLocal = it })
    DomainField("Arrival UTC offset if time is ambiguous", "إزاحة UTC للوصول عند التوقيت الملتبس", arrivalOffset, { arrivalOffset = it })
    DomainField("Itinerary source reference", "مرجع مصدر خط السير", sourceReference, { sourceReference = it })
    Button(onClick = {
        runCatching {
            val origin = TravelStop(originLabel, null, null, originZone)
            val destination = TravelStop(destinationLabel, null, null, destinationZone)
            val departure = TravelSegment.resolveLocal(LocalDateTime.parse(departureLocal), originZone,
                departureOffset.takeIf { it.isNotBlank() }?.let { ZoneOffset.of(it) })
            val arrival = TravelSegment.resolveLocal(LocalDateTime.parse(arrivalLocal), destinationZone,
                arrivalOffset.takeIf { it.isNotBlank() }?.let { ZoneOffset.of(it) })
            val segment = TravelSegment(origin, destination, departure, arrival, sourceReference, Instant.now())
            result = "Departure: ${segment.originLocalDeparture}\nArrival: ${segment.destinationLocalArrival}\nElapsed: ${java.time.Duration.between(departure, arrival).toHours()} hours"
            save("LOCAL_TRAVEL_SEGMENT", mapOf("origin" to originLabel, "destination" to destinationLabel,
                "originZone" to originZone, "destinationZone" to destinationZone, "departureInstant" to departure.toString(),
                "arrivalInstant" to arrival.toString(), "departureLocal" to departureLocal, "arrivalLocal" to arrivalLocal,
                "departureOffset" to departureOffset, "arrivalOffset" to arrivalOffset, "sourceReference" to sourceReference))
        }.onFailure { result = "Route rejected: ${it.message}" }
    }, enabled = listOf(originLabel, destinationLabel, originZone, destinationZone, departureLocal, arrivalLocal, sourceReference).all { it.isNotBlank() }) {
        Text(localized("Validate and save route segment", "تحقق واحفظ مقطع المسار"))
    }
    if (result.isNotBlank()) Text(result)
    Text(localized("Qasr/jam' guidance is not inferred from timezone change; it requires an explicit madhhab and an installed, independently verified ruleset.",
        "لا يُستنتج القصر والجمع من تغيير المنطقة الزمنية؛ بل يتطلبان اختيار المذهب وقواعد مثبتة مستقلة."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HalalPanel(save: (String, Map<String, String>) -> Unit) {
    var product by remember { mutableStateOf("") }; var ingredients by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }; var territory by remember { mutableStateOf("") }
    var certificateNumber by remember { mutableStateOf("") }; var productSite by remember { mutableStateOf("") }
    var validFrom by remember { mutableStateOf("") }; var validUntil by remember { mutableStateOf("") }
    var matchClaim by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }
    DomainField("Product name", "اسم المنتج", product, { product = it })
    DomainField("Ingredients (one per line)", "المكونات (واحد في كل سطر)", ingredients, { ingredients = it }, multiline = true)
    DomainField("Certification organization (optional)", "جهة الشهادة (اختياري)", organization, { organization = it })
    DomainField("Territory", "النطاق الجغرافي", territory, { territory = it })
    DomainField("Product/site covered", "المنتج أو الموقع المشمول", productSite, { productSite = it })
    DomainField("Certificate number", "رقم الشهادة", certificateNumber, { certificateNumber = it })
    DomainField("Valid from (ISO instant, optional)", "صالح من (تاريخ ISO، اختياري)", validFrom, { validFrom = it })
    DomainField("Valid until (ISO instant, optional)", "صالح حتى (تاريخ ISO، اختياري)", validUntil, { validUntil = it })
    ConfirmRow("I report that this certificate entry appears to cover this product/site (not independently verified)",
        "أفيد بأن بيانات الشهادة تبدو شاملة لهذا المنتج أو الموقع (دون تحقق مستقل)", matchClaim) { matchClaim = it }
    DomainField("Source reference", "مرجع المصدر", source, { source = it })
    Button(onClick = {
        runCatching {
            val cert = if (organization.isBlank()) null else CertificationEvidence(organization, territory, productSite,
                certificateNumber, validFrom.takeIf { it.isNotBlank() }?.let(Instant::parse),
                validUntil.takeIf { it.isNotBlank() }?.let(Instant::parse), source, Instant.now(),
                productOrSiteMatchConfirmed = matchClaim)
            val findings = ingredients.lines().map(String::trim).filter(String::isNotBlank).map {
                IngredientFinding(it.take(256), IngredientEvidenceState.INSUFFICIENT_DATA, null)
            }
            val assessed = HalalEvidenceEngine.assess(HalalEvidenceAssessment(product, findings, cert, Instant.now()))
            result = "Ingredient evidence: ${assessed.ingredientStates}\nCertification evidence: ${assessed.certificationState}\nConclusion: ${assessed.conclusion}"
            save("NEEDS_HUMAN_EVIDENCE_REVIEW", mapOf("product" to product, "ingredients" to ingredients,
                "organization" to organization, "territory" to territory, "productSite" to productSite,
                "certificateNumber" to certificateNumber, "validFrom" to validFrom, "validUntil" to validUntil,
                "productOrSiteMatchReportedByUser" to matchClaim.toString(), "source" to source,
                "certificationState" to assessed.certificationState.name, "conclusion" to assessed.conclusion))
        }.onFailure { result = "Assessment could not be saved: ${it.message}" }
    }, enabled = product.isNotBlank()) { Text(localized("Assess evidence and save", "قيّم الأدلة واحفظ")) }
    if (result.isNotBlank()) Text(result)
    Text(localized("A label photo or ingredient name is not a halal/haram ruling or proof of certification.",
        "صورة الملصق أو اسم المكوّن ليس حكماً بالحلال أو الحرام ولا دليلاً على الشهادة."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ZakatPanel(save: (String, Map<String, String>) -> Unit) {
    var currency by remember { mutableStateOf("") }; var cash by remember { mutableStateOf("") }; var dueDebt by remember { mutableStateOf("") }
    var nisab by remember { mutableStateOf("") }; var rate by remember { mutableStateOf("") }
    var haulStart by remember { mutableStateOf("") }; var valuationDate by remember { mutableStateOf("") }
    var haulDays by remember { mutableStateOf("") }
    var assetsConfirmed by remember { mutableStateOf(false) }; var debtsConfirmed by remember { mutableStateOf(false) }; var nisabConfirmed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    DomainField("Currency code (ISO 4217)", "رمز العملة", currency, { currency = it })
    DomainField("Cash assets (minor units, e.g. cents)", "الأصول النقدية (الوحدات الصغرى مثل السنت)", cash, { cash = it })
    DomainField("Due liabilities (minor units)", "الالتزامات المستحقة (الوحدات الصغرى)", dueDebt, { dueDebt = it })
    DomainField("Nisab value (minor units, manually valued)", "قيمة النصاب (تدخل يدوياً)", nisab, { nisab = it })
    DomainField("Rate (basis points; enter explicitly)", "النسبة بنقاط الأساس (أدخلها صراحة)", rate, { rate = it })
    DomainField("Haul start date (YYYY-MM-DD)", "بداية الحول", haulStart, { haulStart = it })
    DomainField("Assumed haul duration in days (manual, unreviewed)", "مدة الحول المفترضة بالأيام (يدوية وغير مراجعة)", haulDays, { haulDays = it })
    DomainField("Valuation date (YYYY-MM-DD)", "تاريخ التقييم", valuationDate, { valuationDate = it })
    ConfirmRow("I confirm the asset value", "أؤكد قيمة الأصول", assetsConfirmed) { assetsConfirmed = it }
    ConfirmRow("I confirm the liabilities", "أؤكد الالتزامات", debtsConfirmed) { debtsConfirmed = it }
    ConfirmRow("I confirm the manually entered nisab value", "أؤكد قيمة النصاب المدخلة يدوياً", nisabConfirmed) { nisabConfirmed = it }
    Button(onClick = {
        runCatching {
            val profile = ZakatRuleProfile("user-entered-unreviewed", "draft-1", currency.uppercase(), rate.toInt(),
                setOf(ZakatAssetCategory.CASH), setOf(ZakatLiabilityCategory.SHORT_TERM_DUE), NisabReference.CUSTOM,
                nisab.toLong(), haulDays.toInt())
            val input = ZakatCaseInput("zakat-${UUID.randomUUID()}", profile,
                listOf(ZakatAssetInput(ZakatAssetCategory.CASH, MoneyAmount(currency.uppercase(), cash.toLong()), LocalDate.parse(valuationDate), assetsConfirmed)),
                listOf(ZakatLiabilityInput(ZakatLiabilityCategory.SHORT_TERM_DUE, MoneyAmount(currency.uppercase(), dueDebt.toLong()), debtsConfirmed)),
                nisabConfirmed, LocalDate.parse(haulStart), LocalDate.parse(valuationDate))
            val calculation = ZakatCalculator.calculate(input)
            result = "Status: ${calculation.status}\nNet eligible: ${calculation.netEligibleMinor} ${calculation.currencyCode} minor units\nUser-entered threshold: ${calculation.nisabMinor}\nArithmetic preview only: ${calculation.amountMinor ?: "not calculated"}\n${calculation.formula}\n${calculation.calculationNote}"
            save("ARITHMETIC_PREVIEW_RULES_UNREVIEWED", mapOf("currency" to currency.uppercase(), "cashMinor" to cash,
                "liabilityMinor" to dueDebt, "nisabMinor" to nisab, "rateBasisPoints" to rate,
                "haulStart" to haulStart, "haulDaysAssumption" to haulDays, "valuationDate" to valuationDate, "assetsConfirmed" to assetsConfirmed.toString(),
                "liabilitiesConfirmed" to debtsConfirmed.toString(), "nisabConfirmed" to nisabConfirmed.toString(),
                "previewAmountMinor" to (calculation.amountMinor?.toString() ?: "")))
        }.onFailure { result = "Invalid inputs: ${it.message}" }
    }, enabled = listOf(currency, cash, dueDebt, nisab, rate, haulStart, haulDays, valuationDate).all { it.isNotBlank() }) {
        Text(localized("Calculate arithmetic preview", "احسب المعاينة الحسابية"))
    }
    if (result.isNotBlank()) Text(result)
    Text(localized("This draft uses only cash and manually entered parameters. It is not a Zakat ruling; a reviewed madhhab-specific ruleset is not installed.",
        "تستخدم هذه المسودة النقد والمعايير المدخلة يدوياً فقط. ليست حكماً للزكاة؛ ولا توجد قاعدة معتمدة خاصة بالمذهب."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InheritancePanel(save: (String, Map<String, String>) -> Unit) {
    var jurisdiction by remember { mutableStateOf("") }; var madhhab by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }; var gross by remember { mutableStateOf("") }; var debts by remember { mutableStateOf("") }
    var funeral by remember { mutableStateOf("") }; var bequest by remember { mutableStateOf("") }; var spouse by remember { mutableStateOf("0") }
    var sons by remember { mutableStateOf("0") }; var daughters by remember { mutableStateOf("0") }
    var fullBrothers by remember { mutableStateOf("0") }; var fullSisters by remember { mutableStateOf("0") }
    var paternalBrothers by remember { mutableStateOf("0") }; var paternalSisters by remember { mutableStateOf("0") }
    var maternalSiblings by remember { mutableStateOf("0") }; var otherHeirs by remember { mutableStateOf("") }
    var father by remember { mutableStateOf(false) }; var mother by remember { mutableStateOf(false) }
    var paternalGrandfather by remember { mutableStateOf(false) }; var maternalGrandmother by remember { mutableStateOf(false) }
    var allHeirsEnumerated by remember { mutableStateOf(false) }; var factsConfirmed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    DomainField("Civil jurisdiction (required)", "الاختصاص المدني (مطلوب)", jurisdiction, { jurisdiction = it })
    Text(localized("Select a madhhab explicitly (no default)", "اختر المذهب صراحة (لا يوجد افتراضي)"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("HANAFI", "MALIKI", "SHAFII", "HANBALI").forEach { value -> Button(onClick = { madhhab = value }) { Text(if (madhhab == value) "✓ $value" else value) } }
    }
    DomainField("Currency", "العملة", currency, { currency = it })
    DomainField("Gross assets (minor units)", "إجمالي الأصول (الوحدات الصغرى)", gross, { gross = it })
    DomainField("Other debts (minor units)", "الديون (الوحدات الصغرى)", debts, { debts = it })
    DomainField("Funeral costs (minor units)", "تكاليف التجهيز (الوحدات الصغرى)", funeral, { funeral = it })
    DomainField("Bequest input (minor units; not validated)", "الوصية المدخلة (غير متحقق منها)", bequest, { bequest = it })
    DomainField("Spouse count", "عدد الأزواج", spouse, { spouse = it })
    DomainField("Sons", "الأبناء", sons, { sons = it })
    DomainField("Daughters", "البنات", daughters, { daughters = it })
    ConfirmRow("Father alive", "الأب على قيد الحياة", father) { father = it }
    ConfirmRow("Mother alive", "الأم على قيد الحياة", mother) { mother = it }
    ConfirmRow("Paternal grandfather alive", "الجد لأب على قيد الحياة", paternalGrandfather) { paternalGrandfather = it }
    ConfirmRow("Maternal grandmother alive", "الجدة لأم على قيد الحياة", maternalGrandmother) { maternalGrandmother = it }
    DomainField("Full brothers", "الإخوة الأشقاء", fullBrothers, { fullBrothers = it })
    DomainField("Full sisters", "الأخوات الشقيقات", fullSisters, { fullSisters = it })
    DomainField("Paternal brothers", "الإخوة لأب", paternalBrothers, { paternalBrothers = it })
    DomainField("Paternal sisters", "الأخوات لأب", paternalSisters, { paternalSisters = it })
    DomainField("Maternal siblings (total)", "الإخوة والأخوات لأم (المجموع)", maternalSiblings, { maternalSiblings = it })
    DomainField("Other potential heirs / family circumstances, including categories not listed above", "ورثة محتملون آخرون أو ظروف عائلية، بما في ذلك الفئات غير المذكورة أعلاه", otherHeirs, { otherHeirs = it }, multiline = true)
    ConfirmRow("I have reviewed and described every potentially relevant heir category, including those not listed above", "راجعت ووصفت جميع فئات الورثة المحتملين، بما فيها الفئات غير المذكورة أعلاه", allHeirsEnumerated) { allHeirsEnumerated = it }
    ConfirmRow("I confirm all family and estate facts", "أؤكد جميع بيانات الأسرة والتركة", factsConfirmed) { factsConfirmed = it }
    Button(onClick = {
        runCatching {
            val case = InheritanceCase("inheritance-${UUID.randomUUID()}", jurisdiction,
                madhhab.takeIf { it.isNotBlank() }?.let(Madhhab::valueOf), currency.uppercase(),
                listOf(EstateAmount(EstateAmountKind.GROSS_ASSETS, currency.uppercase(), gross.toLong(), factsConfirmed),
                    EstateAmount(EstateAmountKind.OTHER_DEBT, currency.uppercase(), debts.toLong(), factsConfirmed),
                    EstateAmount(EstateAmountKind.FUNERAL_COST, currency.uppercase(), funeral.toLong(), factsConfirmed),
                    EstateAmount(EstateAmountKind.BEQUEST, currency.uppercase(), bequest.toLong(), factsConfirmed)),
                FamilyFacts(spouse.toInt(), sons.toInt(), daughters.toInt(), father, mother, paternalGrandfather, maternalGrandmother,
                    fullBrothers.toInt(), fullSisters.toInt(), paternalBrothers.toInt(), paternalSisters.toInt(), maternalSiblings.toInt(),
                    otherHeirs, allHeirsEnumerated), factsConfirmed, "")
            val summary = InheritanceCaseEngine.summarize(case)
            result = "Status: ${summary.distributionStatus}\nProvisional arithmetic remainder: ${summary.provisionalRemainderMinor} ${summary.currencyCode} minor units\n${summary.reason}"
            save("CASE_INTAKE_NO_DISTRIBUTION", mapOf("jurisdiction" to jurisdiction, "madhhab" to madhhab, "currency" to currency.uppercase(),
                "grossMinor" to gross, "debtsMinor" to debts, "funeralMinor" to funeral, "bequestMinor" to bequest,
                "spouseCount" to spouse, "sons" to sons, "daughters" to daughters, "fatherAlive" to father.toString(),
                "motherAlive" to mother.toString(), "paternalGrandfatherAlive" to paternalGrandfather.toString(),
                "maternalGrandmotherAlive" to maternalGrandmother.toString(), "fullBrothers" to fullBrothers, "fullSisters" to fullSisters,
                "paternalBrothers" to paternalBrothers, "paternalSisters" to paternalSisters, "maternalSiblings" to maternalSiblings,
                "otherPotentialHeirs" to otherHeirs, "allPotentialHeirsEnumerated" to allHeirsEnumerated.toString(),
                "factsConfirmed" to factsConfirmed.toString(), "status" to summary.distributionStatus.name))
        }.onFailure { result = "Invalid case: ${it.message}" }
    }, enabled = listOf(jurisdiction, currency, gross, debts, funeral, bequest, spouse, sons, daughters).all { it.isNotBlank() }) {
        Text(localized("Save case intake", "احفظ ملف الحالة"))
    }
    if (result.isNotBlank()) Text(result)
    Text(localized("No shares are distributed. A reviewed jurisdiction- and madhhab-specific ruleset, full heir facts and qualified review are required.",
        "لا يتم توزيع الأنصبة. يلزم قواعد مراجعة خاصة بالاختصاص والمذهب، وبيانات الورثة كاملة ومراجعة مختصة."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CommunityPanel(save: (String, ByteArray) -> Unit) {
    var snapshot by remember { mutableStateOf("") }; var maxAgeDays by remember { mutableStateOf("") }
    var importedEntries by remember { mutableStateOf<List<CommunityEntry>>(emptyList()) }
    var result by remember { mutableStateOf("") }
    DomainField("Paste imported TL-COMMUNITY-V1 snapshot", "الصق لقطة بيانات TL-COMMUNITY-V1 المستوردة", snapshot, { snapshot = it }, multiline = true)
    DomainField("Mark stale after this many days (your policy)", "اعتبر اللقطة قديمة بعد هذا العدد من الأيام (حسب إعدادك)", maxAgeDays, { maxAgeDays = it })
    Button(onClick = {
        runCatching {
            val now = Instant.now()
            val parsed = CommunitySnapshotImporter.parse(snapshot, now)
            val freshness = parsed.snapshot.freshness(now, Duration.ofDays(maxAgeDays.toLong()))
            importedEntries = parsed.snapshot.entries.sortedBy { it.startsAt }.take(100)
            result = "Source: ${parsed.snapshot.sourceId}\nVersion: ${parsed.snapshot.sourceVersion}\nCaptured at source: ${parsed.snapshot.capturedAt}\nEntries: ${parsed.snapshot.entries.size}\nFreshness (your ${maxAgeDays}-day policy): $freshness\nWarnings: ${parsed.warnings.joinToString()}"
            val savedStatus = when (freshness) {
                app.timeline.islamic.feature.community.local.SnapshotFreshness.CURRENT_AT_CAPTURE -> "IMPORTED_SNAPSHOT_CURRENT"
                app.timeline.islamic.feature.community.local.SnapshotFreshness.STALE -> "IMPORTED_SNAPSHOT_STALE"
                app.timeline.islamic.feature.community.local.SnapshotFreshness.CLOCK_OR_CAPTURE_TIME_INVALID -> "IMPORTED_SNAPSHOT_TIME_INVALID"
            }
            save(savedStatus, snapshot.toByteArray(Charsets.UTF_8))
        }.onFailure { result = "Snapshot rejected: ${it.message}" }
    }, enabled = snapshot.isNotBlank() && maxAgeDays.isNotBlank()) { Text(localized("Validate and save snapshot", "تحقق واحفظ اللقطة")) }
    if (result.isNotBlank()) Text(result)
    if (importedEntries.isNotEmpty()) {
        Text(localized("Imported entries (first 100, source data; verify before relying on it)",
            "الفعاليات المستوردة (أول ١٠٠، بيانات المصدر؛ تحقق قبل الاعتماد عليها)"), style = MaterialTheme.typography.titleMedium)
        importedEntries.forEach { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text("${event.category} · ${event.startsAt} – ${event.endsAt} · ${event.zoneId}", style = MaterialTheme.typography.bodySmall)
                    Text(event.locationLabel, style = MaterialTheme.typography.bodySmall)
                    Text("${event.sourceName} · v${event.sourceVersion} · ${event.sourceReference}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(localized("No live directory, remote notifications, chat or user presence is provided.",
        "لا يوجد دليل مباشر أو إشعارات عن بعد أو محادثة أو حالة اتصال المستخدمين."), style = MaterialTheme.typography.bodySmall)
}
