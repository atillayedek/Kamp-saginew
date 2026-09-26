package com.kampusagi.android.presentation.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kampusagi.android.R
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.presentation.community.CreatePostScreen
import com.kampusagi.android.presentation.community.FeedScreen
import com.kampusagi.android.presentation.community.FeedViewModel
import com.kampusagi.android.presentation.community.PostDetailScreen

private enum class Tab(val route: Any, val label: Int, val icon: ImageVector) {
    COMMUNITY(FeedRoute, R.string.tab_community, Icons.Outlined.Forum),
    PROFILE(ProfileRoute, R.string.tab_profile, Icons.Outlined.Person),
}

/** The app for approved students. Only features that work end to end have a tab. */
@Composable
fun MainScreen(
    profile: Profile,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // Keyed by account so a different person signing in never sees the previous feed.
    val feedViewModel: FeedViewModel = hiltViewModel(key = "feed-${profile.id}")
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = Tab.entries.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.route::class) == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<FeedRoute> {
                FeedScreen(
                    viewModel = feedViewModel,
                    onOpenPost = { navController.navigate(PostDetailRoute(it.id)) },
                    onCreatePost = { navController.navigate(CreatePostRoute(it)) },
                )
            }
            composable<PostDetailRoute> {
                PostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPostChanged = feedViewModel::onPostChanged,
                    onPostDeleted = feedViewModel::onPostDeleted,
                )
            }
            composable<CreatePostRoute> {
                CreatePostScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { scope ->
                        feedViewModel.onPostCreated(scope)
                        navController.popBackStack()
                    },
                )
            }
            composable<ProfileRoute> {
                ProfileTab(profile = profile, isAdmin = isAdmin, onOpenAdmin = onOpenAdmin, onSignOut = onSignOut)
            }
        }
    }
}
