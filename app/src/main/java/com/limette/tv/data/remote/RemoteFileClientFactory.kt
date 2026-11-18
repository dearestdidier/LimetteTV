package com.limette.tv.data.remote

import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.feiniu.FeiNiuFileClient
import com.limette.tv.data.smb.SmbStreamServer
import com.limette.tv.data.webdav.WebDavClient
import com.limette.tv.domain.model.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/** 按媒体源协议分发文件客户端。 */
@Singleton
class RemoteFileClientFactory @Inject constructor(
    private val smbStreamServer: SmbStreamServer,
    private val feiNiuClient: FeiNiuClient
) {
    fun create(source: MediaSource, scanConcurrency: Int = 8): RemoteFileClient =
        when (source.type) {
            MediaSource.SourceType.SMB -> smbStreamServer.register(source)
            MediaSource.SourceType.WEBDAV -> WebDavClient(source, scanConcurrency)
            MediaSource.SourceType.FENIU -> FeiNiuFileClient(source, feiNiuClient)
        }
}
