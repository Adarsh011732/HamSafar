package com.thechameleons.chameleonnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.location.Geocoder
import androidx.compose.ui.platform.LocalContext
import com.thechameleons.chameleonnav.model.Destination
import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPickerSheet(
    currentPosition: GeoPoint,
    destinations: List<Destination>,
    onSelectDestination: (Destination) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCoordInput by remember { mutableStateOf(false) }

    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var coordError by remember { mutableStateOf<String?>(null) }

    var geocodedResults by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isGeocoding by remember { mutableStateOf(false) }

    var cachedRecents by remember { mutableStateOf<List<Destination>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = loadCachedDestinations(context)
            withContext(Dispatchers.Main) {
                cachedRecents = loaded
            }
        }
    }

    // Real-time background geocoding fallback: Android Geocoder + OpenStreetMap Photon API
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 3) {
            isGeocoding = true
            delay(300) // debounce
            withContext(Dispatchers.IO) {
                val results = mutableListOf<Destination>()
                try {
                    // 1. Android Native Geocoder
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale("en", "IN"))
                        val addresses = try {
                            geocoder.getFromLocationName(q, 5)
                        } catch (_: Exception) { null }
                        addresses?.forEach { addr ->
                            if (addr.hasLatitude() && addr.hasLongitude()) {
                                val name = addr.featureName ?: addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: q
                                val line = addr.getAddressLine(0) ?: "$name, ${addr.locality ?: ""}"
                                results.add(
                                    Destination(
                                        id = "geo_${addr.latitude}_${addr.longitude}",
                                        name = name,
                                        location = GeoPoint(addr.latitude, addr.longitude),
                                        category = "Online Result",
                                        description = line,
                                        keywords = listOf(q.lowercase())
                                    )
                                )
                            }
                        }
                    }

                    // 2. OpenStreetMap Photon Geocoder Fallback (Covers all Indian streets, villages, colonies)
                    if (results.size < 3) {
                        try {
                            val url = java.net.URL("https://photon.komoot.io/api/?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&limit=6")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            conn.setRequestProperty("User-Agent", "HamSafar/1.0")
                            if (conn.responseCode == 200) {
                                val text = conn.inputStream.bufferedReader().readText()
                                val root = org.json.JSONObject(text)
                                val feats = root.optJSONArray("features")
                                if (feats != null) {
                                    for (i in 0 until feats.length()) {
                                        val f = feats.getJSONObject(i)
                                        val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
                                        val props = f.getJSONObject("properties")
                                        val name = props.optString("name", q)
                                        val city = props.optString("city", props.optString("state", "India"))
                                        val street = props.optString("street", "")
                                        val desc = if (street.isNotBlank()) "$street, $city" else city
                                        val lon = coords.getDouble(0)
                                        val lat = coords.getDouble(1)
                                        if (results.none { it.location.distanceMeters(GeoPoint(lat, lon)) < 200.0 }) {
                                            results.add(
                                                Destination(
                                                    id = "photon_${lat}_${lon}",
                                                    name = name,
                                                    location = GeoPoint(lat, lon),
                                                    category = "Online Result",
                                                    description = desc,
                                                    keywords = listOf(q.lowercase())
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        geocodedResults = results
                        isGeocoding = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { isGeocoding = false }
                }
            }
        } else {
            geocodedResults = emptyList()
            isGeocoding = false
        }
    }

    val categories = remember(destinations) {
        listOf("All") + destinations.map { it.category }.distinct()
    }

    val filteredDestinations = remember(searchQuery, selectedCategory, destinations) {
        val query = searchQuery.trim().lowercase()
        destinations.filter { dest ->
            if (query.isBlank()) {
                selectedCategory == "All" || dest.category == selectedCategory
            } else {
                // When searching, match across all categories so user never misses a local spot
                val nameLower = dest.name.lowercase()
                val descLower = dest.description.lowercase()
                val kwMatch = dest.keywords.any { it.lowercase().contains(query) || query.contains(it.lowercase()) }
                val queryWords = query.split("\\s+".toRegex()).filter { it.isNotBlank() }

                val matchesWords = queryWords.all { word ->
                    val cleanWord = word.replace("saheed", "shaheed")
                    nameLower.contains(word) || nameLower.contains(cleanWord) ||
                    descLower.contains(word) ||
                    dest.keywords.any { it.lowercase().contains(word) || it.lowercase().contains(cleanWord) }
                }

                nameLower.contains(query) || descLower.contains(query) || kwMatch || matchesWords
            }
        }
    }

    val combinedDestinations = remember(filteredDestinations, geocodedResults, cachedRecents, searchQuery) {
        val combined = mutableListOf<Destination>()
        if (searchQuery.isBlank() && cachedRecents.isNotEmpty() && selectedCategory in listOf("All", "Recent & Saved")) {
            combined.addAll(cachedRecents)
        }
        for (f in filteredDestinations) {
            if (combined.none { it.name.equals(f.name, ignoreCase = true) }) {
                combined.add(f)
            }
        }
        for (geo in geocodedResults) {
            if (combined.none { it.location.distanceMeters(geo.location) < 300.0 }) {
                combined.add(geo)
            }
        }
        combined
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextMuted.copy(alpha = 0.4f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .fillMaxHeight(0.85f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Choose Destination",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Offline Navigation • No Internet Required",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceLight)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }


            // Search Bar + Custom Coordinate Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search offline landmarks...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight,
                        focusedBorderColor = CardBorder,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Coordinate input toggle button
                IconButton(
                    onClick = { showCoordInput = !showCoordInput },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (showCoordInput) CardHero else SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = "Coordinates",
                        tint = if (showCoordInput) TextOnDark else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Custom Coordinate Input Section (collapsible)
            if (showCoordInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter Custom GPS Coordinates",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { latInput = it; coordError = null },
                            label = { Text("Latitude (e.g. 28.6139)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lonInput,
                            onValueChange = { lonInput = it; coordError = null },
                            label = { Text("Longitude (e.g. 77.2090)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    if (coordError != null) {
                        Text(
                            text = coordError ?: "",
                            color = PillRedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val lat = latInput.toDoubleOrNull()
                            val lon = lonInput.toDoubleOrNull()
                            if (lat == null || lon == null || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                                coordError = "Please enter valid coordinates (-90..90, -180..180)"
                            } else {
                                val customDest = Destination(
                                    id = "custom_${System.currentTimeMillis()}",
                                    name = "Custom Pin (${String.format(Locale.US, "%.4f, %.4f", lat, lon)})",
                                    location = GeoPoint(lat, lon),
                                    category = "Custom Pin",
                                    description = "User entered GPS coordinates"
                                )
                                onSelectDestination(customDest)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardHero)
                    ) {
                        Text("Navigate to Coordinates", color = TextOnDark, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Categories Filter Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CardHero else SurfaceLight)
                            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) TextOnDark else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (isGeocoding) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Searching online address database...",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Destinations List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (combinedDestinations.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceLight)
                                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No Locations Found",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Try searching for another city, railway station, or landmark, or enter exact GPS coordinates using the pin button.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(combinedDestinations) { dest ->
                    val hasPos = currentPosition.latitude != 0.0 && currentPosition.longitude != 0.0
                    val distMeters = if (hasPos) currentPosition.distanceMeters(dest.location) else 0.0
                    val distText = if (!hasPos) "" else if (distMeters >= 1000) {
                        String.format(Locale.US, "%.1f km", distMeters / 1000.0)
                    } else {
                        String.format(Locale.US, "%.0f m", distMeters)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                saveDestinationToCache(context, dest)
                                onSelectDestination(dest)
                                onDismiss()
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(CardHero.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (dest.category) {
                                            "Government" -> Icons.Default.AccountBalance
                                            "Medical" -> Icons.Default.LocalHospital
                                            "Transit" -> Icons.Default.Flight
                                            "Heritage" -> Icons.Default.Castle
                                            "Convention" -> Icons.Default.Business
                                            "Park & Recreation" -> Icons.Default.Park
                                            else -> Icons.Default.Place
                                        },
                                        contentDescription = null,
                                        tint = AccentDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dest.name,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (dest.description.isNotBlank()) {
                                        Text(
                                            text = dest.description,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (distText.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = distText,
                                        color = AccentDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "offline",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}
}
}

private fun loadCachedDestinations(context: Context): List<Destination> {
    return try {
        val prefs = context.getSharedPreferences("hamsafar_loc_cache", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("saved_places", "[]") ?: "[]"
        val arr = org.json.JSONArray(jsonStr)
        val list = mutableListOf<Destination>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Destination(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    location = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")),
                    category = obj.optString("category", "Recent & Saved"),
                    description = obj.optString("desc", ""),
                    keywords = listOf(obj.getString("name").lowercase())
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveDestinationToCache(context: Context, dest: Destination) {
    try {
        val prefs = context.getSharedPreferences("hamsafar_loc_cache", Context.MODE_PRIVATE)
        val current = loadCachedDestinations(context).toMutableList()
        current.removeAll { it.name.equals(dest.name, ignoreCase = true) || it.location.distanceMeters(dest.location) < 150.0 }
        current.add(0, dest)
        val keep = current.take(30)
        val arr = org.json.JSONArray()
        for (d in keep) {
            val obj = org.json.JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("lat", d.location.latitude)
                put("lon", d.location.longitude)
                put("category", "Recent & Saved")
                put("desc", d.description)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_places", arr.toString()).apply()
    } catch (_: Exception) {}
}



