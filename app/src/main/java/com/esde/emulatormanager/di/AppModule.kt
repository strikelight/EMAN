package com.esde.emulatormanager.di

import android.content.Context
import com.esde.emulatormanager.data.parser.EsdeConfigParser
import com.esde.emulatormanager.data.parser.EsdeConfigWriter
import com.esde.emulatormanager.data.repository.EmulatorRepository
import com.esde.emulatormanager.data.service.AndroidGamesService
import com.esde.emulatormanager.data.service.CustomEmulatorService
import com.esde.emulatormanager.data.service.DeviceIdentificationService
import com.esde.emulatormanager.data.service.EmulatorDetectionService
import com.esde.emulatormanager.data.service.EsdeConfigService
import com.esde.emulatormanager.data.service.GamelistService
import com.esde.emulatormanager.data.service.GogApiService
import com.esde.emulatormanager.data.service.IgdbService
import com.esde.emulatormanager.data.service.MetadataService
import com.esde.emulatormanager.data.service.ProfileService
import com.esde.emulatormanager.data.service.ScreenScraperService
import com.esde.emulatormanager.data.service.SteamApiService
import com.esde.emulatormanager.data.service.VitaGamesService
import com.esde.emulatormanager.data.service.WindowsGamesService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEsdeConfigParser(): EsdeConfigParser {
        return EsdeConfigParser()
    }

    @Provides
    @Singleton
    fun provideEsdeConfigWriter(): EsdeConfigWriter {
        return EsdeConfigWriter()
    }

    @Provides
    @Singleton
    fun provideEmulatorDetectionService(
        @ApplicationContext context: Context
    ): EmulatorDetectionService {
        return EmulatorDetectionService(context)
    }

    @Provides
    @Singleton
    fun provideEsdeConfigService(
        @ApplicationContext context: Context,
        parser: EsdeConfigParser,
        writer: EsdeConfigWriter
    ): EsdeConfigService {
        return EsdeConfigService(context, parser, writer)
    }

    @Provides
    @Singleton
        fun provideCustomEmulatorService(
        @ApplicationContext context: Context
    ): CustomEmulatorService {
        return CustomEmulatorService(context)
    }

    @Provides
    @Singleton
    fun provideEmulatorRepository(
        emulatorDetectionService: EmulatorDetectionService,
        esdeConfigService: EsdeConfigService,
        customEmulatorService: CustomEmulatorService
    ): EmulatorRepository {
        return EmulatorRepository(emulatorDetectionService, esdeConfigService, customEmulatorService)
    }

    @Provides
    @Singleton
    fun provideWindowsGamesService(
        @ApplicationContext context: Context,
        esdeConfigService: EsdeConfigService
    ): WindowsGamesService {
        return WindowsGamesService(context, esdeConfigService)
    }

    @Provides
    @Singleton
    fun provideSteamApiService(): SteamApiService {
        return SteamApiService()
    }

    @Provides
    @Singleton
    fun provideGogApiService(): GogApiService {
        return GogApiService()
    }

    @Provides
    @Singleton
    fun provideDeviceIdentificationService(): DeviceIdentificationService {
        return DeviceIdentificationService()
    }

    @Provides
    @Singleton
    fun provideProfileService(
        @ApplicationContext context: Context,
        esdeConfigService: EsdeConfigService,
        customEmulatorService: CustomEmulatorService,
        androidGamesService: AndroidGamesService,
        windowsGamesService: WindowsGamesService,
        deviceIdentificationService: DeviceIdentificationService,
        igdbService: IgdbService
    ): ProfileService {
        return ProfileService(
            context,
            esdeConfigService,
            customEmulatorService,
            androidGamesService,
            windowsGamesService,
            deviceIdentificationService,
            igdbService
        )
    }

    @Provides
    @Singleton
    fun provideGamelistService(
        esdeConfigService: EsdeConfigService
    ): GamelistService {
        return GamelistService(esdeConfigService)
    }

    @Provides
    @Singleton
    fun provideIgdbService(
        @ApplicationContext context: Context,
        esdeConfigService: EsdeConfigService
    ): IgdbService {
        return IgdbService(context, esdeConfigService)
    }

    @Provides
    @Singleton
    fun provideScreenScraperService(
        @ApplicationContext context: Context
    ): ScreenScraperService {
        return ScreenScraperService(context)
    }

    @Provides
    @Singleton
    fun provideVitaGamesService(
        @ApplicationContext context: Context,
        esdeConfigService: EsdeConfigService,
        gamelistService: GamelistService
    ): VitaGamesService {
        return VitaGamesService(context, esdeConfigService, gamelistService)
    }

    @Provides
    @Singleton
    fun provideMetadataService(
        steamApiService: SteamApiService,
        gogApiService: GogApiService,
        igdbService: IgdbService,
        gamelistService: GamelistService,
        esdeConfigService: EsdeConfigService,
        windowsGamesService: WindowsGamesService,
        androidGamesService: AndroidGamesService,
        screenScraperService: ScreenScraperService,
        vitaGamesService: VitaGamesService
    ): MetadataService {
        return MetadataService(
            steamApiService,
            gogApiService,
            igdbService,
            gamelistService,
            esdeConfigService,
            windowsGamesService,
            androidGamesService,
            screenScraperService,
            vitaGamesService
        )
    }
}