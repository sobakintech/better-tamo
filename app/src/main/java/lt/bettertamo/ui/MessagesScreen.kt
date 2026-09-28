@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package lt.bettertamo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.ReplyAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val monthDay = DateTimeFormatter.ofPattern("MM.dd")
private val fullDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun messageDate(value: LocalDateTime?): String {
    value ?: return ""
    val today = LocalDate.now()
    return when {
        value.toLocalDate() == today -> value.format(clock)
        value.toLocalDate() == today.minusDays(1) -> "Vakar"
        value.toLocalDate().isAfter(today.minusDays(7)) -> "${dayShort[value.dayOfWeek.value - 1]} ${value.format(clock)}"
        value.year == today.year -> value.format(monthDay)
        else -> value.toLocalDate().toString()
    }
}

@Composable
private fun Avatar(text: String, highlighted: Boolean, size: androidx.compose.ui.unit.Dp = 40.dp, tamoLogo: Boolean = false) {
    if (tamoLogo) return Image(painterResource(lt.bettertamo.R.drawable.ic_tamo_logo), "TAMO", Modifier.size(size))
    Surface(shape = CircleShape, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Text(text.take(2).uppercase().ifBlank { "T" }, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun MessagesScreen(open: (String) -> Unit) {
    val vm = LocalPlanner.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val end by vm.messagesEnd.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loaded by vm.loadedRequests.collectAsStateWithLifecycle()
    var folderName by rememberSaveable { mutableStateOf(MessageFolder.RECEIVED.name) }
    val folder = MessageFolder.valueOf(folderName)
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val identity = "$folder:$query"
    RefreshOnResume(identity) { vm.loadMessages(folder, query) }
    val shown = messages.takeIf { loaded["messages"] == identity }.orEmpty()
    val complete = readComplete("messages", identity)
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 5 } == true } }
    LaunchedEffect(nearEnd, shown.size) { if (nearEnd && shown.isNotEmpty() && !end) vm.loadMoreMessages() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Pranešimai", actions = {
                IconButton(onClick = { searching = !searching; if (!searching && query.isNotEmpty()) { search = ""; query = "" } }) {
                    Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, if (searching) "Uždaryti paiešką" else "Ieškoti")
                }
            }) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Surface(onClick = { menu = true }, shape = CircleShape, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(folderIcon(folder), null, Modifier.size(18.dp))
                                Text(folder.title, style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            MessageFolder.entries.forEach { item ->
                                DropdownMenuItem(text = { Text(item.title) }, leadingIcon = { Icon(folderIcon(item), null) }, onClick = { folderName = item.name; menu = false },
                                    trailingIcon = if (item == folder) ({ Icon(Icons.Outlined.Check, null) }) else null)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val unread = shown.count { !it.read }
                    if (folder == MessageFolder.RECEIVED && unread > 0) Text("Neperskaityti: $unread", style = MaterialTheme.typography.labelLarge)
                }
                if (searching) OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), singleLine = true,
                    placeholder = { Text("Ieškoti pranešimuose") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = ""; query = "" }) { Icon(Icons.Outlined.Close, "Išvalyti") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = search.trim() }), shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface))
            }
            ReadStatus("messages", showProgress = false) { vm.loadMessages(folder, query, true) }
            PullToRefreshBox(isRefreshing = "messages" in loading, onRefresh = { vm.loadMessages(folder, query, true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (complete && shown.isEmpty()) item {
                        EmptyPanel(Icons.Outlined.Inbox, if (query.isNotEmpty()) "Nieko nerasta" else "Pranešimų nėra", if (query.isNotEmpty()) "Pabandyk kitą paieškos žodį." else "Šiame aplanke pranešimų nėra.")
                    }
                    items(shown, key = { it.sid }) { header -> MessageRow(header, { open("message:${header.sid}") }, { vm.starMessage(header) }) }
                    if ("messages-more" in loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { open("compose:/Messages/New") }, icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("Rašyti") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), expanded = !list.canScrollBackward,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp))
    }
}

private fun folderIcon(folder: MessageFolder) = when (folder) {
    MessageFolder.RECEIVED -> Icons.Outlined.Inbox
    MessageFolder.STARRED -> Icons.Outlined.StarOutline
    MessageFolder.SENT -> Icons.AutoMirrored.Outlined.Send
    MessageFolder.GROUP -> Icons.Outlined.Groups
    MessageFolder.DELETED -> Icons.Outlined.Delete
}

