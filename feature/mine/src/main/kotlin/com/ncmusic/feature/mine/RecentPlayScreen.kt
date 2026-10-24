package com.ncmusic.feature.mine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 最近播放（本地记录）页：由「我的」页入口进入 */
@Composable
fun RecentPlayScreen(
    onBack: () -> Unit,
    viewModel: RecentPlayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(topBar = { NcTopBar(title = Str.res(R.string.recently_played), onBack = onBack) }) { padding ->
        if (state.songs.isEmpty() && !state.loading) {
            NcEmpty(Str.res(R.string.no_recent_plays))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(
                    state.songs,
                    key = { index, song -> "recent-$index-${song.id}" },
                ) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(index) },
                        index = index,
                        isCurrent = song.id == state.currentSongId,
                    )
                }
            }
        }
    }
}
