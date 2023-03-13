package com.appcues.analytics

import com.appcues.AppcuesConfig
import com.appcues.Storage
import com.appcues.analytics.AnalyticsEvent.ScreenView
import com.appcues.data.remote.appcues.request.ActivityRequest
import com.appcues.data.remote.appcues.request.EventRequest

internal class ActivityRequestBuilder(
    private val config: AppcuesConfig,
    private val storage: Storage,
    private val decorator: AutoPropertyDecorator,
) {

    companion object {

        const val SCREEN_TITLE_ATTRIBUTE = "screenTitle"
        const val SCREEN_TITLE_CONTEXT = "screen_title"
    }

    fun identify(properties: Map<String, Any>? = null) = decorator.decorateIdentify(
        ActivityRequest(
            userId = storage.userId,
            accountId = config.accountId,
            groupId = storage.groupId,
            profileUpdate = properties?.toMutableMap(),
            userSignature = storage.userSignature,
        )
    )

    fun group(properties: Map<String, Any>? = null) = ActivityRequest(
        userId = storage.userId,
        accountId = config.accountId,
        groupId = storage.groupId,
        groupUpdate = properties, // no auto-properties on group calls
        userSignature = storage.userSignature,
    )

    fun track(name: String, properties: Map<String, Any>? = null): ActivityRequest {
        // must do this decoration first, so that any auto-prop updates resulting from it get applied before
        // using in the profileUpdate below
        val trackEvent = decorator.decorateTrack(
            EventRequest(name = name, attributes = properties?.toMutableMap() ?: hashMapOf())
        )

        return ActivityRequest(
            userId = storage.userId,
            profileUpdate = decorator.autoProperties.toMutableMap(),
            accountId = config.accountId,
            groupId = storage.groupId,
            events = listOf(trackEvent),
            userSignature = storage.userSignature,
        )
    }

    fun screen(title: String, properties: MutableMap<String, Any>? = null): ActivityRequest {
        // must do this decoration first, so that any auto-prop updates resulting from it get applied before
        // using in the profileUpdate below
        val screenEvent = decorator.decorateTrack(
            EventRequest(
                // screen calls are really just a special type of event: "appcues:screen_view"
                name = ScreenView.eventName,
                attributes = (properties ?: hashMapOf()).apply { put(SCREEN_TITLE_ATTRIBUTE, title) },
                context = hashMapOf(SCREEN_TITLE_CONTEXT to title)
            )
        )

        return ActivityRequest(
            userId = storage.userId,
            profileUpdate = decorator.autoProperties.toMutableMap(),
            accountId = config.accountId,
            groupId = storage.groupId,
            events = listOf(screenEvent),
            userSignature = storage.userSignature,
        )
    }
}
