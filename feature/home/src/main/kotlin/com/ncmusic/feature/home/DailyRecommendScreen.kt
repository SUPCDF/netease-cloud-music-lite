package com.ncmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 每日推荐页：根据口味生成的 30 首歌曲（需登录） */
@Composable
fun DailyRecommendScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    viewModel: DailyRecommendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        NcTopBar(title = Str.res(R.string.daily_recommendations), onBack = onBack)

        when {
            state.loading -> NcLoading()

            state.needLogin -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Str.res(R.string.log_in_to_see_daily_recommendations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                FilledTonalButton(
                    onClick = onNeedLogin,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(Str.res(R.string.go_to_login))
                }
            }

            state.error != null -> NcError(
                message = state.error ?: Str.res(R.string.failed_to_load),
                onRetry = { viewModel.load(force = true) },
            )

            state.songs.isEmpty() -> NcEmpty(Str.res(R.string.nothing_recommended_today_check_back_tomorrow))

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    DailyHeader(
                        dateText = state.dateText,
                        count = state.songs.size,
                        onPlayAll = { viewModel.playAll() },
                    )
                }
                itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playSong(song) },
                        index = index,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DailyHeader(
    dateText: String,
    count: Int,
    onPlayAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = Str.res(R.string.made_for_your_taste_songs, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onPlayAll) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(Str.res(R.string.play_all), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
