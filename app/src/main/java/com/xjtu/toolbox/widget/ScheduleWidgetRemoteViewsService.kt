package com.xjtu.toolbox.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.xjtu.toolbox.R
import com.xjtu.toolbox.util.XjtuTime

class ScheduleWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetCourseFactory(applicationContext)
    }
}

private class ScheduleWidgetCourseFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val toneBackgrounds = intArrayOf(
        R.drawable.bg_schedule_course_item,
        R.drawable.bg_schedule_course_item_2,
        R.drawable.bg_schedule_course_item_3,
        R.drawable.bg_schedule_course_item_4,
        R.drawable.bg_schedule_course_item_5,
        R.drawable.bg_schedule_course_item_6
    )

    private var courses: List<WidgetCourse> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        courses = ScheduleWidgetUpdater.loadScheduleData(context).courses
    }

    override fun onDestroy() {
        courses = emptyList()
    }

    override fun getCount(): Int = courses.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in courses.indices) return null
        val course = courses[position]
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_course_item)
        val toneIndex = ((course.name + course.location + course.startSection + course.endSection)
            .hashCode()
            .let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % toneBackgrounds.size
        views.setInt(
            R.id.widget_course_item_root,
            "setBackgroundResource",
            toneBackgrounds[toneIndex]
        )
        views.setTextViewText(
            R.id.widget_course_time,
            "${XjtuTime.getClassStartStr(course.startSection)}-${XjtuTime.getClassEndStr(course.endSection)}"
        )
        views.setTextViewText(
            R.id.widget_course_desc,
            "${course.name} · ${course.location.ifBlank { "地点待定" }}"
        )
        views.setOnClickFillInIntent(R.id.widget_course_item_root, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
