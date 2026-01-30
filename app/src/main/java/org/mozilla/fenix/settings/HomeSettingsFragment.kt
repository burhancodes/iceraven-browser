/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.mozilla.fenix.GleanMetrics.CustomizeHome
import org.mozilla.fenix.R
import org.mozilla.fenix.components.Components
import org.mozilla.fenix.components.appstate.AppAction
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.navigateWithBreadcrumb
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.home.pocket.ContentRecommendationsFeatureHelper
import org.mozilla.fenix.utils.Settings
import org.mozilla.fenix.utils.view.addToRadioGroup

/**
 * A [PreferenceFragmentCompat] that displays settings for customizing the Firefox home screen.
 *
 * User interactions with these preferences are persisted in [Settings] and may trigger
 * telemetry events via [CustomizeHome] metrics.
 */
class HomeSettingsFragment : PreferenceFragmentCompat() {

    private val args by navArgs<HomeSettingsFragmentArgs>()

    @VisibleForTesting
    internal var customizeHomeMetrics: CustomizeHome = CustomizeHome

    @VisibleForTesting
    internal var contentRecommendationsHelper: ContentRecommendationsFeatureHelper = ContentRecommendationsFeatureHelper

    @VisibleForTesting
    internal lateinit var fenixSettings: Settings

    @VisibleForTesting
    internal lateinit var fenixComponents: Components

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (!::fenixSettings.isInitialized) {
            fenixSettings = context.settings()
        }
        if (!::fenixComponents.isInitialized) {
            fenixComponents = context.components
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home_preferences, rootKey)
        setupPreferences()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_home_2))
        args.preferenceToScrollTo?.let {
            scrollToPreference(it)
        }
    }

    private fun setupPreferences() {
        requirePreference<SwitchPreference>(R.string.pref_key_show_top_sites).apply {
            isChecked = context.settings().showTopSitesFeature
            onPreferenceChangeListener = object : SharedPreferenceUpdater() {
                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                    CustomizeHome.preferenceToggled.record(
                        CustomizeHome.PreferenceToggledExtra(
                            newValue as Boolean,
                            "most_visited_sites",
                        ),
                    )

                    return super.onPreferenceChange(preference, newValue)
                }
            }
        }

        requirePreference<SwitchPreference>(R.string.pref_key_show_top_recent_sites).apply {
            isChecked = context.settings().showTopRecentSites
            onPreferenceChangeListener = object : SharedPreferenceUpdater() {
                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                    CustomizeHome.preferenceToggled.record(
                        CustomizeHome.PreferenceToggledExtra(
                            newValue as Boolean,
                            "top_recent_sites",
                        ),
                    )

                    return super.onPreferenceChange(preference, newValue)
                }
            }
        }

//        requirePreference<CheckBoxPreference>(R.string.pref_key_enable_contile).apply {
//            isChecked = context.settings().showContileFeature
//            onPreferenceChangeListener = object : SharedPreferenceUpdater() {
//                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
//                    CustomizeHome.preferenceToggled.record(
//                        CustomizeHome.PreferenceToggledExtra(
//                            newValue as Boolean,
//                            "contile",
//                        ),
//                    )
//
//                    return super.onPreferenceChange(preference, newValue)
//                }
//            }
//        }

        requirePreference<SwitchPreference>(R.string.pref_key_recent_tabs).apply {
            isVisible = fenixSettings.showHomepageRecentTabsSectionToggle
            isChecked = fenixSettings.showRecentTabsFeature
            onPreferenceChangeListener = createMetricPreferenceChangeListener("jump_back_in")
        }

        requirePreference<SwitchPreference>(R.string.pref_key_customization_bookmarks).apply {
            isVisible = fenixSettings.showHomepageBookmarksSectionToggle
            isChecked = fenixSettings.showBookmarksHomeFeature
            onPreferenceChangeListener = createMetricPreferenceChangeListener("bookmarks")
        }

        requirePreference<SwitchPreference>(R.string.pref_key_pocket_homescreen_recommendations).apply {
            isVisible = contentRecommendationsHelper.isContentRecommendationsFeatureEnabled(requireContext())
            isChecked = fenixSettings.showPocketRecommendationsFeature
            onPreferenceChangeListener = createMetricPreferenceChangeListener("pocket")
        }

        // requirePreference<CheckBoxPreference>(R.string.pref_key_pocket_sponsored_stories).apply {
        //     isVisible = ContentRecommendationsFeatureHelper.isPocketSponsoredStoriesFeatureEnabled(context)
        //     isChecked = context.settings().showPocketSponsoredStories
        //     onPreferenceChangeListener = object : SharedPreferenceUpdater() {
        //         override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        //             when (newValue) {
        //                 true -> {
        //                     context.components.core.pocketStoriesService.startPeriodicSponsoredContentsRefresh()
        //                 }
        //                 false -> {
        //                     context.components.core.pocketStoriesService.deleteUser()

        //                     context.components.appStore.dispatch(
        //                         ContentRecommendationsAction.SponsoredContentsChange(
        //                             sponsoredContents = emptyList(),
        //                         ),
        //                     )
        //                 }
        //             }

        //             return super.onPreferenceChange(preference, newValue)
        //         }
        //     }
        // }

        requirePreference<SwitchPreference>(R.string.pref_key_history_metadata_feature).apply {
            isVisible = fenixSettings.showHomepageRecentlyVisitedSectionToggle
            isChecked = fenixSettings.historyMetadataUIFeature
            onPreferenceChangeListener = createMetricPreferenceChangeListener("recently_visited")
        }

        val openingScreenRadioHomepage =
            requirePreference<RadioButtonPreference>(R.string.pref_key_start_on_home_always)
        val openingScreenLastTab =
            requirePreference<RadioButtonPreference>(R.string.pref_key_start_on_home_never)
        val openingScreenAfterFourHours =
            requirePreference<RadioButtonPreference>(R.string.pref_key_start_on_home_after_four_hours)

        requirePreference<Preference>(R.string.pref_key_wallpapers).apply {
            setOnPreferenceClickListener {
                view?.findNavController()?.navigateWithBreadcrumb(
                    directions = HomeSettingsFragmentDirections.actionHomeSettingsFragmentToWallpaperSettingsFragment(),
                    navigateFrom = "HomeSettingsFragment",
                    navigateTo = "ActionHomeSettingsFragmentToWallpaperSettingsFragment",
                    crashReporter = fenixComponents.analytics.crashReporter,
                )
                true
            }
        }

        addToRadioGroup(
            openingScreenRadioHomepage,
            openingScreenLastTab,
            openingScreenAfterFourHours,
        )


        val defaultHomepage =
            requirePreference<RadioButtonPreference>(R.string.pref_key_default_homepage)
        val customHomePage =
            requirePreference<RadioButtonPreference>(R.string.pref_key_custom_homepage)

        addToRadioGroup(
            defaultHomepage,
            customHomePage
        )

        requirePreference<EditTextPreference>(R.string.pref_key_custom_homepage_url).apply {
            text = requireContext().settings().customHomepageUrl
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
    }

    private fun createMetricPreferenceChangeListener(metricKey: String): Preference.OnPreferenceChangeListener {
        return Preference.OnPreferenceChangeListener { preference, newValue ->
            val newBooleanValue = newValue as? Boolean ?: return@OnPreferenceChangeListener false

            customizeHomeMetrics.preferenceToggled.record(
                CustomizeHome.PreferenceToggledExtra(
                    newBooleanValue,
                    metricKey,
                ),
            )

            fenixSettings.preferences.edit { putBoolean(preference.key, newBooleanValue) }

            true
        }
    }
}
