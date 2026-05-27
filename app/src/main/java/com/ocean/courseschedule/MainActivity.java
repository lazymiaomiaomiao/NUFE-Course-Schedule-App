package com.ocean.courseschedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private RelativeLayout rootLayout;
    
    // 关键点：用 100% 纯原生 Android 控件（Layout/TextView/Button）构建底部引导条
    // 这样它完全脱离网页 DOM，彻底免疫双指捏合缩放（Zoom）带来的缩水 bug，永远保持完美尺寸和清晰度！
    private LinearLayout nativeBanner;
    private TextView bannerText;
    private Button bannerButton;
    
    private boolean isDataDetected = false;
    private String capturedData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // 开启捏合缩放支持
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // 隐藏系统难看的放大镜按钮
        
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        WebView.setWebContentsDebuggingEnabled(true);

        // 绑定 JS 桥接接口
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        // 设置定制 WebViewClient 以忽略证书错误，并在合适时机切换原生底部浮栏的可见性
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 忽略高校教务系统常见的证书错误，防止白屏
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // 仅在访问南财教务系统网页时显示原生底部引导浮栏并重置其状态
                if (url.contains("jwxt.nufe.edu.cn") || url.contains("nufe.edu.cn")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            nativeBanner.setVisibility(View.VISIBLE);
                            isDataDetected = false;
                            capturedData = null;
                            
                            // 重置底部条为 Material 蓝色底色
                            GradientDrawable bg = (GradientDrawable) nativeBanner.getBackground();
                            bg.setColor(Color.parseColor("#0b57d0"));
                            
                            bannerText.setText("💡 课表助手已连接。请先登录，然后进入「我的课表」页面。");
                            bannerButton.setText("返回课表");
                            bannerButton.setTextColor(Color.parseColor("#0b57d0"));
                        }
                    });
                    
                    // 仅注入后台静默扫描脚本，没有任何网页 DOM 渲染，0 侵入性
                    injectScraper(view);
                } else {
                    // 当返回本地应用时，立刻隐藏底部原生浮栏
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            nativeBanner.setVisibility(View.GONE);
                        }
                    });
                }
            }
        });

        // 用 RelativeLayout 组织层次，将原生引导栏堆叠在 WebView 底部
        rootLayout = new RelativeLayout(this);
        rootLayout.setFitsSystemWindows(true);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 添加 WebView
        RelativeLayout.LayoutParams webViewParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(webView, webViewParams);

        // 创建并配置底部原生卡片引导浮栏
        createNativeBanner();
        RelativeLayout.LayoutParams bannerParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bannerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        rootLayout.addView(nativeBanner, bannerParams);

        // 默认隐藏
        nativeBanner.setVisibility(View.GONE);

        setContentView(rootLayout);
        loadSchedule();
    }

    // 创建纯原生 Material 3 样式的底部浮动条
    private void createNativeBanner() {
        nativeBanner = new LinearLayout(this);
        nativeBanner.setOrientation(LinearLayout.HORIZONTAL);
        nativeBanner.setGravity(Gravity.CENTER_VERTICAL);
        
        // 动态设置填充像素以自适应设备 DPI (左右16dp，上下12dp，底端多补充12dp防遮挡)
        int pLeftRight = dpToPx(16);
        int pTopBottom = dpToPx(12);
        int pBottom = pTopBottom + dpToPx(12);
        nativeBanner.setPadding(pLeftRight, pTopBottom, pLeftRight, pBottom);
        
        // 使用 GradientDrawable 绘制圆角背景：上边框 16dp 大圆角，底部贴合无圆角
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#0b57d0"));
        float r = (float) dpToPx(16);
        bg.setCornerRadii(new float[]{ r, r, r, r, 0, 0, 0, 0 });
        nativeBanner.setBackground(bg);
        
        // 创建文本说明框
        bannerText = new TextView(this);
        bannerText.setText("💡 课表助手已连接。请先登录，然后进入「我的课表」页面。");
        bannerText.setTextColor(Color.WHITE);
        bannerText.setTextSize(13.5f);
        bannerText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        bannerText.setLayoutParams(textParams);
        nativeBanner.addView(bannerText);
        
        // 创建右侧核心操控按钮
        bannerButton = new Button(this);
        bannerButton.setText("返回课表");
        bannerButton.setTextSize(12f);
        bannerButton.setTextColor(Color.parseColor("#0b57d0"));
        bannerButton.setAllCaps(false);
        bannerButton.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
        
        // 按钮大圆角背景
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setColor(Color.WHITE);
        btnBg.setCornerRadius((float) dpToPx(18));
        bannerButton.setBackground(btnBg);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.leftMargin = dpToPx(10);
        bannerButton.setLayoutParams(btnParams);
        
        // 绑定按钮的原生点击操作
        bannerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isDataDetected && capturedData != null) {
                    saveScheduleData(capturedData);
                } else {
                    goBackToApp();
                }
            }
        });
        nativeBanner.addView(bannerButton);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    // 读取本地存储或默认的课表 JSON 并加载网页
    public void loadSchedule() {
        try {
            String html = readAsset("index.html");
            String scheduleJson;

            // 优先读取用户自主导入的课表数据
            File localFile = new File(getFilesDir(), "schedule.json");
            if (localFile.exists()) {
                scheduleJson = readFile(localFile);
            } else {
                // 专属空白初始化测试课表模板
                scheduleJson = "{\n" +
                        "  \"student\": \"未导入课表\",\n" +
                        "  \"studentId\": \"请点击右上角导入\",\n" +
                        "  \"term\": \"2025-2026-2\",\n" +
                        "  \"semesterStart\": \"2026-03-02\",\n" +
                        "  \"sessions\": [],\n" +
                        "  \"unscheduledCourses\": []\n" +
                        "}";
            }

            html = html.replace("__SCHEDULE_JSON__", scheduleJson);
            webView.loadDataWithBaseURL(
                    "https://local.course-schedule/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );
        } catch (IOException error) {
            String message = "<html><body><h1>课表加载失败</h1><pre>"
                    + error.getMessage()
                    + "</pre></body></html>";
            webView.loadData(message, "text/html", "UTF-8");
        }
    }

    // 从 App 内部私有目录读取文件
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

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    // 注入网页数据探测器
    private void injectScraper(WebView view) {
        // 纯静默数据扫描脚本，不干扰网页原有布局
        String js = "(function() {\n" +
                "    if (window.hasInjectedHelper) return;\n" +
                "    window.hasInjectedHelper = true;\n" +
                "\n" +
                "    function findCourseTableData(win) {\n" +
                "        try {\n" +
                "            if (win.courseTableDataList && win.courseTableDataList[0]) {\n" +
                "                return win.courseTableDataList[0];\n" +
                "            }\n" +
                "        } catch(e) {}\n" +
                "        try {\n" +
                "            for (var i = 0; i < win.frames.length; i++) {\n" +
                "                var data = findCourseTableData(win.frames[i]);\n" +
                "                if (data) return data;\n" +
                "            }\n" +
                "        } catch(e) {}\n" +
                "        return null;\n" +
                "    }\n" +
                "\n" +
                "    function findStudentInfoFromDOM(win) {\n" +
                "        try {\n" +
                "            var text = win.document.body.innerText || \"\";\n" +
                "            var greetMatch = text.match(/(?:欢迎您|用户|当前用户)[，：\\s]*([\\u4e00-\\u9fa5]{2,4})\\s*\\((2\\d{9})\\)/) ||\n" +
                "                             text.match(/([\\u4e00-\\u9fa5]{2,4})\\s*\\((2\\d{9})\\)/);\n" +
                "            if (greetMatch) {\n" +
                "                return { name: greetMatch[1], code: greetMatch[2] };\n" +
                "            }\n" +
                "            var nameMatch = text.match(/(?:姓名|学生姓名)[：\\s]*([\\u4e00-\\u9fa5]{2,4})/);\n" +
                "            var codeMatch = text.match(/(?:学号|学生学号)[：\\s]*(2\\d{9})/);\n" +
                "            if (nameMatch || codeMatch) {\n" +
                "                return {\n" +
                "                    name: nameMatch ? nameMatch[1] : null,\n" +
                "                    code: codeMatch ? codeMatch[1] : null\n" +
                "                };\n" +
                "            }\n" +
                "        } catch(e) {}\n" +
                "        try {\n" +
                "            for (var i = 0; i < win.frames.length; i++) {\n" +
                "                var info = findStudentInfoFromDOM(win.frames[i]);\n" +
                "                if (info && (info.name || info.code)) return info;\n" +
                "            }\n" +
                "        } catch(e) {}\n" +
                "        return null;\n" +
                "    }\n" +
                "\n" +
                "    var checkInterval = setInterval(function() {\n" +
                "        var data = findCourseTableData(window);\n" +
                "        if (data) {\n" +
                "            var student = data.studentInfo || {};\n" +
                "            var parsedName = student.name || student.xm || data.studentName || data.xm;\n" +
                "            var parsedCode = student.code || student.xh || data.studentCode || data.studentId || data.xh;\n" +
                "            var adminclass = student.adminclass || student.bjmc || student.bj || data.adminclass || '南京财经大学学生';\n" +
                "            \n" +
                "            if (!parsedName || !parsedCode) {\n" +
                "                var domInfo = findStudentInfoFromDOM(window);\n" +
                "                if (domInfo) {\n" +
                "                    if (!parsedName) parsedName = domInfo.name;\n" +
                "                    if (!parsedCode) parsedCode = domInfo.code;\n" +
                "                }\n" +
                "            }\n" +
                "            \n" +
                "            var studentName = parsedName || '南财学生';\n" +
                "            var studentId = parsedCode || '学号已导入';\n" +
                "            var term = data.semester || '2025-2026-2';\n" +
                "            var semesterStart = data.semesterStart || '2026-03-02';\n" +
                "            \n" +
                "            var dayNames = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];\n" +
                "            var sessions = [];\n" +
                "            \n" +
                "            if (data.activities && data.activities.length > 0) {\n" +
                "                sessions = data.activities.map(function(act) {\n" +
                "                    var teacherName = '待定';\n" +
                "                    if (act.teachers && act.teachers.length > 0) {\n" +
                "                        teacherName = act.teachers[0].split('(')[0];\n" +
                "                    }\n" +
                "                    var location = '';\n" +
                "                    if (act.campus) location += act.campus + ' ';\n" +
                "                    if (act.room) {\n" +
                "                        location += act.room;\n" +
                "                    } else if (act.building) {\n" +
                "                        location += act.building;\n" +
                "                    } else {\n" +
                "                        location += '未指派教室';\n" +
                "                    }\n" +
                "\n" +
                "                    return {\n" +
                "                        courseName: act.courseName,\n" +
                "                        courseCode: act.lessonCode || act.courseCode || 'CODE',\n" +
                "                        weekday: act.weekday,\n" +
                "                        dayName: dayNames[act.weekday % 7],\n" +
                "                        periodStart: act.startUnit,\n" +
                "                        periodEnd: act.endUnit,\n" +
                "                        timeStart: act.startTime || '08:20',\n" +
                "                        timeEnd: act.endTime || '09:45',\n" +
                "                        weekText: act.weeksStr || '1~17周',\n" +
                "                        weeks: act.weekIndexes || [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17],\n" +
                "                        location: location,\n" +
                "                        teacher: teacherName,\n" +
                "                        className: adminclass,\n" +
                "                        capacity: '70/70',\n" +
                "                        courseType: '必修'\n" +
                "                    };\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            var scheduleData = {\n" +
                "                student: studentName,\n" +
                "                studentId: studentId,\n" +
                "                term: term,\n" +
                "                semesterStart: semesterStart,\n" +
                "                sessions: sessions,\n" +
                "                unscheduledCourses: []\n" +
                "            };\n" +
                "            \n" +
                "            // 将解析数据安全发回 Java 原生层\n" +
                "            AndroidBridge.onDataDetected(JSON.stringify(scheduleData));\n" +
                "        }\n" +
                "    }, 1000);\n" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    public void saveScheduleData(final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File(getFilesDir(), "schedule.json");
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    
                    Toast.makeText(MainActivity.this, "🎉 课表全自动解析导入成功！已更新至本地！", Toast.LENGTH_LONG).show();
                    
                    // 关闭原生底栏返回主界面
                    nativeBanner.setVisibility(View.GONE);
                    loadSchedule();
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void goBackToApp() {
        nativeBanner.setVisibility(View.GONE);
        loadSchedule();
    }

    // JSBridge 桥接接口层
    public static class WebAppInterface {
        private final MainActivity activity;

        WebAppInterface(MainActivity activity) {
            this.activity = activity;
        }

        // 当网页后台静默脚本捕获到课程数据时，调用该原生接口改变 native 底部引导条
        @JavascriptInterface
        public void onDataDetected(final String json) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.isDataDetected) return; // 防重复触发
                    activity.isDataDetected = true;
                    activity.capturedData = json;
                    
                    // 改变 native 底部条底色为漂亮的谷歌绿
                    GradientDrawable bg = (GradientDrawable) activity.nativeBanner.getBackground();
                    bg.setColor(Color.parseColor("#137333"));
                    
                    activity.bannerText.setText("🎉 已成功检测到您的真实课表数据！");
                    activity.bannerButton.setText("🚀 立即自动解析导入");
                    activity.bannerButton.setTextColor(Color.parseColor("#137333"));
                }
            });
        }

        @JavascriptInterface
        public void saveSchedule(final String json) {
            activity.saveScheduleData(json);
        }

        @JavascriptInterface
        public void goBackToApp() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.goBackToApp();
                }
            });
        }

        @JavascriptInterface
        public void clearScheduleData() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File file = new File(activity.getFilesDir(), "schedule.json");
                        if (file.exists()) {
                            file.delete();
                        }
                        android.webkit.CookieManager.getInstance().removeAllCookies(null);
                        android.webkit.CookieManager.getInstance().flush();
                        activity.webView.clearCache(true);
                        activity.webView.clearHistory();
                        activity.webView.clearFormData();
                        activity.loadSchedule();
                        Toast.makeText(activity, "🗑️ 已清空已导入课表并彻底清除教务登录缓存", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(activity, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}
