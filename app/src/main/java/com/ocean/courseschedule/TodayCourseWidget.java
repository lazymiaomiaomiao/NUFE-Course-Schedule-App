package com.ocean.courseschedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class TodayCourseWidget extends AppWidgetProvider {

    // 左侧彩色竖条的颜色
    private static final int[] ACCENT_COLORS = {
        Color.parseColor("#5b7a9d"),  // 灰蓝
        Color.parseColor("#8b6f5c"),  // 暖棕
        Color.parseColor("#7a8b6f"),  // 苔绿
        Color.parseColor("#8b5c7a"),  // 梅紫
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // 支持外部广播刷新（如课表导入后通知刷新）
        if ("com.ocean.courseschedule.WIDGET_REFRESH".equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, TodayCourseWidget.class));
            onUpdate(context, manager, ids);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);

        // 点击整个 widget 跳转至 App
        Intent launchIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        // 设置日期
        Date now = new Date();
        SimpleDateFormat dateFmt = new SimpleDateFormat("M.d", Locale.CHINA);
        String[] weekNames = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        Calendar cal = Calendar.getInstance();
        String dateStr = dateFmt.format(now) + " " + weekNames[cal.get(Calendar.DAY_OF_WEEK) - 1];
        views.setTextViewText(R.id.widget_date, dateStr);

        // 读取课表数据
        List<CourseItem> upcoming = getUpcomingCourses(context, now);

        // 清空课程容器
        views.removeAllViews(R.id.widget_courses_container);

        if (upcoming.isEmpty()) {
            // 没有课程
            views.setViewVisibility(R.id.widget_courses_container, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_courses_container, android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE);

            // 最多显示 2 门
            int count = Math.min(upcoming.size(), 2);
            for (int i = 0; i < count; i++) {
                CourseItem c = upcoming.get(i);
                RemoteViews row = buildCourseRow(context, c, i);
                views.addView(R.id.widget_courses_container, row);
            }
        }

        manager.updateAppWidget(widgetId, views);
    }

    private RemoteViews buildCourseRow(Context context, CourseItem c, int index) {
        // 用代码构建水平 LinearLayout: [竖条色块] [文本列]
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_today);

        // 由于 RemoteViews 只能使用预定义布局, 需要使用单独的 course row layout
        // 这里采用内嵌 XML 方式
        row = new RemoteViews(context.getPackageName(), R.layout.widget_course_row);

        int accent = ACCENT_COLORS[index % ACCENT_COLORS.length];
        row.setInt(R.id.course_accent, "setBackgroundColor", accent);
        row.setTextViewText(R.id.course_name, c.name);
        row.setTextViewText(R.id.course_location, c.location);
        row.setTextViewText(R.id.course_time, c.timeStart + " - " + c.timeEnd);

        return row;
    }

    // 获取今天还未结束的课程列表（按开始时间排序）
    private List<CourseItem> getUpcomingCourses(Context context, Date now) {
        List<CourseItem> result = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), "schedule.json");
            if (!file.exists()) return result;

            String json = readFile(file);
            JSONObject root = new JSONObject(json);

            // 计算当前周次
            String semesterStartStr = root.optString("semesterStart", "2026-03-02");
            int currentWeek = calcWeek(semesterStartStr, now);

            // 今天是星期几 (Mon=1 ~ Sun=7)
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            int todayDow = cal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2, ...
            int weekday = todayDow == Calendar.SUNDAY ? 7 : todayDow - 1;

            // 当前时间（分钟数）
            int nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

            JSONArray sessions = root.optJSONArray("sessions");
            if (sessions == null) return result;

            for (int i = 0; i < sessions.length(); i++) {
                JSONObject s = sessions.getJSONObject(i);
                if (s.optInt("weekday") != weekday) continue;

                // 检查周次
                JSONArray weeks = s.optJSONArray("weeks");
                if (weeks != null) {
                    boolean found = false;
                    for (int w = 0; w < weeks.length(); w++) {
                        if (weeks.getInt(w) == currentWeek) { found = true; break; }
                    }
                    if (!found) continue;
                }

                // 检查是否已经结束
                String endTime = s.optString("timeEnd", "23:59");
                int endMin = parseMinutes(endTime);
                if (endMin <= nowMinutes) continue; // 已上完的课移除

                CourseItem item = new CourseItem();
                item.name = s.optString("courseName", "课程");
                item.location = s.optString("location", "");
                // 精简教室名: 去掉 "仙林校区 " 前缀
                if (item.location.startsWith("仙林校区 ")) {
                    item.location = item.location.substring(5);
                }
                item.teacher = s.optString("teacher", "");
                item.timeStart = s.optString("timeStart", "08:20");
                item.timeEnd = s.optString("timeEnd", "09:45");
                item.startMinutes = parseMinutes(item.timeStart);
                result.add(item);
            }

            // 按开始时间排序
            Collections.sort(result, new Comparator<CourseItem>() {
                @Override
                public int compare(CourseItem a, CourseItem b) {
                    return a.startMinutes - b.startMinutes;
                }
            });

        } catch (Exception e) {
            // 解析失败时返回空列表
        }
        return result;
    }

    private int calcWeek(String semesterStart, Date now) {
        try {
            String[] parts = semesterStart.split("-");
            Calendar startCal = Calendar.getInstance();
            startCal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]), 0, 0, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            long diffMs = now.getTime() - startCal.getTimeInMillis();
            int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
            int week = diffDays >= 0 ? diffDays / 7 + 1 : 1;
            return Math.max(1, week);
        } catch (Exception e) {
            return 1;
        }
    }

    private int parseMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    // 内部数据类
    static class CourseItem {
        String name;
        String location;
        String teacher;
        String timeStart;
        String timeEnd;
        int startMinutes;
    }

    // 静态工具：从任意地方触发刷新所有小部件
    public static void refreshAll(Context context) {
        Intent intent = new Intent("com.ocean.courseschedule.WIDGET_REFRESH");
        intent.setComponent(new ComponentName(context, TodayCourseWidget.class));
        context.sendBroadcast(intent);
    }
}
