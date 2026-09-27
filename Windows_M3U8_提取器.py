import re
import html
import tkinter as tk
from tkinter import messagebox, simpledialog
from urllib.parse import quote
from urllib.request import Request, urlopen


BASE_URL = "https://dyttzy.tv"
SEARCH_URL = BASE_URL + "/index.php/vod/search.html?wd="

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0 Safari/537.36"
    )
}


def get_html(url):
    req = Request(url, headers=HEADERS)

    with urlopen(req, timeout=20) as response:
        data = response.read()

        encoding = response.headers.get_content_charset()

        if not encoding:
            encoding = "utf-8"

        return data.decode(encoding, errors="ignore")


def clean_text(text):
    text = html.unescape(text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def get_title(page_html):
    patterns = [
        r'<h1[^>]*>(.*?)</h1>',
        r'<h2[^>]*>(.*?)</h2>',
        r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\'](.*?)["\']',
        r"<title[^>]*>(.*?)</title>",
    ]

    for pattern in patterns:
        match = re.search(pattern, page_html, re.I | re.S)

        if match:
            title = clean_text(match.group(1))

            title = re.sub(
                r"\s*[-_|｜]\s*(电影天堂|dyttzy|电影天堂资源).*?$",
                "",
                title,
                flags=re.I,
            )

            if title:
                return title.strip()

    return "未知作品"


def search_movies(keyword):
    url = SEARCH_URL + quote(keyword)

    page_html = get_html(url)

    results = []

    patterns = [
        r'<a[^>]+href=["\']([^"\']*(?:/vod/|/index\.php/vod/detail)[^"\']*)["\'][^>]*>(.*?)</a>',
        r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>(.*?)</a>',
    ]

    seen = set()

    for pattern in patterns:
        for match in re.finditer(pattern, page_html, re.I | re.S):

            href = html.unescape(match.group(1))
            title = clean_text(match.group(2))

            if not title:
                continue

            if "javascript:" in href.lower():
                continue

            if href.startswith("//"):
                href = "https:" + href

            elif href.startswith("/"):
                href = BASE_URL + href

            elif not href.startswith("http"):
                href = BASE_URL + "/" + href.lstrip("/")

            key = (title, href)

            if key in seen:
                continue

            seen.add(key)

            results.append({
                "title": title,
                "url": href
            })

    return results


def extract_precise_m3u8(page_html):
    """
    优先提取：

    name="copy_dyttm3u8[]"
    value="第1集#https://xxxx/index.m3u8"
    """

    results = []

    pattern = re.compile(
        r'name=["\']copy_dyttm3u8\[\]["\']'
        r'[^>]*value=["\']([^"\']+)["\']',
        re.I
    )

    for match in pattern.finditer(page_html):

        value = html.unescape(match.group(1))

        value = value.replace("&amp;", "&")

        parts = value.split("#", 1)

        if len(parts) != 2:
            continue

        episode = parts[0].strip()
        url = parts[1].strip()

        if not re.match(r"https?://", url, re.I):
            continue

        if ".m3u8" not in url.lower():
            continue

        results.append((episode, url))

    return results


def extract_generic_m3u8(page_html):
    """
    通用备用提取方式。
    """

    results = []

    clean = re.sub(
        r"<script.*?</script>",
        " ",
        page_html,
        flags=re.I | re.S
    )

    clean = re.sub(
        r"<style.*?</style>",
        " ",
        clean,
        flags=re.I | re.S
    )

    clean = re.sub(r"<[^>]+>", " ", clean)

    clean = html.unescape(clean)

    pattern = re.compile(
        r'(第?\s*\d+\s*集)?'
        r'[^h\n\r]{0,20}?'
        r'(https?://[^\s"\'<>]+?\.m3u8[^\s"\'<>]*)',
        re.I
    )

    for match in pattern.finditer(clean):

        episode = match.group(1) or ""

        url = match.group(2)

        url = url.rstrip(".,);]}>")

        if not episode:

            before = clean[max(0, match.start() - 40):match.start()]

            ep_match = re.search(
                r'第\s*(\d+)\s*集',
                before
            )

            if ep_match:
                episode = f"第{ep_match.group(1)}集"

        results.append((episode.strip(), url.strip()))

    return results


def episode_number(episode):

    match = re.search(r"(\d+)", episode or "")

    if match:
        return int(match.group(1))

    return 999999


def unique_sort(items):

    result = []

    seen = set()

    for episode, url in items:

        key = url.strip()

        if key in seen:
            continue

        seen.add(key)

        result.append((episode, url))

    result.sort(
        key=lambda x: (
            episode_number(x[0]),
            x[0],
            x[1]
        )
    )

    return result


class SelectWindow:

    def __init__(self, parent, title, items):

        self.result = None

        self.window = tk.Toplevel(parent)

        self.window.title(title)

        self.window.geometry("650x550")

        self.window.resizable(True, True)

        self.window.transient(parent)

        self.window.grab_set()

        label = tk.Label(
            self.window,
            text="请选择作品：",
            font=("Microsoft YaHei", 12)
        )

        label.pack(
            padx=15,
            pady=(15, 5),
            anchor="w"
        )

        frame = tk.Frame(self.window)

        frame.pack(
            fill="both",
            expand=True,
            padx=15,
            pady=5
        )

        scrollbar = tk.Scrollbar(frame)

        scrollbar.pack(
            side="right",
            fill="y"
        )

        self.listbox = tk.Listbox(
            frame,
            font=("Microsoft YaHei", 11),
            yscrollcommand=scrollbar.set
        )

        self.listbox.pack(
            side="left",
            fill="both",
            expand=True
        )

        scrollbar.config(
            command=self.listbox.yview
        )

        for item in items:

            self.listbox.insert(
                tk.END,
                item
            )

        if items:
            self.listbox.selection_set(0)

        button_frame = tk.Frame(self.window)

        button_frame.pack(
            fill="x",
            padx=15,
            pady=15
        )

        tk.Button(
            button_frame,
            text="确定",
            width=12,
            command=self.confirm
        ).pack(
            side="left",
            padx=5
        )

        tk.Button(
            button_frame,
            text="取消",
            width=12,
            command=self.cancel
        ).pack(
            side="left",
            padx=5
        )

        self.window.protocol(
            "WM_DELETE_WINDOW",
            self.cancel
        )

        self.window.bind(
            "<Double-Button-1>",
            lambda event: self.confirm()
        )

    def confirm(self):

        selection = self.listbox.curselection()

        if not selection:
            messagebox.showwarning(
                "提示",
                "请选择一个作品。",
                parent=self.window
            )
            return

        self.result = selection[0]

        self.window.destroy()

    def cancel(self):

        self.result = None

        self.window.destroy()


def choose_work(root, results):

    display_items = []

    for item in results:

        display_items.append(
            f"{item['title']}    {item['url']}"
        )

    selector = SelectWindow(
        root,
        "选择作品",
        display_items
    )

    root.wait_window(selector.window)

    if selector.result is None:
        return None

    return results[selector.result]


def choose_episode(root, episodes):

    window = tk.Toplevel(root)

    window.title("选择集数")

    window.geometry("400x520")

    window.resizable(True, True)

    window.transient(root)

    window.grab_set()

    result = {
        "value": None
    }

    tk.Label(
        window,
        text="请选择提取方式",
        font=("Microsoft YaHei", 12)
    ).pack(
        pady=15
    )

    def all_episode():

        result["value"] = "all"

        window.destroy()

    tk.Button(
        window,
        text="全部集数",
        width=20,
        height=2,
        command=all_episode
    ).pack(
        pady=5
    )

    tk.Label(
        window,
        text="或选择单集：",
        font=("Microsoft YaHei", 10)
    ).pack(
        pady=(20, 5)
    )

    frame = tk.Frame(window)

    frame.pack(
        fill="both",
        expand=True,
        padx=20
    )

    scrollbar = tk.Scrollbar(frame)

    scrollbar.pack(
        side="right",
        fill="y"
    )

    listbox = tk.Listbox(
        frame,
        yscrollcommand=scrollbar.set,
        font=("Microsoft YaHei", 11)
    )

    listbox.pack(
        side="left",
        fill="both",
        expand=True
    )

    scrollbar.config(
        command=listbox.yview
    )

    for episode, url in episodes:

        listbox.insert(
            tk.END,
            episode
        )

    def single_episode():

        selection = listbox.curselection()

        if not selection:

            messagebox.showwarning(
                "提示",
                "请选择一个集数。",
                parent=window
            )

            return

        result["value"] = selection[0]

        window.destroy()

    tk.Button(
        window,
        text="提取选择的单集",
        width=20,
        height=2,
        command=single_episode
    ).pack(
        pady=15
    )

    root.wait_window(window)

    return result["value"]


def show_result(root, output):

    window = tk.Toplevel(root)

    window.title("M3U8提取结果")

    window.geometry("900x650")

    window.transient(root)

    text = tk.Text(
        window,
        font=("Consolas", 10),
        wrap="none"
    )

    text.pack(
        fill="both",
        expand=True,
        padx=10,
        pady=10
    )

    text.insert(
        "1.0",
        output
    )

    def copy_all():

        root.clipboard_clear()

        root.clipboard_append(output)

        root.update()

        messagebox.showinfo(
            "完成",
            f"已复制 {len(output.splitlines())} 条结果到剪贴板。",
            parent=window
        )

    button_frame = tk.Frame(window)

    button_frame.pack(
        pady=10
    )

    tk.Button(
        button_frame,
        text="复制全部",
        width=15,
        command=copy_all
    ).pack(
        side="left",
        padx=10
    )

    tk.Button(
        button_frame,
        text="关闭",
        width=15,
        command=window.destroy
    ).pack(
        side="left",
        padx=10
    )

    window.grab_set()


def main():

    root = tk.Tk()

    root.withdraw()

    root.title("Windows M3U8 提取器")

    keyword = simpledialog.askstring(
        "搜索作品",
        "请输入影视作品名称：",
        parent=root
    )

    if not keyword:
        root.destroy()
        return

    try:

        results = search_movies(keyword)

    except Exception as e:

        messagebox.showerror(
            "搜索失败",
            str(e),
            parent=root
        )

        root.destroy()
        return

    if not results:

        messagebox.showinfo(
            "没有结果",
            "没有找到相关作品。",
            parent=root
        )

        root.destroy()
        return

    work = choose_work(
        root,
        results
    )

    if not work:

        root.destroy()
        return

    try:

        page_html = get_html(
            work["url"]
        )

    except Exception as e:

        messagebox.showerror(
            "打开失败",
            str(e),
            parent=root
        )

        root.destroy()
        return

    title = get_title(page_html)

    if title == "未知作品":

        title = work["title"]

    episodes = extract_precise_m3u8(
        page_html
    )

    if not episodes:

        episodes = extract_generic_m3u8(
            page_html
        )

    episodes = unique_sort(
        episodes
    )

    if not episodes:

        messagebox.showinfo(
            "提取失败",
            "该页面没有找到 M3U8 地址。",
            parent=root
        )

        root.destroy()
        return

    choice = choose_episode(
        root,
        episodes
    )

    if choice is None:

        root.destroy()
        return

    if choice == "all":

        selected = episodes

    else:

        selected = [
            episodes[choice]
        ]

    output_lines = []

    for episode, url in selected:

        if episode:

            name = title + episode

        else:

            name = title

        output_lines.append(
            f"{url}#{name}"
        )

    output = "\n".join(
        output_lines
    )

    root.clipboard_clear()

    root.clipboard_append(
        output
    )

    root.update()

    show_result(
        root,
        output
    )

    root.mainloop()


if __name__ == "__main__":

    main()
