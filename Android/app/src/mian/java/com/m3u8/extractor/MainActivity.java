package com.m3u8.extractor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
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
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    // =========================================================
    // 网站
    // =========================================================

    private static final String BASE_URL = "https://dyttzy.tv";

    // =========================================================
    // UI
    // =========================================================

    private EditText keywordEdit;
    private Button searchButton;
    private TextView statusText;
    private LinearLayout resultLayout;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // 保存最近一次搜索结果
    private final List<SearchItem> searchItems =
            new ArrayList<>();

    // =========================================================
    // Activity
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUI();
    }

    // =========================================================
    // 创建界面
    // =========================================================

    private void buildUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                24,
                24,
                24,
                24
        );

        // -----------------------------------------------------
        // 标题
        // -----------------------------------------------------

        TextView title =
                new TextView(this);

        title.setText("M3U8 提取器");

        title.setTextSize(26);

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                10,
                0,
                25
        );

        root.addView(title);

        // -----------------------------------------------------
        // 搜索栏
        // -----------------------------------------------------

        LinearLayout searchRow =
                new LinearLayout(this);

        searchRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        keywordEdit =
                new EditText(this);

        keywordEdit.setHint(
                "输入影视名称或关键词"
        );

        keywordEdit.setSingleLine(true);

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        searchRow.addView(
                keywordEdit,
                inputParams
        );

        searchButton =
                new Button(this);

        searchButton.setText("搜索");

        searchRow.addView(
                searchButton,
                new LinearLayout.LayoutParams(
                        130,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(searchRow);

        // -----------------------------------------------------
        // 状态
        // -----------------------------------------------------

        statusText =
                new TextView(this);

        statusText.setText(
                "请输入影视名称或关键词"
        );

        statusText.setTextSize(16);

        statusText.setPadding(
                0,
                20,
                0,
                15
        );

        root.addView(statusText);

        // -----------------------------------------------------
        // 结果滚动区域
        // -----------------------------------------------------

        ScrollView scrollView =
                new ScrollView(this);

        resultLayout =
                new LinearLayout(this);

        resultLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        scrollView.addView(
                resultLayout
        );

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        // -----------------------------------------------------
        // 搜索按钮
        // -----------------------------------------------------

        searchButton.setOnClickListener(v -> {

            String keyword =
                    keywordEdit
                            .getText()
                            .toString()
                            .trim();

            if (keyword.isEmpty()) {

                Toast.makeText(
                        MainActivity.this,
                        "请输入影视名称或关键词",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            search(keyword);
        });

        // 回车搜索
        keywordEdit.setOnEditorActionListener(
                (v, actionId, event) -> {

                    String keyword =
                            keywordEdit
                                    .getText()
                                    .toString()
                                    .trim();

                    if (!keyword.isEmpty()) {

                        search(keyword);

                        return true;
                    }

                    return false;
                }
        );
    }

    // =========================================================
    // 搜索
    // =========================================================

    private void search(String keyword) {

        searchButton.setEnabled(false);

        statusText.setText(
                "正在搜索：" + keyword
        );

        resultLayout.removeAllViews();

        searchItems.clear();

        new Thread(() -> {

            try {

                String encoded =
                        URLEncoder.encode(
                                keyword,
                                "UTF-8"
                        );

                String searchUrl =
                        BASE_URL +
                        "/index.php/vod/search.html?wd=" +
                        encoded;

                String html =
                        get(searchUrl);

                List<SearchItem> results =
                        parseSearchResults(html);

                mainHandler.post(() -> {

                    searchButton.setEnabled(true);

                    searchItems.clear();

                    searchItems.addAll(
                            results
                    );

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

                    showSearchResults(
                            results
                    );
                });

            } catch (Exception e) {

                mainHandler.post(() -> {

                    searchButton.setEnabled(true);

                    statusText.setText(
                            "搜索失败：" +
                            safeError(e)
                    );
                });
            }

        }).start();
    }

    // =========================================================
    // 搜索结果解析
    // =========================================================

    private List<SearchItem> parseSearchResults(
            String html) {

        Map<String, SearchItem> unique =
                new LinkedHashMap<>();

        /*
         * 只提取 a 标签。
         *
         * 最重要的限制：
         *
         * 必须包含 /vod/detail/
         *
         * 这样：
         *
         * 海外动漫
         * 综艺片
         * 大陆综艺
         * 港台综艺
         * 日韩综艺
         *
         * 这些分类页面不会被当成影片。
         */

        Pattern pattern =
                Pattern.compile(
                        "<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
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

            href =
                    decodeHtml(
                            href
                    ).trim();

            // -------------------------------------------------
            // 只允许影片详情页
            // -------------------------------------------------

            if (!isDetailUrl(href)) {
                continue;
            }

            // -------------------------------------------------
            // 提取搜索结果显示的真实影片名称
            //
            // 注意：
            // 这里绝对不能使用用户输入的 keyword
            // -------------------------------------------------

            String originalTitle =
                    extractText(body);

            if (originalTitle.isEmpty()) {
                continue;
            }

            // -------------------------------------------------
            // 清理网站评分、完结状态、更新集数
            // -------------------------------------------------

            String cleanTitle =
                    cleanMovieTitle(
                            originalTitle
                    );

            if (cleanTitle.isEmpty()) {
                continue;
            }

            // -------------------------------------------------
            // 绝对 URL
            // -------------------------------------------------

            href =
                    makeAbsoluteUrl(
                            href
                    );

            // -------------------------------------------------
            // 去重
            // -------------------------------------------------

            if (unique.containsKey(href)) {
                continue;
            }

            SearchItem item =
                    new SearchItem();

            item.title =
                    cleanTitle;

            item.originalTitle =
                    originalTitle;

            item.url =
                    href;

            unique.put(
                    href,
                    item
            );
        }

        return new ArrayList<>(
                unique.values()
        );
    }

    // =========================================================
    // 判断详情页
    // =========================================================

    private boolean isDetailUrl(
            String url) {

        if (url == null) {
            return false;
        }

        String lower =
                url.toLowerCase();

        /*
         * 例如：
         *
         * /index.php/vod/detail/id/123.html
         * /vod/detail/id/123.html
         */

        return lower.contains(
                "/vod/detail/"
        );
    }

    // =========================================================
    // 清理影片名称
    // =========================================================

    private String cleanMovieTitle(
            String title) {

        if (title == null) {
            return "";
        }

        title =
                decodeHtml(title)
                        .trim();

        /*
         * 统一空白
         */

        title =
                title.replace(
                        "\u00A0",
                        " "
                );

        title =
                title.replaceAll(
                        "\\s+",
                        " "
                ).trim();

        /*
         * -----------------------------------------------------
         * 1. 删除最前面的评分
         *
         * 例如：
         *
         * 0.0交锋2026已完结
         * 9.5凡人修仙传更新至第150集
         *
         * ↓
         *
         * 交锋2026已完结
         * 凡人修仙传更新至第150集
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "^\\s*\\d+(?:\\.\\d+)?\\s*",
                        ""
                );

        /*
         * -----------------------------------------------------
         * 2. 删除末尾“已完结”
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "\\s*已完结\\s*$",
                        ""
                );

        /*
         * -----------------------------------------------------
         * 3. 删除：
         *
         * 更新至第40集
         * 更新至40集
         * 更新至第40期
         * 更新至40期
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "\\s*更新至\\s*第?\\s*\\d+\\s*[集期]\\s*$",
                        ""
                );

        /*
         * -----------------------------------------------------
         * 4. 删除：
         *
         * 更新至20260927期
         *
         * 这个主要针对综艺。
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "\\s*更新至\\s*\\d{6,8}\\s*[集期]?\\s*$",
                        ""
                );

        /*
         * -----------------------------------------------------
         * 5. 删除：
         *
         * 全40集
         * 全 40 集
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "\\s*全\\s*\\d+\\s*[集期]\\s*$",
                        ""
                );

        /*
         * -----------------------------------------------------
         * 6. 删除结尾孤立的“第40集”
         *
         * 注意：
         *
         * 只有在最后才删除。
         *
         * “第2季”不会被删除。
         * “第二季”不会被删除。
         * -----------------------------------------------------
         */

        title =
                title.replaceFirst(
                        "\\s*第\\s*\\d+\\s*[集]\\s*$",
                        ""
                );

        /*
         * 最后再次清理空格
         */

        title =
                title.replaceAll(
                        "\\s+",
                        " "
                ).trim();

        return title;
    }

    // =========================================================
    // 显示搜索结果
    // =========================================================

    private void showSearchResults(
            List<SearchItem> results) {

        resultLayout.removeAllViews();

        for (SearchItem item : results) {

            Button button =
                    createButton(
                            item.title
                    );

            resultLayout.addView(
                    button
            );

            button.setOnClickListener(
                    v -> loadDetail(item)
            );
        }
    }

    // =========================================================
    // 读取详情页
    // =========================================================

    private void loadDetail(
            SearchItem item) {

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
                                html
                        );

                mainHandler.post(() -> {

                    if (episodes.isEmpty()) {

                        statusText.setText(
                                "没有解析到 M3U8 集数"
                        );

                        Button back =
                                createButton(
                                        "← 返回搜索结果"
                                );

                        resultLayout.addView(
                                back
                        );

                        back.setOnClickListener(
                                v -> showSearchResults(
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

                mainHandler.post(() -> {

                    statusText.setText(
                            "读取详情失败：" +
                            safeError(e)
                    );

                    Button back =
                            createButton(
                                    "← 返回搜索结果"
                            );

                    resultLayout.addView(
                            back
                    );

                    back.setOnClickListener(
                            v -> showSearchResults(
                                    searchItems
                            )
                    );
                });
            }

        }).start();
    }

    // =========================================================
    // 解析集数
    // =========================================================

    private List<Episode> parseEpisodes(
            String html) {

        Map<String, Episode> map =
                new LinkedHashMap<>();

        /*
         * =====================================================
         * 第一优先级
         *
         * 精确读取：
         *
         * <input
         * name="copy_dyttm3u8[]"
         * value="第1集#https://xxx/index.m3u8">
         *
         * =====================================================
         */

        Pattern p1 =
                Pattern.compile(
                        "<input\\b[^>]*" +
                        "name\\s*=\\s*[\"']copy_dyttm3u8\\[\\][\"']" +
                        "[^>]*" +
                        "value\\s*=\\s*[\"']([^\"']+)[\"']" +
                        "[^>]*>",
                        Pattern.CASE_INSENSITIVE |
                                Pattern.DOTALL
                );

        Matcher m1 =
                p1.matcher(html);

        while (m1.find()) {

            String value =
                    m1.group(1);

            parseEpisodeValue(
                    value,
                    map
            );
        }

        /*
         * =====================================================
         * 第二种属性顺序
         *
         * value 在 name 前面
         * =====================================================
         */

        Pattern p2 =
                Pattern.compile(
                        "<input\\b[^>]*" +
                        "value\\s*=\\s*[\"']([^\"']+)[\"']" +
                        "[^>]*" +
                        "name\\s*=\\s*[\"']copy_dyttm3u8\\[\\][\"']" +
                        "[^>]*>",
                        Pattern.CASE_INSENSITIVE |
                                Pattern.DOTALL
                );

        Matcher m2 =
                p2.matcher(html);

        while (m2.find()) {

            String value =
                    m2.group(1);

            parseEpisodeValue(
                    value,
                    map
            );
        }

        /*
         * =====================================================
         * 第三优先级
         *
         * 有些页面可能不是标准 input，
         * 但源码里面依然存在：
         *
         * 第1集#https://xxx.m3u8
         *
         * =====================================================
         */

        if (map.isEmpty()) {

            Pattern p3 =
                    Pattern.compile(
                            "(第\\s*0*([0-9]+)\\s*集)" +
                            "\\s*#\\s*" +
                            "(https?://[^\"'\\s<>]+?\\.m3u8(?:\\?[^\"'\\s<>]*)?)",
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
                        m3.group(3);

                addEpisode(
                        map,
                        epName,
                        url
                );
            }
        }

        /*
         * =====================================================
         * 第四优先级
         *
         * 如果源码结构发生变化：
         *
         * 第X集
         * ...
         * https://xxx.m3u8
         *
         * =====================================================
         */

        if (map.isEmpty()) {

            Pattern p4 =
                    Pattern.compile(
                            "(第\\s*0*([0-9]+)\\s*集)" +
                            "[\\s\\S]{0,800}?" +
                            "(https?://[^\"'\\s<>]+?\\.m3u8(?:\\?[^\"'\\s<>]*)?)",
                            Pattern.CASE_INSENSITIVE
                    );

            Matcher m4 =
                    p4.matcher(
                            decodeHtml(html)
                    );

            while (m4.find()) {

                String epName =
                        m4.group(1);

                String url =
                        m4.group(3);

                addEpisode(
                        map,
                        epName,
                        url
                );
            }
        }

        /*
         * =====================================================
         * 按集数数字排序
         * =====================================================
         */

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
                            Episode b) {

                        return Integer.compare(
                                a.number,
                                b.number
                        );
                    }
                }
        );

        return list;
    }

    // =========================================================
    // 解析：
    //
    // 第1集#URL
    //
    // =========================================================

    private void parseEpisodeValue(
            String value,
            Map<String, Episode> map) {

        if (value == null) {
            return;
        }

        value =
                decodeHtml(
                        value
                ).trim();

        /*
         * 找第一个 #
         */

        int index =
                value.indexOf("#");

        if (index <= 0) {
            return;
        }

        String episodeName =
                value.substring(
                        0,
                        index
                ).trim();

        String url =
                value.substring(
                        index + 1
                ).trim();

        url =
                cleanUrl(url);

        if (!isM3u8(url)) {
            return;
        }

        addEpisode(
                map,
                episodeName,
                url
        );
    }

    // =========================================================
    // 添加集数
    // =========================================================

    private void addEpisode(
            Map<String, Episode> map,
            String episodeName,
            String url) {

        if (episodeName == null ||
                url == null) {

            return;
        }

        episodeName =
                decodeHtml(
                        episodeName
                ).trim();

        url =
                cleanUrl(url);

        if (!isM3u8(url)) {
            return;
        }

        /*
         * 支持：
         *
         * 第1集
         * 第01集
         * 第001集
         * 1集
         * 01集
         */

        Pattern episodePattern =
                Pattern.compile(
                        "(?:第\\s*)?0*([0-9]+)\\s*集",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher matcher =
                episodePattern.matcher(
                        episodeName
                );

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

        if (number <= 0) {
            return;
        }

        /*
         * 用集数作为唯一键。
         *
         * 如果页面重复出现第1集，
         * 只保留第一条。
         */

        String key =
                String.valueOf(number);

        if (map.containsKey(key)) {
            return;
        }

        Episode episode =
                new Episode();

        episode.number =
                number;

        episode.name =
                "第" +
                number +
                "集";

        episode.url =
                url;

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

        /*
         * -----------------------------------------------------
         * 全部复制
         * -----------------------------------------------------
         */

        Button allButton =
                createButton(
                        "复制全部 " +
                        episodes.size() +
                        " 集"
                );

        resultLayout.addView(
                allButton
        );

        allButton.setOnClickListener(
                v -> {

                    StringBuilder sb =
                            new StringBuilder();

                    for (Episode episode :
                            episodes) {

                        sb.append(
                                episode.url
                        );

                        sb.append("#");

                        sb.append(
                                title
                        );

                        sb.append(
                                episode.name
                        );

                        sb.append("\n");
                    }

                    String result =
                            sb.toString().trim();

                    copyToClipboard(
                            result
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "已复制全部 " +
                            episodes.size() +
                            " 集",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        /*
         * -----------------------------------------------------
         * 返回
         * -----------------------------------------------------
         */

        Button backButton =
                createButton(
                        "← 返回影片列表"
                );

        resultLayout.addView(
                backButton
        );

        backButton.setOnClickListener(
                v -> showSearchResults(
                        searchItems
                )
        );

        /*
         * -----------------------------------------------------
         * 单集
         * -----------------------------------------------------
         */

        for (Episode episode :
                episodes) {

            Button button =
                    createButton(
                            episode.name
                    );

            resultLayout.addView(
                    button
            );

            button.setOnClickListener(
                    v -> {

                        /*
                         * 最终格式：
                         *
                         * URL#搜索结果实际片名第1集
                         *
                         * 例如：
                         *
                         * https://xxx/index.m3u8#交锋2026第1集
                         */

                        String result =
                                episode.url +
                                "#" +
                                title +
                                episode.name;

                        copyToClipboard(
                                result
                        );

                        Toast.makeText(
                                MainActivity.this,
                                "已复制：" +
                                episode.name,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        }
    }

    // =========================================================
    // 网络请求
    // =========================================================

    private String get(
            String urlString)
            throws Exception {

        HttpURLConnection connection =
                null;

        try {

            URL url =
                    new URL(urlString);

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod(
                    "GET"
            );

            connection.setConnectTimeout(
                    15000
            );

            connection.setReadTimeout(
                    20000
            );

            connection.setInstanceFollowRedirects(
                    true
            );

            /*
             * 模拟手机浏览器
             */

            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 " +
                    "(Linux; Android 13) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 " +
                    "Mobile Safari/537.36"
            );

            connection.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml," +
                    "application/xml;q=0.9," +
                    "*/*;q=0.8"
            );

            connection.setRequestProperty(
                    "Accept-Language",
                    "zh-CN,zh;q=0.9"
            );

            connection.setRequestProperty(
                    "Connection",
                    "keep-alive"
            );

            int responseCode =
                    connection.getResponseCode();

            InputStream inputStream;

            if (responseCode >= 400) {

                inputStream =
                        connection.getErrorStream();

            } else {

                inputStream =
                        connection.getInputStream();
            }

            if (inputStream == null) {

                throw new Exception(
                        "HTTP " +
                        responseCode
                );
            }

            /*
             * 获取网页字符集
             */

            String charset =
                    getCharset(
                            connection.getContentType()
                    );

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    inputStream,
                                    Charset.forName(
                                            charset
                                    )
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            String line;

            while (
                    (line =
                            reader.readLine())
                            != null
            ) {

                result.append(
                        line
                );

                result.append(
                        "\n"
                );
            }

            reader.close();

            return result.toString();

        } finally {

            if (connection != null) {

                connection.disconnect();
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
                    ).matcher(
                            contentType
                    );

            if (matcher.find()) {

                String charset =
                        matcher.group(1)
                                .trim();

                try {

                    Charset.forName(
                            charset
                    );

                    return charset;

                } catch (Exception ignored) {
                }
            }
        }

        /*
         * 这个站如果没有明确返回 charset，
         * 优先 UTF-8。
         */

        return "UTF-8";
    }

    // =========================================================
    // HTML → 纯文字
    // =========================================================

    private String extractText(
            String html) {

        if (html == null) {
            return "";
        }

        String text =
                html;

        /*
         * 删除 script
         */

        text =
                text.replaceAll(
                        "(?is)<script[^>]*>.*?</script>",
                        " "
                );

        /*
         * 删除 style
         */

        text =
                text.replaceAll(
                        "(?is)<style[^>]*>.*?</style>",
                        " "
                );

        /*
         * 删除 HTML 标签
         */

        text =
                text.replaceAll(
                        "<[^>]+>",
                        " "
                );

        /*
         * HTML 实体
         */

        text =
                decodeHtml(
                        text
                );

        /*
         * nbsp
         */

        text =
                text.replace(
                        "\u00A0",
                        " "
                );

        /*
         * 合并空白
         */

        text =
                text.replaceAll(
                        "\\s+",
                        " "
                ).trim();

        return text;
    }

    // =========================================================
    // HTML 实体解码
    // =========================================================

    private String decodeHtml(
            String value) {

        if (value == null) {
            return "";
        }

        return Html.fromHtml(
                value,
                Html.FROM_HTML_MODE_LEGACY
        ).toString();
    }

    // =========================================================
    // URL 转绝对地址
    // =========================================================

    private String makeAbsoluteUrl(
            String url) {

        if (url == null ||
                url.isEmpty()) {

            return "";
        }

        url =
                decodeHtml(
                        url
                ).trim();

        /*
         * 已经是完整 URL
         */

        if (url.startsWith(
                "http://"
        ) ||
                url.startsWith(
                        "https://"
                )) {

            return url;
        }

        /*
         * //example.com
         */

        if (url.startsWith(
                "//"
        )) {

            return "https:" + url;
        }

        /*
         * /index.php/...
         */

        if (url.startsWith(
                "/"
        )) {

            return BASE_URL + url;
        }

        /*
         * 相对地址
         */

        return BASE_URL + "/" + url;
    }

    // =========================================================
    // 清理 URL
    // =========================================================

    private String cleanUrl(
            String url) {

        if (url == null) {
            return "";
        }

        url =
                decodeHtml(
                        url
                ).trim();

        /*
         * &amp;
         */

        url =
                url.replace(
                        "&amp;",
                        "&"
                );

        /*
         * 去掉首尾引号
         */

        url =
                url.replaceAll(
                        "^[\"']+",
                        ""
                );

        url =
                url.replaceAll(
                        "[\"']+$",
                        ""
                );

        /*
         * 去掉 HTML 尾部符号
         */

        url =
                url.replaceAll(
                        "[<>\\s]+$",
                        ""
                );

        return url.trim();
    }

    // =========================================================
    // 判断 M3U8
    // =========================================================

    private boolean isM3u8(
            String url) {

        if (url == null) {
            return false;
        }

        return url
                .toLowerCase()
                .contains(".m3u8");
    }

    // =========================================================
    // 创建按钮
    // =========================================================

    private Button createButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(
                text
        );

        button.setTextSize(
                16
        );

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

        button.setLayoutParams(
                params
        );

        return button;
    }

    // =========================================================
    // 复制到剪贴板
    // =========================================================

    private void copyToClipboard(
            String text) {

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        ClipData clip =
                ClipData.newPlainText(
                        "M3U8",
                        text
                );

        clipboard.setPrimaryClip(
                clip
        );
    }

    // =========================================================
    // 错误信息
    // =========================================================

    private String safeError(
            Exception e) {

        if (e == null) {
            return "未知错误";
        }

        String message =
                e.getMessage();

        if (message == null ||
                message.trim().isEmpty()) {

            return e.getClass()
                    .getSimpleName();
        }

        return message;
    }

    // =========================================================
    // 搜索结果对象
    // =========================================================

    private static class SearchItem {

        // 最终使用的干净名称
        String title;

        // 网站原始显示名称
        String originalTitle;

        // 详情页地址
        String url;
    }

    // =========================================================
    // 集数对象
    // =========================================================

    private static class Episode {

        // 集数数字
        int number;

        // 显示名称，例如：第1集
        String name;

        // M3U8 地址
        String url;
    }
}
