package com.rehaan.bluetoothchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rehaan.bluetoothchat.databinding.ItemChatSessionBinding
import com.rehaan.bluetoothchat.domain.model.ChatSession
import com.rehaan.bluetoothchat.utils.toFormattedTime

class ChatSessionAdapter(
    private val onSessionClicked: (ChatSession) -> Unit,
    private val onSessionLongClicked: (ChatSession) -> Unit
) : ListAdapter<ChatSession, ChatSessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemChatSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemChatSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ChatSession) {
            binding.tvDeviceName.text = session.deviceName
            binding.tvLastMessage.text = session.lastMessage.ifEmpty { "Tap to connect" }
            binding.tvTime.text = if (session.lastMessageTime > 0)
                session.lastMessageTime.toFormattedTime()
            else ""

            if (session.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = if (session.unreadCount > 99) "99+" else session.unreadCount.toString()
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }

            val initials = session.deviceName.take(2).uppercase()
            binding.tvAvatar.text = initials

            binding.root.setOnClickListener { onSessionClicked(session) }
            binding.root.setOnLongClickListener {
                onSessionLongClicked(session)
                true
            }
        }
    }
}

class SessionDiffCallback : DiffUtil.ItemCallback<ChatSession>() {
    override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession) =
        oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession) =
        oldItem == newItem
}
