package com.appcues.debugger

import com.appcues.AnalyticType.EVENT
import com.appcues.AnalyticType.GROUP
import com.appcues.AnalyticType.IDENTIFY
import com.appcues.AnalyticType.SCREEN
import com.appcues.R
import com.appcues.analytics.ActivityRequestBuilder
import com.appcues.analytics.AnalyticsTracker
import com.appcues.analytics.AutoPropertyDecorator
import com.appcues.analytics.ExperienceLifecycleEvent
import com.appcues.analytics.SdkMetrics
import com.appcues.analytics.TrackingData
import com.appcues.data.model.ExperienceStepFormState
import com.appcues.data.remote.appcues.request.ActivityRequest
import com.appcues.data.remote.appcues.request.EventRequest
import com.appcues.debugger.model.DebuggerEventItem
import com.appcues.debugger.model.DebuggerEventItemPropertySection
import com.appcues.debugger.model.EventType
import com.appcues.debugger.ui.toEventTitle
import com.appcues.debugger.ui.toEventType
import com.appcues.util.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.coroutines.CoroutineContext

internal class DebuggerRecentEventsManager(
    private val contextWrapper: ContextWrapper,
    private val analyticsTracker: AnalyticsTracker,
) : CoroutineScope {

    companion object {

        private const val MAX_RECENT_EVENTS = 20
        private const val IDENTITY_PROPERTY_PREFIX = "_"
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val events: ArrayList<DebuggerEventItem> = arrayListOf()

    private var filterType: EventType? = null
    private val mutex = Mutex()

    private val _data = MutableSharedFlow<List<DebuggerEventItem>>(replay = 1)

    val data: MutableSharedFlow<List<DebuggerEventItem>>
        get() = _data

    private var lastEventId = 0

    private var isStarted = false

    fun start() {
        if (isStarted) return

        launch {
            analyticsTracker.analyticsFlow.collect { onTrackingData(it) }
        }

        isStarted = true
    }

    fun reset() {
        isStarted = false
        coroutineContext.cancelChildren()
        events.clear()
    }

    private suspend fun onTrackingData(trackingData: TrackingData) = withContext(Dispatchers.IO) {
        when (trackingData.type) {
            IDENTIFY -> onIdentifyActivityRequest(trackingData.request)
            GROUP -> onGroupUpdateActivityRequest(trackingData.request)
            EVENT -> onActivityRequest(trackingData.request)
            SCREEN -> onActivityRequest(trackingData.request)
        }

        if (events.size == 1) {
            events.first().showOnFab = false
        }

        updateData()
    }

    private fun onIdentifyActivityRequest(request: ActivityRequest) {
        events.addFirst(
            DebuggerEventItem(
                id = lastEventId,
                type = EventType.USER_PROFILE,
                // it should always contain updated at property, this is just a safeguard
                // in case something changes in the future to avoid unwanted exceptions
                timestamp = ((request.profileUpdate?.get(AutoPropertyDecorator.UPDATED_AT_PROPERTY) as Date?) ?: Date()).time,
                name = request.userId,
                propertySections = listOf(
                    DebuggerEventItemPropertySection(
                        title = contextWrapper.getString(R.string.appcues_debugger_event_details_properties_title),
                        properties = request.profileUpdate?.toSortedList(),
                    )
                )
            )
        )

        lastEventId++
    }

    private fun onGroupUpdateActivityRequest(request: ActivityRequest) {
        events.addFirst(
            DebuggerEventItem(
                id = lastEventId,
                type = EventType.GROUP_UPDATE,
                timestamp = Date().time,
                name = request.groupId ?: contextWrapper.getString(R.string.appcues_debugger_event_type_group_update_title),
                propertySections = listOf(
                    DebuggerEventItemPropertySection(
                        title = contextWrapper.getString(R.string.appcues_debugger_event_details_properties_title),
                        properties = request.groupUpdate?.toSortedList(),
                    )
                )
            )
        )
        lastEventId++
    }

    private fun onActivityRequest(request: ActivityRequest) {
        request.events?.forEach { event ->
            val type = event.name.toEventType()
            val title = event.name.toEventTitle()?.let { contextWrapper.getString(it) }
            val displayName = getEventDisplayName(event, type, title)

            events.addFirst(
                DebuggerEventItem(
                    id = lastEventId,
                    type = type,
                    timestamp = event.timestamp.time,
                    name = displayName,
                    propertySections = listOf(
                        DebuggerEventItemPropertySection(
                            title = contextWrapper.getString(R.string.appcues_debugger_event_details_properties_title),
                            properties = event.attributes
                                .filterOutScreenProperties(type)
                                .filterOutAutoProperties()
                                .filterOutMetricsProperties()
                                .filterOutInteractionData()
                                .toSortedList(),
                        ),
                        DebuggerEventItemPropertySection(
                            title = contextWrapper.getString(R.string.appcues_debugger_event_details_form_response_title),
                            properties = event.attributes
                                .getFormResponse()
                                .toSortedList(),
                        ),
                        DebuggerEventItemPropertySection(
                            title = contextWrapper.getString(R.string.appcues_debugger_event_details_interaction_data),
                            properties = event.attributes
                                .getInteractionData()
                                .toSortedList(),
                        ),
                        DebuggerEventItemPropertySection(
                            title = contextWrapper.getString(R.string.appcues_debugger_event_details_identity_auto_properties_title),
                            properties = event.attributes
                                .getAutoProperties()
                                .toSortedList()
                        ),
                        DebuggerEventItemPropertySection(
                            title = contextWrapper.getString(R.string.appcues_debugger_event_details_sdk_metrics_properties_title),
                            properties = event.attributes
                                .getMetricsProperties()
                                .toSortedList()
                        )
                    )
                )
            )

            lastEventId++
        }
    }

    private fun getEventDisplayName(
        event: EventRequest,
        type: EventType,
        title: String?
    ) = if (type == EventType.SCREEN && event.attributes.contains(ActivityRequestBuilder.SCREEN_TITLE_ATTRIBUTE)) {
        // screen views are a special case where the title is the screen title
        event.attributes[ActivityRequestBuilder.SCREEN_TITLE_ATTRIBUTE] as String
    } else {
        // otherwise - use the given title for system events or the event name for custom events
        title ?: event.name
    }

    private fun Map<String, Any?>.toSortedList(): List<Pair<String, Any?>> = toList().let { list ->
        arrayListOf<Pair<String, Any?>>().apply {
            addAll(list.filter { it.first.startsWith(IDENTITY_PROPERTY_PREFIX).not() }.sortByPropertyName())
            addAll(list.filter { it.first.startsWith(IDENTITY_PROPERTY_PREFIX) }.sortByPropertyName())
        }
    }

    private fun ArrayList<DebuggerEventItem>.addFirst(element: DebuggerEventItem) {
        add(0, element)
    }

    suspend fun onApplyEventFilter(eventType: EventType?) = withContext(Dispatchers.IO) {
        filterType = eventType

        updateData()
    }

    private suspend fun updateData() = mutex.withLock {
        // this filtering of the `events` list is guarded with a Mutex since other threads
        // could update this list while we are processing through it
        val list = filterType?.let { eventType ->
            events.filter { it.type == eventType }.filterIndexed { index, _ -> index < MAX_RECENT_EVENTS }
        } ?: events.filterIndexed { index, _ -> index < MAX_RECENT_EVENTS }

        _data.emit(list)
    }
}

private fun Map<String, Any>.filterOutScreenProperties(eventType: EventType): Map<String, Any> {
    return if (eventType == EventType.SCREEN) {
        filterNot { it.key == ActivityRequestBuilder.SCREEN_TITLE_ATTRIBUTE }
    } else this
}

private fun Map<String, Any>.filterOutAutoProperties(): Map<String, Any> {
    return filterNot { it.key == AutoPropertyDecorator.IDENTITY_PROPERTY }
}

private fun Map<String, Any>.filterOutMetricsProperties(): Map<String, Any> {
    return filterNot { it.key == SdkMetrics.METRICS_PROPERTY }
}

private fun Map<String, Any>.getAutoProperties(): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return (this[AutoPropertyDecorator.IDENTITY_PROPERTY] as Map<String, Any>?) ?: mapOf()
}

private fun Map<String, Any>.getMetricsProperties(): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return (this[SdkMetrics.METRICS_PROPERTY] as Map<String, Any>?) ?: mapOf()
}

private fun Map<String, Any>.filterOutInteractionData(): Map<String, Any> {
    return filterNot { it.key == ExperienceLifecycleEvent.INTERACTION_DATA_KEY }
}

private fun Map<String, Any>.getFormResponse(): Map<String, Any?> {
    return (this[ExperienceLifecycleEvent.INTERACTION_DATA_KEY] as? ExperienceStepFormState)?.let {
        it.formItems.associate { itemState ->
            itemState.label to itemState.value
        }
    } ?: mapOf()
}

private fun Map<String, Any>.getInteractionData(): Map<String, Any?> {
    this[ExperienceLifecycleEvent.INTERACTION_DATA_KEY].let {
        if (it is ExperienceStepFormState || it == null) return mapOf()

        @Suppress("UNCHECKED_CAST")
        return it as Map<String, Any>
    }
}

private fun List<Pair<String, Any?>>.sortByPropertyName(): List<Pair<String, Any?>> = sortedBy { it.first }
