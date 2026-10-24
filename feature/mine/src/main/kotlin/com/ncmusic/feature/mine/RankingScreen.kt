package com.ncmusic.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 听歌排行页：由「我的」页入口进入 */
@Composable
fun RankingScreen(
    onBack: () -> Unit,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { NcTopBar(title = Str.res(R.string.listening_stats), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.recordsType == 1,
                    onClick = { viewModel.loadRecords(1) },
                    label = { Text(Str.res(R.string.last_week)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = state.recordsType == 0,
                    onClick = { viewModel.loadRecords(0) },
                    label = { Text(Str.res(R.string.all_time)) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Str.res(R.string.total, state.records.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.loading -> NcLoading()

                state.error != null -> NcError(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                )

                state.records.isEmpty() -> NcEmpty(Str.res(R.string.no_listening_history))

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    itemsIndexed(
                        state.records,
                        key = { index, item -> "rank-$index-${item.song?.id}" },
                    ) { index, item ->
                        val song = item.song
                        if (song != null) {
                            SongRow(
                                song = song,
                                onClick = { viewModel.play(item) },
                                index = index,
                                subtitle = Str.res(R.string.plays, song.artistText, item.playCount ?: 0),
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
