package com.taizi.domain.model

/**
 * Which libretro cores can run each system.
 *
 * Core ids are the file stem RetroArch loads, i.e. `<core>_libretro_android.so`
 * in the frontend's `cores` directory. Order is preference (best first). This
 * table is effectively static — the libretro core set for these platforms has
 * been stable for years — so it lives here instead of being discovered at
 * runtime. The repository filters it down to cores actually installed when it
 * can read RetroArch's cores directory.
 */
object RetroArchCores {

    private val bySystem: Map<String, List<String>> = mapOf(
        // ---------- Nintendo ----------
        "gb" to listOf("gambatte", "sameboy", "mesen-s", "tgbdual"),
        "gbc" to listOf("gambatte", "sameboy", "mesen-s", "tgbdual"),
        "gba" to listOf("mgba", "gpsp", "vba_next"),
        "nes" to listOf("fceumm", "nestopia", "mesen", "quicknes"),
        "fds" to listOf("fceumm", "nestopia", "mesen"),
        "snes" to listOf("snes9x", "snes9x2010", "bsnes", "bsnes_hd_beta", "mesen-s"),
        "n64" to listOf("mupen64plus_next", "mupen64plus_next_gles3", "parallel_n64"),
        "gamecube" to listOf("dolphin"),
        "wii" to listOf("dolphin"),
        "nds" to listOf("melonds", "desmume", "desmume2015"),
        "3ds" to listOf("citra"),
        "virtualboy" to listOf("mednafen_vb"),
        "pokemini" to listOf("pokemini"),
        "gameandwatch" to listOf("gw"),

        // ---------- Sony ----------
        "psx" to listOf("pcsx_rearmed", "mednafen_psx", "mednafen_psx_hw", "swanstation"),
        "ps2" to listOf("pcsx2"),
        "psp" to listOf("ppsspp"),

        // ---------- Sega ----------
        "genesis" to listOf("genesis_plus_gx", "picodrive", "blastem"),
        "sms" to listOf("genesis_plus_gx", "picodrive", "smsplus"),
        "gamegear" to listOf("genesis_plus_gx", "picodrive"),
        "sg1000" to listOf("genesis_plus_gx", "bluemsx"),
        "sega32x" to listOf("picodrive"),
        "segacd" to listOf("genesis_plus_gx", "picodrive"),
        "saturn" to listOf("kronos", "mednafen_saturn", "yabause"),
        "dc" to listOf("flycast"),
        "naomi" to listOf("flycast"),

        // ---------- NEC ----------
        "pce" to listOf("mednafen_pce_fast", "mednafen_pce"),
        "supergrafx" to listOf("mednafen_supergrafx", "mednafen_pce"),
        "pcfx" to listOf("mednafen_pcfx"),

        // ---------- Atari ----------
        "atari2600" to listOf("stella", "stella2014"),
        "atari5200" to listOf("atari800"),
        "atari7800" to listOf("prosystem"),
        "atari800" to listOf("atari800"),
        "atarist" to listOf("hatari"),
        "lynx" to listOf("handy"),
        "jaguar" to listOf("virtual_jaguar"),

        // ---------- SNK ----------
        "neogeo" to listOf("fbneo", "mame2003_plus", "mame"),
        "neogeocd" to listOf("neocd", "fbneo"),
        "ngpc" to listOf("mednafen_ngp", "race"),
        "wonderswan" to listOf("mednafen_wswan"),

        // ---------- Arcade ----------
        "mame" to listOf("mame", "mame2003_plus", "mame2010", "fbneo"),
        "fbneo" to listOf("fbneo", "mame2003_plus"),
        "cps1" to listOf("fbneo", "mame2003_plus", "mame"),
        "cps2" to listOf("fbneo", "mame2003_plus", "mame"),
        "cps3" to listOf("fbneo", "mame"),
        "model2" to listOf("mame"),
        "daphne" to listOf("daphne"),

        // ---------- Commodore / Amiga ----------
        "c64" to listOf("vice_x64", "vice_x64sc"),
        "c128" to listOf("vice_x128"),
        "vic20" to listOf("vice_xvic"),
        "cplus4" to listOf("vice_xplus4"),
        "pet" to listOf("vice_xpet"),
        "amiga" to listOf("puae", "puae2021", "uae4arm"),

        // ---------- Amstrad / Sinclair ----------
        "amstradcpc" to listOf("cap32", "crocods"),
        "amstradgx4000" to listOf("cap32", "crocods"),
        "zxspectrum" to listOf("fuse"),
        "zx81" to listOf("81", "fuse"),

        // ---------- MSX ----------
        "msx" to listOf("bluemsx", "fmsx"),
        "msx2" to listOf("bluemsx", "fmsx"),

        // ---------- Apple / BBC / Acorn ----------
        "apple2" to listOf("mame2003_plus", "mame"),
        "apple2gs" to listOf("mame"),
        "archimedes" to listOf("mame"),
        "bbcmicro" to listOf("mame"),
        "electron" to listOf("mame"),

        // ---------- Japanese PCs ----------
        "pc88" to listOf("quasi88"),
        "pc98" to listOf("np2kai", "nekop2"),
        "x1" to listOf("x1"),
        "x68000" to listOf("px68k"),
        "fm7" to listOf("mame"),
        "fmtowns" to listOf("mame"),

        // ---------- DOS / PC ----------
        "dos" to listOf("dosbox_pure", "dosbox_svn"),
        "scummvm" to listOf("scummvm"),

        // ---------- Misc consoles / handhelds ----------
        "colecovision" to listOf("bluemsx", "gearcoleco"),
        "intellivision" to listOf("freeintv"),
        "3do" to listOf("opera", "4do"),
        "vectrex" to listOf("vecx"),
        "odyssey2" to listOf("o2em"),
        "channelf" to listOf("freechaf"),
        "supervision" to listOf("potator"),
        "gamecom" to listOf("mame"),
        "megaduck" to listOf("sameduck"),
        "gamate" to listOf("mame"),
        "gamepock" to listOf("mame"),
        "cdi" to listOf("same_cdi", "mame"),
        "supracan" to listOf("mame"),
        "mac" to listOf("minivmac"),
        "palm" to listOf("mu"),
        "ti99" to listOf("ti99", "mame"),
        "oric" to listOf("mame"),
        "thomson" to listOf("theodore"),
        "samcoupe" to listOf("simcoupe"),
        "trs80" to listOf("mame"),

        // ---------- Fantasy consoles ----------
        "pico8" to listOf("fake08"),
        "tic80" to listOf("tic80"),
        "wasm4" to listOf("wasm4"),
        "vircon32" to listOf("vircon32"),
        "chip8" to listOf("chip8"),

        // ---------- Other ----------
        "arduboy" to listOf("arduous"),
        "uzebox" to listOf("uzem"),
        "vmu" to listOf("vemulator"),
        "scv" to listOf("mame"),
        "advision" to listOf("mame"),
        "crvision" to listOf("mame"),
        "vc4000" to listOf("mame"),
        "pv1000" to listOf("mame"),
        "apfm1000" to listOf("mame"),
        "arcadia" to listOf("mame"),
        "astrocade" to listOf("mame"),
        "gametank" to listOf("gametank"),
        "gp32" to listOf("mame"),
        "vsmile" to listOf("mame"),
        "socrates" to listOf("mame"),
        "gmaster" to listOf("mame"),
        "multivision" to listOf("mame"),
        "j2me" to listOf("freej2me"),
        "openbor" to listOf("openbor"),
        "doom" to listOf("prboom"),
        "quake" to listOf("tyrquake")
    )

    fun forSystem(systemId: String): List<String> = bySystem[systemId].orEmpty()
}
