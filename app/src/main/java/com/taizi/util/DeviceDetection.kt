package com.taizi.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

object DeviceDetection {

    fun isAnbernicRGDS(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()

        if (manufacturer.contains("anbernic")) return true

        if (model.contains("rg") && model.contains("ds")) return true

        if (product.contains("rgds") || product.contains("rg_ds") || product.contains("rg-ds")) return true

        return false
    }

    fun findSecondaryDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null

        for (display in dm.displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY) continue

            if (display.state != Display.STATE_ON) continue

            if (display.flags and Display.FLAG_PRIVATE != 0) continue

            return display
        }

        return null
    }

    fun hasMultipleDisplays(context: Context): Boolean {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        return dm.displays.count { it.displayId != Display.DEFAULT_DISPLAY } > 0
    }
}
