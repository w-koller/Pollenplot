package io.github.wkoller.pollenplot.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import io.github.wkoller.pollenplot.ui.theme.animationsOn

/** How the navigation host moves from one screen to the next. */
internal class NavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

/**
 * The navigation host's transitions, on the theme's springs. A page opened from another comes in an eighth of the
 * width from the side it is going towards as it fades in, and Back sends it the other way, so the direction says
 * whether you went deeper or came back. Moving between tabs, which sit side by side rather than one inside another,
 * fades through. With the phone's animations off, the next screen is simply there.
 */
@Composable
internal fun navTransitions(): NavTransitions {
    if (!animationsOn()) {
        return NavTransitions({ EnterTransition.None }, { ExitTransition.None }, { EnterTransition.None }, { ExitTransition.None })
    }
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val fadeInSpec = motion.defaultEffectsSpec<Float>()
    // Leaving is quicker than arriving, so the new screen is never waiting on the old.
    val fadeOutSpec = motion.fastEffectsSpec<Float>()
    return NavTransitions(
        enter = {
            if (betweenTabs()) {
                fadeIn(fadeInSpec)
            } else {
                slideIntoContainer(SlideDirection.Start, spatial) { it / SLIDE_PART } + fadeIn(fadeInSpec)
            }
        },
        exit = {
            if (betweenTabs()) {
                fadeOut(fadeOutSpec)
            } else {
                slideOutOfContainer(SlideDirection.Start, spatial) { it / SLIDE_PART } + fadeOut(fadeOutSpec)
            }
        },
        popEnter = {
            if (betweenTabs()) {
                fadeIn(fadeInSpec)
            } else {
                slideIntoContainer(SlideDirection.End, spatial) { it / SLIDE_PART } + fadeIn(fadeInSpec)
            }
        },
        popExit = {
            if (betweenTabs()) {
                fadeOut(fadeOutSpec)
            } else {
                slideOutOfContainer(SlideDirection.End, spatial) { it / SLIDE_PART } + fadeOut(fadeOutSpec)
            }
        },
    )
}

/** How far a page slides, as a part of the width: an eighth. */
private const val SLIDE_PART = 8

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() = initialState.isTab() && targetState.isTab()

private fun NavBackStackEntry.isTab() = TopLevelTab.entries.any { tab -> destination.hierarchy.any { it.hasRoute(tab.route::class) } }
