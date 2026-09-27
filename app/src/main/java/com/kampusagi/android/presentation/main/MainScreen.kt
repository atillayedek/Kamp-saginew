package com.kampusagi.android.presentation.main

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.kampusagi.android.presentation.common.avatar.AvatarHostViewModel
import com.kampusagi.android.presentation.common.avatar.LocalAvatarLoader
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.profile.EditProfileScreen
import com.kampusagi.android.presentation.requirement.MyMatchesScreen
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
import com.kampusagi.android.presentation.settings.SettingsScreen

private enum class Tab(val route: Any, val label: Int) {
    COMMUNITY(FeedRoute, R.string.tab_community),
    REQUIREMENTS(RequirementsRoute, R.string.tab_requirements),
    MATCHES(MyMatchesRoute, R.string.tab_matches),
    CHAT(ConversationsRoute, R.string.tab_chat),
    PROFILE(ProfileRoute, R.string.tab_profile),
}

/** Outlined when idle, filled when selected, as in Material Symbols. */
@Composable
private fun Tab.icon(selected: Boolean): ImageVector = when (this) {
    Tab.COMMUNITY -> if (selected) AppIcons.HomeFilled else AppIcons.Home
    Tab.REQUIREMENTS -> if (selected) AppIcons.LightbulbFilled else AppIcons.Lightbulb
    Tab.MATCHES -> if (selected) AppIcons.HandshakeFilled else AppIcons.Handshake
    Tab.CHAT -> if (selected) AppIcons.ChatBubbleFilled else AppIcons.ChatBubble
    Tab.PROFILE -> if (selected) AppIcons.PersonFilled else AppIcons.Person
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
    val avatarLoader = hiltViewModel<AvatarHostViewModel>().loader

    // Android 13+ asks once for permission to show system notifications.
    var notificationsAllowed by remember { mutableStateOf(notificationPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    LaunchedEffect(Unit) {
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    CompositionLocalProvider(LocalAvatarLoader provides avatarLoader) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val unread = if (tab == Tab.CHAT) conversationsViewModel.unreadTotal else 0
                                when {
                                    // The profile tab shows the person's own photo or initials, as on Instagram.
                                    tab == Tab.PROFILE -> UserAvatar(
                                        profile.id,
                                        profile.fullName,
                                        profile.username,
                                        size = 26.dp,
                                        modifier = if (selected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    unread > 0 -> BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                        Icon(tab.icon(selected), contentDescription = null)
                                    }
                                    else -> Icon(tab.icon(selected), contentDescription = null)
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
                    unreadNotifications = notificationsViewModel.unreadCount,
                    onOpenNotifications = {
                        notificationsViewModel.load()
                        navController.navigate(NotificationsRoute) { launchSingleTop = true }
                    },
                )
            }
            composable<PostDetailRoute> {
                PostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPostChanged = feedViewModel::onPostChanged,
                    onPostDeleted = feedViewModel::onPostDeleted,
                    onAuthorBlocked = feedViewModel::onAuthorBlocked,
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
                TabPage(title = R.string.tab_requirements) {
                    RequirementsScreen(
                        viewModel = requirementsViewModel,
                        onCreate = { navController.navigate(CreateRequirementRoute) },
                        onOpenMatches = { navController.navigate(MatchesRoute(it.id, it.title)) },
                    )
                }
            }
            composable<MatchesRoute> {
                MatchesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { id, title -> navController.navigate(ChatRoute(id, title)) },
                )
            }
            composable<MyMatchesRoute> {
                MyMatchesScreen(
                    onOpenChat = { id, title -> navController.navigate(ChatRoute(id, title)) },
                    onCreateRequirement = { navController.navigate(CreateRequirementRoute) },
                )
            }
            composable<ConversationsRoute> {
                TabPage(title = R.string.tab_chat) {
                    ConversationsScreen(
                        viewModel = conversationsViewModel,
                        onOpen = { navController.navigate(ChatRoute(it.id, it.other.displayName())) },
                    )
                }
            }
            composable<ChatRoute> {
                ChatScreen(
                    onBack = {
                        conversationsViewModel.load()
                        navController.popBackStack()
                    },
                    onBlocked = {
                        conversationsViewModel.load()
                        feedViewModel.refresh()
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
                    onBack = { navController.popBackStack() },
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
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onEditProfile = { navController.navigate(EditProfileRoute) },
                    onSignOut = onSignOut,
                )
            }
            composable<EditProfileRoute> {
                EditProfileScreen(profile = profile, onBack = { navController.popBackStack() })
            }
            composable<PremiumRoute> {
                PremiumScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = {
                        // Unblocked people reappear in the feed and chats.
                        feedViewModel.refresh()
                        conversationsViewModel.load()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
    }
}

/** A top-level tab with a large title bar, like the other tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabPage(title: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) })
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

private fun notificationPermissionGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
