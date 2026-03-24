/*
 *   Copyright (c) 2020 Project CHIP Authors
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.google.chip.chiptool.provisioning

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.chip.chiptool.NetworkCredentialsParcelable
import com.google.chip.chiptool.R
import com.google.chip.chiptool.util.FragmentUtil
import java.util.Locale

/**
 * Fragment to collect Wi-Fi network information from user and send it to device being provisioned.
 */
class EnterNetworkFragment : Fragment() {
  private var isUpdatingFields = false

  private val networkType: ProvisionNetworkType
    get() =
      requireNotNull(
        ProvisionNetworkType.fromName(arguments?.getString(ARG_PROVISION_NETWORK_TYPE))
      )

  interface Callback {
    fun onNetworkCredentialsEntered(networkCredentials: NetworkCredentialsParcelable)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layoutRes =
      when (networkType) {
        ProvisionNetworkType.WIFI -> R.layout.enter_wifi_network_fragment
        ProvisionNetworkType.THREAD -> R.layout.enter_thread_network_fragment
      }

    return inflater.inflate(layoutRes, container, false).apply {
      if (networkType == ProvisionNetworkType.WIFI) {
        val ssidEd: EditText = findViewById(R.id.ssidEd)
        val pwdEd: EditText = findViewById(R.id.pwdEd)

        restoreSavedWiFiCredentials(ssidEd, pwdEd)
        attachWhitespaceSanitizer(ssidEd)
        attachWhitespaceSanitizer(pwdEd)
        attachSavedWiFiClearListener(ssidEd, pwdEd)
      } else {
        val channelEd: EditText = findViewById(R.id.channelEd)
        val panIdEd: EditText = findViewById(R.id.panIdEd)
        val xpanIdEd: EditText = findViewById(R.id.xpanIdEd)
        val masterKeyEd: EditText = findViewById(R.id.masterKeyEd)

        restoreSavedThreadCredentials(channelEd, panIdEd, xpanIdEd, masterKeyEd)
        attachWhitespaceSanitizer(channelEd)
        attachWhitespaceSanitizer(panIdEd)
        attachWhitespaceSanitizer(xpanIdEd)
        attachWhitespaceSanitizer(masterKeyEd)
        attachSavedThreadClearListener(channelEd, panIdEd, xpanIdEd, masterKeyEd)
      }

      val saveNetworkBtn: Button = findViewById(R.id.saveNetworkBtn)
      saveNetworkBtn.setOnClickListener { onSaveNetworkClicked(this) }
    }
  }

  private fun onSaveNetworkClicked(view: View) {
    if (networkType == ProvisionNetworkType.WIFI) {
      saveWiFiNetwork(view)
    } else {
      saveThreadNetwork(view)
    }
  }

  private fun saveWiFiNetwork(view: View) {
    val ssidEd: EditText = view.findViewById(R.id.ssidEd)
    val pwdEd: EditText = view.findViewById(R.id.pwdEd)
    val ssid = ssidEd.text?.toString()?.filterNot { it.isWhitespace() }
    val pwd = pwdEd.text?.toString()?.filterNot { it.isWhitespace() }

    if (ssidEd.text.toString() != ssid) {
      updateField(ssidEd, ssid.orEmpty())
    }

    if (pwdEd.text.toString() != pwd) {
      updateField(pwdEd, pwd.orEmpty())
    }

    if (ssid.isNullOrBlank() || pwd.isNullOrBlank()) {
      Toast.makeText(requireContext(), "Ssid and password required.", Toast.LENGTH_SHORT).show()
      return
    }

    val networkCredentials =
      NetworkCredentialsParcelable.forWiFi(
        NetworkCredentialsParcelable.WiFiCredentials(ssid, pwd)
      )
    persistWiFiCredentials(ssid, pwd)
    FragmentUtil.getHost(this, Callback::class.java)
      ?.onNetworkCredentialsEntered(networkCredentials)
  }

  private fun restoreSavedWiFiCredentials(ssidEd: EditText, pwdEd: EditText) {
    val prefs = getPrefs()
    val savedSsid = prefs.getString(WIFI_SSID_PREFS_KEY, null)
    val savedPassword = prefs.getString(WIFI_PASSWORD_PREFS_KEY, null)

    if (savedSsid != null) {
      updateField(ssidEd, savedSsid)
    }

    if (savedPassword != null) {
      updateField(pwdEd, savedPassword)
    }
  }

  private fun persistWiFiCredentials(ssid: String, password: String) {
    getPrefs()
      .edit()
      .putString(WIFI_SSID_PREFS_KEY, ssid)
      .putString(WIFI_PASSWORD_PREFS_KEY, password)
      .apply()
  }

  private fun clearSavedWiFiCredentials() {
    getPrefs()
      .edit()
      .remove(WIFI_SSID_PREFS_KEY)
      .remove(WIFI_PASSWORD_PREFS_KEY)
      .apply()
  }

