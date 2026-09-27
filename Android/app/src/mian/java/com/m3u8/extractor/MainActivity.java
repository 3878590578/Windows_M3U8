package com.m3u8.extractor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import android.graphics.Color;

import android.text.InputType;

import android.view.Gravity;
import android.view.View;
import android.view.Window;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends Activity {

    /*
     * ============================================================
     * 网站配置
     * ============================================================
     */

    private static final String BASE_URL =
            "https://dyttzy.tv";

    private static final String SEARCH_URL =
            BASE_URL +
            "/index.php/vod/search.html?wd=";


    /*
     * ============================================================
     * UI
     * ============================================================
     */

    private EditText keywordInput;

    private Button searchButton;

    private TextView statusText;

    private LinearLayout resultBox;

    private ProgressBar progress;


    /*
     * ============================================================
     * 线程
     * ============================================================
     */

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());


    /*
     * ============================================================
     * 搜索结果
     * ============================================================
     */

    private static class Work {

        String title;

        String url;

        Work(
                String title,
                String url
        ) {
            this.title = title;
            this.url = url;
        }
    }


    /*
     * ============================================================
     * 集数
     * ============================================================
     */

    private static class Episode {

        String episode;

        String url;

        Episode(
                String episode,
                String url
        ) {
            this.episode = episode;
            this.url = url;
        }
    }


    /*
     * ============================================================
     * Activity
     * ============================================================
     */

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        buildUI();
    }


    /*
     * ============================================================
     * 创建界面
     * ============================================================
     */

    private void buildUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(12)
        );

        root.setBackgroundColor(
                Color.WHITE
        );


        /*
         * 标题
         */

        TextView title =
                new TextView(this);

        title.setText(
                "M3U8 提取器"
        );

        title.setTextSize(24);

        title.setTextColor(
                Color.BLACK
        );

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                0,
                0,
                dp(16)
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        /*
         * 搜索区域
         */

        LinearLayout searchRow =
                new LinearLayout(this);

        searchRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        /*
         * 搜索输入框
         */

        keywordInput =
                new EditText(this);

        keywordInput.setHint(
                "请输入影视作品名称"
        );

        keywordInput.setSingleLine(
                true
        );

        keywordInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );


        searchRow.addView(
                keywordInput,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                )
        );


        /*
         * 搜索按钮
         */

        searchButton =
                new Button(this);

        searchButton.setText(
                "搜索"
        );

        searchButton.setAllCaps(
                false
        );

        searchButton.setOnClickListener(
                v -> search()
        );


        LinearLayout.LayoutParams searchButtonParams =
                new LinearLayout.LayoutParams(
                        dp(90),
                        dp(52)
                );

        searchButtonParams.leftMargin =
                dp(8);

        searchRow.addView(
                searchButton,
                searchButtonParams
        );


        root.addView(
                searchRow
        );


        /*
         * 加载动画
         */

        progress =
                new ProgressBar(this);

        progress.setVisibility(
                View.GONE
        );

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        progressParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        progressParams.topMargin =
                dp(12);

        root.addView(
                progress,
                progressParams
        );


        /*
         * 状态
         */

        statusText =
                new TextView(this);

        statusText.setText(
                "请输入作品名称开始搜索"
        );

        statusText.setTextSize(
                14
        );

        statusText.setTextColor(
                Color.DKGRAY
        );

        statusText.setPadding(
                0,
                dp(10),
                0,
                dp(10)
        );

        root.addView(
                statusText
        );


        /*
         * 搜索结果区域
         */

        ScrollView scroll =
                new ScrollView(this);


        resultBox =
                new LinearLayout(this);

        resultBox.setOrientation(
                LinearLayout.VERTICAL
        );


        scroll.addView(
                resultBox
        );


        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        setContentView(
                root
        );
    }


    /*
     * ============================================================
     * 搜索
     * ============================================================
     */

    private void search() {

        String keyword =
                keywordInput
                        .getText()
                        .toString()
                        .trim();


        if (keyword.isEmpty()) {

            toast(
                    "请输入作品名称"
            );

            return;
        }


        setBusy(
                true,
                "正在搜索……"
        );


        executor.execute(
                () -> {

                    try {

                        String url =
                                SEARCH_URL +
                                URLEncoder.encode(
                                        keyword,
                                        "UTF-8"
                                );


                        String html =
                                getHtml(url);


                        List<Work> works =
                                searchWorks(html);


                        mainHandler.post(
                                () -> {

                                    setBusy(
                                            false,
                                            "找到 " +
                                            works.size() +
                                            " 个结果"
                                    );


                                    showWorks(
                                            works
                                    );
                                }
                        );


                    } catch (Exception e) {

                        mainHandler.post(
                                () -> {

                                    setBusy(
                                            false,
                                            "搜索失败"
                                    );

                                    showError(
                                            e.getMessage()
                                    );
                                }
                        );
                    }
                }
        );
    }


    /*
     * ============================================================
     * 搜索作品
     * ============================================================
     */

    private List<Work> searchWorks(
            String html
    ) {

        List<Work> list =
                new ArrayList<>();


        Set<String> seen =
                new HashSet<>();


        Pattern pattern =
                Pattern.compile(
                        "<a[^>]+href=[\"']" +
                        "([^\"']*" +
                        "(?:/vod/|" +
                        "/index\\.php/vod/detail)" +
                        "[^\"']*)" +
                        "[\"'][^>]*>" +
                        "(.*?)</a>",
                        Pattern.CASE_INSENSITIVE |
                        Pattern.DOTALL
                );


        Matcher matcher =
                pattern.matcher(html);


        while (matcher.find()) {

            String href =
                    matcher.group(1);


            String title =
                    cleanText(
                            matcher.group(2)
                    );


            if (title.isEmpty()) {
                continue;
            }


            if (
                    href
                            .toLowerCase()
                            .contains(
                                    "javascript:"
                            )
            ) {
                continue;
            }


            href =
                    absoluteUrl(href);


            String key =
                    title +
                    "|" +
                    href;


            if (seen.add(key)) {

                list.add(
                        new Work(
                                title,
                                href
                        )
                );
            }
        }


        /*
         * 如果第一种搜索规则没有结果，
         * 再使用备用规则。
         */

        if (list.isEmpty()) {

            Pattern fallback =
                    Pattern.compile(
                            "<a[^>]+href=[\"']" +
                            "([^\"']+)" +
                            "[\"'][^>]*>" +
                            "(.*?)</a>",
                            Pattern.CASE_INSENSITIVE |
                            Pattern.DOTALL
                    );


            Matcher m =
                    fallback.matcher(html);


            while (m.find()) {

                String href =
                        m.group(1);


                String title =
                        cleanText(
                                m.group(2)
                        );


                if (title.isEmpty()) {
                    continue;
                }


                if (
                        !href.contains(
                                "/vod/"
                        )
                        &&
                        !href.contains(
                                "/index.php/vod/"
                        )
                ) {
                    continue;
                }


                href =
                        absoluteUrl(href);


                String key =
                        title +
                        "|" +
                        href;


                if (seen.add(key)) {

                    list.add(
                            new Work(
                                    title,
                                    href
                            )
                    );
                }
            }
        }


        return list;
    }


    /*
     * ============================================================
     * 显示搜索结果
     * ============================================================
     */

    private void showWorks(
            List<Work> works
    ) {

        resultBox.removeAllViews();


        if (works.isEmpty()) {

            statusText.setText(
                    "没有找到相关作品"
            );

            return;
        }


        for (Work work : works) {

            Button button =
                    new Button(this);


            button.setText(
                    work.title
            );


            button.setTextSize(
                    16
            );


            button.setGravity(
                    Gravity.CENTER_VERTICAL |
                    Gravity.LEFT
            );


            button.setAllCaps(
                    false
            );


            button.setOnClickListener(
                    v -> openWork(work)
            );


            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(58)
                    );


            params.bottomMargin =
                    dp(6);


            resultBox.addView(
                    button,
                    params
            );
        }
    }


    /*
     * ============================================================
     * 打开作品详情页
     * ============================================================
     */

    private void openWork(
            Work work
    ) {

        setBusy(
                true,
                "正在打开：" +
                work.title
        );


        executor.execute(
                () -> {

                    try {

                        String html =
                                getHtml(
                                        work.url
                                );


                        String title =
                                getTitle(
                                        html
                                );


                        if (
                                title.equals(
                                        "未知作品"
                                )
                        ) {

                            title =
                                    work.title;
                        }


                        /*
                         * 第一优先：
                         *
                         * copy_dyttm3u8[]
                         */

                        List<Episode> episodes =
                                extractPrecise(
                                        html
                                );


                        /*
                         * 第二优先：
                         *
                         * 通用 M3U8
                         */

                        if (episodes.isEmpty()) {

                            episodes =
                                    extractGeneric(
                                            html
                                    );
                        }


                        /*
                         * 去重 + 排序
                         */

                        episodes =
                                uniqueSort(
                                        episodes
                                );


                        String finalTitle =
                                title;

                        List<Episode> finalEpisodes =
                                episodes;


                        mainHandler.post(
                                () -> {

                                    setBusy(
                                            false,
                                            "找到 " +
                                            finalEpisodes.size() +
                                            " 集"
                                    );


                                    if (
                                            finalEpisodes
                                                    .isEmpty()
                                    ) {

                                        showError(
                                                "该页面没有找到 M3U8 地址。"
                                        );

                                        return;
                                    }


                                    chooseEpisode(
                                            finalTitle,
                                            finalEpisodes
                                    );
                                }
                        );


                    } catch (Exception e) {

                        mainHandler.post(
                                () -> {

                                    setBusy(
                                            false,
                                            "打开失败"
                                    );

                                    showError(
                                            e.getMessage()
                                    );
                                }
                        );
                    }
                }
        );
    }


    /*
     * ============================================================
     * 选择集数
     * ============================================================
     */

    private void chooseEpisode(
            String title,
            List<Episode> episodes
    ) {

        String[] choices =
                new String[
                        episodes.size() + 1
                ];


        choices[0] =
                "全部集数";


        for (
                int i = 0;
                i < episodes.size();
                i++
        ) {

            String episode =
                    episodes
                            .get(i)
                            .episode;


            if (
                    episode == null
                    ||
                    episode.isEmpty()
            ) {

                choices[i + 1] =
                        "未知集数";

            } else {

                choices[i + 1] =
                        episode;
            }
        }


        new AlertDialog.Builder(this)

                .setTitle(
                        "选择提取方式"
                )

                .setItems(
                        choices,
                        (dialog, which) -> {

                            if (which == 0) {

                                showOutput(
                                        title,
                                        episodes
                                );

                            } else {

                                List<Episode> one =
                                        new ArrayList<>();


                                one.add(
                                        episodes.get(
                                                which - 1
                                        )
                                );


                                showOutput(
                                        title,
                                        one
                                );
                            }
                        }
                )

                .setNegativeButton(
                        "取消",
                        null
                )

                .show();
    }


    /*
     * ============================================================
     * 显示最终结果
     * ============================================================
     */

    private void showOutput(
            String title,
            List<Episode> selected
    ) {

        StringBuilder output =
                new StringBuilder();


        for (Episode episode : selected) {

            String name =
                    title;


            if (
                    episode.episode != null
                    &&
                    !episode.episode.isEmpty()
            ) {

                name +=
                        episode.episode;
            }


            output
                    .append(
                            episode.url
                    )
                    .append("#")
                    .append(
                            name
                    )
                    .append("\n");
        }


        String result =
                output
                        .toString()
                        .trim();


        /*
         * 自动复制
         */

        copyText(
                result
        );


        /*
         * 多行文本框
         */

        EditText text =
                new EditText(this);


        text.setText(
                result
        );


        text.setTextSize(
                12
        );


        text.setGravity(
                Gravity.TOP |
                Gravity.LEFT
        );


        text.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );


        text.setSelectAllOnFocus(
                false
        );


        ScrollView scroll =
                new ScrollView(this);


        scroll.setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
        );


        scroll.addView(
                text
        );


        AlertDialog dialog =
                new AlertDialog.Builder(this)

                        .setTitle(
                                "提取结果"
                        )

                        .setView(
                                scroll
                        )

                        .setPositiveButton(
                                "复制全部",
                                null
                        )

                        .setNegativeButton(
                                "关闭",
                                null
                        )

                        .create();


        dialog.setOnShowListener(
                d -> {

                    Button copyButton =
                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );


                    copyButton.setOnClickListener(
                            v -> {

                                copyText(
                                        result
                                );


                                toast(
                                        "已复制全部结果"
                                );
                            }
                    );
                }
        );


        dialog.show();
    }


    /*
     * ============================================================
     * 精确提取
     *
     * 针对：
     *
     * name="copy_dyttm3u8[]"
     * value="第1集#https://xxx/index.m3u8"
     *
     * ============================================================
     */

    private List<Episode> extractPrecise(
            String html
    ) {

        List<Episode> list =
                new ArrayList<>();


        Pattern pattern =
                Pattern.compile(
                        "name=[\"']" +
                        "copy_dyttm3u8\\[\\]" +
                        "[\"']" +
                        "[^>]*" +
                        "value=[\"']" +
                        "([^\"']+)" +
                        "[\"']",
                        Pattern.CASE_INSENSITIVE
                );


        Matcher matcher =
                pattern.matcher(html);


        while (matcher.find()) {

            String value =
                    htmlUnescape(
                            matcher.group(1)
                    );


            String[] parts =
                    value.split(
                            "#",
                            2
                    );


            if (parts.length != 2) {
                continue;
            }


            String episode =
                    parts[0].trim();


            String url =
                    parts[1].trim();


            if (
                    !url.matches(
                            "(?i)^https?://.*\\.m3u8.*"
                    )
            ) {
                continue;
            }


            list.add(
                    new Episode(
                            episode,
                            url
                    )
            );
        }


        return list;
    }


    /*
     * ============================================================
     * 通用 M3U8 提取
     * ============================================================
     */

    private List<Episode> extractGeneric(
            String html
    ) {

        List<Episode> list =
                new ArrayList<>();


        /*
         * 删除 script
         */

        String clean =
                html.replaceAll(
                        "(?is)<script.*?</script>",
                        " "
                );


        /*
         * 删除 style
         */

        clean =
                clean.replaceAll(
                        "(?is)<style.*?</style>",
                        " "
                );


        /*
         * 删除 HTML 标签
         */

        clean =
                clean.replaceAll(
                        "(?is)<[^>]+>",
                        " "
                );


        clean =
                htmlUnescape(
                        clean
                );


        Pattern pattern =
                Pattern.compile(
                        "(第?\\s*\\d+\\s*集)?" +
                        "[^h\\n\\r]{0,20}?" +
                        "(https?://[^\\s\"'<>]+?" +
                        "\\.m3u8" +
                        "[^\\s\"'<>]*)",
                        Pattern.CASE_INSENSITIVE
                );


        Matcher matcher =
                pattern.matcher(clean);


        while (matcher.find()) {

            String episode =
                    matcher.group(1);


            if (episode == null) {
                episode = "";
            }


            episode =
                    episode.trim();


            String url =
                    matcher.group(2)
                            .trim();


            /*
             * 去除 URL 尾部垃圾字符
             */

            while (
                    url.endsWith(".")
                    ||
                    url.endsWith(",")
                    ||
                    url.endsWith(")")
                    ||
                    url.endsWith("]")
                    ||
                    url.endsWith(">")
            ) {

                url =
                        url.substring(
                                0,
                                url.length() - 1
                        );
            }


            /*
             * 如果当前没有集数，
             * 向前查找“第X集”
             */

            if (episode.isEmpty()) {

                int start =
                        Math.max(
                                0,
                                matcher.start() - 40
                        );


                String before =
                        clean.substring(
                                start,
                                matcher.start()
                        );


                Matcher ep =
                        Pattern.compile(
                                "第\\s*(\\d+)\\s*集"
                        ).matcher(
                                before
                        );


                if (ep.find()) {

                    episode =
                            "第" +
                            ep.group(1) +
                            "集";
                }
            }


            list.add(
                    new Episode(
                            episode,
                            url
                    )
            );
        }


        return list;
    }


    /*
     * ============================================================
     * 去重 + 排序
     * ============================================================
     */

    private List<Episode> uniqueSort(
            List<Episode> input
    ) {

        Map<String, Episode> map =
                new LinkedHashMap<>();


        for (Episode episode : input) {

            if (
                    !map.containsKey(
                            episode.url
                    )
            ) {

                map.put(
                        episode.url,
                        episode
                );
            }
        }


        List<Episode> list =
                new ArrayList<>(
                        map.values()
                );


        Collections.sort(
                list,
                new Comparator<Episode>() {

                    @Override
                    public int compare(
                            Episode a,
                            Episode b
                    ) {

                        int na =
                                episodeNumber(
                                        a.episode
                                );


                        int nb =
                                episodeNumber(
                                        b.episode
                                );


                        if (na != nb) {

                            return Integer.compare(
                                    na,
                                    nb
                            );
                        }


                        return a.url.compareTo(
                                b.url
                        );
                    }
                }
        );


        return list;
    }


    /*
     * ============================================================
     * 提取集数数字
     * ============================================================
     */

    private int episodeNumber(
            String episode
    ) {

        if (episode == null) {
            return 999999;
        }


        Matcher matcher =
                Pattern.compile(
                        "(\\d+)"
                ).matcher(
                        episode
                );


        if (matcher.find()) {

            try {

                return Integer.parseInt(
                        matcher.group(1)
                );

            } catch (Exception ignored) {
            }
        }


        return 999999;
    }


    /*
     * ============================================================
     * 获取作品标题
     * ============================================================
     */

    private String getTitle(
            String html
    ) {

        String[] patterns = {

                "<h1[^>]*>(.*?)</h1>",

                "<h2[^>]*>(.*?)</h2>",

                "<meta[^>]+" +
                "property=[\"']og:title[\"']" +
                "[^>]+" +
                "content=[\"'](.*?)[\"']",

                "<title[^>]*>(.*?)</title>"
        };


        for (String pattern : patterns) {

            Matcher matcher =
                    Pattern.compile(
                            pattern,
                            Pattern.CASE_INSENSITIVE |
                            Pattern.DOTALL
                    ).matcher(
                            html
                    );


            if (matcher.find()) {

                String title =
                        cleanText(
                                matcher.group(1)
                        );


                title =
                        title.replaceAll(
                                "(?i)\\s*[-_|｜]\\s*" +
                                "(电影天堂|dyttzy|电影天堂资源).*?$",
                                ""
                        );


                if (
                        !title.trim().isEmpty()
                ) {

                    return title.trim();
                }
            }
        }


        return "未知作品";
    }


    /*
     * ============================================================
     * 获取网页
     * ============================================================
     */

    private String getHtml(
            String url
    ) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(url)
                                .openConnection();


        connection.setRequestMethod(
                "GET"
        );


        connection.setConnectTimeout(
                20000
        );


        connection.setReadTimeout(
                20000
        );


        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 " +
                "(Linux; Android 13) " +
                "AppleWebKit/537.36 " +
                "Chrome/120.0 " +
                "Mobile Safari/537.36"
        );


        InputStream input =
                connection.getInputStream();


        ByteArrayOutputStream output =
                new ByteArrayOutputStream();


        byte[] buffer =
                new byte[8192];


        int length;


        while (
                (length =
                        input.read(buffer))
                        != -1
        ) {

            output.write(
                    buffer,
                    0,
                    length
            );
        }


        input.close();


        connection.disconnect();


        return new String(
                output.toByteArray(),
                StandardCharsets.UTF_8
        );
    }


    /*
     * ============================================================
     * HTML 文本清理
     * ============================================================
     */

    private String cleanText(
            String text
    ) {

        text =
                htmlUnescape(
                        text
                );


        text =
                text.replaceAll(
                        "(?is)<[^>]+>",
                        ""
                );


        text =
                text.replaceAll(
                        "\\s+",
                        " "
                );


        return text.trim();
    }


    /*
     * ============================================================
     * HTML 实体
     * ============================================================
     */

    private String htmlUnescape(
            String text
    ) {

        return text
                .replace(
                        "&amp;",
                        "&"
                )
                .replace(
                        "&quot;",
                        "\""
                )
                .replace(
                        "&#39;",
                        "'"
                )
                .replace(
                        "&lt;",
                        "<"
                )
                .replace(
                        "&gt;",
                        ">"
                );
    }


    /*
     * ============================================================
     * 相对 URL → 完整 URL
     * ============================================================
     */

    private String absoluteUrl(
            String href
    ) {

        if (
                href.startsWith(
                        "//"
                )
        ) {

            return "https:" +
                    href;
        }


        if (
                href.startsWith(
                        "/"
                )
        ) {

            return BASE_URL +
                    href;
        }


        if (
                href.startsWith(
                        "http://"
                )
                ||
                href.startsWith(
                        "https://"
                )
        ) {

            return href;
        }


        return BASE_URL +
                "/" +
                href;
    }


    /*
     * ============================================================
     * 复制到剪贴板
     * ============================================================
     */

    private void copyText(
            String text
    ) {

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );


        clipboard.setPrimaryClip(
                ClipData.newPlainText(
                        "M3U8",
                        text
                )
        );
    }


    /*
     * ============================================================
     * 加载状态
     * ============================================================
     */

    private void setBusy(
            boolean busy,
            String status
    ) {

        searchButton.setEnabled(
                !busy
        );


        progress.setVisibility(
                busy
                        ? View.VISIBLE
                        : View.GONE
        );


        statusText.setText(
                status
        );
    }


    /*
     * ============================================================
     * 错误提示
     * ============================================================
     */

    private void showError(
            String message
    ) {

        if (
                message == null
                ||
                message.trim().isEmpty()
        ) {

            message =
                    "未知错误";
        }


        new AlertDialog.Builder(this)

                .setTitle(
                        "提示"
                )

                .setMessage(
                        message
                )

                .setPositiveButton(
                        "确定",
                        null
                )

                .show();
    }


    /*
     * ============================================================
     * Toast
     * ============================================================
     */

    private void toast(
            String text
    ) {

        Toast.makeText(
                this,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }


    /*
     * ============================================================
     * dp
     * ============================================================
     */

    private int dp(
            int value
    ) {

        return (int)
                (
                        value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                );
    }


    /*
     * ============================================================
     * 退出
     * ============================================================
     */

    @Override
    protected void onDestroy() {

        executor.shutdownNow();

        super.onDestroy();
    }
}
