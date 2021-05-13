package org.session.libsession.utilities

import android.content.Context
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import okio.Buffer
import org.session.libsession.messaging.file_server.FileServerAPIV2
import org.session.libsession.utilities.preferences.ProfileKeyUtil
import org.session.libsignal.service.api.crypto.ProfileCipherOutputStream
import org.session.libsignal.service.internal.push.ProfileAvatarData
import org.session.libsignal.service.internal.push.http.DigestingRequestBody
import org.session.libsignal.service.internal.push.http.ProfileCipherOutputStreamFactory
import org.session.libsignal.service.loki.utilities.retryIfNeeded
import org.session.libsignal.utilities.ThreadUtils
import java.io.ByteArrayInputStream
import java.util.*

object ProfilePictureUtilities {

    fun upload(profilePicture: ByteArray, encodedProfileKey: String, context: Context): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        ThreadUtils.queue {
            val inputStream = ByteArrayInputStream(profilePicture)
            val outputStream = ProfileCipherOutputStream.getCiphertextLength(profilePicture.size.toLong())
            val profileKey = ProfileKeyUtil.getProfileKeyFromEncodedString(encodedProfileKey)
            val pad = ProfileAvatarData(inputStream, outputStream, "image/jpeg", ProfileCipherOutputStreamFactory(profileKey))
            val drb = DigestingRequestBody(pad.data, pad.outputStreamFactory, pad.contentType, pad.dataLength, null)
            val b = Buffer()
            drb.writeTo(b)
            val data = b.readByteArray()
            var id: Long = 0
            try {
                id = retryIfNeeded(4) {
                    FileServerAPIV2.upload(data)
                }.get()
            } catch (e: Exception) {
                deferred.reject(e)
            }
            TextSecurePreferences.setLastProfilePictureUpload(context, Date().time)
            val url = "${FileServerAPIV2.SERVER}/files/$id"
            TextSecurePreferences.setProfilePictureURL(context, url)
            deferred.resolve(Unit)
        }
        return deferred.promise
    }
}