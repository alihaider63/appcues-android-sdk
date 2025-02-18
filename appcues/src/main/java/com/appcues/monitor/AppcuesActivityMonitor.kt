package com.appcues.monitor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

internal object AppcuesActivityMonitor : Application.ActivityLifecycleCallbacks {

    interface ActivityMonitorListener {

        fun onActivityChanged(activity: Activity)

        fun onConfigurationChanged(activity: Activity)
    }

    private var _isPaused = false
    val isPaused: Boolean
        get() = _isPaused

    private var notifyConfigurationChanged = false

    private var activityWeakReference: WeakReference<Activity>? = null
    val activity: Activity?
        get() = activityWeakReference?.get()

    private val activityMonitorListener: HashSet<ActivityMonitorListener> = hashSetOf()

    fun subscribe(listener: ActivityMonitorListener) {
        activityMonitorListener.add(listener)
    }

    fun unsubscribe(listener: ActivityMonitorListener) {
        activityMonitorListener.remove(listener)
    }

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun reset(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        _isPaused = true
        activityWeakReference = null
    }

    override fun onActivityResumed(activity: Activity) {
        _isPaused = false

        if (this.activity == null) {
            this.activityWeakReference = WeakReference(activity)
            // notifies all subscribers that activity has changed
            activityMonitorListener.forEach { it.onActivityChanged(activity) }
        }

        if (notifyConfigurationChanged) {
            // notifies all subscribers that activity has changed
            activityMonitorListener.forEach { it.onConfigurationChanged(activity) }
            notifyConfigurationChanged = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // whenever we hit onActivityCreated + savedInstanceState is not null
        // it means configuration has changed (same activity was re-created
        if (savedInstanceState != null) {
            notifyConfigurationChanged = true
        } else {
            if (this.activity != activity) {
                this.activityWeakReference = null
            }
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) {
        _isPaused = true
    }

    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
