package com.ncmusic.lite.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ncmusic.feature.comment.CommentScreen
import com.ncmusic.feature.home.DailyRecommendScreen
import com.ncmusic.feature.mv.MvScreen
import com.ncmusic.feature.home.HomeScreen
import com.ncmusic.feature.home.SearchScreen
import com.ncmusic.feature.login.LoginScreen
import com.ncmusic.feature.mine.AccountInfoScreen
import com.ncmusic.feature.mine.MineScreen
import com.ncmusic.feature.mine.RankingScreen
import com.ncmusic.feature.mine.RecentPlayScreen
import com.ncmusic.feature.people.ArtistScreen
import com.ncmusic.feature.people.ChatScreen
import com.ncmusic.feature.people.ConversationListScreen
import com.ncmusic.feature.people.UserListScreen
import com.ncmusic.feature.people.UserProfileScreen
import com.ncmusic.feature.player.PlayerScreen
import com.ncmusic.feature.playlist.PlaylistDetailScreen
import com.ncmusic.feature.settings.SettingsScreen

/**
 * 一级 tab 之间切换：轻微淡入 + 缩放（不整页横推，切换不晃眼）。
 */
private fun tabEnter(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 220)) +
        scaleIn(
            initialScale = 0.97f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )

private fun tabExit(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 170)) +
        scaleOut(
            targetScale = 0.99f,
            animationSpec = tween(durationMillis = 170),
        )

