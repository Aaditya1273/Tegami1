package com.tegami.di

import android.content.Context
import android.content.SharedPreferences
import com.tegami.BuildConfig
import com.tegami.config.NetworkConfig
import com.tegami.evm.EvmRpc
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    @Provides
    @Singleton
    fun sharedPrefs(@ApplicationContext ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("tegami", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun networkConfig(@ApplicationContext ctx: Context): NetworkConfig =
        NetworkConfig.parse(ctx.assets.open("${BuildConfig.NETWORK}.json").bufferedReader().use { it.readText() })
            .also { check(it.network == BuildConfig.NETWORK) { "asset/network mismatch" } }

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(Android) {
        engine {
            connectTimeout = 10_000
            socketTimeout = 20_000
        }
    }

    @Provides
    @Singleton
    fun evmRpc(http: HttpClient, config: NetworkConfig) = EvmRpc(http, config.rpcUrl)
}
