package com.qkqwork.simplecrypt

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// KernelSU / OxygenOS Style Colors
val KsuRed = Color(0xFFD32F2F)
val KsuBackground = Color(0xFFF7F7F7)
val KsuSurface = Color(0xFFFFFFFF)
val KsuPrimary = Color(0xFF000000)
val KsuTextSecondary = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager(this)
        setContent {
            KsuTheme {
                val viewModel: MainViewModel = viewModel { MainViewModel(prefs) }
                SimpleCryptApp(viewModel, prefs)
            }
        }
    }
}

@Composable
fun KsuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = KsuRed,
            background = KsuBackground,
            surface = KsuSurface
        ),
        shapes = Shapes(
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    object Messages : Screen("messages", R.string.nav_messages, Icons.Default.Email)
    object Friends : Screen("friends", R.string.nav_friends, Icons.Default.Person)
    object Accounts : Screen("accounts", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun SimpleCryptApp(viewModel: MainViewModel, prefs: PreferenceManager) {
    val navController = rememberNavController()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val items = listOf(Screen.Messages, Screen.Friends, Screen.Accounts)

    Box(Modifier.fillMaxSize().background(KsuBackground)) {
        Scaffold(
            containerColor = KsuBackground,
            bottomBar = {
                NavigationBar(containerColor = KsuSurface, tonalElevation = 8.dp) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, null) },
                            label = { Text(stringResource(screen.labelRes), fontSize = 10.sp) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KsuRed,
                                selectedTextColor = KsuRed,
                                unselectedIconColor = KsuTextSecondary,
                                unselectedTextColor = KsuTextSecondary,
                                indicatorColor = KsuRed.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = Screen.Accounts.route, modifier = Modifier.padding(innerPadding)) {
                composable(Screen.Messages.route) { MessageScreen(viewModel) }
                composable(Screen.Friends.route) { FriendsScreen(viewModel, prefs) }
                composable(Screen.Accounts.route) { AccountsScreen(viewModel, prefs) }
            }
        }

        if (isProcessing) {
            Dialog(onDismissRequest = { }) {
                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = KsuSurface,
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = KsuRed, strokeWidth = 3.dp)
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.processing), fontWeight = FontWeight.Bold, color = KsuPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun KsuSection(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
        color = KsuTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun KsuItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    statusIcon: ImageVector? = null,
    statusTint: Color = KsuTextSecondary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = KsuSurface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = KsuPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = KsuPrimary)
                if (summary != null) Text(summary, color = KsuTextSecondary, fontSize = 13.sp)
            }
            if (statusIcon != null) {
                Icon(statusIcon, null, tint = statusTint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun MessageScreen(viewModel: MainViewModel) {
    val account = viewModel.activeAccount
    val context = LocalContext.current
    if (account == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未选择身份", color = KsuTextSecondary)
        }
        return
    }

    var friendName by remember { mutableStateOf("") }
    var plainText by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        KsuTopTitle(stringResource(R.string.nav_messages), account.userId)

        KsuSection(stringResource(R.string.encrypt_title))
        Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = KsuSurface) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = friendName, onValueChange = { friendName = it }, label = { Text(stringResource(R.string.friend_name_label)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = plainText, onValueChange = { plainText = it }, label = { Text(stringResource(R.string.message_content_label)) }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.encryptMessage(friendName, plainText) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KsuRed)
                ) { Text(stringResource(R.string.encrypt_btn), color = Color.White) }
                
                if (viewModel.lastEncryptedResult.isNotEmpty()) {
                    Text(viewModel.lastEncryptedResult, fontSize = 10.sp, color = KsuTextSecondary, modifier = Modifier.padding(top = 8.dp), maxLines = 4)
                }
            }
        }

        KsuSection(stringResource(R.string.decrypt_title))
        Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = KsuSurface) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = jsonInput, onValueChange = { jsonInput = it }, label = { Text(stringResource(R.string.json_input_label)) }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.decryptMessage(jsonInput) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KsuPrimary)
                ) { Text(stringResource(R.string.decrypt_btn), color = Color.White) }
                
                if (viewModel.lastDecryptedResult.isNotEmpty()) {
                    val isError = viewModel.lastDecryptedResult.contains("Error")
                    Text(viewModel.lastDecryptedResult, fontWeight = FontWeight.Bold, color = if (isError) KsuRed else Color(0xFF2E7D32), modifier = Modifier.padding(top = 12.dp))
                    Text(viewModel.lastDecryptionInfo, fontSize = 12.sp, color = KsuTextSecondary)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun FriendsScreen(viewModel: MainViewModel, prefs: PreferenceManager) {
    var input by remember { mutableStateOf("") }
    var friendsList by remember { mutableStateOf(prefs.getAllFriends()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        KsuTopTitle(stringResource(R.string.nav_friends))

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                KsuSection("添加")
                Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = KsuSurface) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(stringResource(R.string.add_friend_hint)) }, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val p = MessageHelper.parsePublicKeyMessage(input)
                            if (p != null) {
                                scope.launch(Dispatchers.IO) {
                                    prefs.saveFriendKey(p.first, p.second, p.third)
                                    friendsList = prefs.getAllFriends()
                                }
                                input = ""
                            }
                        }) { Icon(Icons.Default.Add, null, tint = KsuRed) }
                    }
                }
            }

            item { KsuSection("列表") }

            items(friendsList) { fid ->
                var expanded by remember { mutableStateOf(false) }
                KsuItem(
                    title = fid,
                    summary = "版本: V${prefs.getFriendVersion(fid)}",
                    icon = Icons.Default.Person,
                    statusIcon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    onClick = { expanded = !expanded }
                )
                AnimatedVisibility(expanded) {
                    Surface(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.5f)) {
                        Column(Modifier.padding(16.dp)) {
                            val key = prefs.getFriendKey(fid) ?: ""
                            Text(key, fontSize = 10.sp, color = KsuTextSecondary, maxLines = 2)
                            Row(Modifier.padding(top = 8.dp)) {
                                Button(onClick = { 
                                    val m = MessageHelper.createPublicKeyMessage(fid, prefs.getFriendVersion(fid), key)
                                    clipboard.setText(AnnotatedString(m)) 
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy_json), fontSize = 12.sp) }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { 
                                    viewModel.deleteFriend(fid)
                                    friendsList = prefs.getAllFriends()
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun AccountsScreen(viewModel: MainViewModel, prefs: PreferenceManager) {
    var newId by remember { mutableStateOf("") }
    var useTee by remember { mutableStateOf(true) }
    var showImport by remember { mutableStateOf(false) }
    var importTxt by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        KsuTopTitle(stringResource(R.string.nav_settings))

        KsuSection(stringResource(R.string.new_identity_title))
        Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = KsuSurface) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = newId, onValueChange = { newId = it }, label = { Text(stringResource(R.string.identity_name_hint)) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useTee = !useTee }) {
                    Checkbox(checked = useTee, onCheckedChange = { useTee = it }, colors = CheckboxDefaults.colors(checkedColor = KsuRed))
                    Text(stringResource(R.string.use_tee_label), fontSize = 14.sp)
                }
                Button(
                    onClick = { if (newId.isNotBlank()) { viewModel.addAccount(newId, useTee); newId = "" } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(stringResource(R.string.create_btn)) }
            }
        }

        KsuSection("维护")
        KsuItem(title = "导入软件证书", summary = "恢复已备份的软件模式证书", icon = Icons.Default.Add, onClick = { showImport = true })
        KsuItem(title = stringResource(R.string.export_data_btn), summary = "备份所有配置到剪贴板", icon = Icons.Default.Share, onClick = {
            val d = prefs.exportAllDataJson()
            clipboard.setText(AnnotatedString(d))
            Toast.makeText(context, "备份已复制", Toast.LENGTH_SHORT).show()
        })

        KsuSection(stringResource(R.string.identities_section))
        viewModel.accounts.forEach { acc ->
            val isActive = viewModel.activeUserId == acc.userId
            var showHist by remember { mutableStateOf(false) }
            
            KsuItem(
                title = acc.userId,
                summary = "V${acc.currentVersion} | ${if (acc.useTee) "硬件 TEE" else "软件模式"}",
                icon = if (acc.isHardwareVerified) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                statusIcon = if (isActive) Icons.Default.Check else null,
                statusTint = Color(0xFF2E7D32),
                onClick = { viewModel.switchAccount(acc.userId) }
            )
            
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = { viewModel.generateNewKey(acc) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.new_key_btn), fontSize = 11.sp) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showHist = !showHist }, modifier = Modifier.weight(1f)) { Text(if (showHist) "关闭" else "历史", fontSize = 11.sp) }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.deleteAccount(acc.userId) }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray) }
            }

            AnimatedVisibility(showHist) {
                Column(Modifier.padding(start = 24.dp, end = 16.dp, bottom = 12.dp)) {
                    (acc.currentVersion downTo 1).forEach { v ->
                        KsuItem(title = "版本 V$v", summary = "点击复制该版本公钥", onClick = {
                            scope.launch {
                                val alias = "user_${acc.userId}_v$v"
                                val pk = withContext(Dispatchers.Default) { CryptoManager.getPublicKeyFromAny(alias, prefs) }
                                if (pk != null) {
                                    val m = MessageHelper.createPublicKeyMessage(acc.userId, v, CryptoManager.publicKeyToString(pk))
                                    clipboard.setText(AnnotatedString(m))
                                    Toast.makeText(context, "已复制 V$v 公钥", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入证书") },
            text = { OutlinedTextField(value = importTxt, onValueChange = { importTxt = it }, label = { Text("粘贴证书字符串") }) },
            confirmButton = { Button(onClick = { viewModel.importAccount(importTxt) { showImport = false; importTxt = "" } }) { Text("导入") } }
        )
    }
}

@Composable
fun KsuTopTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 56.dp, bottom = 16.dp)) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Black, color = KsuPrimary)
        if (subtitle != null) {
            Text(subtitle, fontSize = 16.sp, color = KsuRed, fontWeight = FontWeight.Bold)
        }
    }
}
