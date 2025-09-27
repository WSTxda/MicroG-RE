/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import com.google.android.gms.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.microg.gms.gcm.*

class PushNotificationAdvancedFragment : PreferenceFragmentCompat() {
    private lateinit var confirmNewApps: TwoStatePreference
    private lateinit var networkMobile: ListPreference
    private lateinit var networkWifi: ListPreference
    private lateinit var networkRoaming: ListPreference
    private lateinit var networkOther: ListPreference
    private lateinit var database: GcmDatabase

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_push_notification_settings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = GcmDatabase(context)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onPause() {
        super.onPause()
        database.close()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onBindPreferences() {
        confirmNewApps = preferenceScreen.findPreference(GcmPrefs.PREF_CONFIRM_NEW_APPS) ?: confirmNewApps
        networkMobile = preferenceScreen.findPreference(GcmPrefs.PREF_NETWORK_MOBILE) ?: networkMobile
        networkWifi = preferenceScreen.findPreference(GcmPrefs.PREF_NETWORK_WIFI) ?: networkWifi
        networkRoaming = preferenceScreen.findPreference(GcmPrefs.PREF_NETWORK_ROAMING) ?: networkRoaming
        networkOther = preferenceScreen.findPreference(GcmPrefs.PREF_NETWORK_OTHER) ?: networkOther

        confirmNewApps.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                if (newValue is Boolean) {
                    setGcmServiceConfiguration(appContext, getGcmServiceInfo(appContext).configuration.copy(confirmNewApps = newValue))
                }
                updateContent()
            }
            true
        }
        networkMobile.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                (newValue as? String)?.toIntOrNull()?.let {
                    setGcmServiceConfiguration(appContext, getGcmServiceInfo(appContext).configuration.copy(mobile = it))
                }
                updateContent()
            }
            true
        }
        networkWifi.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                (newValue as? String)?.toIntOrNull()?.let {
                    setGcmServiceConfiguration(appContext, getGcmServiceInfo(appContext).configuration.copy(wifi = it))
                }
                updateContent()
            }
            true
        }
        networkRoaming.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                (newValue as? String)?.toIntOrNull()?.let {
                    setGcmServiceConfiguration(appContext, getGcmServiceInfo(appContext).configuration.copy(roaming = it))
                }
                updateContent()
            }
            true
        }
        networkOther.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                (newValue as? String)?.toIntOrNull()?.let {
                    setGcmServiceConfiguration(appContext, getGcmServiceInfo(appContext).configuration.copy(other = it))
                }
                updateContent()
            }
            true
        }

        findPreference<Preference>("pref_push_notification_reset")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setIcon(R.drawable.ic_unregister)
                .setTitle(R.string.gcm_remove_registers_dialog_title)
                .setMessage(R.string.gcm_remove_registers_dialog_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            database.resetDatabase()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    private suspend fun updateContent() {
        val appContext = requireContext().applicationContext
        val serviceInfo = getGcmServiceInfo(appContext)
        confirmNewApps.isChecked = serviceInfo.configuration.confirmNewApps
        networkMobile.value = serviceInfo.configuration.mobile.toString()
        networkMobile.summary = getSummaryString(serviceInfo.configuration.mobile, serviceInfo.learntMobileInterval)
        networkWifi.value = serviceInfo.configuration.wifi.toString()
        networkWifi.summary = getSummaryString(serviceInfo.configuration.wifi, serviceInfo.learntWifiInterval)
        networkRoaming.value = serviceInfo.configuration.roaming.toString()
        networkRoaming.summary = getSummaryString(serviceInfo.configuration.roaming, serviceInfo.learntMobileInterval)
        networkOther.value = serviceInfo.configuration.other.toString()
        networkOther.summary = getSummaryString(serviceInfo.configuration.other, serviceInfo.learntOtherInterval)
    }

    private fun getSummaryString(value: Int, learnt: Int): String = when (value) {
        -1 -> getString(R.string.push_notifications_summary_off)
        0 -> getString(R.string.push_notifications_summary_automatic, getHeartbeatString(learnt))
        else -> getString(R.string.push_notifications_summary_manual, getHeartbeatString(value * 60000))
    }

    private fun getHeartbeatString(heartbeatMs: Int): String {
        return if (heartbeatMs < 120000) {
            getString(R.string.push_notifications_summary_values_seconds, (heartbeatMs / 1000).toString())
        } else getString(R.string.push_notifications_summary_values_minutes, (heartbeatMs / 60000).toString())
    }

    companion object {
        @Suppress("unused")
        private val HEARTBEAT_PREFS = arrayOf(GcmPrefs.PREF_NETWORK_MOBILE, GcmPrefs.PREF_NETWORK_ROAMING, GcmPrefs.PREF_NETWORK_WIFI, GcmPrefs.PREF_NETWORK_OTHER)
    }
}