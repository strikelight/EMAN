package com.esde.emulatormanager.data

/**
 * Represents a recommended emulator with download information.
 * These are the top recommended emulators for each gaming system on Android.
 */
data class RecommendedEmulator(
    val id: String,
    val displayName: String,
    val description: String,
    val supportedSystems: List<String>,
    val downloadUrl: String,            // Primary download URL (Play Store preferred)
    val alternateUrl: String? = null,   // Alternative download (GitHub, website)
    val isPaid: Boolean = false,
    val isOpenSource: Boolean = false,
    val notes: String? = null           // Any additional notes (e.g., "Discontinued", "Fork of X")
)

/**
 * Database of recommended emulators organized by system.
 * These are curated recommendations based on performance, compatibility, and ease of use.
 * Every system has at least one free option available.
 */
object RecommendedEmulators {

    val emulators = listOf(
        // ==================== Multi-system ====================
        RecommendedEmulator(
            id = "retroarch",
            displayName = "RetroArch",
            description = "All-in-one emulator with 100+ cores for retro systems",
            supportedSystems = listOf(
                // Nintendo
                "nes", "famicom", "fds", "snes", "superfamicom", "gb", "gbc", "gba",
                "n64", "nds", "virtualboy", "pokemini",
                // Sega
                "genesis", "megadrive", "mastersystem", "gamegear", "segacd", "sega32x",
                "saturn", "dreamcast", "sg-1000",
                // Sony
                "psx", "psp",
                // Atari
                "atari2600", "atari5200", "atari7800", "atarilynx", "atarijaguar", "atarist",
                // Arcade
                "arcade", "mame", "fbneo", "neogeo", "neogeocd", "cps1", "cps2", "cps3",
                // Computers
                "dos", "amiga", "c64", "vic20", "amstradcpc", "msx", "msx2",
                "zxspectrum", "pc88", "pc98", "x68000",
                // Other
                "pcengine", "pcenginecd", "tg16", "tgcd", "wonderswan", "wonderswancolor",
                "ngp", "ngpc", "vectrex", "supervision", "channelf", "odyssey2",
                "colecovision", "intellivision"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            alternateUrl = "https://www.retroarch.com/?page=platforms",
            isOpenSource = true,
            notes = "Free! Best all-in-one solution for retro gaming"
        ),
        RecommendedEmulator(
            id = "lemuroid",
            displayName = "Lemuroid",
            description = "User-friendly RetroArch frontend with automatic game detection",
            supportedSystems = listOf(
                "nes", "snes", "gb", "gbc", "gba", "n64", "nds", "genesis", "megadrive",
                "mastersystem", "gamegear", "psx", "psp", "arcade", "atari2600", "atarilynx",
                "pcengine", "ngp", "ngpc", "wonderswan", "wonderswancolor"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.swordfish.lemuroid",
            alternateUrl = "https://github.com/Swordfish90/Lemuroid",
            isOpenSource = true,
            notes = "Free! Great for beginners, simpler than RetroArch"
        ),

        // ==================== Nintendo Switch ====================
        RecommendedEmulator(
            id = "eden",
            displayName = "Eden",
            description = "Nintendo Switch emulator based on Yuzu",
            supportedSystems = listOf("switch"),
            downloadUrl = "https://github.com/eden-emulator/Releases/releases",
            isOpenSource = true,
            notes = "Free! Requires Snapdragon 8 Gen 1+"
        ),

        // ==================== Nintendo Wii U ====================
        RecommendedEmulator(
            id = "cemu",
            displayName = "Cemu",
            description = "Nintendo Wii U emulator with great compatibility",
            supportedSystems = listOf("wiiu"),
            downloadUrl = "https://cemu.info/",
            alternateUrl = "https://github.com/cemu-project/Cemu",
            isOpenSource = true,
            notes = "Free! Requires powerful device"
        ),

        // ==================== Nintendo GameCube / Wii ====================
        RecommendedEmulator(
            id = "dolphin",
            displayName = "Dolphin",
            description = "Best GameCube and Wii emulator",
            supportedSystems = listOf("gc", "wii"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.dolphinemu.dolphinemu",
            alternateUrl = "https://dolphin-emu.org/download/",
            isOpenSource = true,
            notes = "Free! Excellent compatibility"
        ),

        // ==================== Nintendo 3DS ====================
        RecommendedEmulator(
            id = "lime3ds",
            displayName = "Lime3DS",
            description = "Nintendo 3DS emulator, Citra fork",
            supportedSystems = listOf("3ds"),
            downloadUrl = "https://github.com/Lime3DS/Lime3DS/releases",
            isOpenSource = true,
            notes = "Free! Active fork after Citra shutdown"
        ),
        RecommendedEmulator(
            id = "azahar",
            displayName = "Azahar",
            description = "Nintendo 3DS emulator, Citra fork",
            supportedSystems = listOf("3ds"),
            downloadUrl = "https://azahar-emu.org/",
            alternateUrl = "https://github.com/azahar-emu/azahar",
            isOpenSource = true,
            notes = "Free! Another active Citra fork"
        ),

        // ==================== Nintendo DS ====================
        RecommendedEmulator(
            id = "drastic",
            displayName = "DraStic",
            description = "Best Nintendo DS emulator, excellent performance",
            supportedSystems = listOf("nds"),
            downloadUrl = "https://archive.org/details/dra-stic-ds-emulator-r-2.6.0.4a",
            isPaid = true,
            notes = "Paid - best DS emulation available (removed from Play Store, use archive link)"
        ),
        RecommendedEmulator(
            id = "melonds",
            displayName = "melonDS",
            description = "Free and open-source Nintendo DS emulator",
            supportedSystems = listOf("nds"),
            downloadUrl = "https://play.google.com/store/apps/details?id=me.magnum.melonds",
            alternateUrl = "https://github.com/rafaelvcaetano/melonDS-android",
            isOpenSource = true,
            notes = "Free! Great DS emulation with good performance"
        ),
        RecommendedEmulator(
            id = "nds_retroarch",
            displayName = "DeSmuME (RetroArch)",
            description = "Nintendo DS via RetroArch core",
            supportedSystems = listOf("nds"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use desmume or melonds core"
        ),

        // ==================== Nintendo N64 ====================
        RecommendedEmulator(
            id = "mupen64plus_fz",
            displayName = "Mupen64Plus FZ",
            description = "Best Nintendo 64 emulator for Android",
            supportedSystems = listOf("n64"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita",
            alternateUrl = "https://github.com/mupen64plus-ae/mupen64plus-ae",
            isOpenSource = true,
            notes = "Free! Excellent compatibility"
        ),
        RecommendedEmulator(
            id = "n64_retroarch",
            displayName = "Mupen64Plus (RetroArch)",
            description = "N64 via RetroArch core",
            supportedSystems = listOf("n64"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use mupen64plus_next core"
        ),

        // ==================== Game Boy / Game Boy Color ====================
        RecommendedEmulator(
            id = "pizza_boy_gbc",
            displayName = "Pizza Boy GBC Pro",
            description = "Excellent Game Boy / Game Boy Color emulator",
            supportedSystems = listOf("gb", "gbc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=it.dbtecno.pizzaboypro",
            isPaid = true,
            notes = "Paid - best GB/GBC emulator"
        ),
        RecommendedEmulator(
            id = "myoldboy",
            displayName = "My OldBoy!",
            description = "Fast Game Boy / Game Boy Color emulator",
            supportedSystems = listOf("gb", "gbc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.fastemulator.gbc",
            isPaid = true,
            notes = "Paid - good link cable emulation"
        ),
        RecommendedEmulator(
            id = "gb_retroarch",
            displayName = "Gambatte (RetroArch)",
            description = "Game Boy/Color via RetroArch core",
            supportedSystems = listOf("gb", "gbc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use gambatte or mgba core"
        ),

        // ==================== Game Boy Advance ====================
        RecommendedEmulator(
            id = "pizza_boy_gba",
            displayName = "Pizza Boy GBA Pro",
            description = "Excellent Game Boy Advance emulator",
            supportedSystems = listOf("gba"),
            downloadUrl = "https://play.google.com/store/apps/details?id=it.dbtecno.pizzaboygbapro",
            isPaid = true,
            notes = "Paid - best GBA emulator"
        ),
        RecommendedEmulator(
            id = "myboy",
            displayName = "My Boy!",
            description = "Fast Game Boy Advance emulator with link support",
            supportedSystems = listOf("gba"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.fastemulator.gba",
            isPaid = true,
            notes = "Paid - good link cable emulation"
        ),
        RecommendedEmulator(
            id = "gba_retroarch",
            displayName = "mGBA (RetroArch)",
            description = "Game Boy Advance via RetroArch core",
            supportedSystems = listOf("gba"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use mgba core - excellent accuracy"
        ),

        // ==================== NES / Famicom ====================
        RecommendedEmulator(
            id = "nes_emu",
            displayName = "NES.emu",
            description = "Accurate NES/Famicom emulator",
            supportedSystems = listOf("nes", "famicom", "fds"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.NesEmu",
            isPaid = true,
            notes = "Paid - very accurate emulation"
        ),
        RecommendedEmulator(
            id = "nes_retroarch",
            displayName = "Mesen/FCEUmm (RetroArch)",
            description = "NES/Famicom via RetroArch core",
            supportedSystems = listOf("nes", "famicom", "fds"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use mesen or fceumm core"
        ),

        // ==================== SNES / Super Famicom ====================
        RecommendedEmulator(
            id = "snes9x_ex",
            displayName = "Snes9x EX+",
            description = "Excellent SNES emulator",
            supportedSystems = listOf("snes", "superfamicom", "satellaview", "sufami"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.Snes9xPlus",
            isPaid = true,
            notes = "Paid - great compatibility"
        ),
        RecommendedEmulator(
            id = "snes_retroarch",
            displayName = "Snes9x/bsnes (RetroArch)",
            description = "SNES via RetroArch core",
            supportedSystems = listOf("snes", "superfamicom", "satellaview", "sufami"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use snes9x or bsnes core"
        ),

        // ==================== PlayStation (PSX) ====================
        RecommendedEmulator(
            id = "duckstation",
            displayName = "DuckStation",
            description = "Fast and accurate PlayStation emulator",
            supportedSystems = listOf("psx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation",
            alternateUrl = "https://github.com/stenzek/duckstation",
            isOpenSource = true,
            notes = "Free! Best PS1 emulator"
        ),
        RecommendedEmulator(
            id = "epsxe",
            displayName = "ePSXe",
            description = "Classic PlayStation emulator with great compatibility",
            supportedSystems = listOf("psx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.epsxe.ePSXe",
            isPaid = true,
            notes = "Paid - long-established, reliable"
        ),

        // ==================== PlayStation 2 ====================
        RecommendedEmulator(
            id = "aethersx2",
            displayName = "AetherSX2 / NetherSX2",
            description = "PlayStation 2 emulator (discontinued)",
            supportedSystems = listOf("ps2"),
            downloadUrl = "https://www.aethersx2.com/archive/",
            isOpenSource = true,
            notes = "Free! Find NetherSX2 patches online"
        ),

        // ==================== PlayStation 3 ====================
        RecommendedEmulator(
            id = "rpcsx",
            displayName = "RPCSX",
            description = "PlayStation 3 emulator for Android (experimental)",
            supportedSystems = listOf("ps3"),
            downloadUrl = "https://github.com/RPCSX/rpcsx-ui-android/releases",
            alternateUrl = "https://rpcsx.org/",
            isOpenSource = true,
            notes = "Free! Early development, requires powerful device"
        ),
        RecommendedEmulator(
            id = "aps3e",
            displayName = "aPS3e",
            description = "PlayStation 3 emulator for Android",
            supportedSystems = listOf("ps3"),
            downloadUrl = "https://play.google.com/store/apps/details?id=aenu.aps3e",
            notes = "Free! Experimental PS3 emulation"
        ),

        // ==================== Xbox 360 ====================
        RecommendedEmulator(
            id = "ax360e",
            displayName = "aX360e",
            description = "Xbox 360 emulator for Android (experimental)",
            supportedSystems = listOf("xbox360"),
            downloadUrl = "https://play.google.com/store/apps/details?id=aenu.ax360e.free",
            notes = "Free! Early development, limited compatibility"
        ),

        // ==================== PSP ====================
        RecommendedEmulator(
            id = "ppsspp",
            displayName = "PPSSPP",
            description = "Best PSP emulator, excellent compatibility",
            supportedSystems = listOf("psp"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppssppgold",
            alternateUrl = "https://www.ppsspp.org/downloads.html",
            isOpenSource = true,
            notes = "Free version available! Gold supports dev"
        ),
        RecommendedEmulator(
            id = "ppsspp_free",
            displayName = "PPSSPP (Free)",
            description = "Free version of PPSSPP",
            supportedSystems = listOf("psp"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp",
            alternateUrl = "https://www.ppsspp.org/downloads.html",
            isOpenSource = true,
            notes = "Free! Same features as Gold"
        ),

        // ==================== PS Vita ====================
        RecommendedEmulator(
            id = "vita3k",
            displayName = "Vita3K",
            description = "PlayStation Vita emulator (experimental)",
            supportedSystems = listOf("psvita"),
            downloadUrl = "https://vita3k.org/",
            alternateUrl = "https://github.com/Vita3K/Vita3K-Android",
            isOpenSource = true,
            notes = "Free! Still in development"
        ),

        // ==================== Sega Genesis / Mega Drive ====================
        RecommendedEmulator(
            id = "md_emu",
            displayName = "MD.emu",
            description = "Accurate Sega Genesis/Mega Drive emulator",
            supportedSystems = listOf("genesis", "megadrive", "segacd", "sega32x"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.MdEmu",
            isPaid = true,
            notes = "Paid - very accurate emulation"
        ),
        RecommendedEmulator(
            id = "genesis_retroarch",
            displayName = "Genesis Plus GX (RetroArch)",
            description = "Genesis/Mega Drive via RetroArch core",
            supportedSystems = listOf("genesis", "megadrive", "segacd", "sega32x", "mastersystem", "gamegear", "sg-1000"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use genesis_plus_gx or picodrive core"
        ),

        // ==================== Sega Master System ====================
        RecommendedEmulator(
            id = "mastersystem_emu",
            displayName = "MasterGear",
            description = "Sega Master System and Game Gear emulator",
            supportedSystems = listOf("mastersystem", "gamegear"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.fms.mg",
            isPaid = true,
            notes = "Paid - dedicated SMS/GG emulator"
        ),
        RecommendedEmulator(
            id = "sms_retroarch",
            displayName = "Genesis Plus GX (RetroArch)",
            description = "Master System via RetroArch",
            supportedSystems = listOf("mastersystem", "sg-1000"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use genesis_plus_gx core"
        ),

        // ==================== Sega Game Gear ====================
        RecommendedEmulator(
            id = "gg_retroarch",
            displayName = "Genesis Plus GX (RetroArch)",
            description = "Game Gear via RetroArch",
            supportedSystems = listOf("gamegear"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use genesis_plus_gx or gearsystem core"
        ),

        // ==================== Sega Saturn ====================
        RecommendedEmulator(
            id = "yabause",
            displayName = "Yaba Sanshiro 2",
            description = "Sega Saturn emulator",
            supportedSystems = listOf("saturn"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.devmiyax.yabasanshioro2",
            isOpenSource = true,
            notes = "Free! Best standalone Saturn emulator"
        ),
        RecommendedEmulator(
            id = "saturn_retroarch",
            displayName = "Beetle Saturn (RetroArch)",
            description = "Sega Saturn via RetroArch core",
            supportedSystems = listOf("saturn"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use beetle_saturn or yabasanshiro"
        ),

        // ==================== Sega Dreamcast ====================
        RecommendedEmulator(
            id = "redream",
            displayName = "Redream",
            description = "Fast and accurate Dreamcast emulator",
            supportedSystems = listOf("dreamcast"),
            downloadUrl = "https://play.google.com/store/apps/details?id=io.recompiled.redream",
            notes = "Free at 480p, Premium for 1080p"
        ),
        RecommendedEmulator(
            id = "flycast",
            displayName = "Flycast",
            description = "Open-source Dreamcast/Naomi emulator",
            supportedSystems = listOf("dreamcast", "naomi", "atomiswave"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.flycast.emulator",
            alternateUrl = "https://github.com/flyinghead/flycast",
            isOpenSource = true,
            notes = "Free! Great Naomi/Atomiswave support"
        ),

        // ==================== Arcade / MAME / Neo Geo ====================
        RecommendedEmulator(
            id = "mame4droid",
            displayName = "MAME4droid",
            description = "Arcade emulator based on MAME",
            supportedSystems = listOf("arcade", "mame", "neogeo", "neogeocd", "cps1", "cps2", "cps3"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.seleuco.mame4droid",
            isOpenSource = true,
            notes = "Free! 2024 version uses newer MAME"
        ),
        RecommendedEmulator(
            id = "fbneo_retroarch",
            displayName = "FinalBurn Neo (RetroArch)",
            description = "Arcade/Neo Geo via RetroArch core",
            supportedSystems = listOf("arcade", "neogeo", "neogeocd", "cps1", "cps2", "cps3", "fbneo"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use fbneo core - excellent arcade"
        ),
        RecommendedEmulator(
            id = "neogeo_emu",
            displayName = "NEO.emu",
            description = "Neo Geo AES/MVS emulator",
            supportedSystems = listOf("neogeo", "neogeocd"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.NeoEmu",
            isPaid = true,
            notes = "Paid - dedicated Neo Geo emulator"
        ),
        RecommendedEmulator(
            id = "neogeo_retroarch",
            displayName = "FinalBurn Neo (RetroArch)",
            description = "Neo Geo AES/MVS via RetroArch",
            supportedSystems = listOf("neogeo", "neogeocd"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use fbneo core - excellent compatibility"
        ),

        // ==================== Atari 2600 ====================
        RecommendedEmulator(
            id = "a2600_emu",
            displayName = "2600.emu",
            description = "Atari 2600 emulator",
            supportedSystems = listOf("atari2600"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.A2600Emu",
            isPaid = true,
            notes = "Paid - accurate 2600 emulation"
        ),
        RecommendedEmulator(
            id = "atari2600_retroarch",
            displayName = "Stella (RetroArch)",
            description = "Atari 2600 via RetroArch core",
            supportedSystems = listOf("atari2600"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use stella core"
        ),

        // ==================== Atari 5200 / 7800 ====================
        RecommendedEmulator(
            id = "atari78_retroarch",
            displayName = "ProSystem (RetroArch)",
            description = "Atari 7800 via RetroArch core",
            supportedSystems = listOf("atari7800"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use prosystem core"
        ),
        RecommendedEmulator(
            id = "atari52_retroarch",
            displayName = "Atari800 (RetroArch)",
            description = "Atari 5200/800 via RetroArch core",
            supportedSystems = listOf("atari5200", "atari800"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use atari800 core"
        ),

        // ==================== Atari Lynx ====================
        RecommendedEmulator(
            id = "lynx_retroarch",
            displayName = "Handy (RetroArch)",
            description = "Atari Lynx via RetroArch core",
            supportedSystems = listOf("atarilynx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use handy core"
        ),

        // ==================== Atari Jaguar ====================
        RecommendedEmulator(
            id = "jaguar_retroarch",
            displayName = "Virtual Jaguar (RetroArch)",
            description = "Atari Jaguar via RetroArch core",
            supportedSystems = listOf("atarijaguar"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use virtualjaguar core - limited compat"
        ),

        // ==================== Atari ST ====================
        RecommendedEmulator(
            id = "atarist_retroarch",
            displayName = "Hatari (RetroArch)",
            description = "Atari ST via RetroArch core",
            supportedSystems = listOf("atarist"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use hatari core"
        ),

        // ==================== Commodore 64 ====================
        RecommendedEmulator(
            id = "c64_emu",
            displayName = "C64.emu",
            description = "Commodore 64 emulator",
            supportedSystems = listOf("c64"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.C64Emu",
            isPaid = true,
            notes = "Paid - excellent C64 emulation"
        ),
        RecommendedEmulator(
            id = "c64_retroarch",
            displayName = "VICE (RetroArch)",
            description = "Commodore 64 via RetroArch core",
            supportedSystems = listOf("c64", "vic20", "c128", "plus4", "pet"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use vice_x64 core"
        ),

        // ==================== Commodore VIC-20 ====================
        RecommendedEmulator(
            id = "vic20_retroarch",
            displayName = "VICE VIC-20 (RetroArch)",
            description = "VIC-20 via RetroArch core",
            supportedSystems = listOf("vic20"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use vice_xvic core"
        ),

        // ==================== Commodore Amiga ====================
        RecommendedEmulator(
            id = "amiga_retroarch",
            displayName = "PUAE (RetroArch)",
            description = "Commodore Amiga via RetroArch core",
            supportedSystems = listOf("amiga", "amiga500", "amiga1200", "amigacd32"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use puae core"
        ),

        // ==================== MSX / MSX2 ====================
        RecommendedEmulator(
            id = "msx_emu",
            displayName = "MSX.emu",
            description = "MSX/MSX2 emulator",
            supportedSystems = listOf("msx", "msx2"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.MsxEmu",
            isPaid = true,
            notes = "Paid - excellent MSX emulation"
        ),
        RecommendedEmulator(
            id = "msx_retroarch",
            displayName = "blueMSX (RetroArch)",
            description = "MSX/MSX2 via RetroArch core",
            supportedSystems = listOf("msx", "msx2", "colecovision"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use bluemsx or fmsx core"
        ),

        // ==================== ZX Spectrum ====================
        RecommendedEmulator(
            id = "spectrum_retroarch",
            displayName = "Fuse (RetroArch)",
            description = "ZX Spectrum via RetroArch core",
            supportedSystems = listOf("zxspectrum"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use fuse core"
        ),

        // ==================== Amstrad CPC ====================
        RecommendedEmulator(
            id = "cpc_retroarch",
            displayName = "Caprice32 (RetroArch)",
            description = "Amstrad CPC via RetroArch core",
            supportedSystems = listOf("amstradcpc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use cap32 core"
        ),

        // ==================== PC Engine / TurboGrafx-16 ====================
        RecommendedEmulator(
            id = "pce_emu",
            displayName = "PCE.emu",
            description = "PC Engine/TurboGrafx-16 emulator",
            supportedSystems = listOf("pcengine", "pcenginecd", "tg16", "tgcd", "supergrafx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.PceEmu",
            isPaid = true,
            notes = "Paid - accurate PCE emulation"
        ),
        RecommendedEmulator(
            id = "pce_retroarch",
            displayName = "Beetle PCE (RetroArch)",
            description = "PC Engine via RetroArch core",
            supportedSystems = listOf("pcengine", "pcenginecd", "tg16", "tgcd", "supergrafx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use beetle_pce_fast core"
        ),

        // ==================== Neo Geo Pocket ====================
        RecommendedEmulator(
            id = "ngp_emu",
            displayName = "NGP.emu",
            description = "Neo Geo Pocket/Color emulator",
            supportedSystems = listOf("ngp", "ngpc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.NgpEmu",
            isPaid = true,
            notes = "Paid - dedicated NGP emulator"
        ),
        RecommendedEmulator(
            id = "ngp_retroarch",
            displayName = "Beetle NeoPop (RetroArch)",
            description = "Neo Geo Pocket via RetroArch core",
            supportedSystems = listOf("ngp", "ngpc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use beetle_ngp core"
        ),

        // ==================== WonderSwan ====================
        RecommendedEmulator(
            id = "swan_emu",
            displayName = "Swan.emu",
            description = "WonderSwan/Color emulator",
            supportedSystems = listOf("wonderswan", "wonderswancolor"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.SwanEmu",
            isPaid = true,
            notes = "Paid - dedicated WonderSwan emulator"
        ),
        RecommendedEmulator(
            id = "swan_retroarch",
            displayName = "Beetle Cygne (RetroArch)",
            description = "WonderSwan via RetroArch core",
            supportedSystems = listOf("wonderswan", "wonderswancolor"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use beetle_wswan core"
        ),

        // ==================== Virtual Boy ====================
        RecommendedEmulator(
            id = "vb_retroarch",
            displayName = "Beetle VB (RetroArch)",
            description = "Virtual Boy via RetroArch core",
            supportedSystems = listOf("virtualboy"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use beetle_vb core"
        ),

        // ==================== ColecoVision ====================
        RecommendedEmulator(
            id = "coleco_retroarch",
            displayName = "blueMSX (RetroArch)",
            description = "ColecoVision via RetroArch core",
            supportedSystems = listOf("colecovision"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use bluemsx or gearcoleco core"
        ),

        // ==================== Intellivision ====================
        RecommendedEmulator(
            id = "intv_retroarch",
            displayName = "FreeIntv (RetroArch)",
            description = "Intellivision via RetroArch core",
            supportedSystems = listOf("intellivision"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use freeintv core"
        ),

        // ==================== Vectrex ====================
        RecommendedEmulator(
            id = "vectrex_retroarch",
            displayName = "vecx (RetroArch)",
            description = "Vectrex via RetroArch core",
            supportedSystems = listOf("vectrex"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use vecx core"
        ),

        // ==================== Channel F ====================
        RecommendedEmulator(
            id = "channelf_retroarch",
            displayName = "FreeChaF (RetroArch)",
            description = "Fairchild Channel F via RetroArch",
            supportedSystems = listOf("channelf"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use freechaf core"
        ),

        // ==================== Odyssey 2 ====================
        RecommendedEmulator(
            id = "odyssey2_retroarch",
            displayName = "O2EM (RetroArch)",
            description = "Magnavox Odyssey 2 via RetroArch",
            supportedSystems = listOf("odyssey2"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use o2em core"
        ),

        // ==================== ScummVM ====================
        RecommendedEmulator(
            id = "scummvm",
            displayName = "ScummVM",
            description = "Play classic adventure games (LucasArts, Sierra, etc.)",
            supportedSystems = listOf("scummvm"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.scummvm.scummvm",
            alternateUrl = "https://www.scummvm.org/downloads/",
            isOpenSource = true,
            notes = "Free! Supports hundreds of games"
        ),

        // ==================== DOS ====================
        RecommendedEmulator(
            id = "dosbox_retroarch",
            displayName = "DOSBox Pure (RetroArch)",
            description = "DOS emulator via RetroArch core",
            supportedSystems = listOf("dos"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use dosbox_pure core"
        ),
        RecommendedEmulator(
            id = "magic_dosbox",
            displayName = "Magic DOSBox",
            description = "Feature-rich DOSBox for Android",
            supportedSystems = listOf("dos"),
            downloadUrl = "https://play.google.com/store/apps/details?id=bruenor.magicbox",
            isPaid = true,
            notes = "Paid - great touch controls"
        ),

        // ==================== Japanese Computers ====================
        RecommendedEmulator(
            id = "pc88_retroarch",
            displayName = "Quasi88 (RetroArch)",
            description = "NEC PC-88 via RetroArch core",
            supportedSystems = listOf("pc88"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use quasi88 core"
        ),
        RecommendedEmulator(
            id = "pc98_retroarch",
            displayName = "Neko Project II (RetroArch)",
            description = "NEC PC-98 via RetroArch core",
            supportedSystems = listOf("pc98"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use np2kai core"
        ),
        RecommendedEmulator(
            id = "x68000_retroarch",
            displayName = "PX68K (RetroArch)",
            description = "Sharp X68000 via RetroArch core",
            supportedSystems = listOf("x68000"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
            isOpenSource = true,
            notes = "Free! Use px68k core"
        ),

        // ==================== Windows / PC ====================
        RecommendedEmulator(
            id = "winlator",
            displayName = "Winlator",
            description = "Run Windows games on Android via Wine",
            supportedSystems = listOf("windows", "pc"),
            downloadUrl = "https://github.com/brunodev85/winlator/releases",
            isOpenSource = true,
            notes = "Free! Best Windows compatibility layer"
        ),
        RecommendedEmulator(
            id = "gamehub_lite",
            displayName = "GameHub Lite",
            description = "Steam game launcher for Android",
            supportedSystems = listOf("windows", "pc"),
            downloadUrl = "https://github.com/Producdevity/gamehub-lite/releases",
            notes = "Free! Easy Steam game setup"
        ),
        RecommendedEmulator(
            id = "gamenative",
            displayName = "GameNative",
            description = "Launch Steam, GOG, and Epic Games titles from ES-DE on Android",
            supportedSystems = listOf("windows", "pc"),
            downloadUrl = "https://github.com/utkarshdalal/GameNative/releases",
            notes = "Free! Required for GOG and Epic game support in this app"
        )
    )

    /**
     * Get recommended emulators for a specific system
     */
    fun getForSystem(systemId: String): List<RecommendedEmulator> {
        return emulators.filter { it.supportedSystems.contains(systemId) }
    }

    /**
     * Get all unique system IDs that have recommendations
     */
    fun getSupportedSystems(): Set<String> {
        return emulators.flatMap { it.supportedSystems }.toSet()
    }

    /**
     * Get recommendations grouped by system category
     */
    fun getGroupedByCategory(): Map<String, List<RecommendedEmulator>> {
        return mapOf(
            "Multi-System" to emulators.filter { it.id in listOf("retroarch", "lemuroid") },
            "Nintendo Switch" to getForSystem("switch"),
            "Nintendo Wii U" to getForSystem("wiiu"),
            "Nintendo GameCube/Wii" to emulators.filter { it.supportedSystems.contains("gc") || it.supportedSystems.contains("wii") }
                .filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Nintendo 3DS" to getForSystem("3ds"),
            "Nintendo DS" to getForSystem("nds").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Nintendo 64" to getForSystem("n64").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "SNES / Super Famicom" to getForSystem("snes").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "NES / Famicom" to getForSystem("nes").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Game Boy Advance" to getForSystem("gba").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Game Boy / Color" to getForSystem("gb").filterNot { it.id in listOf("retroarch", "lemuroid", "pizza_boy_gba", "myboy", "gba_retroarch") },
            "Virtual Boy" to getForSystem("virtualboy").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "PlayStation 3" to getForSystem("ps3"),
            "PlayStation Vita" to getForSystem("psvita"),
            "PlayStation 2" to getForSystem("ps2"),
            "PlayStation (PSX)" to getForSystem("psx").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "PSP" to getForSystem("psp").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Xbox 360" to getForSystem("xbox360"),
            "Sega Dreamcast" to getForSystem("dreamcast").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Sega Saturn" to getForSystem("saturn").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Sega Genesis / Mega Drive" to getForSystem("genesis").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Sega Master System" to getForSystem("mastersystem").filterNot { it.id in listOf("retroarch", "lemuroid", "genesis_retroarch") },
            "Sega Game Gear" to getForSystem("gamegear").filterNot { it.id in listOf("retroarch", "lemuroid", "genesis_retroarch") },
            "Sega 32X" to getForSystem("sega32x").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Arcade / MAME" to getForSystem("arcade").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Neo Geo" to getForSystem("neogeo").filterNot { it.id in listOf("retroarch", "lemuroid", "mame4droid", "fbneo_retroarch") },
            "Atari 2600" to getForSystem("atari2600").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Atari 5200 / 7800" to (getForSystem("atari5200") + getForSystem("atari7800")).filterNot { it.id in listOf("retroarch", "lemuroid") }.distinctBy { it.id },
            "Atari Lynx" to getForSystem("atarilynx").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Atari Jaguar" to getForSystem("atarijaguar").filterNot { it.id in listOf("retroarch") },
            "Atari ST" to getForSystem("atarist").filterNot { it.id in listOf("retroarch") },
            "Commodore 64" to getForSystem("c64").filterNot { it.id in listOf("retroarch") },
            "Commodore VIC-20" to getForSystem("vic20").filterNot { it.id in listOf("retroarch", "c64_retroarch") },
            "Commodore Amiga" to getForSystem("amiga").filterNot { it.id in listOf("retroarch") },
            "MSX / MSX2" to getForSystem("msx").filterNot { it.id in listOf("retroarch") },
            "ZX Spectrum" to getForSystem("zxspectrum").filterNot { it.id in listOf("retroarch") },
            "Amstrad CPC" to getForSystem("amstradcpc").filterNot { it.id in listOf("retroarch") },
            "PC Engine / TurboGrafx-16" to getForSystem("pcengine").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "Neo Geo Pocket" to getForSystem("ngp").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "WonderSwan" to getForSystem("wonderswan").filterNot { it.id in listOf("retroarch", "lemuroid") },
            "ColecoVision" to getForSystem("colecovision").filterNot { it.id in listOf("retroarch", "msx_retroarch") },
            "Intellivision" to getForSystem("intellivision").filterNot { it.id in listOf("retroarch") },
            "Vectrex" to getForSystem("vectrex").filterNot { it.id in listOf("retroarch") },
            "ScummVM" to getForSystem("scummvm"),
            "DOS" to getForSystem("dos").filterNot { it.id in listOf("retroarch") },
            "Japanese PCs (PC-88/98/X68000)" to (getForSystem("pc88") + getForSystem("pc98") + getForSystem("x68000")).filterNot { it.id in listOf("retroarch") }.distinctBy { it.id },
            "Windows / PC" to getForSystem("windows")
        ).filterValues { it.isNotEmpty() }
    }
}
