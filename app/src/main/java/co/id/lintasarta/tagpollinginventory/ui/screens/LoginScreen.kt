package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.location.CityLocationHelper
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.ConnectionTestResult
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimaryDark
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit = {}
) {
    val currentLocation by viewModel.currentLocation.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val rnimsBaseUrl by viewModel.rnimsBaseUrl.collectAsState()
    val rnimsServerConnected by viewModel.rnimsServerConnected.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    LoginScreenContent(
        currentLocation = currentLocation,
        networkStatus = networkStatus,
        rnimsBaseUrl = rnimsBaseUrl,
        rnimsServerConnected = rnimsServerConnected,
        connectionTestResult = connectionTestResult,
        canGoBack = currentUser.isLoggedIn && currentUser.username.isNotBlank(),
        onBackClick = onBackClick,
        onUpdateBaseUrl = { viewModel.setRnimsBaseUrl(it) },
        onTestConnection = { viewModel.testRnimsConnection() },
        onLogin = { username, password, callback ->
            viewModel.loginRnims(username, password, callback)
        },
        onLoginOffline = { viewModel.loginOffline() }
    )
}

@Composable
fun LoginScreenContent(
    currentLocation: LocationData = LocationData(),
    networkStatus: NetworkStatus = NetworkStatus(isOnline = true),
    rnimsBaseUrl: String = RnimsApiClient.DEFAULT_USB_URL,
    rnimsServerConnected: Boolean? = null,
    connectionTestResult: ConnectionTestResult? = null,
    canGoBack: Boolean = false,
    onBackClick: () -> Unit = {},
    onUpdateBaseUrl: (String) -> Unit = {},
    onTestConnection: () -> Unit = {},
    onLogin: (String, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onLoginOffline: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("surveyor_mpo") }
    var password by remember { mutableStateOf("password123") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeveloperStaging by remember { mutableStateOf(false) }

    // Resolusi nama kota otomatis di background IO agar tidak memblokir UI thread (mencegah ANR)
    var cityName by remember { mutableStateOf("Ambon") }
    LaunchedEffect(currentLocation.latitude, currentLocation.longitude) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolved = CityLocationHelper.resolveCityName(
                context = context,
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude
            )
            cityName = resolved
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // =========================================================================
            // 1. BRAND HEADER SECTION (Biru Telecom Lintasarta dengan Badge Lokasi Kota)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(TelecomPrimaryDark, TelecomPrimary)
                        )
                    )
                    .padding(top = 40.dp, bottom = 28.dp, start = 20.dp, end = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Navigation / Back & City Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canGoBack) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Logo / Brand Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Dynamic City Badge (Sesuai lokasi GPS surveyor: Ambon, Tual, Timika, dll.)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            border = null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = cityName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Title & Tagline
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TAG POLLING INVENTORY",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "RNIMS Mobile Field Operations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }

                    // =========================================================================================
                    // TODO DEVELOPER STAGING ONLY:
                    // Bagian selector server staging (USB ADB 127.0.0.1, Wi-Fi Staging, Production) ini
                    // HANYA untuk kebutuhan development & staging QA di kantor/lab.
                    // Saat rilis production ke lapangan:
                    // 1. Sembunyikan / hapus staging selector ini.
                    // 2. Kunci URL server 100% langsung ke Production (https://rnims.lintasarta.co.id/api).
                    // =========================================================================================
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showDeveloperStaging = !showDeveloperStaging },
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Konfigurasi Server Staging (Dev)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Status ping singkat
                            val pingLabel = when {
                                rnimsServerConnected == true -> "${connectionTestResult?.latencyMs ?: 0}ms"
                                rnimsServerConnected == false -> "Offline"
                                else -> "Cek"
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rnimsServerConnected == true) Color(0xFF2E7D32) else Color(0xFFE65100)
                            ) {
                                Text(
                                    text = pingLabel,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Collapsible Developer Staging Controls
                    AnimatedVisibility(
                        visible = showDeveloperStaging,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "PILIH TARGET LINGKUNGAN (DEV ONLY):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_USB_URL,
                                    onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_USB_URL) },
                                    label = { Text("USB ADB", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_WIFI_URL,
                                    onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_WIFI_URL) },
                                    label = { Text("Wi-Fi Staging", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_PROD_URL,
                                    onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_PROD_URL) },
                                    label = { Text("Prod", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (rnimsBaseUrl.contains("127.0.0.1")) {
                                Text(
                                    text = "Terminal Laptop: adb reverse tcp:8000 tcp:8000",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = rnimsBaseUrl,
                                    onValueChange = { onUpdateBaseUrl(it) },
                                    label = { Text("URL Base RNIMS", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = onTestConnection,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ping", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. MAIN LOGIN FORM CARD
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-16).dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Masuk ke Akun Surveyor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Masukkan kredensial RNIMS Anda untuk autentikasi awal profil & otorisasi survei lapangan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Field Username / Email
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text("Username atau Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TelecomPrimary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Field Password with Toggle
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TelecomPrimary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password",
                                    tint = Color.Gray
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (username.isNotBlank() && password.isNotBlank()) {
                                    isLoggingIn = true
                                    errorMessage = null
                                    onLogin(username, password) { success, msg ->
                                        isLoggingIn = false
                                        if (!success) errorMessage = msg
                                    }
                                }
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error Banner
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB71C1C),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Tombol Utama Login
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Username dan password tidak boleh kosong"
                                return@Button
                            }
                            isLoggingIn = true
                            errorMessage = null
                            onLogin(username, password) { success, msg ->
                                isLoggingIn = false
                                if (!success) {
                                    errorMessage = msg
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoggingIn,
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Menghubungi Server...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Masuk ke Sistem RNIMS",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tombol Masuk Mode Offline
                    OutlinedButton(
                        onClick = onLoginOffline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Masuk Mode Offline",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }

                    // Callout Banner Operasional 100% Offline
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(10.dp),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Operasional Lapangan 100% Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = "Login online diperlukan satu kali saat awal. Setelah berhasil, profil dan 143 rute segmen tersimpan aman di perangkat sehingga survei tiang dapat langsung dikerjakan di lokasi tanpa sinyal internet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF334155),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. CORPORATE FOOTER
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PT Aplikanusa Lintasarta",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Regional Operations • Lintasarta Telecom • v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    TagPollingInventoryTheme {
        LoginScreenContent(
            currentLocation = LocationData(
                latitude = -3.6954,
                longitude = 128.1814
            ),
            networkStatus = NetworkStatus(isOnline = true),
            rnimsBaseUrl = "http://127.0.0.1:8000"
        )
    }
}
