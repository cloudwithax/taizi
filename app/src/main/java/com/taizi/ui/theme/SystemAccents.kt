package com.taizi.ui.theme

import androidx.compose.ui.graphics.Color

data class SystemAccent(
    val label: String,
    val primary: Color,
    val secondary: Color
)

private val Fallback = SystemAccent(
    label = "ROM",
    primary = Color(0xFFFF2E63),
    secondary = Color(0xFF6C1B36)
)

private val AccentMap: Map<String, SystemAccent> = mapOf(
    "gb"       to SystemAccent("GB",   Color(0xFF8BBF4A), Color(0xFF2E3B16)),
    "gbc"      to SystemAccent("GBC",  Color(0xFFE4572E), Color(0xFF4A1E10)),
    "gba"      to SystemAccent("GBA",  Color(0xFF5B4BFF), Color(0xFF1B1745)),
    "nds"      to SystemAccent("NDS",  Color(0xFFE11D48), Color(0xFF48101B)),
    "nes"      to SystemAccent("NES",  Color(0xFFB8262A), Color(0xFF400B0D)),
    "snes"     to SystemAccent("SNES", Color(0xFF6E3FC5), Color(0xFF25153F)),
    "n64"      to SystemAccent("N64",  Color(0xFF1FA350), Color(0xFF0C361B)),
    "gamecube" to SystemAccent("GC",   Color(0xFF6A3BBE), Color(0xFF22123D)),
    "gc"       to SystemAccent("GC",   Color(0xFF6A3BBE), Color(0xFF22123D)),
    "wii"      to SystemAccent("Wii",  Color(0xFFD8DEE9), Color(0xFF2F3540)),
    "md"       to SystemAccent("MD",   Color(0xFF2676DA), Color(0xFF0C2A4D)),
    "genesis"  to SystemAccent("MD",   Color(0xFF2676DA), Color(0xFF0C2A4D)),
    "megadrive" to SystemAccent("MD",  Color(0xFF2676DA), Color(0xFF0C2A4D)),
    "sms"      to SystemAccent("SMS",  Color(0xFF1E88E5), Color(0xFF0A2E4F)),
    "gg"       to SystemAccent("GG",   Color(0xFF0CB3B8), Color(0xFF053B3D)),
    "saturn"   to SystemAccent("SAT",  Color(0xFFE2A31C), Color(0xFF4A3609)),
    "dreamcast" to SystemAccent("DC",  Color(0xFFF28C28), Color(0xFF4F2D0C)),
    "psx"      to SystemAccent("PS1",  Color(0xFF7C8DA4), Color(0xFF1F2A38)),
    "ps1"      to SystemAccent("PS1",  Color(0xFF7C8DA4), Color(0xFF1F2A38)),
    "psp"      to SystemAccent("PSP",  Color(0xFF2E6BE6), Color(0xFF0C244E)),
    "ps2"      to SystemAccent("PS2",  Color(0xFF1C3D8F), Color(0xFF0A1638)),
    "arcade"   to SystemAccent("ARC",  Color(0xFFFFB300), Color(0xFF4A3200)),
    "mame"     to SystemAccent("MAME", Color(0xFFFFB300), Color(0xFF4A3200)),
    "neogeo"   to SystemAccent("NEO",  Color(0xFFE00020), Color(0xFF4A000B)),
    "pce"      to SystemAccent("PCE",  Color(0xFFE8821F), Color(0xFF4A2609)),
    "tg16"     to SystemAccent("TG16", Color(0xFFE8821F), Color(0xFF4A2609)),
    "atari2600" to SystemAccent("2600", Color(0xFFC02020), Color(0xFF3D0A0A)),
    "atari7800" to SystemAccent("7800", Color(0xFFB71C1C), Color(0xFF380808)),
    "lynx"     to SystemAccent("LYNX", Color(0xFFCC7A1A), Color(0xFF3D230A)),
    "jaguar"   to SystemAccent("JAG",  Color(0xFFCC2626), Color(0xFF3D0C0C)),
    "ngp"      to SystemAccent("NGP",  Color(0xFF263238), Color(0xFF10171B)),
    "ngpc"     to SystemAccent("NGPC", Color(0xFF263238), Color(0xFF10171B)),
    "wonderswan" to SystemAccent("WS", Color(0xFF455A64), Color(0xFF17222A)),
    "wsc"      to SystemAccent("WSC",  Color(0xFF455A64), Color(0xFF17222A)),
    "ws"       to SystemAccent("WS",   Color(0xFF455A64), Color(0xFF17222A)),
    "segacd"   to SystemAccent("SCD",  Color(0xFF2676DA), Color(0xFF0C2A4D)),
    "gamegear" to SystemAccent("GG",   Color(0xFF0CB3B8), Color(0xFF053B3D)),
    "mastersystem" to SystemAccent("SMS", Color(0xFF1E88E5), Color(0xFF0A2E4F)),
    "virtualboy" to SystemAccent("VB", Color(0xFFD32F2F), Color(0xFF4A0E0E)),
    "vb"       to SystemAccent("VB",   Color(0xFFD32F2F), Color(0xFF4A0E0E)),
    "colecovision" to SystemAccent("CV", Color(0xFF5D4037), Color(0xFF1E1510)),
    "coleco"   to SystemAccent("CV",   Color(0xFF5D4037), Color(0xFF1E1510)),
    "intellivision" to SystemAccent("INTV", Color(0xFF6D4C41), Color(0xFF231A15)),
    "intv"     to SystemAccent("INTV", Color(0xFF6D4C41), Color(0xFF231A15)),
    "vectrex"  to SystemAccent("VEC",  Color(0xFF546E7A), Color(0xFF1E2A2F)),
    "3do"      to SystemAccent("3DO",  Color(0xFF546E7A), Color(0xFF1E2A2F)),
    "dos"      to SystemAccent("DOS",  Color(0xFFA7A7A7), Color(0xFF262626)),
    "scummvm"  to SystemAccent("SVM",  Color(0xFFE85D75), Color(0xFF3D1922)),
    "c64"      to SystemAccent("C64",  Color(0xFF6C5CE7), Color(0xFF1F1A4A))
)

fun accentFor(systemId: String): SystemAccent {
    val key = systemId.lowercase().trim()
    return AccentMap[key] ?: Fallback
}