@Composable
private fun MessageRow(header: MessageHeader, onClick: () -> Unit, onStar: () -> Unit) {
    val unread = !header.read
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (unread) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(header.avatar.ifBlank { header.person.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) } }, unread, tamoLogo = header.tamoLogo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(header.person, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (header.attachments) Icon(Icons.Outlined.AttachFile, "Yra priedų", Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(messageDate(header.date), style = MaterialTheme.typography.labelMedium, color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (header.important) ImportantMark(MaterialTheme.typography.bodyMedium)
                    Text(header.subject.ifBlank { "(be temos)" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (header.sent && header.recipientCount != null && header.readCount != null) Text("Perskaitė ${header.readCount} iš ${header.recipientCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!header.sent) IconButton(onClick = onStar, modifier = Modifier.semantics { contentDescription = if (header.starred) "Nuimti žymę" else "Pažymėti žvaigždute" }) {
                Icon(if (header.starred) Icons.Filled.Star else Icons.Outlined.StarOutline, null, tint = if (header.starred) Color(0xFFF5B301) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImportantMark(style: TextStyle) {
    Text("!", Modifier.clearAndSetSemantics { contentDescription = "Svarbus" }, style = style.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.error)
}

@Composable
fun MessageScreen(sid: String, back: () -> Unit, open: (String) -> Unit) {
    val vm = LocalPlanner.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val detail by vm.message.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val header = detail?.header?.takeIf { it.sid == sid } ?: messages.find { it.sid == sid }
    LaunchedEffect(sid) { messages.find { it.sid == sid }?.let(vm::openMessage) ?: back() }
    val current = detail?.takeIf { it.header.sid == sid }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (header?.sent == true) "Išsiųstas pranešimas" else "Pranešimas", back, actions = {
            if (header != null && !header.sent) {
                IconButton(onClick = { vm.starMessage(header) }) {
                    Icon(if (header.starred) Icons.Filled.Star else Icons.Outlined.StarOutline, if (header.starred) "Nuimti žymę" else "Pažymėti žvaigždute", tint = if (header.starred) Color(0xFFF5B301) else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = { vm.markUnread(header); back() }) { Icon(Icons.Outlined.MarkEmailUnread, "Pažymėti kaip neskaitytą") }
            }
        })
        ReadStatus("message", showProgress = false) { header?.let(vm::openMessage) }
        if (header == null) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (header.important) ImportantMark(MaterialTheme.typography.headlineSmall)
                SelectionContainer { Text(header.subject.ifBlank { "(be temos)" }, style = MaterialTheme.typography.headlineSmall) }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(header.avatar, false, tamoLogo = header.tamoLogo)
                    Column(Modifier.weight(1f)) {
                        Text(if (header.sent) "Kam: ${header.person}" else header.person, style = MaterialTheme.typography.titleSmall)
                        if (header.personTitle.isNotBlank()) Text(header.personTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(header.date?.format(fullDate).orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (current == null) {
                if ("message" in loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    val linkColor = MaterialTheme.colorScheme.primary
                    val body = remember(current.body, linkColor) {
                        AnnotatedString.fromHtml(current.body.replace("\n", "<br>").replace(Regex("</p>\\s*(?=.)"), "</p><br>"), linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))
                    }
                    SelectionContainer(Modifier.padding(16.dp)) { Text(body, style = MaterialTheme.typography.bodyLarge) }
                }
                if (current.files.isNotEmpty()) {
                    val opening by vm.openingFile.collectAsStateWithLifecycle()
                    Text("Priedai", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    current.files.forEach { file ->
                        Surface(onClick = { vm.openFile(file) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (opening == file.sid) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Download, "Atsisiųsti")
                            }
                        }
                    }
                }
                if (header.sent && current.recipients.isNotEmpty()) {
                    Text("Gavėjai", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.recipients.forEach { SmallTag(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }
        }
        if (!header.sent) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open("compose:/Messages/Reply/${header.id}") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Atsakyti")
                }
                if ((current?.recipientCount ?: 0) in 2..30) OutlinedButton(onClick = { open("compose:/Messages/ReplyAll/${header.id}") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.ReplyAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Visiems")
                }
            }
        }
    }
}

@Composable
fun ComposeMessageScreen(route: String, back: () -> Unit) {
    val vm = LocalPlanner.current
    val holder = remember(route) { WebHolder() }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    val title = when {
        route.contains("ReplyAll") -> "Atsakyti visiems"
        route.contains("Reply") -> "Atsakyti"
        else -> "Naujas pranešimas"
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, back)
        TamoWebContent(holder, "/goto/bendrauk", Modifier.weight(1f), then = "https://bendrauk.tamo.lt$route", onLeave = {
            vm.loadMessages(MessageFolder.RECEIVED, "", true)
            back()
        })
    }
}
