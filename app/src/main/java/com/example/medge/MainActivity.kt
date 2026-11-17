package com.example.medge

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// Paleta de colores Medge (Verde Bosque + Huevo de pato + Aguamarina + Blanco)
val MedgeDarkBg = Color(0xFF162418)
val MedgeSurface = Color(0xFF233827)
val MedgePurple = Color(0xFF82B185)
val MedgeCyan = Color(0xFFC2E8C4)
val MedgeTextPrimary = Color(0xFFFFFFFF)
val MedgeTextSecondary = Color(0xFFE8F5E9)

const val ADMIN_EMAIL = "diego.ornelas3228@alumnos.udg.mx"

data class Wallpaper(
    val id: String = "",
    val title: String = "",
    val tag: String = "",
    val imageUrl: String = "",
    val isAiGenerated: Boolean = false
)

// Copia la foto elegida de la galería al almacenamiento interno de la app para que sea permanente
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "wallpaper_${UUID.randomUUID()}.jpg"
        val file = File(context.filesDir, fileName)
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Guarda la lista de wallpapers locales en SharedPreferences
fun saveLocalWallpapers(context: Context, list: List<Wallpaper>) {
    val prefs = context.getSharedPreferences("medge_local_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    list.forEach { wp ->
        val obj = JSONObject().apply {
            put("id", wp.id)
            put("title", wp.title)
            put("tag", wp.tag)
            put("imageUrl", wp.imageUrl)
            put("isAiGenerated", wp.isAiGenerated)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("local_wallpapers", jsonArray.toString()).apply()
}

// Carga los wallpapers locales guardados previamente
fun loadLocalWallpapers(context: Context): List<Wallpaper> {
    val prefs = context.getSharedPreferences("medge_local_prefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("local_wallpapers", null) ?: return emptyList()
    val result = mutableListOf<Wallpaper>()
    try {
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            result.add(
                Wallpaper(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    tag = obj.optString("tag", ""),
                    imageUrl = obj.optString("imageUrl", ""),
                    isAiGenerated = obj.optBoolean("isAiGenerated", false)
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

// Normaliza una etiqueta (quita espacios extra y pone la primera letra en mayúscula)
fun normalizeTag(rawTag: String): String {
    val trimmed = rawTag.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isBlank()) return ""
    return trimmed.lowercase().replaceFirstChar { it.uppercase() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = MedgePurple,
                secondary = MedgeCyan,
                background = MedgeDarkBg,
                surface = MedgeSurface,
                onPrimary = Color.White,
                onBackground = MedgeTextPrimary,
                onSurface = MedgeTextPrimary
            )
            MaterialTheme(colorScheme = darkColors) {
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
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isEmailVerified by remember { mutableStateOf(auth.currentUser?.isEmailVerified == true) }
    val isAdmin = currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true) && isEmailVerified
    val canAddWallpaper = currentUser != null && isEmailVerified

    var wallpaperList by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }
    val localWallpapers = remember {
        mutableStateListOf<Wallpaper>().apply {
            addAll(loadLocalWallpapers(context))
        }
    }
    var favoriteUrls by remember { mutableStateOf<Set<String>>(emptySet()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("Todos") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var selectedWallpaperForDetail by remember { mutableStateOf<Wallpaper?>(null) }
    var fullScreenWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var wallpaperPendingDelete by remember { mutableStateOf<Wallpaper?>(null) }

    // Escuchar cambios de sesión en Firebase Auth
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            isEmailVerified = firebaseAuth.currentUser?.isEmailVerified == true
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Escuchar wallpapers públicos desde Firestore en tiempo real
    DisposableEffect(Unit) {
        val registration = db.collection("wallpapers")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                wallpaperList = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val imageUrl = doc.getString("imageUrl") ?: return@mapNotNull null
                    Wallpaper(
                        id = doc.id,
                        title = title,
                        tag = doc.getString("tag") ?: "",
                        imageUrl = imageUrl,
                        isAiGenerated = doc.getBoolean("isAiGenerated") ?: false
                    )
                }
            }
        onDispose { registration.remove() }
    }

    // Escuchar favoritos del usuario autenticado y verificado
    DisposableEffect(currentUser?.uid, isEmailVerified) {
        val uid = currentUser?.uid
        if (uid == null || !isEmailVerified) {
            favoriteUrls = emptySet()
            onDispose { }
        } else {
            val reg = db.collection("users").document(uid).collection("favorites")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        favoriteUrls = snapshot.documents.mapNotNull { it.getString("imageUrl") }.toSet()
                    }
                }
            onDispose { reg.remove() }
        }
    }

    val allWallpapers = localWallpapers + wallpaperList
    val existingTags = remember(allWallpapers) {
        allWallpapers
            .map { normalizeTag(it.tag) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
    val availableTags = remember(existingTags) {
        listOf("Todos", "Favoritos", "Con IA", "Sin IA") + existingTags
    }

    // Si la etiqueta seleccionada fue eliminada, regresar a "Todos"
    LaunchedEffect(availableTags) {
        if (!availableTags.contains(selectedTag)) {
            selectedTag = "Todos"
        }
    }

    val filteredList = allWallpapers.filter { wallpaper ->
        val matchesSearch = wallpaper.title.contains(searchQuery, ignoreCase = true) ||
                wallpaper.tag.contains(searchQuery, ignoreCase = true)
        val matchesTag = when (selectedTag) {
            "Todos" -> true
            "Favoritos" -> favoriteUrls.contains(wallpaper.imageUrl)
            "Con IA" -> wallpaper.isAiGenerated
            "Sin IA" -> !wallpaper.isAiGenerated
            else -> normalizeTag(wallpaper.tag).equals(selectedTag, ignoreCase = true)
        }
        matchesSearch && matchesTag
    }

    fun deleteWallpaperConfirmed(wallpaper: Wallpaper) {
        if (wallpaper.imageUrl.startsWith("file://")) {
            localWallpapers.removeAll { it.id == wallpaper.id }
            saveLocalWallpapers(context, localWallpapers)
            Uri.parse(wallpaper.imageUrl).path?.let { File(it).delete() }
            Toast.makeText(context, "Wallpaper local eliminado", Toast.LENGTH_SHORT).show()
        } else {
            db.collection("wallpapers").document(wallpaper.id).delete()
                .addOnSuccessListener {
                    Toast.makeText(context, "Wallpaper eliminado", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error al eliminar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun deleteTagEverywhere(tagToDelete: String) {
        var updatedLocal = false
        for (i in localWallpapers.indices) {
            if (normalizeTag(localWallpapers[i].tag).equals(tagToDelete, ignoreCase = true)) {
                localWallpapers[i] = localWallpapers[i].copy(tag = "")
                updatedLocal = true
            }
        }
        if (updatedLocal) {
            saveLocalWallpapers(context, localWallpapers)
        }

        val cloudMatches = wallpaperList.filter {
            normalizeTag(it.tag).equals(tagToDelete, ignoreCase = true)
        }
        if (cloudMatches.isEmpty()) {
            Toast.makeText(context, "Etiqueta \"$tagToDelete\" eliminada", Toast.LENGTH_SHORT).show()
        } else {
            val batch = db.batch()
            cloudMatches.forEach { wp ->
                val ref = db.collection("wallpapers").document(wp.id)
                batch.update(ref, "tag", "")
            }
            batch.commit()
                .addOnSuccessListener {
                    Toast.makeText(context, "Etiqueta \"$tagToDelete\" eliminada", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error en Firestore: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        val uid = currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Inicia sesión para guardar favoritos", Toast.LENGTH_SHORT).show()
            showAuthDialog = true
            return
        }
        if (!isEmailVerified) {
            Toast.makeText(context, "Verifica tu correo para guardar favoritos", Toast.LENGTH_SHORT).show()
            return
        }
        val docId = wallpaper.imageUrl.hashCode().toString()
        val favRef = db.collection("users").document(uid).collection("favorites").document(docId)
        if (favoriteUrls.contains(wallpaper.imageUrl)) {
            favRef.delete()
        } else {
            favRef.set(
                hashMapOf(
                    "title" to wallpaper.title,
                    "tag" to wallpaper.tag,
                    "imageUrl" to wallpaper.imageUrl,
                    "isAiGenerated" to wallpaper.isAiGenerated
                )
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MedgeDarkBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Medge",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedgeTextPrimary
                        )
                        Text(
                            text = when {
                                isAdmin -> "Modo Administrador (Verificado)"
                                currentUser != null && !isEmailVerified -> "Verifica tu correo"
                                currentUser != null -> currentUser?.email ?: "Usuario"
                                else -> "Explora fondos exclusivos"
                            },
                            fontSize = 12.sp,
                            color = if (isAdmin) MedgeCyan else MedgeTextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = { showManageTagsDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MedgeCyan),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Label,
                                    contentDescription = "Etiquetas",
                                    tint = MedgeCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Etiquetas", color = MedgeCyan, fontSize = 12.sp)
                            }
                        }

                        if (currentUser != null && !isEmailVerified) {
                            OutlinedButton(
                                onClick = {
                                    currentUser?.reload()?.addOnSuccessListener {
                                        isEmailVerified = auth.currentUser?.isEmailVerified == true
                                        if (isEmailVerified) {
                                            Toast.makeText(context, "Correo verificado", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Aún no verificado. Revisa tu bandeja.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MedgeCyan)
                            ) {
                                Text("Ya verifiqué", color = MedgeCyan, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (currentUser == null) {
                                    showAuthDialog = true
                                } else {
                                    auth.signOut()
                                    Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedgeSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (currentUser == null) "Iniciar sesión" else "Salir",
                                color = MedgeTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MedgeTextSecondary
                        )
                    },
                    placeholder = { Text("Buscar fondos o categorías...", color = MedgeTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedgePurple,
                        unfocusedBorderColor = MedgeSurface,
                        focusedContainerColor = MedgeSurface,
                        unfocusedContainerColor = MedgeSurface
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableTags) { tag ->
                        val selected = selectedTag == tag
                        FilterChip(
                            selected = selected,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MedgePurple,
                                selectedLabelColor = Color.White,
                                containerColor = MedgeSurface,
                                labelColor = MedgeTextSecondary
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (canAddWallpaper) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MedgePurple,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar wallpaper",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            if (filteredList.isEmpty()) {
                Text(
                    text = "No hay wallpapers disponibles",
                    color = MedgeTextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { wallpaper ->
                        WallpaperCard(
                            wallpaper = wallpaper,
                            isFavorite = favoriteUrls.contains(wallpaper.imageUrl),
                            isAdmin = isAdmin,
                            onFavoriteClick = { toggleFavorite(wallpaper) },
                            onDeleteClick = { wallpaperPendingDelete = wallpaper },
                            onCardClick = { selectedWallpaperForDetail = wallpaper }
                        )
                    }
                }
            }
        }
    }

    // Confirmación antes de eliminar un wallpaper (solo Admin)
    wallpaperPendingDelete?.let { wallpaper ->
        AlertDialog(
            onDismissRequest = { wallpaperPendingDelete = null },
            containerColor = MedgeSurface,
            title = { Text("Eliminar wallpaper", color = MedgeTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "¿Seguro que deseas eliminar \"${wallpaper.title}\" permanentemente?",
                    color = MedgeTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteWallpaperConfirmed(wallpaper)
                        wallpaperPendingDelete = null
                        if (selectedWallpaperForDetail?.id == wallpaper.id) {
                            selectedWallpaperForDetail = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { wallpaperPendingDelete = null }) {
                    Text("Cancelar", color = MedgeTextSecondary)
                }
            }
        )
    }

    // Diálogo de administración para eliminar etiquetas existentes (solo Admin)
    if (showManageTagsDialog && isAdmin) {
        ManageTagsDialog(
            existingTags = existingTags,
            allWallpapers = allWallpapers,
            onDismiss = { showManageTagsDialog = false },
            onDeleteTag = { tagToDelete -> deleteTagEverywhere(tagToDelete) }
        )
    }

    if (showAuthDialog) {
        UserAuthDialog(
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = { isNewUser ->
                showAuthDialog = false
                isEmailVerified = auth.currentUser?.isEmailVerified == true
                val msg = if (isNewUser) {
                    "Cuenta creada. Revisa tu correo para verificarla."
                } else {
                    "Sesión iniciada"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    selectedWallpaperForDetail?.let { wallpaper ->
        WallpaperDetailDialog(
            wallpaper = wallpaper,
            isFavorite = favoriteUrls.contains(wallpaper.imageUrl),
            isAdmin = isAdmin,
            onDismiss = { selectedWallpaperForDetail = null },
            onToggleFavorite = { toggleFavorite(wallpaper) },
            onDeleteClick = { wallpaperPendingDelete = wallpaper },
            onExpandClick = {
                selectedWallpaperForDetail = null
                fullScreenWallpaper = wallpaper
            }
        )
    }

    fullScreenWallpaper?.let { wallpaper ->
        FullScreenWallpaperDialog(
            wallpaper = wallpaper,
            onDismiss = { fullScreenWallpaper = null }
        )
    }

    // Disponible para Administrador y cualquier Usuario con correo verificado
    if (showAddDialog && canAddWallpaper) {
        AddWallpaperDialog(
            existingTags = existingTags,
            onDismiss = { showAddDialog = false },
            onAdd = { title, tag, url, isAi ->
                val normalizedTag = normalizeTag(tag)
                if (url.startsWith("file://")) {
                    val newLocal = Wallpaper(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        tag = normalizedTag,
                        imageUrl = url,
                        isAiGenerated = isAi
                    )
                    localWallpapers.add(0, newLocal)
                    saveLocalWallpapers(context, localWallpapers)
                    Toast.makeText(context, "Wallpaper guardado en el teléfono", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                } else {
                    val newWallpaper = hashMapOf(
                        "title" to title,
                        "tag" to normalizedTag,
                        "imageUrl" to url,
                        "isAiGenerated" to isAi
                    )
                    db.collection("wallpapers")
                        .add(newWallpaper)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Wallpaper agregado", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error Firestore: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        )
    }
}

@Composable
fun ManageTagsDialog(
    existingTags: List<String>,
    allWallpapers: List<Wallpaper>,
    onDismiss: () -> Unit,
    onDeleteTag: (String) -> Unit
) {
    var tagPendingDelete by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MedgeSurface,
        title = { Text("Administrar Etiquetas", color = MedgeTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (existingTags.isEmpty()) {
                Text("No hay etiquetas creadas todavía.", color = MedgeTextSecondary, fontSize = 14.sp)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(existingTags) { tag ->
                        val count = allWallpapers.count {
                            normalizeTag(it.tag).equals(tag, ignoreCase = true)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MedgeDarkBg, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tag, color = MedgeTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    text = if (count == 1) "1 fondo" else "$count fondos",
                                    color = MedgeCyan,
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(onClick = { tagPendingDelete = tag }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Eliminar etiqueta",
                                    tint = MedgeTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo", color = MedgeCyan)
            }
        }
    )

    tagPendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            containerColor = MedgeSurface,
            title = { Text("Eliminar etiqueta", color = MedgeTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "¿Eliminar la etiqueta \"$tag\"? Los fondos conservarán su imagen, pero quedarán sin esta etiqueta.",
                    color = MedgeTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTag(tag)
                        tagPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Eliminar etiqueta", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) {
                    Text("Cancelar", color = MedgeTextSecondary)
                }
            }
        )
    }
}

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    isAdmin: Boolean,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MedgeSurface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = wallpaper.imageUrl,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 120f
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isAdmin) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) MedgeCyan else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = wallpaper.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (wallpaper.tag.isNotBlank()) {
                    Text(
                        text = wallpaper.tag,
                        color = MedgeCyan,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun UserAuthDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: (isNewUser: Boolean) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MedgeSurface,
        title = {
            Text(
                text = if (isRegisterMode) "Crear cuenta en Medge" else "Iniciar sesión",
                color = MedgeTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = MedgeTextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isRegisterMode) {
                    TextButton(
                        enabled = !isLoading,
                        onClick = {
                            val cleanEmail = email.trim()
                            if (cleanEmail.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Escribe tu correo electrónico arriba para restablecer tu contraseña",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@TextButton
                            }
                            isLoading = true
                            auth.sendPasswordResetEmail(cleanEmail)
                                .addOnSuccessListener {
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        "Enlace de recuperación enviado a $cleanEmail",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        "Error: ${e.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            color = MedgeTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                TextButton(
                    onClick = { isRegisterMode = !isRegisterMode },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
                        color = MedgeCyan,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    val cleanEmail = email.trim()
                    if (cleanEmail.isBlank() || password.length < 6) {
                        Toast.makeText(context, "Ingresa un correo válido y contraseña de al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    if (isRegisterMode) {
                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                            .addOnSuccessListener { result ->
                                result.user?.sendEmailVerification()
                                isLoading = false
                                onAuthSuccess(true)
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        auth.signInWithEmailAndPassword(cleanEmail, password)
                            .addOnSuccessListener {
                                isLoading = false
                                onAuthSuccess(false)
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MedgePurple)
            ) {
                Text(if (isRegisterMode) "Registrarme" else "Entrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MedgeTextSecondary)
            }
        }
    )
}

@Composable
fun WallpaperDetailDialog(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isApplying by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MedgeSurface,
        title = { Text(wallpaper.title, color = MedgeTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = wallpaper.imageUrl,
                    contentDescription = wallpaper.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onExpandClick() }
                )

                if (wallpaper.tag.isNotBlank()) {
                    Text("Categoría: ${wallpaper.tag}", color = MedgeCyan, fontSize = 13.sp)
                }
                if (wallpaper.isAiGenerated) {
                    Text("Generado con IA", color = MedgeTextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onExpandClick,
                    border = BorderStroke(1.dp, MedgeCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Expandir",
                        tint = MedgeCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Expandir", color = MedgeCyan, fontSize = 13.sp)
                }

                Button(
                    enabled = !isApplying,
                    onClick = {
                        isApplying = true
                        scope.launch {
                            val success = applyWallpaperFromUrl(context, wallpaper.imageUrl)
                            isApplying = false
                            if (success) {
                                Toast.makeText(context, "Fondo de pantalla aplicado", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "No se pudo aplicar el fondo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedgePurple),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isApplying) "Aplicando..." else "Establecer fondo",
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = MedgeTextSecondary
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) MedgeCyan else MedgeTextSecondary
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = MedgeTextSecondary)
                }
            }
        }
    )
}

@Composable
fun FullScreenWallpaperDialog(
    wallpaper: Wallpaper,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isApplying by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = wallpaper.imageUrl,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            )

            Surface(
                onClick = onDismiss,
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cerrar",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wallpaper.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (wallpaper.tag.isNotBlank()) {
                        Text(
                            text = wallpaper.tag,
                            color = MedgeCyan,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    enabled = !isApplying,
                    onClick = {
                        isApplying = true
                        scope.launch {
                            val success = applyWallpaperFromUrl(context, wallpaper.imageUrl)
                            isApplying = false
                            if (success) {
                                Toast.makeText(context, "Fondo de pantalla aplicado", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "No se pudo aplicar el fondo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedgePurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isApplying) "Aplicando..." else "Aplicar fondo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

suspend fun applyWallpaperFromUrl(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val result = (loader.execute(request) as? SuccessResult)?.drawable
        val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext false
        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.setBitmap(bitmap)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWallpaperDialog(
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onAdd: (title: String, tag: String, imageUrl: String, isAiGenerated: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isAiGenerated by remember { mutableStateOf(false) }

    val dialogTags = remember(existingTags) {
        mutableStateListOf<String>().apply { addAll(existingTags) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            imageUrl = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MedgeSurface,
        title = { Text("Agregar Wallpaper", color = MedgeTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Etiqueta (elige o escribe)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            val cleanNewTag = normalizeTag(tag)
                            if (cleanNewTag.isBlank()) {
                                Toast.makeText(context, "Escribe el nombre de la etiqueta", Toast.LENGTH_SHORT).show()
                            } else {
                                val existingMatch = dialogTags.firstOrNull {
                                    it.equals(cleanNewTag, ignoreCase = true)
                                }
                                if (existingMatch != null) {
                                    tag = existingMatch
                                    Toast.makeText(context, "La etiqueta \"$existingMatch\" ya existe, se seleccionó", Toast.LENGTH_SHORT).show()
                                } else {
                                    dialogTags.add(cleanNewTag)
                                    tag = cleanNewTag
                                    Toast.makeText(context, "Etiqueta \"$cleanNewTag\" agregada", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        border = BorderStroke(1.dp, MedgeCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text("+ Etiqueta", color = MedgeCyan, fontSize = 12.sp)
                    }
                }

                if (dialogTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(dialogTags) { existingTag ->
                            val isSelected = tag.trim().equals(existingTag, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { tag = existingTag },
                                label = { Text(existingTag, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedgePurple,
                                    selectedLabelColor = Color.White,
                                    containerColor = MedgeDarkBg,
                                    labelColor = MedgeTextSecondary
                                )
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MedgeCyan)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "Galería",
                        tint = MedgeCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedImageUri != null) "Imagen seleccionada de galería" else "Elegir imagen de la galería",
                        color = MedgeCyan
                    )
                }

                selectedImageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Vista previa",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = {
                        imageUrl = it
                        if (it.isNotBlank()) selectedImageUri = null
                    },
                    label = { Text("O pega un enlace HTTPS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isAiGenerated,
                        onCheckedChange = { isAiGenerated = it }
                    )
                    Text("Generado con IA", color = MedgeTextPrimary, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanTag = normalizeTag(tag)
                    val cleanUrl = imageUrl.trim()
                    if (cleanTitle.isBlank()) {
                        Toast.makeText(context, "Escribe un título", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val uri = selectedImageUri
                    if (uri != null) {
                        val savedPath = saveImageToInternalStorage(context, uri)
                        if (savedPath != null) {
                            onAdd(cleanTitle, cleanTag, savedPath, isAiGenerated)
                        } else {
                            Toast.makeText(context, "No se pudo copiar la imagen de galería", Toast.LENGTH_SHORT).show()
                        }
                    } else if (cleanUrl.startsWith("https://")) {
                        onAdd(cleanTitle, cleanTag, cleanUrl, isAiGenerated)
                    } else {
                        Toast.makeText(context, "Elige una imagen de galería o pega un enlace https://", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MedgePurple)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MedgeTextSecondary)
            }
        }
    )
}