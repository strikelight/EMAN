package com.esde.emulatormanager.data

import com.esde.emulatormanager.data.model.KnownEmulator

/**
 * Database of known emulators with their package names and supported systems.
 * This helps match installed apps with ES-DE compatible emulators.
 *
 * The launchCommand field contains the command arguments (excluding the %EMULATOR_NAME% part).
 * The emulationActivity field specifies the activity that should be used in es_find_rules.xml
 * (this is often different from the main activity - it's the activity that handles game launching)
 */
object KnownEmulators {

    val emulators = listOf(
        // RetroArch - Multi-system
        KnownEmulator(
            id = "retroarch",
            displayName = "RetroArch",
            packageNames = listOf(
                "com.retroarch",
                "com.retroarch.aarch64",
                "com.retroarch.ra32"
            ),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            supportedSystems = listOf(
                "3do", "amiga", "amstradcpc", "arcade", "atari2600", "atari5200", "atari7800",
                "atarilynx", "atarist", "c64", "cps1", "cps2", "cps3", "dos", "dreamcast",
                "famicom", "fba", "fbneo", "fds", "gamegear", "gb", "gba", "gbc", "gc",
                "genesis", "mame", "mastersystem", "megadrive", "msx", "msx2", "n64",
                "nds", "neogeo", "neogeocd", "nes", "ngp", "ngpc", "pc88", "pc98",
                "pcengine", "pcenginecd", "psx", "satellaview", "saturn", "segacd",
                "sega32x", "sg-1000", "snes", "sufami", "superfamicom", "supervision",
                "tg16", "tgcd", "vectrex", "virtualboy", "wii", "wonderswan",
                "wonderswancolor", "x68000", "zxspectrum"
            ),
            isRetroArch = true
        ),

        // Nintendo 64
        KnownEmulator(
            id = "mupen64plus_fz",
            displayName = "Mupen64Plus FZ",
            packageNames = listOf(
                "org.mupen64plusae.v3.fzurita",
                "org.mupen64plusae.v3.fzurita.pro"
            ),
            activityName = "paulscode.android.mupen64plusae.SplashActivity",
            supportedSystems = listOf("n64"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // PlayStation
        KnownEmulator(
            id = "duckstation",
            displayName = "DuckStation",
            packageNames = listOf("com.github.stenzek.duckstation"),
            activityName = "com.github.stenzek.duckstation.EmulationActivity",
            supportedSystems = listOf("psx"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "epsxe",
            displayName = "ePSXe",
            packageNames = listOf("com.epsxe.ePSXe"),
            activityName = "com.epsxe.ePSXe.ePSXe",
            supportedSystems = listOf("psx"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // PlayStation 2
        KnownEmulator(
            id = "aethersx2",
            displayName = "AetherSX2",
            packageNames = listOf("xyz.aethersx2.android"),
            activityName = "xyz.aethersx2.android.EmulationActivity",
            supportedSystems = listOf("ps2"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %ACTION%=android.intent.action.MAIN %EXTRA_bootPath%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "nethersx2",
            displayName = "NetherSX2",
            packageNames = listOf("xyz.nethersx2.android"),
            activityName = null,
            supportedSystems = listOf("ps2"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %ACTION%=android.intent.action.MAIN %EXTRA_bootPath%=%ROMSAF%"
        ),

        // PSP
        KnownEmulator(
            id = "ppsspp",
            displayName = "PPSSPP",
            packageNames = listOf(
                "org.ppsspp.ppsspp",
                "org.ppsspp.ppssppgold"
            ),
            activityName = "org.ppsspp.ppsspp.PpssppActivity",
            supportedSystems = listOf("psp"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // Nintendo DS
        KnownEmulator(
            id = "drastic",
            displayName = "DraStic",
            packageNames = listOf("com.dsemu.drastic"),
            activityName = "com.dsemu.drastic.DraSticActivity",
            supportedSystems = listOf("nds"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "melonds",
            displayName = "melonDS",
            packageNames = listOf("me.magnum.melonds"),
            activityName = "me.magnum.melonds.ui.emulator.EmulatorActivity",
            supportedSystems = listOf("nds"),
            launchCommand = "%ACTION%=me.magnum.melonds.LAUNCH_ROM %EXTRA_uri%=%ROMSAF%"
        ),

        // Nintendo 3DS
        KnownEmulator(
            id = "citra",
            displayName = "Citra",
            packageNames = listOf(
                "org.citra.citra_emu",
                "org.citra.citra_emu.canary"
            ),
            activityName = "org.citra.citra_emu.ui.main.MainActivity",
            supportedSystems = listOf("3ds"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "lime3ds",
            displayName = "Lime3DS",
            packageNames = listOf("io.github.lime3ds.android"),
            activityName = null,
            supportedSystems = listOf("3ds"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %DATA%=%ROMSAF%"
        ),

        // GameCube / Wii
        KnownEmulator(
            id = "dolphin",
            displayName = "Dolphin",
            packageNames = listOf(
                "org.dolphinemu.dolphinemu",
                "org.dolphinemu.dolphinemu.mmjr",
                "org.dolphinemu.dolphinemu.mmjr2"
            ),
            activityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            supportedSystems = listOf("gc", "wii"),
            launchCommand = "%ACTION%=android.intent.action.MAIN %CATEGORY%=android.intent.category.LEANBACK_LAUNCHER %EXTRA_AutoStartFile%=%ROMSAF%"
        ),

        // Dreamcast
        KnownEmulator(
            id = "redream",
            displayName = "Redream",
            packageNames = listOf("io.recompiled.redream"),
            activityName = "io.recompiled.redream.MainActivity",
            supportedSystems = listOf("dreamcast"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "flycast",
            displayName = "Flycast",
            packageNames = listOf("com.flycast.emulator"),
            activityName = null,
            supportedSystems = listOf("dreamcast", "naomi", "atomiswave"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // Switch
        KnownEmulator(
            id = "yuzu",
            displayName = "Yuzu",
            packageNames = listOf("org.yuzu.yuzu_emu"),
            activityName = "org.yuzu.yuzu_emu.ui.main.MainActivity",
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "yuzu_ea",
            displayName = "Yuzu EA",
            packageNames = listOf("org.yuzu.yuzu_emu.ea"),
            activityName = "org.yuzu.yuzu_emu.ui.main.MainActivity",
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "eden",
            displayName = "Eden",
            packageNames = listOf("dev.eden.eden_emulator"),
            activityName = "org.yuzu.yuzu_emu.ui.main.MainActivity",
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "eden_nightly",
            displayName = "Eden Nightly",
            packageNames = listOf("dev.eden.eden_nightly", "dev.eden.eden_emulator.nightly"),
            activityName = "org.yuzu.yuzu_emu.ui.main.MainActivity",
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "skyline",
            displayName = "Skyline",
            packageNames = listOf("skyline.emu"),
            activityName = "skyline.emu.MainActivity",
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%"
        ),
        KnownEmulator(
            id = "sudachi",
            displayName = "Sudachi",
            packageNames = listOf("org.sudachi.sudachi_emu"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.sudachi.sudachi_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "suyu",
            displayName = "Suyu",
            packageNames = listOf("org.suyu.suyu_emu"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.suyu.suyu_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "citron",
            displayName = "Citron",
            packageNames = listOf("org.citron.citron_emu"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.citron.citron_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "uzuy",
            displayName = "Uzuy",
            packageNames = listOf("org.uzuy.uzuy_emu"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.uzuy.uzuy_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "sumi",
            displayName = "Sumi",
            packageNames = listOf("com.sumi.SumiEmulator"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.sumi.sumi_emu.activities.EmulationActivity"
        ),
        KnownEmulator(
            id = "ziunx",
            displayName = "Ziunx",
            packageNames = listOf("org.Ziunx.Ziunx_emu.ea"),
            activityName = null,
            supportedSystems = listOf("switch"),
            launchCommand = "%ACTION%=android.nfc.action.TECH_DISCOVERED %DATA%=%ROMPROVIDER%",
            emulationActivity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),

        // Sega Genesis / Mega Drive
        KnownEmulator(
            id = "md_emu",
            displayName = "MD.emu",
            packageNames = listOf("com.explusalpha.MdEmu"),
            activityName = "com.imagine.BaseActivity",
            supportedSystems = listOf("genesis", "megadrive", "segacd", "sega32x"),
            launchCommand = "%DATA%=%ROMPROVIDER%"
        ),

        // GBA
        KnownEmulator(
            id = "gba_emu",
            displayName = "GBA.emu",
            packageNames = listOf("com.explusalpha.GbaEmu"),
            activityName = "com.imagine.BaseActivity",
            supportedSystems = listOf("gba"),
            launchCommand = "%DATA%=%ROMPROVIDER%"
        ),
        KnownEmulator(
            id = "pizza_boy_gba",
            displayName = "Pizza Boy GBA",
            packageNames = listOf(
                "it.dbtecno.pizzaboygba",
                "it.dbtecno.pizzaboygbapro"
            ),
            activityName = null,
            supportedSystems = listOf("gba"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %EXTRA_rom_uri%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "myboy",
            displayName = "My Boy!",
            packageNames = listOf(
                "com.fastemulator.gba",
                "com.fastemulator.gbafull"
            ),
            activityName = null,
            supportedSystems = listOf("gba"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // GB / GBC
        KnownEmulator(
            id = "gbc_emu",
            displayName = "GBC.emu",
            packageNames = listOf("com.explusalpha.GbcEmu"),
            activityName = "com.imagine.BaseActivity",
            supportedSystems = listOf("gb", "gbc"),
            launchCommand = "%DATA%=%ROMPROVIDER%"
        ),
        KnownEmulator(
            id = "pizza_boy_gbc",
            displayName = "Pizza Boy GBC",
            packageNames = listOf(
                "it.dbtecno.pizzaboy",
                "it.dbtecno.pizzaboypro"
            ),
            activityName = null,
            supportedSystems = listOf("gb", "gbc"),
            launchCommand = "%ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %EXTRA_rom_uri%=%ROM%"
        ),
        KnownEmulator(
            id = "myoldboy",
            displayName = "My OldBoy!",
            packageNames = listOf(
                "com.fastemulator.gbc",
                "com.fastemulator.gbcfull"
            ),
            activityName = null,
            supportedSystems = listOf("gb", "gbc"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // NES
        KnownEmulator(
            id = "nes_emu",
            displayName = "NES.emu",
            packageNames = listOf("com.explusalpha.NesEmu"),
            activityName = "com.imagine.BaseActivity",
            supportedSystems = listOf("nes", "fds", "famicom"),
            launchCommand = "%DATA%=%ROMPROVIDER%"
        ),

        // SNES
        KnownEmulator(
            id = "snes9x_ex",
            displayName = "Snes9x EX+",
            packageNames = listOf("com.explusalpha.Snes9xPlus"),
            activityName = "com.imagine.BaseActivity",
            supportedSystems = listOf("snes", "superfamicom", "satellaview", "sufami"),
            launchCommand = "%DATA%=%ROMSAF%"
        ),

        // PS Vita
        KnownEmulator(
            id = "vita3k",
            displayName = "Vita3K",
            packageNames = listOf("org.vita3k.emulator"),
            activityName = null,
            supportedSystems = listOf("psvita"),
            launchCommand = "%EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita",
            emulationActivity = "org.vita3k.emulator.Emulator"
        ),
        KnownEmulator(
            id = "vita3k_zx",
            displayName = "Vita3K ZX",
            packageNames = listOf("org.vita3k.emulator.ikhoeyZX"),
            activityName = null,
            supportedSystems = listOf("psvita"),
            launchCommand = "%EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita",
            emulationActivity = "org.vita3k.emulator.Emulator"
        ),

        // Wii U
        KnownEmulator(
            id = "cemu",
            displayName = "Cemu",
            packageNames = listOf("info.cemu.Cemu", "info.cemu.cemu"),
            activityName = null,
            supportedSystems = listOf("wiiu"),
            launchCommand = "%DATA%=%ROMSAF%",
            emulationActivity = "info.cemu.cemu.emulation.EmulationActivity"
        ),
        KnownEmulator(
            id = "cemu_fork",
            displayName = "Cemu Fork",
            packageNames = listOf("info.cemu.cemu.debug"),
            activityName = null,
            supportedSystems = listOf("wiiu"),
            launchCommand = "%DATA%=%ROMSAF%",
            emulationActivity = "info.cemu.cemu.emulation.EmulationActivity"
        ),

        // Windows / PC Games (via Wine/Proton-based launchers)
        KnownEmulator(
            id = "gamehub",
            displayName = "GameHub",
            packageNames = listOf("com.nickmafra.gamehub"),
            activityName = "com.nickmafra.gamehub.MainActivity",
            supportedSystems = listOf("windows", "pc"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "gamehub_lite",
            displayName = "GameHub Lite",
            packageNames = listOf("com.nickmafra.gamehublite", "gamehub.lite"),
            activityName = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity",
            supportedSystems = listOf("windows", "pc"),
            // For .steam files: pass basename (Steam App ID) and autoStartGame=true to launch directly
            launchCommand = "%ACTION%=gamehub.lite.LAUNCH_GAME %EXTRA_steamAppId%=%BASENAME% %EXTRA_autoStartGame%=true",
            emulationActivity = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity",
            supportedExtensions = ".steam"
        ),
        KnownEmulator(
            id = "gamenative_steam",
            displayName = "GameNative (Steam)",
            packageNames = listOf("app.gamenative", "com.nickmafra.gamenative"),
            activityName = "app.gamenative.MainActivity",
            supportedSystems = listOf("windows", "pc"),
            // %EXTRAINTEGER_app_id% passes the Steam App ID as an integer
            // %INJECT%=%ROM% reads the .steam file content (Steam App ID) and uses it as the extra value
            launchCommand = "%ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=STEAM",
            supportedExtensions = ".steam"
        ),
        KnownEmulator(
            id = "gamenative_gog",
            displayName = "GameNative (GOG)",
            packageNames = listOf("app.gamenative", "com.nickmafra.gamenative"),
            activityName = "app.gamenative.MainActivity",
            supportedSystems = listOf("windows", "pc"),
            // %EXTRAINTEGER_app_id% passes the GOG product ID as an integer (GameNative uses getIntExtra)
            // %INJECT%=%ROM% reads the .gog file content (product ID) and uses it as the extra value
            launchCommand = "%ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=GOG",
            supportedExtensions = ".gog"
        ),
        KnownEmulator(
            id = "gamenative_epic",
            displayName = "GameNative (Epic)",
            packageNames = listOf("app.gamenative", "com.nickmafra.gamenative"),
            activityName = "app.gamenative.MainActivity",
            supportedSystems = listOf("windows", "pc"),
            // %EXTRAINTEGER_app_id% passes the Epic internal DB ID as an integer (GameNative uses getIntExtra)
            // %INJECT%=%ROM% reads the .epic file content (internal DB ID) and uses it as the extra value
            launchCommand = "%ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=EPIC",
            supportedExtensions = ".epic"
        ),
        KnownEmulator(
            id = "gamenative_amazon",
            displayName = "GameNative (Amazon)",
            packageNames = listOf("app.gamenative", "com.nickmafra.gamenative"),
            activityName = "app.gamenative.MainActivity",
            supportedSystems = listOf("windows", "pc"),
            // %EXTRAINTEGER_app_id% passes the Amazon internal DB ID as an integer (GameNative uses getIntExtra)
            // %INJECT%=%ROM% reads the .amazon file content (internal DB ID) and uses it as the extra value
            launchCommand = "%ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=AMAZON",
            supportedExtensions = ".amazon"
        ),
        KnownEmulator(
            id = "winlator",
            displayName = "Winlator",
            packageNames = listOf(
                "com.winlator",
                "com.winlator.debug"
            ),
            activityName = null,
            supportedSystems = listOf("windows", "pc"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "mobox",
            displayName = "Mobox",
            packageNames = listOf("com.xtr3d.mobox"),
            activityName = null,
            supportedSystems = listOf("windows", "pc"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // Xbox 360
        KnownEmulator(
            id = "ax360e",
            displayName = "aX360e",
            packageNames = listOf("aenu.ax360e.free", "aenu.ax360e"),
            activityName = null,
            supportedSystems = listOf("xbox360"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),

        // PlayStation 3
        KnownEmulator(
            id = "aps3e",
            displayName = "aPS3e",
            packageNames = listOf("aenu.aps3e"),
            activityName = null,
            supportedSystems = listOf("ps3"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        ),
        KnownEmulator(
            id = "rpcsx",
            displayName = "RPCSX",
            packageNames = listOf("org.rpcsx.rpcsx", "com.rpcsx.android"),
            activityName = null,
            supportedSystems = listOf("ps3"),
            launchCommand = "%ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%"
        )
    )

    /**
     * Find emulators that support a given system
     */
    fun getEmulatorsForSystem(systemId: String): List<KnownEmulator> {
        return emulators.filter { it.supportedSystems.contains(systemId) }
    }

    /**
     * Find a known emulator by package name (exact match)
     */
    fun findByPackageName(packageName: String): KnownEmulator? {
        return emulators.find { it.packageNames.contains(packageName) }
    }

    /**
     * Find a known emulator by package name pattern (fuzzy match)
     * This helps match emulators with varying package names (e.g., different forks)
     */
    fun findByPackageNameFuzzy(packageName: String, appName: String): KnownEmulator? {
        // First try exact match
        findByPackageName(packageName)?.let { return it }

        // Try partial package name matching
        val packageLower = packageName.lowercase()
        val appNameLower = appName.lowercase()

        // Match by common emulator patterns in package name
        return when {
            // Vita3K variants
            packageLower.contains("vita3k") -> emulators.find { it.id == "vita3k" }

            // Cemu variants
            packageLower.contains("cemu") -> emulators.find { it.id == "cemu" }

            // Switch emulators (Yuzu-based)
            packageLower.contains("yuzu") || appNameLower.contains("yuzu") ->
                emulators.find { it.id == "yuzu" }

            // Eden (Yuzu fork)
            packageLower.contains("eden") || appNameLower.contains("eden") ->
                emulators.find { it.id == "eden" }

            // Sudachi
            packageLower.contains("sudachi") || appNameLower.contains("sudachi") ->
                emulators.find { it.id == "sudachi" }

            // Citron
            packageLower.contains("citron") || appNameLower.contains("citron") ->
                emulators.find { it.id == "citron" }

            // Suyu
            packageLower.contains("suyu") || appNameLower.contains("suyu") ->
                emulators.find { it.id == "suyu" }

            // RetroArch
            packageLower.contains("retroarch") -> emulators.find { it.id == "retroarch" }

            // Dolphin
            packageLower.contains("dolphin") -> emulators.find { it.id == "dolphin" }

            // PPSSPP
            packageLower.contains("ppsspp") -> emulators.find { it.id == "ppsspp" }

            // DuckStation
            packageLower.contains("duckstation") -> emulators.find { it.id == "duckstation" }

            // DraStic
            packageLower.contains("drastic") -> emulators.find { it.id == "drastic" }

            // Citra / Lime3DS
            packageLower.contains("citra") -> emulators.find { it.id == "citra" }
            packageLower.contains("lime3ds") -> emulators.find { it.id == "lime3ds" }

            // AetherSX2 / NetherSX2
            packageLower.contains("aethersx2") -> emulators.find { it.id == "aethersx2" }
            packageLower.contains("nethersx2") -> emulators.find { it.id == "nethersx2" }

            // Mupen64Plus
            packageLower.contains("mupen64") -> emulators.find { it.id == "mupen64plus_fz" }

            // melonDS
            packageLower.contains("melonds") -> emulators.find { it.id == "melonds" }

            // Redream / Flycast
            packageLower.contains("redream") -> emulators.find { it.id == "redream" }
            packageLower.contains("flycast") -> emulators.find { it.id == "flycast" }

            // Windows game launchers
            packageLower.contains("gamehub") && !packageLower.contains("lite") ->
                emulators.find { it.id == "gamehub" }
            packageLower.contains("gamehublite") || (packageLower.contains("gamehub") && packageLower.contains("lite")) ->
                emulators.find { it.id == "gamehub_lite" }
            packageLower.contains("gamenative") -> emulators.find { it.id == "gamenative_gog" }
            packageLower.contains("winlator") -> emulators.find { it.id == "winlator" }
            packageLower.contains("mobox") -> emulators.find { it.id == "mobox" }

            // Xbox 360
            packageLower.contains("ax360e") || appNameLower.contains("ax360e") ->
                emulators.find { it.id == "ax360e" }

            // PlayStation 3
            packageLower.contains("aps3e") || appNameLower.contains("aps3e") ->
                emulators.find { it.id == "aps3e" }
            packageLower.contains("rpcsx") || appNameLower.contains("rpcsx") ->
                emulators.find { it.id == "rpcsx" }

            else -> null
        }
    }

    /**
     * Get all unique supported system IDs
     */
    fun getAllSupportedSystems(): Set<String> {
        return emulators.flatMap { it.supportedSystems }.toSet()
    }
}
