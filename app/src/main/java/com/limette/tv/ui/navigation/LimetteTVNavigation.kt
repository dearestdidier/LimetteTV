package com.limette.tv.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.player.PlayerActivity
import com.limette.tv.ui.screens.detail.DetailScreen
import com.limette.tv.ui.screens.favorites.FavoritesScreen
import com.limette.tv.ui.screens.home.HomeScreen
import com.limette.tv.ui.screens.library.MediaLibraryScreen
import com.limette.tv.ui.screens.search.SearchScreen
import com.limette.tv.ui.screens.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val DETAIL = "detail"
}

@Composable
fun LimetteTVNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: MainViewModel = hiltViewModel()

    // 媒体 ID 含 "::" 与路径分隔符，用共享状态传递，避免路由编码问题
    var detailId by remember { mutableStateOf("") }

    fun play(media: WebDavMediaEntity, startPosition: Long) {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_VIDEO_URL, media.filePath)
            putExtra(PlayerActivity.EXTRA_TITLE, media.title)
            putExtra(PlayerActivity.EXTRA_MEDIA_ID, media.id)
            putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition)
        }
        context.startActivity(intent)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenDetail = { id -> detailId = id; navController.navigate(Routes.DETAIL) },
                onPlay = ::play
            )
        }
        composable(Routes.LIBRARY) {
            MediaLibraryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> detailId = id; navController.navigate(Routes.DETAIL) },
                onPlay = ::play
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onPlay = ::play)
        }
        composable(Routes.DETAIL) {
            DetailScreen(
                viewModel = viewModel,
                mediaId = detailId,
                onBack = { navController.popBackStack() },
                onPlay = ::play
            )
        }
    }
}
