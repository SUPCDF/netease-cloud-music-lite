package com.ncmusic.feature.comment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.model.CommentThread
import com.ncmusic.designsystem.component.NcTopBar

/** 歌曲评论页：精彩评论 + 最新评论 + 评论输入框（评论区本体与 MV 页共用） */
@Composable
fun CommentScreen(
    songId: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: CommentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { NcTopBar(title = Str.res(R.string.comments, state.total), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CommentPane(
                threadId = CommentThread.song(songId),
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onUserClick = onUserClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
