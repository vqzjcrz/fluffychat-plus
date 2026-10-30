package dev.hush.client.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hush.client.ui.chat.ChatScreen
import dev.hush.client.ui.home.HomeScreen
import dev.hush.client.ui.login.LoginScreen
import dev.hush.client.ui.settings.SettingsScreen
import dev.hush.client.ui.theme.LocalPalette

@Composable
fun HushRoot(vm: AppViewModel) {
    val token by vm.token.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(p.background)) {
        if (token == null) LoginScreen(vm) else MainShell(vm)
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    val route = vm.route
    BackHandler(enabled = route != Route.Home) { vm.back() }
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState !is Route.Home) {
                (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 10 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 10 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
            }
        },
        label = "route",
    ) { target ->
        when (target) {
            Route.Home -> HomeScreen(vm)
            is Route.Chat -> ChatScreen(vm, target.channelId)
            Route.Settings -> SettingsScreen(vm)
        }
    }
}
