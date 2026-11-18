package com.limette.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.limette.tv.ui.navigation.LimetteTVNavigation
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.LimetteTVTheme
import dagger.hilt.android.AndroidEntryPoint

/** 启动即进首页，没有开屏图、没有启动页。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LimetteTVTheme {
                Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
                    LimetteTVNavigation()
                }
            }
        }
    }
}
