package com.lexipopup.presentation.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val DIFFICULTY_COLORS = mapOf(
    1 to Color(0xFF4CAF50),
    2 to Color(0xFF2196F3),
    3 to Color(0xFFFF9800),
    4 to Color(0xFFF44336)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedWordListScreen(
    viewModel: SeedWordListViewModel = hiltViewModel(),
    onWordSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val words by viewModel.words.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val isFiltered = searchQuery.isNotBlank()
    val primaryColor = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Built-in Dictionary",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (totalCount > 0) "$totalCount seed words · offline ready"
                                else "Loading…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Gradient accent line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── Search bar ──────────────────────────────────────────────────
            Surface(
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                "Search words, meanings, parts of speech…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = primaryColor
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotBlank(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        singleLine = true
                    )
                }
            }

            // ── Stats banner ────────────────────────────────────────────────
            AnimatedContent(
                targetState = Pair(isFiltered, words.size),
                label = "stats"
            ) { (filtered, count) ->
                if (!isLoading || count > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(primaryColor.copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (filtered) Icons.Default.FilterList else Icons.Default.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = primaryColor
                            )
                            Text(
                                if (filtered) "Showing $count of $totalCount words"
                                else "$count words · tap any to see full definition",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (filtered && count == 0 && !isLoading) {
                            TextButton(
                                onClick = viewModel::clearSearch,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Word list ───────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && words.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = primaryColor)
                            Text(
                                "Loading built-in words…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (words.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                            )
                            Text(
                                "No results for \"$searchQuery\"",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Try a different word or partial spelling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear search")
                            }
                        }
                    }
                } else {
                    LazyColumn(state = listState) {
                        itemsIndexed(words) { index, entry ->
                            SeedWordCard(
                                index = index + 1,
                                word = entry.word,
                                pos = entry.partOfSpeech,
                                pronunciation = entry.pronunciation,
                                meaning = entry.shortMeaning,
                                difficultyLevel = entry.difficultyLevel,
                                isFavorite = entry.isFavorite,
                                onClick = { onWordSelected(entry.word) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                // Floating scroll-to-top button
                val showScrollTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 5 }
                }
                AnimatedVisibility(
                    visible = showScrollTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Scroll to top", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedWordCard(
    index: Int,
    word: String,
    pos: String,
    pronunciation: String,
    meaning: String,
    difficultyLevel: Int,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val diffColor = DIFFICULTY_COLORS[difficultyLevel] ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Index badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        // Word content
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    word,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFFFFC107)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (pos.isNotBlank()) {
                    Text(
                        pos,
                        style = MaterialTheme.typography.labelSmall,
                        color = diffColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(diffColor.copy(0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (pronunciation.isNotBlank()) {
                    Text(
                        pronunciation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (meaning.isNotBlank()) {
                Text(
                    meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Difficulty dot + chevron
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(diffColor)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f)
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
    )
}
