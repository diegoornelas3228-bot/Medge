package com.example.medge

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.medge.ui.theme.MedgeTheme
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

data class Wallpaper(
    val title: String = "",
    val imageUrl: String = "",
    val tag: String = "",
    val isAiGenerated: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MedgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WallpaperHomeScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperHomeScreen() {
    val wallpaperList = remember { mutableStateListOf<Wallpaper>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("Todos") }
    var hideAiImages by remember { mutableStateOf(false) }

    // Estados para los Diálogos
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedWallpaper by remember { mutableStateOf<Wallpaper?>(null) } // Guarda la imagen seleccionada para ver en grande

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadWallpapers() {
        val db = Firebase.firestore
        db.collection("wallpapers")
            .get()
            .addOnSuccessListener { snapshot ->
                wallpaperList.clear()
                for (doc in snapshot.documents) {
                    val wallpaper = doc.toObject(Wallpaper::class.java)
                    if (wallpaper != null && wallpaper.imageUrl.isNotBlank()) {
                        wallpaperList.add(wallpaper)
                    }
                }
                isRefreshing = false
            }
            .addOnFailureListener {
                isRefreshing = false
            }
    }

    LaunchedEffect(Unit) {
        loadWallpapers()
    }

    val availableTags = remember(wallpaperList.size) {
        val tags = wallpaperList.map { it.tag.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("Todos") + tags
    }

    val filteredList = wallpaperList.filter { wallpaper ->
        val matchesSearch = wallpaper.title.contains(searchQuery, ignoreCase = true) ||
                wallpaper.tag.contains(searchQuery, ignoreCase = true)
        val matchesTag = if (selectedTag == "Todos") true else wallpaper.tag.equals(selectedTag, ignoreCase = true)
        val matchesAiFilter = if (hideAiImages) !wallpaper.isAiGenerated else true

        matchesSearch && matchesTag && matchesAiFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medge - Wallpapers") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    loadWallpapers()
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. Buscador
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por título o etiqueta...") },
                    leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 8.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✖", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // 2. Barra de Etiquetas
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = hideAiImages,
                            onClick = { hideAiImages = !hideAiImages },
                            label = { Text(if (hideAiImages) "🚫 Sin IA" else "🤖 Mostrar IA") }
                        )
                    }

                    items(availableTags) { tag ->
                        FilterChip(
                            selected = (tag == selectedTag),
                            onClick = { selectedTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                // 3. Grid de Wallpapers
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList) { wallpaper ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.clickable {
                                selectedWallpaper = wallpaper // Al hacer clic se selecciona la imagen
                            }
                        ) {
                            Box(modifier = Modifier.height(260.dp)) {
                                AsyncImage(
                                    model = wallpaper.imageUrl,
                                    contentDescription = wallpaper.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (wallpaper.tag.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = wallpaper.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (wallpaper.isAiGenerated) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(bottomStart = 8.dp),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = "IA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo para Subir Imagen
        if (showAddDialog) {
            AddWallpaperDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, tag, url, isAi ->
                    val db = Firebase.firestore
                    val newWallpaper = hashMapOf(
                        "title" to title.trim(),
                        "tag" to tag.trim(),
                        "imageUrl" to url.trim(),
                        "isAiGenerated" to isAi
                    )
                    db.collection("wallpapers").add(newWallpaper).addOnSuccessListener {
                        loadWallpapers()
                    }
                    showAddDialog = false
                }
            )
        }

        // Diálogo de Pantalla Completa (Vista Previa al Hacer Clic)
        selectedWallpaper?.let { wallpaper ->
            WallpaperDetailDialog(
                wallpaper = wallpaper,
                onDismiss = { selectedWallpaper = null }
            )
        }
    }
}

// Ventana Emergente con Selector de Proporción / Ajuste de Pantalla
@Composable
fun WallpaperDetailDialog(
    wallpaper: Wallpaper,
    onDismiss: () -> Unit
) {
    // Estado para controlar cómo se escala la imagen
    var scaleMode by remember { mutableStateOf(ContentScale.Crop) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Contenedor que simula la pantalla del teléfono
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = wallpaper.imageUrl,
                        contentDescription = wallpaper.title,
                        contentScale = scaleMode, // Escala dinámica elegida por el usuario
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Selector de modo de proporción / ajuste
                Text(
                    text = "Ajuste de pantalla:",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = (scaleMode == ContentScale.Crop),
                        onClick = { scaleMode = ContentScale.Crop },
                        label = { Text("Recortar", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = (scaleMode == ContentScale.Fit),
                        onClick = { scaleMode = ContentScale.Fit },
                        label = { Text("Completa", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = (scaleMode == ContentScale.FillBounds),
                        onClick = { scaleMode = ContentScale.FillBounds },
                        label = { Text("Estirar", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
fun AddWallpaperDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var isAiGenerated by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Wallpaper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Etiqueta (ej. Anime, Paisaje)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL de la imagen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isAiGenerated,
                        onCheckedChange = { isAiGenerated = it }
                    )
                    Text(
                        text = "Imagen generada por IA",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()

                    val cleanTitle = title.trim()
                    val cleanTag = tag.trim()
                    val cleanUrl = urlText.trim()

                    if (cleanTitle.isBlank()) {
                        Toast.makeText(context, "Ingresa un título", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                        onAdd(cleanTitle, cleanTag, cleanUrl, isAiGenerated)
                    } else {
                        Toast.makeText(context, "Ingresa una URL válida (http/https)", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onDismiss()
                }
            ) {
                Text("Cancelar")
            }
        }
    )
}