/** 全局导航图：所有页面在此注册 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Route.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // 二级页默认：从右侧推入 + 淡入，返回时反向（旧页轻微左移做视差）
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) { it } + fadeIn(animationSpec = tween(durationMillis = 200))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) { -it / 4 } + fadeOut(animationSpec = tween(durationMillis = 180))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) { -it / 4 } + fadeIn(animationSpec = tween(durationMillis = 200))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) { it } + fadeOut(animationSpec = tween(durationMillis = 180))
        },
    ) {
        // ---------- 一级页面 ----------
        composable(
            route = Route.HOME,
            enterTransition = { tabEnter() },
            exitTransition = { tabExit() },
            popEnterTransition = { tabEnter() },
            popExitTransition = { tabExit() },
        ) {
            HomeScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Route.playlistDetail(playlistId))
                },
                onSearchClick = { navController.navigate(Route.SEARCH) },
                onDailyClick = { navController.navigate(Route.DAILY) },
                onNeedLogin = { navController.navigate(Route.LOGIN) },
            )
        }
        composable(
            route = Route.MINE,
            enterTransition = { tabEnter() },
            exitTransition = { tabExit() },
            popEnterTransition = { tabEnter() },
            popExitTransition = { tabExit() },
        ) {
            MineScreen(
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onAccountClick = { navController.navigate(Route.ACCOUNT) },
                onSettingsClick = { navController.navigate(Route.SETTINGS) },
                onRecentPlayClick = { navController.navigate(Route.RECENT_PLAY) },
                onDownloadsClick = { navController.navigate(Route.DOWNLOADS) },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Route.playlistDetail(playlistId))
                },
                onMessagesClick = { navController.navigate(Route.MESSAGES) },
                onFollowsClick = { uid ->
                    navController.navigate(Route.userList(uid, "follows"))
                },
                onFollowedsClick = { uid ->
                    navController.navigate(Route.userList(uid, "followeds"))
                },
                onRankingClick = { navController.navigate(Route.RANKING) },
            )
        }

        // ---------- 二级页面 ----------
        composable(Route.RECENT_PLAY) {
            RecentPlayScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.RANKING) {
            RankingScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.DOWNLOADS) {
            com.ncmusic.feature.mine.DownloadScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Route.playlistDetail(playlistId))
                },
                onArtistClick = { artistId ->
                    navController.navigate(Route.artist(artistId))
                },
                onAlbumClick = { albumId ->
                    navController.navigate(Route.album(albumId))
                },
                onUserClick = { uid ->
                    navController.navigate(Route.userProfile(uid))
                },
            )
        }
        composable(Route.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = { navController.popBackStack() },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEqualizerClick = { navController.navigate(Route.EQUALIZER) },
            )
        }
        composable(Route.ACCOUNT) {
            AccountInfoScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.EQUALIZER) {
            com.ncmusic.feature.settings.EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.DAILY) {
            DailyRecommendScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(Route.LOGIN) },
            )
        }
        composable(
            route = Route.PLAYER,
            enterTransition = {
                slideInVertically(
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(durationMillis = 200))
            },
            exitTransition = {
                slideOutVertically(
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
            },
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onOpenComments = { songId -> navController.navigate(Route.comment(songId)) },
                onOpenMv = { mvId -> navController.navigate(Route.mv(mvId)) },
                onOpenArtist = { artistId -> navController.navigate(Route.artist(artistId)) },
                onOpenEqualizer = { navController.navigate(Route.EQUALIZER) },
            )
        }
        composable(
            route = Route.ARTIST,
            arguments = listOf(navArgument(Route.ARTIST_ARG_ID) { type = NavType.LongType }),
        ) {
            ArtistScreen(
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId -> navController.navigate(Route.album(albumId)) },
            )
        }
        composable(
            route = Route.ALBUM,
            arguments = listOf(navArgument(Route.ALBUM_ARG_ID) { type = NavType.LongType }),
        ) {
            com.ncmusic.feature.playlist.AlbumScreen(
                onBack = { navController.popBackStack() },
                onOpenArtist = { artistId -> navController.navigate(Route.artist(artistId)) },
            )
        }
        composable(
            route = Route.USER_LIST,
            arguments = listOf(
                navArgument(Route.USER_LIST_ARG_UID) { type = NavType.LongType },
                navArgument(Route.USER_LIST_ARG_TYPE) { type = NavType.StringType },
            ),
        ) {
            UserListScreen(
                onBack = { navController.popBackStack() },
                onUserClick = { uid -> navController.navigate(Route.userProfile(uid)) },
            )
        }
        composable(
            route = Route.USER_PROFILE,
            arguments = listOf(navArgument(Route.USER_PROFILE_ARG_UID) { type = NavType.LongType }),
        ) { entry ->
            val uid = entry.arguments?.getLong(Route.USER_PROFILE_ARG_UID) ?: 0L
            UserProfileScreen(
                onBack = { navController.popBackStack() },
                onChatClick = { uid -> navController.navigate(Route.chat(uid)) },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Route.playlistDetail(playlistId))
                },
                onOpenUserList = { type -> navController.navigate(Route.userList(uid, type)) },
            )
        }
        composable(
            route = Route.CHAT,
            arguments = listOf(navArgument(Route.CHAT_ARG_UID) { type = NavType.LongType }),
        ) {
            ChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.MESSAGES) {
            ConversationListScreen(
                onBack = { navController.popBackStack() },
                onConversationClick = { uid -> navController.navigate(Route.chat(uid)) },
            )
        }
        composable(
            route = Route.PLAYLIST_DETAIL,
            arguments = listOf(navArgument(Route.PLAYLIST_ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            PlaylistDetailScreen(
                playlistId = entry.arguments?.getLong(Route.PLAYLIST_ARG_ID) ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.COMMENT,
            arguments = listOf(navArgument(Route.COMMENT_ARG_SONG_ID) { type = NavType.LongType }),
        ) { entry ->
            CommentScreen(
                songId = entry.arguments?.getLong(Route.COMMENT_ARG_SONG_ID) ?: 0L,
                onBack = { navController.popBackStack() },
                onUserClick = { uid -> navController.navigate(Route.userProfile(uid)) },
            )
        }
        composable(
            route = Route.MV,
            arguments = listOf(navArgument(Route.MV_ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            MvScreen(
                mvId = entry.arguments?.getLong(Route.MV_ARG_ID) ?: 0L,
                onBack = { navController.popBackStack() },
                onUserClick = { uid -> navController.navigate(Route.userProfile(uid)) },
            )
        }
    }
}
