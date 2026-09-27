package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.domain.model.PostCategory

fun PostCategory.labelRes(): Int = when (this) {
    PostCategory.GENERAL -> R.string.category_general
    PostCategory.QUESTION -> R.string.category_question
    PostCategory.STUDY -> R.string.category_study
    PostCategory.EVENT -> R.string.category_event
    PostCategory.ANNOUNCEMENT -> R.string.category_announcement
    PostCategory.MARKETPLACE -> R.string.category_marketplace
    PostCategory.HOUSING -> R.string.category_housing
    PostCategory.LOST_FOUND -> R.string.category_lost_found
    PostCategory.CAREER -> R.string.category_career
    PostCategory.SPORTS -> R.string.category_sports
}

val PostCategory.icon: ImageVector
    @Composable get() = when (this) {
        PostCategory.GENERAL -> AppIcons.Forum
        PostCategory.QUESTION -> AppIcons.Help
        PostCategory.STUDY -> AppIcons.MenuBook
        PostCategory.EVENT -> AppIcons.Event
        PostCategory.ANNOUNCEMENT -> AppIcons.Campaign
        PostCategory.MARKETPLACE -> AppIcons.Storefront
        PostCategory.HOUSING -> AppIcons.Apartment
        PostCategory.LOST_FOUND -> AppIcons.Search
        PostCategory.CAREER -> AppIcons.Work
        PostCategory.SPORTS -> AppIcons.SportsSoccer
    }

@Composable
fun CategoryChip(
    category: PostCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(stringResource(category.labelRes())) },
        leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier,
    )
}
