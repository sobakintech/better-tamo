package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lt.bettertamo.data.*

val LocalSchoolData = staticCompositionLocalOf { SchoolData() }
val LocalPlanner = staticCompositionLocalOf<PlannerViewModel> { error("PlannerViewModel is missing") }

@Composable
fun LoginScreen(busy: Boolean, error: String?, login: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun submit() {
        if (busy || username.isBlank() || password.isEmpty()) return
        val entered = password
        password = ""
        visible = false
        focus.clearFocus()
        login(username.trim(), entered)
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Better TAMO", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Prisijungti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Tavo pamokos, namų darbai ir pažymiai.", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(username, { username = it }, label = { Text("Naudotojo vardas") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                OutlinedTextField(password, { password = it }, label = { Text("Slaptažodis") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                    IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Slėpti slaptažodį" else "Rodyti slaptažodį") }
                })
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = ::submit, enabled = !busy && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Prisijungti")
                }
            }
        }
    }
}

@Composable
fun RoleScreen(account: SchoolSession, select: (String) -> Unit, busy: Boolean = false, signOut: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pasirinkite paskyrą", style = MaterialTheme.typography.headlineMedium)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            account.roles.forEach { role ->
                Card(onClick = { select(role.id) }, enabled = !busy) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(role.title, style = MaterialTheme.typography.titleMedium)
                        if (role.subtitle.isNotBlank()) Text(role.subtitle)
                    }
                }
            }
            TextButton(onClick = signOut, enabled = !busy) { Text("Atsijungti") }
        }
    }
}

@Composable
fun ReadStatus(key: String, showProgress: Boolean = true, retry: () -> Unit) {
    val vm = LocalPlanner.current
    val loading by vm.loading.collectAsStateWithLifecycle()
    val errors by vm.readErrors.collectAsStateWithLifecycle()
    if (showProgress && key in loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    errors[key]?.let { message ->
        Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(20.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = retry) { Text("Kartoti") }
            }
        }
    }
}
