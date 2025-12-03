
package com.example.gcs

import android.content.Context
import com.example.voiptest.R
import com.google.auth.oauth2.GoogleCredentials

object GoogleCloudAuth {
    fun getCredentials(context: Context): GoogleCredentials {
        // The credential file is located in res/raw, so we open it using openRawResource
        val inputStream = context.resources.openRawResource(R.raw.credentials)
        return GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
    }
}
