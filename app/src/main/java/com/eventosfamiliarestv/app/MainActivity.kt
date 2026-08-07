package com.eventosfamiliarestv.app

import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val channels = MutableList(6) { index -> Channel(nombre = "Canal ${index + 1}") }
    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val columns = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        adapter = ChannelAdapter(channels, ::handleChannel)
        binding.channelGrid.layoutManager = GridLayoutManager(this, columns)
        binding.channelGrid.adapter = adapter
        loadChannels()
    }

    private fun loadChannels() {
        FirebaseDatabase.getInstance().reference.child("canales")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (index in 1..6) {
                        snapshot.child("canal$index").getValue(Channel::class.java)?.let {
                            channels[index - 1] = it.copy(nombre = it.nombre.ifBlank { "Canal $index" })
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
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_URL, channel.url)
                        putExtra(PlayerActivity.EXTRA_NAME, channel.nombre)
                    })
                } else {
                    input.error = getString(R.string.wrong_password)
                    input.selectAll()
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
