package com.lexipopup.presentation.dictionary

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val ALPHABET = ('A'..'Z').map { it.toString() } + listOf("#")
private val POS_FILTERS = listOf("" to "All", "noun" to "Noun", "verb" to "Verb", "adjective" to "Adj", "adverb" to "Adv")
private val SORT_OPTIONS = listOf("alpha" to "A–Z", "frequency" to "Frequency", "difficulty" to "Difficulty")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryBrowserScreen(
    viewModel: DictionaryBrowserViewModel = hiltViewModel(),
    onWordSelected: (String) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedLetter by viewModel.selectedLetter.collectAsState()
    val browseWords by viewModel.browseWords.collectAsState()
    val letterCount by viewModel.letterCount.collectAsState()
    val wordOfDay by viewModel.wordOfDay.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val filterPos by viewModel.filterPos.collectAsState()

    val isSearching = searchQuery.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    var showSortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { viewModel.onSearchQuery(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Search bar ───────────────────────────────────────────────────────
        Surface(shadowElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("Search any word or phrase…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word…")
                                }
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Default.Mic, "Voice search", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true
                )

                // Suggestions chips (shown while typing)
                AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { s ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.onSearchQuery(s)
                                    onWordSelected(s)
                                },
                                label = { Text(s, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        if (isSearching) {
            // ── SEARCH RESULTS ───────────────────────────────────────────────
            SearchResultsContent(
                query = searchQuery,
                results = searchResults,
                isLoading = isLoading,
                onWordSelected = onWordSelected
            )
        } else {
            // ── BROWSE MODE ──────────────────────────────────────────────────
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

                // Word of the Day
                item {
                    WordOfDayCard(wordOfDay = wordOfDay, onClick = { wordOfDay?.let { onWordSelected(it.word) } })
                }

                // Alphabet quick-jump bar
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "🔤  ALPHABETICAL BROWSE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        // Two rows of letters
                        val row1 = ALPHABET.take(13)
                        val row2 = ALPHABET.drop(13)
                        AlphabetRow(row1, selectedLetter) { viewModel.selectLetter(it) }
                        Spacer(Modifier.height(4.dp))
                        AlphabetRow(row2, selectedLetter) { viewModel.selectLetter(it) }
                    }
                }

                // Filter row (POS + sort)
                item {
                    FilterSortRow(
                        filterPos = filterPos,
                        sortBy = sortBy,
                        showSortMenu = showSortMenu,
                        onShowSortMenu = { showSortMenu = it },
                        onFilterPos = viewModel::setFilterPos,
                        onSortBy = viewModel::setSortBy
                    )
                }

                // Word count header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📋  WORDS STARTING WITH '$selectedLetter'",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (letterCount > 0) {
                            Text(
                                "$letterCount words",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Browse word list
                if (isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (browseWords.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No words for '$selectedLetter' yet\nLook up words with Moon+ Reader to populate", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    items(browseWords) { entry ->
                        BrowseWordRow(
                            word = entry.word,
                            pos = entry.partOfSpeech,
                            meaning = entry.shortMeaning,
                            posColor = Color(entry.partOfSpeechColor),
                            isFavorite = entry.isFavorite,
                            onClick = { onWordSelected(entry.word) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WordOfDayCard(wordOfDay: com.lexipopup.domain.models.WordEntry?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.08f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                Text("WORD OF THE DAY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = primary)
            }
            if (wordOfDay != null) {
                Text(wordOfDay.word.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (wordOfDay.partOfSpeech.isNotBlank()) {
                        Text(wordOfDay.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (wordOfDay.pronunciation.isNotBlank()) {
                        Text(wordOfDay.pronunciation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
                Text(wordOfDay.shortMeaning.take(120), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onClick, label = { Text("📖 Full details") }, leadingIcon = null)
                }
            } else {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AlphabetRow(letters: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(letters) { letter ->
            val isSelected = letter == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(letter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterSortRow(
    filterPos: String,
    sortBy: String,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onFilterPos: (String) -> Unit,
    onSortBy: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        POS_FILTERS.forEach { (pos, label) ->
            FilterChip(
                selected = filterPos == pos,
                onClick = { onFilterPos(pos) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
        Spacer(Modifier.width(4.dp))
        Box {
            FilterChip(
                selected = sortBy != "alpha",
                onClick = { onShowSortMenu(true) },
                label = { Text(SORT_OPTIONS.find { it.first == sortBy }?.second ?: "A–Z", fontSize = 12.sp) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { onShowSortMenu(false) }) {
                SORT_OPTIONS.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSortBy(key); onShowSortMenu(false) },
                        leadingIcon = { if (sortBy == key) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    results: List<com.lexipopup.domain.models.WordEntry>,
    isLoading: Boolean,
    onWordSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "🔍  \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isLoading && results.isNotEmpty()) {
                Text("${results.size} results", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                    Text("No results for \"$query\"", fontWeight = FontWeight.Bold)
                    Text("Try a shorter or different spelling", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Most relevant (first 3)
            if (results.size >= 3) {
                Text(
                    "🏆  MOST RELEVANT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))
                ) {
                    Column {
                        results.take(3).forEachIndexed { i, e ->
                            BrowseWordRow(
                                word = e.word,
                                pos = e.partOfSpeech,
                                meaning = e.shortMeaning,
                                posColor = Color(e.partOfSpeechColor),
                                isFavorite = e.isFavorite,
                                onClick = { onWordSelected(e.word) }
                            )
                            if (i < 2) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "📚  ALL MATCHES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            LazyColumn {
                items(results) { entry ->
                    BrowseWordRow(
                        word = entry.word,
                        pos = entry.partOfSpeech,
                        meaning = entry.shortMeaning,
                        posColor = Color(entry.partOfSpeechColor),
                        isFavorite = entry.isFavorite,
                        onClick = { onWordSelected(entry.word) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun BrowseWordRow(
    word: String,
    pos: String,
    meaning: String,
    posColor: Color,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pos.isNotBlank()) {
                    Text(
                        pos,
                        style = MaterialTheme.typography.labelSmall,
                        color = posColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(posColor.copy(0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (isFavorite) Icon(Icons.Default.Star, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFC107))
            }
            if (meaning.isNotBlank()) {
                Text(
                    meaning.take(90),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp)
}
