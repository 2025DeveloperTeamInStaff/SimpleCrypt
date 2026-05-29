package com.qkqwork.simplecrypt

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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

val MiuiBlue = Color(0xFF007AFF)
val MiuiBackground = Color(0xFFF4F4F4)
val MiuiCard = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager(this)
        setContent {
            MiuiTheme {
                val viewModel: MainViewModel = viewModel { MainViewModel(prefs) }
                SimpleCryptApp(viewModel, prefs)
            }
        }
    }
}

@Composable
fun MiuiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = MiuiBlue, background = MiuiBackground, surface = MiuiCard),
        shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(32.dp)),
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MiuiCard, tonalElevation = 8.dp) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, null) },
                            label = { Text(stringResource(screen.labelRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MiuiBlue, selectedTextColor = MiuiBlue)
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
                Card(modifier = Modifier.size(220.dp), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(12.dp)) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = MiuiBlue)
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.processing), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.wait_msg), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(viewModel: MainViewModel) {
    val account = viewModel.activeAccount
    val clipboard = LocalClipboardManager.current
    
    if (account == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请在设置中选择或创建身份", color = Color.Gray)
        }
        return
    }

    var friendName by remember { mutableStateOf("") }
    var plainText by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).fillMaxSize()) {
        Text(stringResource(R.string.active_identity, account.userId), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MiuiBlue)
        if (account.useTee) {
            Text(
                text = if (account.isHardwareVerified) stringResource(R.string.hw_secured) else stringResource(R.string.sw_emulated),
                color = if (account.isHardwareVerified) Color(0xFF4CAF50) else Color.Red,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(16.dp))

        MiuiCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.encrypt_title), fontWeight = FontWeight.Bold)
                OutlinedTextField(value = friendName, onValueChange = { friendName = it }, label = { Text(stringResource(R.string.friend_name_label)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = plainText, onValueChange = { plainText = it }, label = { Text(stringResource(R.string.message_content_label)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { viewModel.encryptMessage(friendName, plainText) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.encrypt_btn)) }
                
                if (viewModel.lastEncryptedResult.isNotEmpty()) {
                    LaunchedEffect(viewModel.lastEncryptedResult) {
                        if (viewModel.lastEncryptedResult.startsWith("{")) {
                            clipboard.setText(AnnotatedString(viewModel.lastEncryptedResult))
                        }
                    }
                    Text(viewModel.lastEncryptedResult, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        MiuiCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.decrypt_title), fontWeight = FontWeight.Bold)
                OutlinedTextField(value = jsonInput, onValueChange = { jsonInput = it }, label = { Text(stringResource(R.string.json_input_label)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { viewModel.decryptMessage(jsonInput) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.decrypt_btn)) }
                
                if (viewModel.lastDecryptedResult.isNotEmpty()) {
                    val isError = viewModel.lastDecryptedResult.contains("Error")
                    Text(viewModel.lastDecryptedResult, fontWeight = FontWeight.Bold, color = if (isError) Color.Red else MiuiBlue, modifier = Modifier.padding(top = 8.dp))
                    if (viewModel.lastDecryptionInfo.isNotEmpty()) {
                        Text(viewModel.lastDecryptionInfo, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(viewModel: MainViewModel, prefs: PreferenceManager) {
    var input by remember { mutableStateOf("") }
    var friendsList by remember { mutableStateOf(prefs.getAllFriends()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.padding(20.dp).fillMaxSize()) {
        Text(stringResource(R.string.contacts_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            }) { Icon(Icons.Default.Add, null, tint = MiuiBlue) }
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(Modifier.fillMaxSize()) {
            items(friendsList) { fid ->
                var expanded by remember { mutableStateOf(false) }
                MiuiCard(Modifier.padding(vertical = 4.dp).clickable { expanded = !expanded }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fid, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("V${prefs.getFriendVersion(fid)}", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { 
                                viewModel.deleteFriend(fid)
                                friendsList = prefs.getAllFriends()
                            }) {
                                Icon(Icons.Default.Delete, null, tint = Color.LightGray)
                            }
                            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                        }
                        AnimatedVisibility(expanded) {
                            Column(Modifier.padding(top = 12.dp)) {
                                val key = prefs.getFriendKey(fid) ?: ""
                                Text(key, fontSize = 10.sp, color = Color.Gray, maxLines = 3)
                                Button(
                                    onClick = { 
                                        val msg = MessageHelper.createPublicKeyMessage(fid, prefs.getFriendVersion(fid), key)
                                        clipboard.setText(AnnotatedString(msg)) 
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(stringResource(R.string.copy_json)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsScreen(viewModel: MainViewModel, prefs: PreferenceManager) {
    var newId by remember { mutableStateOf("") }
    var useTee by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).fillMaxSize()) {
        Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))

        MiuiCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.new_identity_title), fontWeight = FontWeight.Bold)
                OutlinedTextField(value = newId, onValueChange = { newId = it }, label = { Text(stringResource(R.string.identity_name_hint)) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useTee, onCheckedChange = { useTee = it })
                    Text(stringResource(R.string.use_tee_label))
                }
                Button(onClick = { if (newId.isNotBlank()) { viewModel.addAccount(newId, useTee); newId = "" } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_btn)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { 
            val data = prefs.exportAllDataJson()
            clipboard.setText(AnnotatedString(data))
            Toast.makeText(context, "备份已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text(stringResource(R.string.export_data_btn)) }

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.identities_section), fontWeight = FontWeight.SemiBold)

        viewModel.accounts.forEach { acc ->
            val active = viewModel.activeUserId == acc.userId
            var showHistory by remember { mutableStateOf(false) }
            
            MiuiCard(Modifier.padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(acc.userId, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        
                        IconButton(onClick = { viewModel.deleteAccount(acc.userId) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.LightGray)
                        }

                        if (!active) Button(onClick = { viewModel.switchAccount(acc.userId) }) { Text(stringResource(R.string.switch_btn)) }
                        else Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50))
                    }
                    Text("V${acc.currentVersion} | ${if (acc.useTee) "TEE" else "Soft"}", fontSize = 12.sp, color = Color.Gray)
                    if (acc.useTee) {
                        Text(
                            if (acc.isHardwareVerified) stringResource(R.string.hw_secured) else stringResource(R.string.sw_emulated),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (acc.isHardwareVerified) Color(0xFF4CAF50) else Color.Red
                        )
                    }
                    
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(onClick = { viewModel.generateNewKey(acc) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.new_key_btn)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showHistory = !showHistory }, modifier = Modifier.weight(1f)) { 
                            Text(if (showHistory) stringResource(R.string.hide_history) else stringResource(R.string.view_history)) 
                        }
                    }
                    
                    AnimatedVisibility(showHistory) {
                        Column(Modifier.padding(top = 16.dp)) {
                            (acc.currentVersion downTo 1).forEach { v ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MiuiBackground.copy(alpha = 0.5f))) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Version $v", modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        IconButton(onClick = {
                                            scope.launch {
                                                val alias = "user_${acc.userId}_v$v"
                                                val pk = withContext(Dispatchers.Default) { CryptoManager.getPublicKeyFromAny(alias, prefs) }
                                                if (pk != null) {
                                                    val m = MessageHelper.createPublicKeyMessage(acc.userId, v, CryptoManager.publicKeyToString(pk))
                                                    clipboard.setText(AnnotatedString(m))
                                                    Toast.makeText(context, "已复制 V$v 公钥", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) { Icon(Icons.Default.Share, null, Modifier.size(20.dp), tint = MiuiBlue) }
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

@Composable
fun MiuiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MiuiCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(content = content)
    }
}
