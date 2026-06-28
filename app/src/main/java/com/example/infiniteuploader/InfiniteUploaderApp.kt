package com.example.infiniteuploader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun InfiniteUploaderApp(
    credentialsManager: CredentialsManager,
    s3Service: S3Service
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(padding)
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = viewModel { LoginViewModel(credentialsManager, s3Service) },
                    onLoginSuccess = { navController.navigate("explorer") {
                        popUpTo("login") { inclusive = true }
                    } },
                    onShowSnackbar = { snackbarHostState.showSnackbar(it) }
                )
            }
            composable("explorer") {
                ExplorerScreen(
                    viewModel = viewModel { ExplorerViewModel(s3Service, workManager) },
                    onExit = {
                        navController.navigate("login") {
                            popUpTo("explorer") { inclusive = true }
                        }
                    },
                    onShowSnackbar = { snackbarHostState.showSnackbar(it) },
                    onOpenUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit
) {
    var expandedRegion by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf(false) }
    val regions = listOf(
        "ap-south-1", "ap-northeast-3", "ap-northeast-2", "ap-southeast-1", "ap-southeast-2", "ap-northeast-1",
        "us-east-1", "us-east-2", "us-west-1", "us-west-2",
        "ca-central-1",
        "eu-central-1", "eu-west-1", "eu-west-2", "eu-west-3", "eu-north-1",
        "sa-east-1"
    )

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LoginEvent.Success -> onLoginSuccess()
                is LoginEvent.Error -> onShowSnackbar(event.message)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Infinite Uploader", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        // Profile Selector
        if (viewModel.savedAccessKeys.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { expandedKeys = true }) {
                    Text("Select Saved Profile")
                }
                DropdownMenu(expanded = expandedKeys, onDismissRequest = { expandedKeys = false }) {
                    viewModel.savedAccessKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key) },
                            onClick = {
                                viewModel.onAccessKeySelected(key)
                                expandedKeys = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = viewModel.accessKeyId,
            onValueChange = { viewModel.accessKeyId = it },
            label = { Text("AWS Access Key ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = viewModel.secretAccessKey,
            onValueChange = { viewModel.secretAccessKey = it },
            label = { Text("AWS Secret Access Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = viewModel.bucketName,
            onValueChange = { viewModel.bucketName = it },
            label = { Text("Bucket Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // Region Selector
        Box {
            OutlinedTextField(
                value = viewModel.region,
                onValueChange = {},
                readOnly = true,
                label = { Text("Region") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expandedRegion = true }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
            )
            DropdownMenu(expanded = expandedRegion, onDismissRequest = { expandedRegion = false }) {
                regions.forEach { region ->
                    DropdownMenuItem(
                        text = { Text(region) },
                        onClick = {
                            viewModel.region = region
                            expandedRegion = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = viewModel.saveCredentials, onCheckedChange = { viewModel.saveCredentials = it })
            Text("Save Credentials")
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.connect() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    onExit: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var selectedFile by remember { mutableStateOf<S3Item.File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<S3Item.File?>(null) }
    val breadcrumbScrollState = rememberScrollState()
    val activeUploads by viewModel.activeUploads.observeAsState(emptyList())
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            val fileName = getFileName(context, it)
            viewModel.uploadFile(it, fileName) 
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExplorerEvent.Error -> onShowSnackbar(event.message)
                is ExplorerEvent.Message -> onShowSnackbar(event.message)
                is ExplorerEvent.OpenUrl -> onOpenUrl(event.url)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Infinite Uploader", style = MaterialTheme.typography.titleMedium)
                        Text(
                            viewModel.currentBucket, 
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.disconnect()
                            onExit()
                        }
                    }) {
                        Icon(Icons.Default.Logout, "Exit")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch("*/*") }) {
                Icon(Icons.Default.Upload, "Upload")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Breadcrumbs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(breadcrumbScrollState)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Root",
                    modifier = Modifier.clickable { viewModel.navigateToRoot() },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                viewModel.currentPath.forEachIndexed { index, segment ->
                    Text(" > ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        segment,
                        modifier = Modifier.clickable { viewModel.navigateBack(index) },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.refreshItems() },
                modifier = Modifier.weight(1f)
            ) {
                if (viewModel.isLoading && !viewModel.isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.items) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (item is S3Item.Folder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (item is S3Item.Folder) {
                                        viewModel.navigateTo(item)
                                    } else if (item is S3Item.File) {
                                        selectedFile = item
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Upload Progress Section
            val ongoingUploads = activeUploads.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            AnimatedVisibility(visible = ongoingUploads.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Text("Uploading Files", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    ongoingUploads.forEach { workInfo ->
                        val progress = workInfo.progress.getInt("progress", 0)
                        val fileName = workInfo.progress.getString("fileName") ?: "File"
                        
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("$progress%", style = MaterialTheme.typography.bodySmall)
                            }
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet for File Actions
    if (selectedFile != null) {
        ModalBottomSheet(onDismissRequest = { selectedFile = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(selectedFile!!.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            viewModel.viewFile(selectedFile!!)
                            selectedFile = null
                        }) { Icon(Icons.Default.Visibility, "View") }
                        Text("View")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            viewModel.downloadFile(selectedFile!!)
                            selectedFile = null
                        }) { Icon(Icons.Default.Download, "Download") }
                        Text("Download")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            showDeleteDialog = selectedFile
                            selectedFile = null
                        }) { Icon(Icons.Default.Delete, "Delete") }
                        Text("Delete")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete ${showDeleteDialog!!.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(showDeleteDialog!!)
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file_${System.currentTimeMillis()}"
}
