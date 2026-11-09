package com.relay.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.relay.manager.data.Station
import com.relay.manager.data.StationStore
import com.relay.manager.data.StationType
import com.relay.manager.data.UsageLedger
import com.relay.manager.ui.*
import com.relay.manager.ui.theme.AppTheme
import com.relay.manager.ui.theme.BgDeep

private sealed class Screen {
    data object Stations : Screen()
    data object Dashboard : Screen()
    data class Detail(val station: Station) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var store: StationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StationStore(this)

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = BgDeep) {
                    Box(Modifier.fillMaxSize()) {
                        GlowBackground()

                        var screen by remember { mutableStateOf<Screen>(Screen.Stations) }
                        var stations by remember { mutableStateOf(store.list()) }

                        BackHandler(enabled = screen !is Screen.Stations) {
                            screen = Screen.Stations
                        }

                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                val forward = targetState !is Screen.Stations
                                if (forward) {
                                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                                        .togetherWith(slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(220)))
                                } else {
                                    (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)))
                                        .togetherWith(slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(220)))
                                }
                            },
                            label = "nav"
                        ) { sc ->
                            when (sc) {
                                is Screen.Stations -> StationsScreen(
                                    stations = stations,
                                    onAdd = { store.add(it); stations = store.list() },
                                    onUpdate = { store.update(it); stations = store.list() },
                                    onDelete = {
                                        store.remove(it.id)
                                        UsageLedger(this@MainActivity, it.id).clear()
                                        stations = store.list()
                                    },
                                    onOpen = { screen = Screen.Detail(it) },
                                    onOpenDashboard = { screen = Screen.Dashboard },
                                )

                                is Screen.Dashboard -> DashboardScreen(
                                    stations = stations,
                                    onBack = { screen = Screen.Stations },
                                    onOpenStation = { screen = Screen.Detail(it) },
                                )

                                is Screen.Detail -> {
                                    val s = sc.station
                                    if (s.type == StationType.SUB) {
                                        SubDetailScreen(station = s, onBack = { screen = Screen.Stations })
                                    } else {
                                        CpaDetailScreen(station = s, onBack = { screen = Screen.Stations })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
