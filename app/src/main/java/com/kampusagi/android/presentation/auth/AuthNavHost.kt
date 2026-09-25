package com.kampusagi.android.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable private data object SignInRoute
@Serializable private data object SignUpRoute
@Serializable private data object ForgotPasswordRoute
@Serializable private data class VerifyEmailRoute(val email: String)

@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SignInRoute, modifier = modifier) {
        composable<SignInRoute> {
            SignInScreen(
                onCreateAccount = { navController.navigate(SignUpRoute) },
                onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onVerificationSent = { email ->
                    navController.navigate(VerifyEmailRoute(email)) {
                        popUpTo(SignInRoute)
                    }
                },
                onBackToSignIn = { navController.popBackStack() },
            )
        }
        composable<VerifyEmailRoute> { entry ->
            VerifyEmailScreen(
                email = entry.toRoute<VerifyEmailRoute>().email,
                onBackToSignIn = { navController.popBackStack(SignInRoute, inclusive = false) },
            )
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordScreen(onBackToSignIn = { navController.popBackStack() })
        }
    }
}
