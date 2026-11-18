package com.limette.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.feiNiuDataStore: DataStore<Preferences> by preferencesDataStore(name = "limette_feiniumedia")

data class FeiNiuConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val username: String = "",
    val password: String = ""
) {
    val isUsable: Boolean get() = enabled && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/** 飞牛影视连接配置（仅用于读取 NAS 上已刮削好的海报/缩略图，纯局域网访问）。 */
@Singleton
class FeiNiuPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val BASE_URL = stringPreferencesKey("base_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    val config: Flow<FeiNiuConfig> = context.feiNiuDataStore.data.map { p ->
        FeiNiuConfig(
            enabled = p[Keys.ENABLED] ?: false,
            baseUrl = p[Keys.BASE_URL].orEmpty(),
            username = p[Keys.USERNAME].orEmpty(),
            password = p[Keys.PASSWORD].orEmpty()
        )
    }

    suspend fun current(): FeiNiuConfig = config.first()

    suspend fun save(config: FeiNiuConfig) {
        context.feiNiuDataStore.edit { p ->
            p[Keys.ENABLED] = config.enabled
            p[Keys.BASE_URL] = config.baseUrl.trim().trimEnd('/')
            p[Keys.USERNAME] = config.username.trim()
            p[Keys.PASSWORD] = config.password
        }
    }
}
