package com.appcues

import com.appcues.LoggingLevel.NONE

/**
 * A configuration object that defines the behavior and policies for Appcues.
 */
data class AppcuesConfig internal constructor(
    internal val accountId: String,
    internal val applicationId: String,
) {
    internal companion object {
        const val SESSION_TIMEOUT_DEFAULT = 1800 // 30 minutes by default
        const val ACTIVITY_STORAGE_MAX_SIZE = 25
    }

    /**
     * Set the logging level for the SDK.
     */
    var loggingLevel: LoggingLevel = NONE

    /**
     * Defines a custom api base path for the SDK.  This path should consist of the scheme, host, and any additional
     * path prefix required. If Not defined it will point to the default Appcues host: https://api.appcues.net/
     */
    var apiBasePath: String? = null

    /**
     * Set the factory responsible for generating anonymous user IDs.
     */
    var anonymousIdFactory: (() -> String)? = null

    /**
     *  Set the session timeout for the configuration, in seconds. This timeout value is used to determine if a new session is started
     *  upon the application returning to the foreground. The default value is 1800 seconds (30 minutes).
     */
    var sessionTimeout: Int = SESSION_TIMEOUT_DEFAULT
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /**
     * Set the activity storage max size for the configuration - maximum 25, minimum 0. This value determines how many analytics
     * requests can be stored on the local device and retried later, in the case of the device network connection being unavailable.
     * Only the most recent requests, up to this count, are retained.
     */
    var activityStorageMaxSize: Int = ACTIVITY_STORAGE_MAX_SIZE
        set(value) {
            field = value.coerceAtLeast(0).coerceAtMost(ACTIVITY_STORAGE_MAX_SIZE)
        }

    /**
     *  Sets the activity storage max age for the configuration, in seconds.  This value determines how long an item can be stored
     *  on the local device and retried later, in the case of the device network connection being unavailable.  Only
     *  requests that are more recent than the max age will be retried - or all, if not set.
     */
    var activityStorageMaxAge: Int? = null
        set(value) {
            field = value?.coerceAtLeast(0)
        }

    /**
     * Set the interceptor for additional control over SDK runtime behaviors.
     */
    var interceptor: AppcuesInterceptor? = null

    /**
     * Set the listener to be notified about the display of Experience content.
     */
    var experienceListener: ExperienceListener? = null

    /**
     * Sets the listener to be notified about published analytics.
     */
    var analyticsListener: AnalyticsListener? = null
}
