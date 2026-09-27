package com.m3u8.extractor;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String BASE_URL = "https://dyttzy.tv";

    private EditText keywordEdit;
    private Button searchButton;
    private TextView statusText;
    private LinearLayout resultLayout;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<SearchItem> searchItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUI();
    }

    // =========================================================
    // UI
    // =========================================================

    private void buildUI() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("M3U8 提取器");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 25);

        root.addView(title);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        keywordEdit = new EditText(this);
        keywordEdit.setHint("输入影视名称");
        keywordEdit.setSingleLine(true);

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        searchRow.addView(keywordEdit, inputParams);

        searchButton = new Button(this);
        searchButton.setText("搜索");

        searchRow.addView(
                searchButton,
                new LinearLayout.LayoutParams(
                        130,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(searchRow);

        statusText = new TextView(this);
        statusText.setText("请输入影视名称");
        statusText.setTextSize(16);
        statusText.setPadding(0, 20, 0, 15);

        root.addView(statusText);

        ScrollView scrollView = new ScrollView(this);

        resultLayout = new LinearLayout(this);
        resultLayout.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(resultLayout);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        searchButton.setOnClickListener(v -> {

            String keyword = keywordEdit.getText()
                    .toString()
                    .trim();

            if (keyword.isEmpty()) {
                Toast.makeText(
                        this,
                        "请输入影视名称",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            search(keyword);
        });
    }

    // =========================================================
    // 搜索
    // =========================================================

    private void search(String keyword) {

        searchButton.setEnabled(false);

        statusText.setText("正在搜索……");

        resultLayout.removeAllViews();

        searchItems.clear();

        new Thread(() -> {

            try {

                String encoded =
                        URLEncoder.encode(
                                keyword,
                                "UTF-8"
                        );

                String url =
                        BASE_URL +
                        "/index.php/vod/search.html?wd=" +
                        encoded;

                String html = get(url);

                List<SearchItem> results =
                        parseSearchResults(html);

                mainHandler.post(() -> {

                    searchButton.setEnabled(true);

                    searchItems.clear();
                    searchItems.addAll(results);

                    if (results.isEmpty()) {

                        statusText.setText(
                                "没有找到相关影片"
                        );

                        return;
                    }

                    statusText.setText(
                            "找到 " +
                            results.size() +
                            " 部影片"
                    );

                    showSearchResults(results);
                });

            } catch (Exception e) {

                mainHandler.post(() -> {

                    searchButton.setEnabled(true);

                    statusText.setText(
                            "搜索失败：" +
                            e.getMessage()
                    );
                });
            }

        }).start();
    }

    // =========================================================
    // 搜索结果解析
    // =========================================================

    private List<SearchItem> parseSearchResults(String html) {

        Map<String, SearchItem> unique =
                new LinkedHashMap<>();

        /*
         * 重点：
         *
         * 不能：
         *
         * <a href="...">所有文字</a>
         *
         * 全部拿来当结果。
         *
         * 必须限制为 /vod/detail/
         */

        Pattern pattern = Pattern.compile(
                "<a[^>]+href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE |
                        Pattern.DOTALL
        );

        Matcher matcher =
                pattern.matcher(html);

        while (matcher.find()) {

            String href =
                    matcher.group(1);

            String body =
                    matcher.group(2);

            if (href == null) {
                continue;
            }

            href = Html.fromHtml(href).toString().trim();

            /*
             * 只接受影片详情页
             */

            if (!isDetailUrl(href)) {
                continue;
            }

            String title =
                    extractText(body);

            if (title.isEmpty()) {
                continue;
            }

            /*
             * 去掉一些页面无意义文字
             */

            if (isBadTitle(title)) {
                continue;
            }

            href = makeAbsoluteUrl(href);

            /*
             * 去重
             */

            if (!unique.containsKey(href)) {

                SearchItem item =
                        new SearchItem();

                item.title = title;
                item.url = href;

                unique.put(href, item);
            }
        }

        return new ArrayList<>(unique.values());
    }

    // =========================================================
    // 判断是不是详情页
    // =========================================================

    private boolean isDetailUrl(String url) {

        String lower =
                url.toLowerCase();

        /*
         * 常见 MacCMS：
         *
         * /index.php/vod/detail/id/123.html
         *
         * /index.php/vod/detail/id-123.html
         *
         * /vod/detail/id/123.html
         */

        return lower.contains("/vod/detail/");
    }

    // =========================================================
    // 排除分类/导航
    // =========================================================

    private boolean isBadTitle(String title) {

        String t = title.trim();

        if (t.length() < 1) {
            return true;
        }

        String[] bad = {

                "首页",
                "电影",
                "电视剧",
                "综艺",
                "动漫",
                "海外动漫",
                "国产动漫",
                "日本动漫",
                "欧美动漫",
                "综艺片",
                "大陆综艺",
                "港台综艺",
                "日韩综艺",
                "欧美综艺",
                "动作片",
                "喜剧片",
                "爱情片",
                "科幻片",
                "恐怖片",
                "剧情片",
                "战争片",
                "记录片",
                "动画片",
                "伦理片",
                "大陆剧",
                "港剧",
                "台剧",
                "韩剧",
                "日剧",
                "欧美剧"
        };

        for (String s : bad) {

            if (t.equals(s)) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // 显示搜索结果
    // =========================================================

    private void showSearchResults(
            List<SearchItem> results) {

        resultLayout.removeAllViews();

        for (SearchItem item : results) {

            Button button =
                    new Button(this);

            button.setText(item.title);

            button.setTextSize(17);

            button.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.setMargins(0, 6, 0, 6);

            resultLayout.addView(
                    button,
                    params
            );

            button.setOnClickListener(v ->
                    loadDetail(item)
            );
        }
    }

    // =========================================================
    // 获取详情页
    // =========================================================

    private void loadDetail(SearchItem item) {

        statusText.setText(
                "正在读取：" +
                item.title
        );

        resultLayout.removeAllViews();

        new Thread(() -> {

            try {

                String html =
                        get(item.url);

                List<Episode> episodes =
                        parseEpisodes(
                                html,
                                item.title,
                                item.url
                        );

                mainHandler.post(() -> {

                    if (episodes.isEmpty()) {

                        statusText.setText(
                                "没有解析到集数"
                        );

                        Button back =
                                createButton("← 返回搜索结果");

                        resultLayout.addView(back);

                        back.setOnClickListener(v ->
                                showSearchResults(
                                        searchItems
                                )
                        );

                        return;
                    }

                    statusText.setText(
                            item.title +
                            "：共 " +
                            episodes.size() +
                            " 集"
                    );

                    showEpisodes(
                            item.title,
                            episodes
                    );
                });

            } catch (Exception e) {

                mainHandler.post(() ->
                        statusText.setText(
                                "读取详情失败：" +
                                e.getMessage()
                        )
                );
            }

        }).start();
    }

    // =========================================================
    // 解析集数
    // =========================================================

    private List<Episode> parseEpisodes(
            String html,
            String title,
            String detailUrl) {

        Map<String, Episode> map =
                new LinkedHashMap<>();

        /*
         * 第一优先级：
         *
         * copy_dyttm3u8[]="第1集#URL"
         */

        Pattern p1 = Pattern.compile(
                "name\\s*=\\s*[\"']copy_dyttm3u8\\[\\][\"']" +
                "[^>]*value\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE |
                        Pattern.DOTALL
        );

        Matcher m1 =
                p1.matcher(html);

        while (m1.find()) {

            String value =
                    decodeHtml(m1.group(1));

            parseEpisodeValue(
                    value,
                    map
            );
        }

        /*
         * 有些页面属性顺序反过来：
         *
         * value="第1集#URL"
         * name="copy_dyttm3u8[]"
         */

        Pattern p2 = Pattern.compile(
                "<input[^>]*" +
                "(?:value\\s*=\\s*[\"']([^\"']+)[\"'][^>]*" +
                "name\\s*=\\s*[\"']copy_dyttm3u8\\[\\][\"']" +
                "|" +
                "name\\s*=\\s*[\"']copy_dyttm3u8\\[\\][\"'][^>]*" +
                "value\\s*=\\s*[\"']([^\"']+)[\"'])" +
                "[^>]*>",
                Pattern.CASE_INSENSITIVE |
                        Pattern.DOTALL
        );

        Matcher m2 =
                p2.matcher(html);

        while (m2.find()) {

            String value =
                    m2.group(1);

            if (value == null) {
                value = m2.group(2);
            }

            if (value == null) {
                continue;
            }

            parseEpisodeValue(
                    decodeHtml(value),
                    map
            );
        }

        /*
         * 第二备用：
         *
         * 直接找：
         *
         * 第1集#https://xxx.m3u8
         */

        if (map.isEmpty()) {

            Pattern p3 = Pattern.compile(
                    "(第\\s*\\d+\\s*集)" +
                    "\\s*#\\s*" +
                    "(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher m3 =
                    p3.matcher(
                            decodeHtml(html)
                    );

            while (m3.find()) {

                String epName =
                        m3.group(1);

                String url =
                        cleanUrl(m3.group(2));

                addEpisode(
                        map,
                        epName,
                        url
                );
            }
        }

        /*
         * 第三备用：
         *
         * 找页面中的 m3u8，
         * 但必须尝试从附近文字判断集数。
         */

        if (map.isEmpty()) {

            Pattern p4 = Pattern.compile(
                    "(第\\s*0*([0-9]+)\\s*集)" +
                    "[^\\r\\n]{0,500}?" +
                    "(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher m4 =
                    p4.matcher(
                            decodeHtml(html)
                    );

            while (m4.find()) {

                String ep =
                        m4.group(1);

                String url =
                        cleanUrl(m4.group(3));

                addEpisode(
                        map,
                        ep,
                        url
                );
            }
        }

        List<Episode> list =
                new ArrayList<>(
                        map.values()
                );

        /*
         * 按集数数字排序
         */

        Collections.sort(
                list,
                Comparator.comparingInt(
                        e -> e.number
                )
        );

        return list;
    }

    // =========================================================
    // 解析：
    // 第1集#URL
    // =========================================================

    private void parseEpisodeValue(
            String value,
            Map<String, Episode> map) {

        if (value == null) {
            return;
        }

        value = value.trim();

        int index =
                value.indexOf("#");

        if (index <= 0) {
            return;
        }

        String epName =
                value.substring(
                        0,
                        index
                ).trim();

        String url =
                value.substring(
                        index + 1
                ).trim();

        url = cleanUrl(url);

        if (!isM3u8(url)) {
            return;
        }

        addEpisode(
                map,
                epName,
                url
        );
    }

    // =========================================================
    // 添加集数
    // =========================================================

    private void addEpisode(
            Map<String, Episode> map,
            String epName,
            String url) {

        Matcher matcher =
                Pattern.compile(
                        "(?:第\\s*)?0*([0-9]+)(?:\\s*集)?",
                        Pattern.CASE_INSENSITIVE
                ).matcher(epName);

        if (!matcher.find()) {
            return;
        }

        int number;

        try {

            number =
                    Integer.parseInt(
                            matcher.group(1)
                    );

        } catch (Exception e) {
            return;
        }

        String key =
                String.valueOf(number);

        if (map.containsKey(key)) {
            return;
        }

        Episode episode =
                new Episode();

        episode.number = number;
        episode.name =
                "第" +
                number +
                "集";

        episode.url = url;

        map.put(
                key,
                episode
        );
    }

    // =========================================================
    // 显示集数
    // =========================================================

    private void showEpisodes(
            String title,
            List<Episode> episodes) {

        resultLayout.removeAllViews();

        Button allButton =
                createButton(
                        "复制全部 " +
                        episodes.size() +
                        " 集"
                );

        resultLayout.addView(allButton);

        allButton.setOnClickListener(v -> {

            StringBuilder sb =
                    new StringBuilder();

            for (Episode e : episodes) {

                sb.append(e.url)
                        .append("#")
                        .append(title)
                        .append(e.name)
                        .append("\n");
            }

            copyToClipboard(
                    sb.toString().trim()
            );

            Toast.makeText(
                    this,
                    "已复制全部集数",
                    Toast.LENGTH_SHORT
            ).show();
        });

        Button back =
                createButton(
                        "← 返回影片列表"
                );

        resultLayout.addView(back);

        back.setOnClickListener(v ->
                showSearchResults(
                        searchItems
                )
        );

        for (Episode episode : episodes) {

            Button button =
                    createButton(
                            episode.name
                    );

            resultLayout.addView(button);

            button.setOnClickListener(v -> {

                String result =
                        episode.url +
                        "#" +
                        title +
                        episode.name;

                copyToClipboard(result);

                Toast.makeText(
                        this,
                        "已复制：" +
                        episode.name,
                        Toast.LENGTH_SHORT
                ).show();
            });
        }
    }

    // =========================================================
    // 网络请求
    // =========================================================

    private String get(String urlString)
            throws Exception {

        HttpURLConnection conn = null;

        try {

            URL url =
                    new URL(urlString);

            conn =
                    (HttpURLConnection)
                            url.openConnection();

            conn.setRequestMethod("GET");

            conn.setConnectTimeout(15000);

            conn.setReadTimeout(20000);

            conn.setInstanceFollowRedirects(true);

            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) " +
                    "AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36"
            );

            conn.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml," +
                    "application/xml;q=0.9,*/*;q=0.8"
            );

            conn.setRequestProperty(
                    "Accept-Language",
                    "zh-CN,zh;q=0.9"
            );

            int code =
                    conn.getResponseCode();

            InputStream input;

            if (code >= 400) {
                input = conn.getErrorStream();
            } else {
                input = conn.getInputStream();
            }

            if (input == null) {
                throw new Exception(
                        "HTTP " + code
                );
            }

            String charset =
                    getCharset(
                            conn.getContentType()
                    );

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    input,
                                    charset
                            )
                    );

            StringBuilder sb =
                    new StringBuilder();

            String line;

            while ((line =
                    reader.readLine()) != null) {

                sb.append(line)
                        .append("\n");
            }

            reader.close();

            return sb.toString();

        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // =========================================================
    // 获取字符集
    // =========================================================

    private String getCharset(
            String contentType) {

        if (contentType != null) {

            Matcher matcher =
                    Pattern.compile(
                            "charset\\s*=\\s*([^;\\s]+)",
                            Pattern.CASE_INSENSITIVE
                    ).matcher(contentType);

            if (matcher.find()) {

                return matcher.group(1)
                        .trim();
            }
        }

        return "UTF-8";
    }

    // =========================================================
    // HTML文字
    // =========================================================

    private String extractText(
            String html) {

        if (html == null) {
            return "";
        }

        String text =
                html.replaceAll(
                        "<script[\\s\\S]*?</script>",
                        ""
                );

        text =
                text.replaceAll(
                        "<style[\\s\\S]*?</style>",
                        ""
                );

        text =
                text.replaceAll(
                        "<[^>]+>",
                        " "
                );

        text =
                Html.fromHtml(
                        text
                ).toString();

        text =
                text.replace(
                        "\u00A0",
                        " "
                );

        return text
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String decodeHtml(
            String value) {

        if (value == null) {
            return "";
        }

        return Html.fromHtml(
                value
        ).toString();
    }

    // =========================================================
    // URL处理
    // =========================================================

    private String makeAbsoluteUrl(
            String url) {

        if (url.startsWith("http://") ||
                url.startsWith("https://")) {

            return url;
        }

        if (url.startsWith("//")) {

            return "https:" + url;
        }

        if (url.startsWith("/")) {

            return BASE_URL + url;
        }

        return BASE_URL + "/" + url;
    }

    private String cleanUrl(
            String url) {

        if (url == null) {
            return "";
        }

        url =
                Html.fromHtml(
                        url
                ).toString()
                        .trim();

        url =
                url.replace(
                        "&amp;",
                        "&"
                );

        /*
         * 去掉末尾 HTML 标记
         */

        url =
                url.replaceAll(
                        "[\"'<>\\s]+$",
                        ""
                );

        return url;
    }

    private boolean isM3u8(
            String url) {

        return url != null &&
                url.toLowerCase()
                        .contains(".m3u8");
    }

    // =========================================================
    // Button
    // =========================================================

    private Button createButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(text);

        button.setTextSize(16);

        button.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                6,
                0,
                6
        );

        button.setLayoutParams(params);

        return button;
    }

    // =========================================================
    // 剪贴板
    // =========================================================

    private void copyToClipboard(
            String text) {

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        getSystemService(
                                CLIPBOARD_SERVICE
                        );

        android.content.ClipData clip =
                android.content.ClipData.newPlainText(
                        "M3U8",
                        text
                );

        clipboard.setPrimaryClip(clip);
    }

    // =========================================================
    // 数据类
    // =========================================================

    private static class SearchItem {

        String title;
        String url;
    }

    private static class Episode {

        int number;
        String name;
        String url;
    }
}
