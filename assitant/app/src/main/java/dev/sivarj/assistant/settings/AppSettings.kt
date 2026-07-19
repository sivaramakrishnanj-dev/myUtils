package dev.sivarj.assistant.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object PrefsKeys {
    val AWS_ACCESS_KEY = stringPreferencesKey("aws_access_key")
    val AWS_SECRET_KEY = stringPreferencesKey("aws_secret_key")
    val AWS_REGION = stringPreferencesKey("aws_region")
    val BEDROCK_MODEL_ID = stringPreferencesKey("bedrock_model_id")
    val S3_BUCKET = stringPreferencesKey("s3_bucket")
}

data class AwsConfig(
    val accessKey: String = "",
    val secretKey: String = "",
    val region: String = "us-east-1",
    val bedrockModelId: String = "us.anthropic.claude-sonnet-4-20250514-v1:0",
    val s3Bucket: String = "",
) {
    val isConfigured: Boolean
        get() = accessKey.isNotBlank() && secretKey.isNotBlank()
}

class AppSettings(private val context: Context) {

    val awsConfig: Flow<AwsConfig> = context.dataStore.data.map { prefs ->
        AwsConfig(
            accessKey = prefs[PrefsKeys.AWS_ACCESS_KEY] ?: "",
            secretKey = prefs[PrefsKeys.AWS_SECRET_KEY] ?: "",
            region = prefs[PrefsKeys.AWS_REGION] ?: "us-east-1",
            bedrockModelId = prefs[PrefsKeys.BEDROCK_MODEL_ID] ?: "us.anthropic.claude-sonnet-4-20250514-v1:0",
            s3Bucket = prefs[PrefsKeys.S3_BUCKET] ?: "",
        )
    }

    suspend fun save(config: AwsConfig) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.AWS_ACCESS_KEY] = config.accessKey
            prefs[PrefsKeys.AWS_SECRET_KEY] = config.secretKey
            prefs[PrefsKeys.AWS_REGION] = config.region
            prefs[PrefsKeys.BEDROCK_MODEL_ID] = config.bedrockModelId
            prefs[PrefsKeys.S3_BUCKET] = config.s3Bucket
        }
    }
}
