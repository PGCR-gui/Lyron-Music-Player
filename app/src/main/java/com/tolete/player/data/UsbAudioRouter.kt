package com.tolete.player.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Roteamento para DAC/fone USB.
 * No Android não há WASAPI exclusive de verdade; o melhor caminho de app é
 * [AudioTrack.setPreferredDevice] no USB + PCM float + buffer de qualidade.
 */
object UsbAudioRouter {

    fun findUsbOutput(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            val sinks = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
            val usb = sinks.filter { isUsbType(it.type) }
            // Prefere dispositivo (DAC) a headset USB genérico quando ambos existirem
            usb.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
                ?: usb.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                ?: usb.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun usbOutputs(context: Context): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink && isUsbType(it.type) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun label(device: AudioDeviceInfo?): String {
        if (device == null) return "Nenhum USB detectado"
        val name = device.productName?.toString()?.trim().orEmpty()
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            else -> "USB"
        }
        return if (name.isNotBlank()) "$type · $name" else type
    }

    fun isUsbType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET) return true
        // TYPE_USB_HEADSET = 22 on older, TYPE_USB_DEVICE = 21
        return false
    }

    /** Melhor taxa suportada pelo dispositivo próxima do pedido (ou a maior). */
    fun pickSampleRate(device: AudioDeviceInfo?, preferredHz: Int, fallbackHz: Int): Int {
        if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return if (preferredHz > 0) preferredHz else fallbackHz
        }
        val rates = try {
            device.sampleRates?.filter { it > 0 }?.sorted() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (rates.isEmpty()) return if (preferredHz > 0) preferredHz else fallbackHz
        if (preferredHz > 0) {
            rates.firstOrNull { it == preferredHz }?.let { return it }
            // mais próxima ≥ pedido, senão a mais alta
            rates.firstOrNull { it >= preferredHz }?.let { return it }
            return rates.last()
        }
        return rates.last()
    }
}
