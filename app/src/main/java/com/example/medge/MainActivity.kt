package com.example.medge

import android.app.WallpaperManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.medge.ui.theme.MedgeTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

// Correo único con privilegios de administrador
const val ADMIN_EMAIL = "diego.ornelas3228@alumnos.udg.mx"

data class Wallpaper(
    @DocumentId var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl") var imageUrl: String = "",
    @get:PropertyName("tag") @set:PropertyName("tag") var tag: String = "",
    @get:PropertyName("isAiGenerated") @set:PropertyName("isAiGenerated") var isAiGenerated: Boolean = false
)

fun urlToDocId(url: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }.take(28)
}

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
    var onlyFavorites by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Estado de autenticación, verificación y rol
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    var isEmailVerified by remember { mutableStateOf(auth.currentUser?.isEmailVerified == true) }
    val isAdmin = isEmailVerified && currentUser?.email?.equals(ADMIN_EMAIL, ignoreCase = true) == true

    // Conjunto de URLs marcadas como favoritas por el usuario actual
    var favoriteUrls by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Control de diálogos
    var selectedWallpaperForDetail by remember { mutableStateOf<Wallpaper?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Escuchar cambios de sesión en tiempo real
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            currentUser = user
            isEmailVerified = user?.isEmailVerified == true
            if (user == null) {
                favoriteUrls = emptySet()
                onlyFavorites = false
            }
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Escuchar favoritos del usuario autenticado Y verificado en tiempo real
    DisposableEffect(currentUser?.uid, isEmailVerified) {
        val uid = currentUser?.uid
        if (uid == null || !isEmailVerified) {
            favoriteUrls = emptySet()
            onDispose { }
        } else {
            val registration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("favorites")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val urls = snapshot.documents.mapNotNull { it.getString("imageUrl") }.toSet()
                    favoriteUrls = urls
                }
            onDispose {
                registration.remove()
            }
        }
    }

    // Cargar wallpapers desde Firestore
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

    // Función para agregar o quitar un wallpaper de favoritos
    fun toggleFavorite(wallpaper: Wallpaper) {
        val uid = currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Inicia sesión para guardar favoritos", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isEmailVerified) {
            Toast.makeText(context, "Verifica tu correo electrónico primero", Toast.LENGTH_SHORT).show()
            return
        }

        val docId = urlToDocId(wallpaper.imageUrl)
        val favRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("favorites")
            .document(docId)

        if (favoriteUrls.contains(wallpaper.imageUrl)) {
            favRef.delete()
                .addOnSuccessListener {
                    Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al quitar favorito", Toast.LENGTH_SHORT).show()
                }
        } else {
            val data = hashMapOf(
                "title" to wallpaper.title,
                "imageUrl" to wallpaper.imageUrl,
                "tag" to wallpaper.tag
            )
            favRef.set(data)
                .addOnSuccessListener {
                    Toast.makeText(context, "Agregado a favoritos ❤️", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al guardar favorito", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Función exclusiva del administrador verificado para eliminar un wallpaper
    fun deleteWallpaper(wallpaper: Wallpaper) {
        if (!isAdmin || wallpaper.id.isBlank()) {
            Toast.makeText(context, "Permisos insuficientes o documento inválido", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance().collection("wallpapers")
            .document(wallpaper.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Wallpaper eliminado correctamente", Toast.LENGTH_SHORT).show()
                selectedWallpaperForDetail = null
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al eliminar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    val availableTags = remember(wallpaperList) {
        val tags = wallpaperList.map { it.tag.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("Todos") + tags
    }

    val filteredList = wallpaperList.filter { wallpaper ->
        val matchesSearch = searchQuery.isEmpty() ||
                wallpaper.title.contains(searchQuery, ignoreCase = true) ||
                wallpaper.tag.contains(searchQuery, ignoreCase = true)

        val matchesTag = selectedTag == null || selectedTag == "Todos" ||
                wallpaper.tag.trim().equals(selectedTag?.trim(), ignoreCase = true)

        val matchesAiFilter = if (hideAiImages) !wallpaper.isAiGenerated else true

        val matchesFavFilter = if (onlyFavorites) favoriteUrls.contains(wallpaper.imageUrl) else true

        matchesSearch && matchesTag && matchesAiFilter && matchesFavFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Medge - Wallpapers")
                        if (currentUser != null) {
                            Text(
                                text = when {
                                    !isEmailVerified -> "⚠️ Correo sin verificar"
                                    isAdmin -> "Modo Administrador (Verificado)"
                                    else -> "Usuario: ${currentUser?.email}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isEmailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                actions = {
                    if (currentUser == null) {
                        TextButton(
                            onClick = { showAuthDialog = true }
                        ) {
                            Text(
                                text = "Iniciar sesión",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                val estado = when {
                                    !isEmailVerified -> "Cuenta pendiente de verificación"
                                    isAdmin -> "Sesión activa como Administrador"
                                    else -> "Sesión activa como Usuario verificado"
                                }
                                Toast.makeText(context, estado, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = if (isAdmin) "Admin" else "Perfil",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        TextButton(
                            onClick = {
                                auth.signOut()
                                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "Salir",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentUser != null && !isEmailVerified) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Verifica tu correo (${currentUser?.email}) para desbloquear funciones de cuenta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    currentUser?.reload()?.addOnSuccessListener {
                                        val verified = auth.currentUser?.isEmailVerified == true
                                        isEmailVerified = verified
                                        if (verified) {
                                            Toast.makeText(context, "¡Correo verificado con éxito!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Aún no aparece verificado. Revisa tu correo (o Spam).", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Ya verifiqué", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    currentUser?.sendEmailVerification()
                                        ?.addOnSuccessListener {
                                            Toast.makeText(context, "Correo de verificación reenviado", Toast.LENGTH_SHORT).show()
                                        }
                                        ?.addOnFailureListener { e ->
                                            Toast.makeText(context, "Espera un momento: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Reenviar correo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentUser != null && isEmailVerified) {
                    item {
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { onlyFavorites = !onlyFavorites },
                            label = { Text("❤️ Favoritos (${favoriteUrls.size})") }
                        )
                    }
                }

                item {
                    FilterChip(
                        selected = hideAiImages,
                        onClick = { hideAiImages = !hideAiImages },
                        label = { Text(if (hideAiImages) "🚫 Sin IA" else "🤖 Todas las imágenes") }
                    )
                }

                items(availableTags) { tag ->
                    FilterChip(
                        selected = (tag == selectedTag),
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text(tag) }
                    )
                }
            }

            if (onlyFavorites && filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no tienes wallpapers en favoritos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList) { wallpaper ->
                        WallpaperItem(
                            wallpaper = wallpaper,
                            isFavorite = favoriteUrls.contains(wallpaper.imageUrl),
                            onClick = { selectedWallpaperForDetail = wallpaper }
                        )
                    }
                }
            }
        }
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
            isLoggedIn = currentUser != null,
            isVerified = isEmailVerified,
            isAdmin = isAdmin,
            onToggleFavorite = { toggleFavorite(wallpaper) },
            onDeleteWallpaper = { deleteWallpaper(wallpaper) },
            onDismiss = { selectedWallpaperForDetail = null }
        )
    }

    if (showAddDialog && isAdmin) {
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
                        Toast.makeText(context, "Error: Verifica permisos o enlace HTTPS", Toast.LENGTH_SHORT).show()
                    }
            }
        )
    }
}

@Composable
fun UserAuthDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: (Boolean) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(if (isRegisterMode) "Crear Cuenta de Usuario" else "Iniciar Sesión")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRegisterMode)
                        "Regístrate con un correo real; te enviaremos un enlace de verificación."
                    else
                        "Ingresa con tu cuenta de usuario o administrador.",
                    style = MaterialTheme.typography.bodySmall
                )

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
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isRegisterMode) {
                    TextButton(
                        enabled = !isLoading,
                        onClick = {
                            focusManager.clearFocus()
                            val cleanEmail = email.trim()
                            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                Toast.makeText(
                                    context,
                                    "Escribe tu correo arriba primero para enviarte el enlace",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@TextButton
                            }

                            isLoading = true
                            FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
                                .addOnSuccessListener {
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        "Si el correo está registrado, recibirás un enlace para cambiar tu contraseña (revisa Spam).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        "Error al enviar: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                TextButton(
                    enabled = !isLoading,
                    onClick = { isRegisterMode = !isRegisterMode },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión"
                        else "¿No tienes cuenta? Regístrate aquí"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    focusManager.clearFocus()
                    val cleanEmail = email.trim()
                    val cleanPass = password.trim()

                    if (cleanEmail.isBlank() || cleanPass.length < 6) {
                        Toast.makeText(
                            context,
                            "Ingresa un correo válido y contraseña de mínimo 6 caracteres",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    isLoading = true
                    val auth = FirebaseAuth.getInstance()

                    if (isRegisterMode) {
                        auth.createUserWithEmailAndPassword(cleanEmail, cleanPass)
                            .addOnSuccessListener { result ->
                                result.user?.sendEmailVerification()
                                isLoading = false
                                onAuthSuccess(true)
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Error al registrar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        auth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                            .addOnSuccessListener {
                                isLoading = false
                                onAuthSuccess(false)
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegisterMode) "Registrarme" else "Ingresar")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading,
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

@Composable
fun WallpaperItem(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
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

            if (isFavorite) {
                Text(
                    text = "❤️",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun WallpaperDetailDialog(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    isLoggedIn: Boolean,
    isVerified: Boolean,
    isAdmin: Boolean,
    onToggleFavorite: () -> Unit,
    onDeleteWallpaper: () -> Unit,
    onDismiss: () -> Unit
) {
    var scaleMode by remember { mutableStateOf(ContentScale.Crop) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSettingWallpaper by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Wallpaper") },
            text = { Text("¿Seguro que deseas eliminar \"${wallpaper.title}\" de la base de datos? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteWallpaper()
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

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
                        .height(330.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = wallpaper.imageUrl,
                        contentDescription = wallpaper.title,
                        contentScale = scaleMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(6.dp))

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

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            !isLoggedIn -> "🤍 Inicia sesión para guardar en Favoritos"
                            !isVerified -> "⚠️ Verifica tu correo para usar Favoritos"
                            isFavorite -> "❤️ Quitar de Favoritos"
                            else -> "🤍 Agregar a Favoritos"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

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

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🗑️ Eliminar Wallpaper")
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
                    onValueChange = { if (it.length <= 60) title = it },
                    label = { Text("Título (máx. 60)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tag,
                    onValueChange = { if (it.length <= 30) tag = it },
                    label = { Text("Etiqueta (ej. Anime, Paisaje)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL de la imagen (https://)") },
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

                    if (cleanUrl.startsWith("https://")) {
                        onAdd(cleanTitle, cleanTag, cleanUrl, isAiGenerated)
                    } else {
                        Toast.makeText(context, "La URL debe iniciar con https://", Toast.LENGTH_SHORT).show()
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