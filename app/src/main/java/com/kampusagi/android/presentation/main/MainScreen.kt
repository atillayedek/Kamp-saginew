package com.kampusagi.android.presentation.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import com.kampusagi.android.presentation.chat.ChatScreen
import com.kampusagi.android.presentation.chat.ConversationsScreen
import com.kampusagi.android.presentation.chat.ConversationsViewModel
import com.kampusagi.android.presentation.chat.displayName
import com.kampusagi.android.presentation.community.CreatePostScreen
import com.kampusagi.android.presentation.community.FeedScreen
import com.kampusagi.android.presentation.community.FeedViewModel
import com.kampusagi.android.presentation.community.PostDetailScreen
import com.kampusagi.android.presentation.notification.NotificationsScreen
import com.kampusagi.android.presentation.notification.NotificationsViewModel
import com.kampusagi.android.presentation.premium.PremiumScreen
import com.kampusagi.android.presentation.requirement.CreateRequirementScreen
import com.kampusagi.android.presentation.requirement.MatchesScreen
import com.kampusagi.android.presentation.requirement.RequirementsScreen
import com.kampusagi.android.presentation.requirement.RequirementsViewModel

private enum class Tab(val route: Any, val label: Int, val icon: ImageVector) {
    COMMUNITY(FeedRoute, R.string.tab_community, Icons.Outlined.Forum),
    REQUIREMENTS(RequirementsRoute, R.string.tab_requirements, Icons.Outlined.Lightbulb),
    CHAT(ConversationsRoute, R.string.tab_chat, Icons.Outlined.ChatBubbleOutline),
    NOTIFICATIONS(NotificationsRoute, R.string.tab_notifications, Icons.Outlined.Notifications),
    PROFILE(ProfileRoute, R.string.tab_profile, Icons.Outlined.Person),
}

/** The app for approved students. Only features that work end to end have a tab. */
@Composable
fun MainScreen(
    profile: Profile,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    openNotificationsRequested: Boolean,
    onNotificationsOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Keyed by account so a different person signing in never sees the previous feed.
    val feedViewModel: FeedViewModel = hiltViewModel(key = "feed-${profile.id}")
    val requirementsViewModel: RequirementsViewModel = hiltViewModel(key = "requirements-${profile.id}")
    val conversationsViewModel: ConversationsViewModel = hiltViewModel(key = "conversations-${profile.id}")
    val notificationsViewModel: NotificationsViewModel = hiltViewModel(key = "notifications-${profile.id}")

    // Android 13+ asks once for permission to show push notifications.
    var notificationsAllowed by remember { mutableStateOf(notificationPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    LaunchedEffect(Unit) {
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificationsViewModel.isPushConfigured) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(openNotificationsRequested) {
        if (openNotificationsRequested) {
            onNotificationsOpened()
            notificationsViewModel.load()
            navController.navigate(NotificationsRoute) { launchSingleTop = true }
        }
    }
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
                            icon = {
                                val unread = when (tab) {
                                    Tab.CHAT -> conversationsViewModel.unreadTotal
                                    Tab.NOTIFICATIONS -> notificationsViewModel.unreadCount
                                    else -> 0
                                }
                                if (unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
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
            composable<RequirementsRoute> {
                RequirementsScreen(
                    viewModel = requirementsViewModel,
                    onCreate = { navController.navigate(CreateRequirementRoute) },
                    onOpenMatches = { navController.navigate(MatchesRoute(it.id, it.title)) },
                )
            }
            composable<MatchesRoute> {
                MatchesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { id, title -> navController.navigate(ChatRoute(id, title)) },
                )
            }
            composable<ConversationsRoute> {
                ConversationsScreen(
                    viewModel = conversationsViewModel,
                    onOpen = { navController.navigate(ChatRoute(it.id, it.other.displayName())) },
                )
            }
            composable<ChatRoute> {
                ChatScreen(
                    onBack = {
                        conversationsViewModel.load()
                        navController.popBackStack()
                    },
                )
            }
            composable<CreateRequirementRoute> {
                CreateRequirementScreen(
                    onBack = { navController.popBackStack() },
                    onPublished = {
                        requirementsViewModel.load()
                        navController.popBackStack()
                    },
                )
            }
            composable<NotificationsRoute> {
                NotificationsScreen(
                    viewModel = notificationsViewModel,
                    notificationsAllowed = notificationsAllowed,
                    onOpen = { notification ->
                        when {
                            notification.conversationId != null -> navController.navigate(
                                ChatRoute(notification.conversationId, notification.actorName.orEmpty()),
                            )
                            notification.postId != null -> navController.navigate(PostDetailRoute(notification.postId))
                        }
                    },
                )
            }
            composable<ProfileRoute> {
                ProfileTab(
                    profile = profile,
                    isAdmin = isAdmin,
                    onOpenAdmin = onOpenAdmin,
                    onOpenPremium = { navController.navigate(PremiumRoute) },
                    onSignOut = onSignOut,
                )
            }
            composable<PremiumRoute> {
                PremiumScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun notificationPermissionGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
