package com.eventosfamiliarestv.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eventosfamiliarestv.app.databinding.ItemChannelBinding

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

    inner class Holder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel, position: Int) {
            binding.channelNumber.text = (position + 1).toString()
            binding.channelName.text = channel.nombre.ifBlank { "Canal ${position + 1}" }
            binding.channelState.text = if (channel.activo && channel.url.isUsable()) "DISPONIBLE" else "PRÓXIMAMENTE"
            binding.channelCard.alpha = if (channel.activo && channel.url.isUsable()) 1f else 0.62f
            binding.channelCard.setOnClickListener { onClick(channel) }
            binding.channelCard.setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f).setDuration(120).start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = channels.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(channels[position], position)
}

fun String.isUsable(): Boolean = isNotBlank() && !equals("pendiente", true) && (startsWith("http://") || startsWith("https://"))
