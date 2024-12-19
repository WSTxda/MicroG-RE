/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.R
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import org.microg.gms.checkin.CheckinPreferences
import org.microg.gms.gcm.GcmDatabase
import org.microg.gms.gcm.GcmPrefs
import org.microg.gms.ui.settings.SettingsProvider
import org.microg.gms.ui.settings.getAllSettingsProviders
import org.microg.tools.ui.ResourceSettingsFragment

class SettingsFragment : ResourceSettingsFragment() {
    private val createdPreferences = mutableListOf<Preference>()

    companion object {
        const val PREF_ABOUT = "pref_about"
        const val PREF_GCM = "pref_gcm"
        const val PREF_PRIVACY = "pref_privacy"
        const val PREF_CHECKIN = "pref_checkin"
        const val PREF_ACCOUNTS = "pref_accounts"
        const val PREF_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
        const val PREF_DEVELOPER = "pref_developer"
        const val PREF_GITHUB = "pref_github"
        const val PREF_IGNORE_BATTERY_OPTIMIZATION = "pref_ignore_battery_optimization"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        findPreference<Preference>(PREF_ACCOUNTS)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.accountManagerFragment)
            true
        }
        findPreference<Preference>(PREF_CHECKIN)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openCheckinSettings)
            true
        }
        findPreference<Preference>(PREF_GCM)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openGcmSettings)
            true
        }
        findPreference<Preference>(PREF_PRIVACY)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.privacyFragment)
            true
        }
        findPreference<Preference>(PREF_ABOUT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openAbout)
            true
        }
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                iconActivityVisibility(MainSettingsActivity::class.java, !isEnabled)
                true
            }
        }
        findPreference<Preference>(PREF_DEVELOPER)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openLink(getString(R.string.developer_link))
            true
        }
        findPreference<Preference>(PREF_GITHUB)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openLink(getString(R.string.github_link))
            true
        }

        findPreference<Preference>(PREF_ABOUT)!!.summary =
            getString(org.microg.tools.ui.R.string.about_version_str, AboutFragment.getSelfVersion(context))

        for (entry in getAllSettingsProviders(requireContext()).flatMap { it.getEntriesStatic(requireContext()) }) {
            entry.createPreference()
        }

        findPreference<Preference>(PREF_IGNORE_BATTERY_OPTIMIZATION)?.isVisible =
            !isBatteryOptimizationPermissionGranted()

        findPreference<Preference>(PREF_IGNORE_BATTERY_OPTIMIZATION)?.setOnPreferenceClickListener {
            requestBatteryOptimizationPermission()
            true
        }
    }

    private fun isBatteryOptimizationPermissionGranted(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = requireContext().packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationPermission() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }

    private fun updateBatteryOptimizationPreferenceVisibility() {
        findPreference<Preference>(PREF_IGNORE_BATTERY_OPTIMIZATION)?.apply {
            isVisible = !isBatteryOptimizationPermissionGranted()
            setOnPreferenceClickListener {
                requestBatteryOptimizationPermission()
                true
            }
        }
    }

    private fun isIconActivityVisible(activityClass: Class<*>): Boolean {
        val packageManager = requireActivity().packageManager
        val componentName = ComponentName(requireContext(), activityClass)
        return when (packageManager.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
    }

    private fun iconActivityVisibility(activityClass: Class<*>, showActivity: Boolean) {
        val packageManager = requireActivity().packageManager
        val componentName = ComponentName(requireContext(), activityClass)

        val newState = if (showActivity) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        packageManager.setComponentEnabledSetting(
            componentName, newState, PackageManager.DONT_KILL_APP
        )
    }

    private fun updateHideLauncherIconSwitchState() {
        val isActivityVisible = isIconActivityVisible(MainSettingsActivity::class.java)
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.isChecked = !isActivityVisible
    }

    private fun SettingsProvider.Companion.Entry.createPreference(): Preference? {
        val preference = Preference(requireContext()).fillFromEntry(this)
        try {
            if (findPreference<PreferenceCategory>(when (group) {
                    SettingsProvider.Companion.Group.HEADER -> "prefcat_header"
                    SettingsProvider.Companion.Group.GOOGLE -> "prefcat_google_services"
                    SettingsProvider.Companion.Group.OTHER -> "prefcat_other_services"
                    SettingsProvider.Companion.Group.FOOTER -> "prefcat_footer"
                })?.addPreference(preference) == true) {
                createdPreferences.add(preference)
                return preference
            } else {
                Log.w(TAG, "Preference not added $key")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed adding preference $key", e)
        }
        return null
    }

    private fun Preference.fillFromEntry(entry: SettingsProvider.Companion.Entry): Preference {
        key = entry.key
        title = entry.title
        summary = entry.summary
        icon = entry.icon
        isPersistent = false
        isVisible = true
        setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), entry.navigationId)
            true
        }
        return this
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Error opening link: $url", e)
            // Handle the case where the browser or app to handle the link is not available
        }
    }

    override fun onResume() {
        super.onResume()

        val fab = requireActivity().findViewById<ExtendedFloatingActionButton>(R.id.preference_fab)
        fab?.visibility = View.GONE

        updateBatteryOptimizationPreferenceVisibility()
        updateHideLauncherIconSwitchState()

        val context = requireContext()
        if (GcmPrefs.get(requireContext()).isEnabled) {
            val database = GcmDatabase(context)
            val regCount = database.registrationList.size
            database.close()
            findPreference<Preference>(PREF_GCM)!!.summary =
                context.getString(org.microg.gms.base.core.R.string.service_status_enabled_short) + " - " +
                        context.resources.getQuantityString(R.plurals.gcm_registered_apps_counter, regCount, regCount)
        } else {
            findPreference<Preference>(PREF_GCM)!!.setSummary(org.microg.gms.base.core.R.string.service_status_disabled_short)
        }

        findPreference<Preference>(PREF_CHECKIN)!!.setSummary(
            if (CheckinPreferences.isEnabled(requireContext())) org.microg.gms.base.core.R.string.service_status_enabled_short
            else org.microg.gms.base.core.R.string.service_status_disabled_short
        )

        lifecycleScope.launch {
            val entries = getAllSettingsProviders(requireContext()).flatMap { it.getEntriesDynamic(requireContext()) }
            for (preference in createdPreferences) {
                if (!entries.any { it.key == preference.key }) preference.isVisible = false
            }
            for (entry in entries) {
                val preference = createdPreferences.find { it.key == entry.key }
                if (preference != null) preference.fillFromEntry(entry)
                else entry.createPreference()
            }
        }
    }

    init {
        preferencesResource = R.xml.preferences_start
    }
}