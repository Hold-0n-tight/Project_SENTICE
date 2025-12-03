package com.example.voiptest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voiptest.databinding.ItemSenderChatBinding

class SenderViewHolder(private val binding: ItemSenderChatBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bindItem(senderMessage: MessageModel.SenderMessage) {
        binding.tvMessage.text = senderMessage.message
    }

    companion object {
        fun create(parent: ViewGroup): SenderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = ItemSenderChatBinding.inflate(layoutInflater, parent, false)
            return SenderViewHolder(view)
        }
    }

}