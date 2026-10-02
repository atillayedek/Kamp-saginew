package com.kampusagi.android.presentation.premium

import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import android.util.Log
import android.net.Uri
import android.content.Intent
import android.content.ActivityNotFoundException
import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.SubscriptionStatus
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.requirement.formatStartsAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    onOpenLegal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.premium_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        when (val state = viewModel.state) {
            PremiumState.Loading -> LoadingView()
            is PremiumState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.premium_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is PremiumState.Loaded -> Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CurrentStatus(state.subscription)
                PremiumPerks()
                PromoCodeCard(
                    code = viewModel.promoCode,
                    onCodeChange = viewModel::onPromoCodeChange,
                    onRedeem = viewModel::redeemPromoCode,
                    busy = viewModel.isWorking,
                )
                viewModel.message?.let { Text(stringResource(it.textRes()), style = MaterialTheme.typography.bodyMedium) }
                viewModel.error?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                when {
                    state.plans.isEmpty() -> Text(stringResource(R.string.premium_no_plans), style = MaterialTheme.typography.bodyLarge)
                    !state.storeAvailable -> Text(
                        stringResource(R.string.premium_store_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.plans.isNotEmpty() && state.storeAvailable) {
                    PurchaseTerms(viewModel, onOpenLegal)
                }
                state.plans.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(item.plan.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.plan.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.premium_limits, item.plan.maxActiveRequirements),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val offer = item.offer
                            if (offer != null) {
                                Text(offer.formattedPrice, style = MaterialTheme.typography.titleMedium)
                                val current = state.subscription.productId == item.plan.productId
                                PrimaryButton(
                                    text = stringResource(if (current) R.string.premium_current_plan else R.string.action_subscribe),
                                    onClick = { activity?.let { viewModel.buy(it, item.plan) } },
                                    enabled = !current && activity != null && viewModel.canBuy,
                                    loading = viewModel.isWorking,
                                )
                            } else if (state.storeAvailable) {
                                Text(
                                    stringResource(R.string.premium_product_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.plans.isNotEmpty()) {
                    LinkButton(stringResource(R.string.action_restore_purchases), viewModel::restore, enabled = !viewModel.isWorking)
                    ManageSubscriptionLink(state.subscription.productId)
                    Text(
                        stringResource(R.string.premium_legal_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromoCodeCard(code: String, onCodeChange: (String) -> Unit, onRedeem: () -> Unit, busy: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.premium_promo_title), style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.premium_promo_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.premium_promo_action),
                onClick = onRedeem,
                enabled = code.trim().length >= 4,
                loading = busy,
            )
        }
    }
}

/** What Premium unlocks besides the plan's limits; each is enforced by the server. */
@Composable
private fun PremiumPerks() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.premium_perks_title), style = MaterialTheme.typography.titleMedium)
            listOf(
                R.string.premium_perk_channels,
                R.string.premium_perk_channel_photo,
                R.string.premium_perk_channel_polls,
                R.string.premium_perk_badge,
                R.string.premium_perk_requirements,
            ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun CurrentStatus(subscription: SubscriptionStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                if (subscription.isGift) {
                    stringResource(R.string.premium_status_gift, subscription.planName.orEmpty())
                } else if (subscription.isPremium) {
                    stringResource(R.string.premium_status_active, subscription.planName.orEmpty())
                } else {
                    stringResource(R.string.premium_status_free)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            formatStartsAt(subscription.expiresAt)?.let {
                Text(stringResource(R.string.premium_renews_or_ends, it), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.premium_limits, subscription.maxActiveRequirements),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun PremiumMessage.textRes(): Int = when (this) {
    PremiumMessage.PURCHASE_ACTIVATED -> R.string.premium_activated
    PremiumMessage.PURCHASE_PENDING -> R.string.premium_pending
    PremiumMessage.NOTHING_TO_RESTORE -> R.string.premium_nothing_to_restore
    PremiumMessage.PROMO_REDEEMED -> R.string.premium_promo_redeemed
}

/** 6502 / Mesafeli Sözleşmeler Yönetmeliği: both boxes before buying; nothing is pre-ticked. */
@Composable
private fun PurchaseTerms(viewModel: PremiumViewModel, onOpenLegal: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.premium_pre_info_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.premium_pre_info_summary), style = MaterialTheme.typography.bodySmall)
            viewModel.purchaseDocuments.forEach { doc ->
                LinkButton(doc.title, { onOpenLegal(doc.docType) })
            }
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = viewModel.contractAccepted, onCheckedChange = viewModel::onContractAcceptedChange)
                Text(stringResource(R.string.premium_accept_contract), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = viewModel.withdrawalWaived, onCheckedChange = viewModel::onWithdrawalWaivedChange)
                Text(stringResource(R.string.premium_waive_withdrawal), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

/** Google Play's own subscription page, where the subscription is cancelled. */
@Composable
private fun ManageSubscriptionLink(productId: String?) {
    val context = LocalContext.current
    LinkButton(stringResource(R.string.premium_manage_subscription), {
        val url = buildString {
            append("https://play.google.com/store/account/subscriptions?package=").append(context.packageName)
            if (productId != null) append("&sku=").append(productId)
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.w("Premium", "No app to manage subscriptions", e)
        }
    })
}
