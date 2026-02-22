package com.esde.emulatormanager.data

/**
 * Represents a BIOS or firmware file required by a system.
 *
 * @param filename The exact filename the emulator expects
 * @param notes    Optional short note (e.g. region, "optional")
 */
data class BiosFile(
    val filename: String,
    val notes: String? = null
)

/**
 * Static map of system IDs to their required BIOS / firmware files.
 * Only systems that actually require (or strongly benefit from) external BIOS files are listed.
 * Filenames are case-sensitive on most platforms.
 */
object SystemBios {

    private val biosFiles: Map<String, List<BiosFile>> = mapOf(

        // ── Nintendo ──────────────────────────────────────────────────────────

        // Famicom Disk System — REQUIRED by all cores (e.g. RetroArch fceumm/nestopia)
        "fds" to listOf(
            BiosFile("disksys.rom")
        ),

        // Game Boy Advance — REQUIRED by gpSP, optional for mGBA (improves accuracy)
        "gba" to listOf(
            BiosFile("gba_bios.bin", "optional for mGBA, required for gpSP")
        ),

        // Nintendo DS — optional for melonDS (open-source fallback built in)
        "nds" to listOf(
            BiosFile("bios7.bin", "ARM7 BIOS"),
            BiosFile("bios9.bin", "ARM9 BIOS"),
            BiosFile("firmware.bin")
        ),

        // GameCube — optional IPL.bin for boot animation / system fonts (Dolphin)
        "gc" to listOf(
            BiosFile("IPL.bin", "optional, for boot animation & system fonts")
        ),

        // ── Sony ──────────────────────────────────────────────────────────────

        // PlayStation 1 — at least one region variant required (DuckStation / PCSX-ReARMed)
        "psx" to listOf(
            BiosFile("scph1001.bin", "US"),
            BiosFile("scph5500.bin", "JP"),
            BiosFile("scph5501.bin", "US v2"),
            BiosFile("scph5502.bin", "EU"),
            BiosFile("scph7001.bin", "US v3, optional")
        ),

        // PlayStation 2 — REQUIRED by AetherSX2 / NetherSX2 (dump from own console)
        "ps2" to listOf(
            BiosFile("SCPH-10000.bin", "JP"),
            BiosFile("SCPH-30001.bin", "US"),
            BiosFile("SCPH-50000.bin", "EU/PAL"),
            BiosFile("SCPH-70012.bin", "US slim, common choice")
        ),

        // PlayStation Portable — no BIOS required; PPSSPP uses HLE
        // (omitted intentionally)

        // PlayStation Vita — Vita3K requires firmware installed via its own UI
        "psvita" to listOf(
            BiosFile("PSP2UPDAT.PUP", "install via Vita3K → File → Install Firmware")
        ),

        // ── Sega ──────────────────────────────────────────────────────────────

        // Sega CD / Mega CD — REQUIRED by Genesis Plus GX
        "segacd" to listOf(
            BiosFile("bios_CD_U.bin", "US"),
            BiosFile("bios_CD_E.bin", "EU"),
            BiosFile("bios_CD_J.bin", "JP")
        ),

        // Sega Saturn — optional but strongly recommended (Yabause / YabaSanshiro)
        "saturn" to listOf(
            BiosFile("sega_101.bin", "JP"),
            BiosFile("mpr-17933.bin", "US / EU")
        ),

        // Dreamcast — optional for Flycast (HLE works); dc/ subfolder in RetroArch/system
        "dreamcast" to listOf(
            BiosFile("dc_boot.bin"),
            BiosFile("dc_flash.bin")
        ),

        // ── NEC ───────────────────────────────────────────────────────────────

        // PC Engine CD / TurboGrafx-CD — REQUIRED for CD games (Beetle PCE)
        "pcenginecd" to listOf(
            BiosFile("syscard3.pce", "recommended"),
            BiosFile("syscard2.pce", "alternative"),
            BiosFile("syscard1.pce", "alternative")
        ),
        "tgcd" to listOf(
            BiosFile("syscard3.pce", "recommended"),
            BiosFile("syscard2.pce", "alternative"),
            BiosFile("syscard1.pce", "alternative")
        ),

        // PC-88 — REQUIRED by QUASI88 core
        "pc88" to listOf(
            BiosFile("n88.rom"),
            BiosFile("n88_0.rom"),
            BiosFile("n88_1.rom"),
            BiosFile("n88_2.rom"),
            BiosFile("n88_3.rom"),
            BiosFile("n88n.rom", "N88 BASIC")
        ),

        // PC-98 — REQUIRED by Neko Project II Kai; files go in system/np2kai/
        "pc98" to listOf(
            BiosFile("bios.rom"),
            BiosFile("font.rom"),
            BiosFile("itf.rom"),
            BiosFile("sound.rom"),
            BiosFile("2608_bd.wav"),
            BiosFile("2608_hh.wav"),
            BiosFile("2608_rim.wav"),
            BiosFile("2608_sd.wav"),
            BiosFile("2608_tom.wav"),
            BiosFile("2608_top.wav")
        ),

        // ── Arcade ────────────────────────────────────────────────────────────

        // Neo Geo — REQUIRED by FBNeo / MAME; place alongside roms or in BIOS folder
        "neogeo" to listOf(
            BiosFile("neogeo.zip", "place alongside roms or in BIOS folder")
        ),

        // FinalBurn Neo — Neo Geo games require the same BIOS zip
        "fbneo" to listOf(
            BiosFile("neogeo.zip", "for Neo Geo games")
        ),

        // Neo Geo CD — REQUIRED; files go in system/neocd/
        "neogeocd" to listOf(
            BiosFile("neocd.bin")
        ),

        // ── Atari ─────────────────────────────────────────────────────────────

        // Atari Lynx — optional but recommended (Beetle Lynx / Handy)
        "atarilynx" to listOf(
            BiosFile("lynxboot.img", "optional but recommended")
        ),

        // Atari 5200 — optional (Atari800 core; HLE fallback available)
        "atari5200" to listOf(
            BiosFile("5200.rom", "optional but recommended")
        ),

        // Atari 7800 — optional (ProSystem core)
        "atari7800" to listOf(
            BiosFile("7800 BIOS (U).rom", "NTSC, optional"),
            BiosFile("7800 BIOS (E).rom", "PAL, optional")
        ),

        // ── Computers ─────────────────────────────────────────────────────────

        // Amiga — optional Kickstart ROMs; core has AROS fallback (PUAE / FS-UAE)
        "amiga" to listOf(
            BiosFile("kick34005.A500", "Kickstart 1.3 — A500"),
            BiosFile("kick37175.A500", "Kickstart 2.04 — A500+"),
            BiosFile("kick40063.A600", "Kickstart 3.1 — A600"),
            BiosFile("kick40068.A1200", "Kickstart 3.1 — A1200"),
            BiosFile("kick40068.A4000", "Kickstart 3.1 — A4000")
        ),

        // MSX — REQUIRED by blueMSX; files go in system/Machines/ & system/Databases/
        "msx" to listOf(
            BiosFile("MSX.ROM"),
            BiosFile("MSX2.ROM"),
            BiosFile("MSX2EXT.ROM"),
            BiosFile("MSXDOS2.ROM")
        ),

        // MSX2 — same files as MSX, extended set
        "msx2" to listOf(
            BiosFile("MSX2.ROM"),
            BiosFile("MSX2EXT.ROM"),
            BiosFile("MSXDOS2.ROM"),
            BiosFile("MSX2P.ROM", "MSX2+"),
            BiosFile("MSX2PEXT.ROM", "MSX2+ extended")
        ),

        // Sharp X68000 — REQUIRED by px68k; files go in system/keropi/
        "x68000" to listOf(
            BiosFile("iplrom.dat"),
            BiosFile("cgrom.dat", "optional, for extra fonts")
        ),

        // ── Nintendo Switch / Wii U ───────────────────────────────────────────

        // Wii U — Cemu needs encryption keys (dump from own console)
        "wiiu" to listOf(
            BiosFile("keys.txt", "console encryption keys, dump from own console")
        ),

        // Nintendo Switch — Eden / Yuzu needs keys + firmware (dump from own Switch)
        "switch" to listOf(
            BiosFile("prod.keys"),
            BiosFile("title.keys")
        ),

        // ── Other ─────────────────────────────────────────────────────────────

        // ColecoVision — REQUIRED by Gearcoleco
        "colecovision" to listOf(
            BiosFile("coleco.rom")
        ),

        // Intellivision — REQUIRED by FreeIntv (both files needed)
        "intellivision" to listOf(
            BiosFile("exec.bin", "Executive ROM"),
            BiosFile("grom.bin", "Graphics ROM")
        ),

        // Odyssey2 / Videopac — REQUIRED by O2EM
        "odyssey2" to listOf(
            BiosFile("o2rom.bin", "Odyssey2 / Videopac G7000"),
            BiosFile("c52.bin", "French variant, optional"),
            BiosFile("g7400.bin", "Videopac+ G7400, optional"),
            BiosFile("jopac.bin", "French G7400 variant, optional")
        )
    )

    /** Returns the list of BIOS files for the given system ID, or an empty list. */
    fun getForSystem(systemId: String): List<BiosFile> =
        biosFiles[systemId] ?: emptyList()
}
