package com.example.voiptest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voiptest.databinding.ItemReceiverChatBinding

class ReceiverViewHolder(private val binding: ItemReceiverChatBinding): RecyclerView.ViewHolder(binding.root) {
    fun bindItem(receiverMessage: MessageModel.ReceiverMessage){
        binding.tvMessage.text = receiverMessage.message
    }

    companion object {
        fun create(parent: ViewGroup): ReceiverViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = ItemReceiverChatBinding.inflate(layoutInflater, parent, false)
            return ReceiverViewHolder(view)
        }
    }
}