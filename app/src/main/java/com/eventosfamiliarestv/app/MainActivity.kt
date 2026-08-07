package com.eventosfamiliarestv.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.eventosfamiliarestv.app.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val channels = MutableList(6) { index -> Channel(nombre = "Canal ${index + 1}") }
    private lateinit var adapter: ChannelAdapter
    private val database by lazy { FirebaseDatabase.getInstance() }
    private val deviceId by lazy {
        sha256(Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device")
    }
    private var serverTimeOffset = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val columns = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        adapter = ChannelAdapter(channels, ::handleChannel)
        binding.channelGrid.layoutManager = GridLayoutManager(this, columns)
        binding.channelGrid.adapter = adapter
        database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) = Unit
        })
        loadChannels()
    }

    private fun loadChannels() {
        database.reference.child("canales")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (index in 1..6) {
                        snapshot.child("canal$index").getValue(Channel::class.java)?.let {
                            channels[index - 1] = it.copy(id = "canal$index", nombre = it.nombre.ifBlank { "Canal $index" })
                        }
                    }
                    adapter.notifyDataSetChanged()
                    binding.statusText.text = "6 CANALES"
                    binding.channelGrid.post { binding.channelGrid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.statusText.text = getString(R.string.connection_error)
                }
            })
    }

    private fun handleChannel(channel: Channel) {
        if (!channel.activo || !channel.url.isUsable()) {
            Toast.makeText(this, R.string.unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = getString(R.string.access_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setPadding(48, 24, 48, 24)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.access_title, channel.nombre))
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.access, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (sha256(input.text.toString()) == channel.claveHash.lowercase()) {
                    dialog.dismiss()
                    reserveSession(channel)
                } else {
                    input.error = getString(R.string.wrong_password)
                    input.selectAll()
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun reserveSession(channel: Channel) {
        binding.statusText.text = getString(R.string.checking_access)
        val sessionRef = database.reference.child("sesiones").child(channel.id)
        val now = System.currentTimeMillis() + serverTimeOffset

        sessionRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentDevice = currentData.child("dispositivo").getValue(String::class.java)
                val lastActivity = currentData.child("ultimaActividad").getValue(Long::class.java) ?: 0L
                val expired = now - lastActivity > SESSION_TIMEOUT_MS

                if (currentDevice == null || currentDevice == deviceId || expired) {
                    currentData.value = mapOf(
                        "dispositivo" to deviceId,
                        "ultimaActividad" to now
                    )
                    return Transaction.success(currentData)
                }
                return Transaction.abort()
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                binding.statusText.text = "6 CANALES"
                when {
                    error != null -> Toast.makeText(this@MainActivity, R.string.session_error, Toast.LENGTH_LONG).show()
                    !committed -> Toast.makeText(this@MainActivity, R.string.channel_in_use, Toast.LENGTH_LONG).show()
                    else -> startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_URL, channel.url)
                        putExtra(PlayerActivity.EXTRA_NAME, channel.nombre)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id)
                        putExtra(PlayerActivity.EXTRA_DEVICE_ID, deviceId)
                    })
                }
            }
        })
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val SESSION_TIMEOUT_MS = 120_000L
    }
}
