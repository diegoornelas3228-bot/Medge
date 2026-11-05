package com.example.medge

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.example.medge.ui.theme.MedgeTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Modelo de datos mapeado para Firestore
data class Wallpaper(
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl") var imageUrl: String = "",
    @get:PropertyName("tag") @set:PropertyName("tag") var tag: String = "",
    @get:PropertyName("isAiGenerated") @set:PropertyName("isAiGenerated") var isAiGenerated: Boolean = false
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
    var wallpaperList by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var hideAiImages by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Control de diálogos
    var selectedWallpaperForDetail by remember { mutableStateOf<Wallpaper?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Cargar wallpapers desde Firestore en tiempo real
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("wallpapers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.toObjects(Wallpaper::class.java)
                    wallpaperList = list
                }
            }
    }

    // Lista única de etiquetas para la barra horizontal
    val availableTags = remember(wallpaperList) {
        val tags = wallpaperList.map { it.tag.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("Todos") + tags
    }

    // Lógica de filtrado combinado (Búsqueda + Etiqueta + IA)
    val filteredList = wallpaperList.filter { wallpaper ->
        val matchesSearch = searchQuery.isEmpty() ||
                wallpaper.title.contains(searchQuery, ignoreCase = true) ||
                wallpaper.tag.contains(searchQuery, ignoreCase = true)

        val matchesTag = selectedTag == null || selectedTag == "Todos" ||
                wallpaper.tag.trim().equals(selectedTag?.trim(), ignoreCase = true)

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
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Barra de Etiquetas y Filtro de IA
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Chip para alternar filtro de IA
                item {
                    FilterChip(
                        selected = hideAiImages,
                        onClick = { hideAiImages = !hideAiImages },
                        label = { Text(if (hideAiImages) "🚫 Sin IA" else "🤖 Todas las imágenes") }
                    )
                }

                // Chips para las categorías/etiquetas
                items(availableTags) { tag ->
                    FilterChip(
                        selected = (tag == selectedTag),
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text(tag) }
                    )
                }
            }

            // 2. Cuadrícula de Wallpapers (Grid de Imágenes sin texto)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList) { wallpaper ->
                    WallpaperItem(
                        wallpaper = wallpaper,
                        onClick = { selectedWallpaperForDetail = wallpaper }
                    )
                }
            }
        }
    }

    // Diálogo de detalle al hacer clic en un wallpaper
    selectedWallpaperForDetail?.let { wallpaper ->
        WallpaperDetailDialog(
            wallpaper = wallpaper,
            onDismiss = { selectedWallpaperForDetail = null }
        )
    }

    // Diálogo para subir un nuevo wallpaper
    if (showAddDialog) {
        AddWallpaperDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, tag, url, isAi ->
                val newWallpaper = hashMapOf(
                    "title" to title,
                    "tag" to tag,
                    "imageUrl" to url,
                    "isAiGenerated" to isAi
                )
                FirebaseFirestore.getInstance().collection("wallpapers")
                    .add(newWallpaper)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Wallpaper agregado", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                    }
            }
        )
    }
}

// Componente para representar cada tarjeta de Wallpaper en la cuadrícula
@Composable
fun WallpaperItem(
    wallpaper: Wallpaper,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = wallpaper.imageUrl,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Ventana Emergente con Selector de Proporción y Opción HD
@Composable
fun WallpaperDetailDialog(
    wallpaper: Wallpaper,
    onDismiss: () -> Unit
) {
    var scaleMode by remember { mutableStateOf(ContentScale.Crop) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSettingWallpaper by remember { mutableStateOf(false) }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = wallpaper.imageUrl,
                        contentDescription = wallpaper.title,
                        contentScale = scaleMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Selector de proporción
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

                Spacer(modifier = Modifier.height(12.dp))

                // Botón para aplicar como Fondo de Pantalla
                Button(
                    onClick = {
                        isSettingWallpaper = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val loader = ImageLoader(context)
                                val request = ImageRequest.Builder(context)
                                    .data(wallpaper.imageUrl)
                                    .allowHardware(false)
                                    .build()

                                val result = (loader.execute(request) as? SuccessResult)?.drawable
                                val bitmap = (result as? BitmapDrawable)?.bitmap

                                if (bitmap != null) {
                                    val wallpaperManager = WallpaperManager.getInstance(context)
                                    wallpaperManager.setBitmap(bitmap)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "¡Fondo de pantalla aplicado!", Toast.LENGTH_SHORT).show()
                                        isSettingWallpaper = false
                                        onDismiss()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
                                        isSettingWallpaper = false
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    isSettingWallpaper = false
                                }
                            }
                        }
                    },
                    enabled = !isSettingWallpaper,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSettingWallpaper) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Establecer como Fondo")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

// Ventana Emergente para Agregar Nuevo Wallpaper
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