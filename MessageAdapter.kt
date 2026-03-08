package com.rehaan.bluetoothchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.rehaan.bluetoothchat.R
import com.rehaan.bluetoothchat.databinding.ItemMessageReceivedBinding
import com.rehaan.bluetoothchat.databinding.ItemMessageSentBinding
import com.rehaan.bluetoothchat.domain.model.Message
import com.rehaan.bluetoothchat.domain.model.MessageDirection
import com.rehaan.bluetoothchat.domain.model.MessageType
import com.rehaan.bluetoothchat.utils.toFormattedTime
import com.rehaan.bluetoothchat.utils.toReadableFileSize
import java.io.File

private const val VIEW_TYPE_SENT = 1
private const val VIEW_TYPE_RECEIVED = 2

class MessageAdapter(
    private val onVoicePlayClicked: (filePath: String, messageId: Long) -> Unit,
    private val onImageClicked: (filePath: String) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).direction == MessageDirection.SENT)
            VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(
                ItemMessageSentBinding.inflate(inflater, parent, false)
            )
            else -> ReceivedViewHolder(
                ItemMessageReceivedBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    inner class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvTimestamp.text = message.timestamp.toFormattedTime()

            when (message.type) {
                MessageType.TEXT -> {
                    showText(message.content)
                }
                MessageType.IMAGE -> {
                    showImage(message.filePath)
                }
                MessageType.VOICE -> {
                    showVoice(message)
                }
                MessageType.FILE, MessageType.DOCUMENT -> {
                    showFile(message)
                }
            }
        }

        private fun showText(text: String) {
            binding.tvMessage.visibility = View.VISIBLE
            binding.layoutFile.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.tvMessage.text = text
        }

        private fun showImage(filePath: String?) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutFile.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = if (filePath != null) View.VISIBLE else View.GONE

            if (filePath != null) {
                Glide.with(binding.root.context)
                    .load(File(filePath))
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(binding.ivImage)
                binding.ivImage.setOnClickListener { onImageClicked(filePath) }
            }
        }

        private fun showVoice(message: Message) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutFile.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.layoutVoice.visibility = View.VISIBLE
            binding.tvVoiceDuration.text = "${message.duration}s"
            binding.btnVoicePlay.setOnClickListener {
                message.filePath?.let { onVoicePlayClicked(it, message.id) }
            }
        }

        private fun showFile(message: Message) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.layoutFile.visibility = View.VISIBLE
            binding.tvFileName.text = message.fileName ?: message.content
            binding.tvFileSize.text = message.fileSize.toReadableFileSize()
        }
    }

    inner class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvTimestamp.text = message.timestamp.toFormattedTime()

            when (message.type) {
                MessageType.TEXT -> {
                    showText(message.content)
                }
                MessageType.IMAGE -> {
                    showImage(message.filePath)
                }
                MessageType.VOICE -> {
                    showVoice(message)
                }
                MessageType.FILE, MessageType.DOCUMENT -> {
                    showFile(message)
                }
            }
        }

        private fun showText(text: String) {
            binding.tvMessage.visibility = View.VISIBLE
            binding.layoutFile.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.tvMessage.text = text
        }

        private fun showImage(filePath: String?) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutFile.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = if (filePath != null) View.VISIBLE else View.GONE

            if (filePath != null) {
                Glide.with(binding.root.context)
                    .load(File(filePath))
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(binding.ivImage)
                binding.ivImage.setOnClickListener { onImageClicked(filePath) }
            }
        }

        private fun showVoice(message: Message) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutFile.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.layoutVoice.visibility = View.VISIBLE
            binding.tvVoiceDuration.text = "${message.duration}s"
            binding.btnVoicePlay.setOnClickListener {
                message.filePath?.let { onVoicePlayClicked(it, message.id) }
            }
        }

        private fun showFile(message: Message) {
            binding.tvMessage.visibility = View.GONE
            binding.layoutVoice.visibility = View.GONE
            binding.ivImage.visibility = View.GONE
            binding.layoutFile.visibility = View.VISIBLE
            binding.tvFileName.text = message.fileName ?: message.content
            binding.tvFileSize.text = message.fileSize.toReadableFileSize()
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
}
