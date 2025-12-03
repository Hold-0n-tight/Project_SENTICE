package com.example.voiptest

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val itemList = arrayListOf<MessageModel>()

    fun addItem(item: MessageModel){
        itemList.add(item)
        notifyItemInserted(itemList.size - 1)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when(viewType){
            TYPE_SENDER -> SenderViewHolder.create(parent)
            else -> ReceiverViewHolder.create(parent)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder){
            is SenderViewHolder -> holder.bindItem(itemList[position] as MessageModel.SenderMessage)
            is ReceiverViewHolder -> holder.bindItem(itemList[position] as MessageModel.ReceiverMessage)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    override fun getItemViewType(position: Int): Int {
        return when(itemList[position]){
            is MessageModel.SenderMessage -> TYPE_SENDER
            is MessageModel.ReceiverMessage -> TYPE_RECEIVER
            else -> -1
        }

    }
    companion object{
        const val TYPE_SENDER = 0
        const val TYPE_RECEIVER = 1
    }

}