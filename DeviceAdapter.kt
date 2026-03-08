package com.rehaan.bluetoothchat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rehaan.bluetoothchat.databinding.ItemDeviceBinding
import com.rehaan.bluetoothchat.domain.model.DeviceInfo

class DeviceAdapter(
    private val onDeviceClicked: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceInfo) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceAddress.text = device.address
            binding.tvDeviceStatus.text = if (device.isPaired) "Paired" else "Available"
            binding.ivDeviceIcon.setImageResource(
                if (device.isPaired)
                    com.rehaan.bluetoothchat.R.drawable.ic_bluetooth_connected
                else
                    com.rehaan.bluetoothchat.R.drawable.ic_bluetooth
            )
            binding.root.setOnClickListener { onDeviceClicked(device) }
        }
    }
}

class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
    override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo) =
        oldItem.address == newItem.address
    override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo) =
        oldItem == newItem
}
