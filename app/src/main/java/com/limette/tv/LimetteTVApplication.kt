package com.limette.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.remote.LocalImageAuth
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class LimetteTVApplication : Application(), ImageLoaderFactory {

    /**
     * 图片加载统一带上鉴权：
     *  - /v/api/v1/sys/img/... → 飞牛影视 token
     *  - WebDAV 主机            → Basic 认证（用于 NAS 目录内本地封面）
     *  - 127.0.0.1 SMB 代理    → 无需认证
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url
                    val builder = request.newBuilder()
                    when {
                        url.encodedPath.startsWith("/v/api/v1/sys/img") ->
                            FeiNiuClient.Auth.tokenFor(url.host)?.let { builder.header("Authorization", it) }

                        url.host != "127.0.0.1" ->
                            LocalImageAuth.basicFor("${url.host}:${url.port}")
                                ?.let { builder.header("Authorization", it) }
                    }
                    chain.proceed(builder.build())
                }
                .build()
        )
        .crossfade(true)
        .build()
}