  private fun restoreSavedThreadCredentials(
    channelEd: EditText,
    panIdEd: EditText,
    xpanIdEd: EditText,
    masterKeyEd: EditText
  ) {
    val prefs = getPrefs()
    val savedChannel = prefs.getString(THREAD_CHANNEL_PREFS_KEY, null)
    val savedPanId = prefs.getString(THREAD_PAN_ID_PREFS_KEY, null)
    val savedXpanId = prefs.getString(THREAD_XPAN_ID_PREFS_KEY, null)
    val savedMasterKey = prefs.getString(THREAD_MASTER_KEY_PREFS_KEY, null)

    if (savedChannel != null) {
      updateField(channelEd, savedChannel)
    }

    if (savedPanId != null) {
      updateField(panIdEd, savedPanId)
    }

    if (savedXpanId != null) {
      updateField(xpanIdEd, savedXpanId)
    }

    if (savedMasterKey != null) {
      updateField(masterKeyEd, savedMasterKey)
    }
  }

  private fun persistThreadCredentials(
    channel: String,
    panId: String,
    xpanId: String,
    masterKey: String
  ) {
    getPrefs()
      .edit()
      .putString(THREAD_CHANNEL_PREFS_KEY, channel)
      .putString(THREAD_PAN_ID_PREFS_KEY, panId)
      .putString(THREAD_XPAN_ID_PREFS_KEY, xpanId)
      .putString(THREAD_MASTER_KEY_PREFS_KEY, masterKey)
      .apply()
  }

  private fun clearSavedThreadCredentials() {
    getPrefs()
      .edit()
      .remove(THREAD_CHANNEL_PREFS_KEY)
      .remove(THREAD_PAN_ID_PREFS_KEY)
      .remove(THREAD_XPAN_ID_PREFS_KEY)
      .remove(THREAD_MASTER_KEY_PREFS_KEY)
      .apply()
  }

  private fun updateField(editText: EditText, value: String) {
    isUpdatingFields = true
    editText.setText(value)
    editText.setSelection(value.length)
    isUpdatingFields = false
  }

