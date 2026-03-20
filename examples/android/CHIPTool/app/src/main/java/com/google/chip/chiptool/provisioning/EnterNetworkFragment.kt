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

/**
 * Fragment to collect Wi-Fi network information from user and send it to device being provisioned.
 */
class EnterNetworkFragment : Fragment() {
  private var isUpdatingWiFiFields = false

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
      updateWiFiField(ssidEd, ssid.orEmpty())
    }

    if (pwdEd.text.toString() != pwd) {
      updateWiFiField(pwdEd, pwd.orEmpty())
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
      updateWiFiField(ssidEd, savedSsid)
    }

    if (savedPassword != null) {
      updateWiFiField(pwdEd, savedPassword)
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

  private fun updateWiFiField(editText: EditText, value: String) {
    isUpdatingWiFiFields = true
    editText.setText(value)
    editText.setSelection(value.length)
    isUpdatingWiFiFields = false
  }

  private fun attachWhitespaceSanitizer(editText: EditText) {
    editText.addTextChangedListener(
      object : TextWatcher {
        private var isSanitizing = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
          if (isSanitizing || isUpdatingWiFiFields || s == null) {
            return
          }

          val sanitized = s.toString().filterNot { it.isWhitespace() }
          if (sanitized == s.toString()) {
            return
          }

          isSanitizing = true
          updateWiFiField(editText, sanitized)
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
          if (isUpdatingWiFiFields) {
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

  private fun saveThreadNetwork(view: View) {
    val channelEd: EditText = view.findViewById(R.id.channelEd)
    val panIdEd: EditText = view.findViewById(R.id.panIdEd)
    val xpanIdEd: EditText = view.findViewById(R.id.xpanIdEd)
    val masterKeyEd: EditText = view.findViewById(R.id.masterKeyEd)
    val channelStr = channelEd.text
    val panIdStr = panIdEd.text

    if (channelStr.isNullOrBlank()) {
      Toast.makeText(requireContext(), "Channel is empty", Toast.LENGTH_SHORT).show()
      return
    }

    if (panIdStr.isNullOrBlank()) {
      Toast.makeText(requireContext(), "PAN ID is empty", Toast.LENGTH_SHORT).show()
      return
    }

    if (xpanIdEd.text.isNullOrBlank()) {
      Toast.makeText(requireContext(), "XPAN ID is empty", Toast.LENGTH_SHORT).show()
      return
    }

    val xpanIdStr = xpanIdEd.text.toString().filterNot { c -> c == ':' }
    if (xpanIdStr.length != NUM_XPANID_BYTES * 2) {
      Toast.makeText(requireContext(), "Extended PAN ID is invalid", Toast.LENGTH_SHORT).show()
      return
    }

    if (masterKeyEd.text.isNullOrBlank()) {
      Toast.makeText(requireContext(), "Master Key is empty", Toast.LENGTH_SHORT).show()
      return
    }

    val masterKeyStr = masterKeyEd.text.toString().filterNot { c -> c == ':' }
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
