package com.taizi.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

object DeviceDetection {

    fun isAnbernicRGDS(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("anbernic")) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.SOC_MODEL.equals("RK3568", ignoreCase = true) &&
                Build.HARDWARE.equals("rk30board", ignoreCase = true)) return true
        }

        val model = Build.MODEL.lowercase()
        if (model.contains("rg") && model.contains("ds")) return true

        val product = Build.PRODUCT.lowercase()
        if (product.contains("rgds") || product.contains("rg_ds") || product.contains("rg-ds")) return true

        return false
    }

    fun findSecondaryDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null

        for (display in dm.displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY) continue

            if (display.state != Display.STATE_ON) continue

            if (display.flags and Display.FLAG_PRIVATE != 0) continue

            if (display.flags and Display.FLAG_PRESENTATION != 0) return display
        }

        return null
    }

    fun hasMultipleDisplays(context: Context): Boolean {
        return findSecondaryDisplay(context) != null
    }
}