  private fun attachWhitespaceSanitizer(editText: EditText) {
    editText.addTextChangedListener(
      object : TextWatcher {
        private var isSanitizing = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
          if (isSanitizing || isUpdatingFields || s == null) {
            return
          }

          val sanitized = s.toString().filterNot { it.isWhitespace() }
          if (sanitized == s.toString()) {
            return
          }

          isSanitizing = true
          updateField(editText, sanitized)
          isSanitizing = false
        }
      }
    )
  }

  private fun attachSavedWiFiClearListener(ssidEd: EditText, pwdEd: EditText) {
    val clearListener =
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
          if (isUpdatingFields) {
            return
          }

          if (ssidEd.text.isNullOrEmpty() && pwdEd.text.isNullOrEmpty()) {
            clearSavedWiFiCredentials()
          }
        }
      }

    ssidEd.addTextChangedListener(clearListener)
    pwdEd.addTextChangedListener(clearListener)
  }

  private fun attachSavedThreadClearListener(
    channelEd: EditText,
    panIdEd: EditText,
    xpanIdEd: EditText,
    masterKeyEd: EditText
  ) {
    val clearListener =
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
          if (isUpdatingFields) {
            return
          }

          if (
            channelEd.text.isNullOrEmpty() &&
              panIdEd.text.isNullOrEmpty() &&
              xpanIdEd.text.isNullOrEmpty() &&
              masterKeyEd.text.isNullOrEmpty()
          ) {
            clearSavedThreadCredentials()
          }
        }
      }

    channelEd.addTextChangedListener(clearListener)
    panIdEd.addTextChangedListener(clearListener)
    xpanIdEd.addTextChangedListener(clearListener)
    masterKeyEd.addTextChangedListener(clearListener)
  }

  private fun saveThreadNetwork(view: View) {
    val channelEd: EditText = view.findViewById(R.id.channelEd)
    val panIdEd: EditText = view.findViewById(R.id.panIdEd)
    val xpanIdEd: EditText = view.findViewById(R.id.xpanIdEd)
    val masterKeyEd: EditText = view.findViewById(R.id.masterKeyEd)
    val channelStr = channelEd.text?.toString()?.filterNot { it.isWhitespace() }
    val panIdStr =
      panIdEd.text?.toString()?.filterNot { it.isWhitespace() }?.uppercase(Locale.US)
    val xpanIdInput =
      xpanIdEd.text?.toString()?.filterNot { it.isWhitespace() }?.uppercase(Locale.US)
    val masterKeyInput =
      masterKeyEd.text?.toString()?.filterNot { it.isWhitespace() }?.uppercase(Locale.US)

    if (channelEd.text.toString() != channelStr) {
      updateField(channelEd, channelStr.orEmpty())
    }

    if (panIdEd.text.toString() != panIdStr) {
      updateField(panIdEd, panIdStr.orEmpty())
    }

    if (xpanIdEd.text.toString() != xpanIdInput) {
      updateField(xpanIdEd, xpanIdInput.orEmpty())
    }

    if (masterKeyEd.text.toString() != masterKeyInput) {
      updateField(masterKeyEd, masterKeyInput.orEmpty())
    }

    if (channelStr.isNullOrBlank()) {
      Toast.makeText(requireContext(), "Channel is empty", Toast.LENGTH_SHORT).show()
      return
    }

    if (panIdStr.isNullOrBlank()) {
      Toast.makeText(requireContext(), "PAN ID is empty", Toast.LENGTH_SHORT).show()
      return
    }

    if (xpanIdInput.isNullOrBlank()) {
      Toast.makeText(requireContext(), "XPAN ID is empty", Toast.LENGTH_SHORT).show()
      return
    }

    val xpanIdStr = xpanIdInput.filterNot { c -> c == ':' }
    if (xpanIdStr.length != NUM_XPANID_BYTES * 2) {
      Toast.makeText(requireContext(), "Extended PAN ID is invalid", Toast.LENGTH_SHORT).show()
      return
    }

    if (masterKeyInput.isNullOrBlank()) {
      Toast.makeText(requireContext(), "Master Key is empty", Toast.LENGTH_SHORT).show()
      return
    }

    val masterKeyStr = masterKeyInput.filterNot { c -> c == ':' }
    if (masterKeyStr.length != NUM_MASTER_KEY_BYTES * 2) {
      Toast.makeText(requireContext(), "Master key is invalid", Toast.LENGTH_SHORT).show()
      return
    }

    val operationalDataset =
      makeThreadOperationalDataset(
        channelStr.toString().toInt(),
        panIdStr.toString().toInt(16),
        xpanIdStr.hexToByteArray(),
        masterKeyStr.hexToByteArray()
      )

    val networkCredentials =
      NetworkCredentialsParcelable.forThread(
        NetworkCredentialsParcelable.ThreadCredentials(operationalDataset)
      )
    persistThreadCredentials(
      channelStr,
      panIdStr,
      xpanIdInput,
      masterKeyInput
    )
    FragmentUtil.getHost(this, Callback::class.java)
      ?.onNetworkCredentialsEntered(networkCredentials)
  }

  private fun makeThreadOperationalDataset(
    channel: Int,
    panId: Int,
    xpanId: ByteArray,
    masterKey: ByteArray
  ): ByteArray {
    // channel
    var dataset = byteArrayOf(TYPE_CHANNEL.toByte(), NUM_CHANNEL_BYTES.toByte())
    dataset += 0x00.toByte() // Channel Page 0.
    dataset += (channel.shr(8) and 0xFF).toByte()
    dataset += (channel and 0xFF).toByte()

    // PAN ID
    dataset += TYPE_PANID.toByte()
    dataset += NUM_PANID_BYTES.toByte()
    dataset += (panId.shr(8) and 0xFF).toByte()
    dataset += (panId and 0xFF).toByte()

    // Extended PAN ID
    dataset += TYPE_XPANID.toByte()
    dataset += NUM_XPANID_BYTES.toByte()
    dataset += xpanId

    // Network Master Key
    dataset += TYPE_MASTER_KEY.toByte()
    dataset += NUM_MASTER_KEY_BYTES.toByte()
    dataset += masterKey

    return dataset
  }

  private fun String.hexToByteArray(): ByteArray {
    return chunked(2).map { byteStr -> byteStr.toUByte(16).toByte() }.toByteArray()
  }

  private fun getPrefs() =
    requireContext().getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE)

  companion object {
    private const val TAG = "EnterNetworkFragment"
    private const val ARG_PROVISION_NETWORK_TYPE = "provision_network_type"
    private const val NETWORK_COMMISSIONING_CLUSTER_ENDPOINT = 0
    private const val PREFERENCE_FILE_KEY = "com.google.chip.chiptool.PREFERENCE_FILE_KEY"
    private const val WIFI_SSID_PREFS_KEY = "wifi_ssid"
    private const val WIFI_PASSWORD_PREFS_KEY = "wifi_password"
    private const val THREAD_CHANNEL_PREFS_KEY = "thread_channel"
    private const val THREAD_PAN_ID_PREFS_KEY = "thread_pan_id"
    private const val THREAD_XPAN_ID_PREFS_KEY = "thread_xpan_id"
    private const val THREAD_MASTER_KEY_PREFS_KEY = "thread_master_key"

    private const val NUM_CHANNEL_BYTES = 3
    private const val NUM_PANID_BYTES = 2
    private const val NUM_XPANID_BYTES = 8
    private const val NUM_MASTER_KEY_BYTES = 16
    private const val TYPE_CHANNEL = 0 // Type of Thread Channel TLV.
    private const val TYPE_PANID = 1 // Type of Thread PAN ID TLV.
    private const val TYPE_XPANID = 2 // Type of Thread Extended PAN ID TLV.
    private const val TYPE_MASTER_KEY = 5 // Type of Thread Network Master Key TLV.

    fun newInstance(provisionNetworkType: ProvisionNetworkType): EnterNetworkFragment {
      return EnterNetworkFragment().apply {
        arguments =
          Bundle(1).apply { putString(ARG_PROVISION_NETWORK_TYPE, provisionNetworkType.name) }
      }
    }
  }
}
