# -*- coding: utf-8 -*-
"""
Ловець легальних роздач ігор — фоновий застосунок для Windows (один .exe).

Стежить за офіційними безкоштовними роздачами через публічний API GamerPower
(Epic Games, Steam, GOG, Prime Gaming тощо). Коли з'являється нова роздача,
що підходить під ключові слова:
  - показує сповіщення Windows,
  - КОПІЮЄ посилання на роздачу в буфер обміну (Ctrl+V — і вставив),
  - (за бажанням) відкриває сторінку роздачі в браузері.

Працює у треї (іконка біля годинника). Нічого не зламує і не перебирає ключі —
тільки офіційні безкоштовні роздачі, які ти забираєш сам за посиланням.
"""

import json
import os
import sys
import threading
import time
import webbrowser

import requests

API_URL = "https://www.gamerpower.com/api/giveaways"

DEFAULT_CONFIG = {
    "keywords": [],                     # [] = ловити ВСІ роздачі ігор/ключів
    "check_interval_minutes": 15,
    "types": ["game"],                  # "game" / "loot" / "dlc"
    "copy_to_clipboard": True,
    "auto_open_browser": False,
    "autostart": True,                  # запускатися разом із Windows
}

APP_RUN_NAME = "GiveawayCatcher"
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


# ----------------------- шляхи поряд з .exe -----------------------

def app_dir():
    """Папка, де лежить .exe (або цей .py при запуску з Python)."""
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


BASE = app_dir()
CONFIG_FILE = os.path.join(BASE, "config.json")
SEEN_FILE = os.path.join(BASE, "seen.json")
LOG_FILE = os.path.join(BASE, "log.txt")


# ----------------------------- утиліти -----------------------------

def log(msg):
    line = time.strftime("[%Y-%m-%d %H:%M:%S] ") + msg
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass
    print(line, flush=True)


def load_config():
    cfg = dict(DEFAULT_CONFIG)
    try:
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            user = json.load(f)
        if isinstance(user, dict):
            cfg.update(user)
    except FileNotFoundError:
        # створюємо приклад конфігу поряд з .exe
        try:
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(DEFAULT_CONFIG, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
    except Exception as e:
        log("Не вдалося прочитати config.json (%s), беру типові налаштування." % e)
    return cfg


def load_seen():
    try:
        with open(SEEN_FILE, "r", encoding="utf-8") as f:
            return set(json.load(f))
    except Exception:
        return set()


def save_seen(seen):
    try:
        with open(SEEN_FILE, "w", encoding="utf-8") as f:
            json.dump(sorted(seen), f)
    except Exception as e:
        log("Не вдалося зберегти seen.json: %s" % e)


def copy_to_clipboard(text):
    try:
        import pyperclip
        pyperclip.copy(text)
        return True
    except Exception as e:
        log("Буфер обміну недоступний (%s)" % e)
        return False


def notify(title, message, url=None):
    try:
        from winotify import Notification, audio
        toast = Notification(app_id="Ловець роздач", title=title,
                             msg=message, duration="long")
        toast.set_audio(audio.Default, loop=False)
        if url:
            toast.add_actions(label="Відкрити роздачу", launch=url)
        toast.show()
    except Exception as e:
        log("Сповіщення недоступне (%s): %s — %s" % (e, title, message))


# --------------------- автозапуск із Windows ---------------------

def _exe_path():
    if getattr(sys, "frozen", False):
        return sys.executable
    return os.path.abspath(__file__)


def autostart_enabled():
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            val, _ = winreg.QueryValueEx(k, APP_RUN_NAME)
            return bool(val)
    except Exception:
        return False


def set_autostart(enable):
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0,
                            winreg.KEY_SET_VALUE) as k:
            if enable:
                winreg.SetValueEx(k, APP_RUN_NAME, 0, winreg.REG_SZ,
                                  '"%s"' % _exe_path())
            else:
                try:
                    winreg.DeleteValue(k, APP_RUN_NAME)
                except FileNotFoundError:
                    pass
        return True
    except Exception as e:
        log("Автозапуск: не вдалося змінити (%s)" % e)
        return False


def fetch_giveaways(types):
    params = {}
    if types:
        params["type"] = ".".join(types)
    r = requests.get(API_URL, params=params, timeout=30,
                     headers={"User-Agent": "giveaway-tracker"})
    r.raise_for_status()
    data = r.json()
    return data if isinstance(data, list) else []


def matches(giveaway, keywords):
    if not keywords:
        return True
    title = (giveaway.get("title") or "").lower()
    return any(kw.lower() in title for kw in keywords)


# ----------------------------- логіка -----------------------------

class Tracker:
    def __init__(self):
        self.cfg = load_config()
        self.seen = load_seen()
        self.stop_event = threading.Event()
        self.last_url = None

    def check_once(self, announce=True):
        cfg = self.cfg
        try:
            giveaways = fetch_giveaways(cfg.get("types"))
        except Exception as e:
            log("Помилка запиту до API: %s" % e)
            if announce:
                notify("Ловець роздач", "Не вдалося зв'язатися з сервером роздач. "
                                        "Перевір інтернет — спробую пізніше.")
            return 0

        new_hits = []
        for g in giveaways:
            gid = str(g.get("id"))
            if gid in self.seen:
                continue
            if matches(g, cfg.get("keywords")):
                new_hits.append(g)
            self.seen.add(gid)

        for g in new_hits:
            title = g.get("title", "Без назви")
            platforms = g.get("platforms", "")
            worth = g.get("worth", "")
            url = g.get("open_giveaway_url") or g.get("open_giveaway") or ""
            extra = ("  •  було %s" % worth) if worth and worth != "N/A" else ""
            log("НОВА РОЗДАЧА: %s (%s) %s" % (title, platforms, url))
            if cfg.get("copy_to_clipboard") and url:
                if copy_to_clipboard(url):
                    self.last_url = url
                    log("  посилання скопійовано в буфер обміну")
            notify("🎁 Роздача: " + title,
                   (platforms + extra) or "Безкоштовно зараз!", url)
            if cfg.get("auto_open_browser") and url:
                webbrowser.open(url)
            time.sleep(1)

        save_seen(self.seen)
        if not new_hits and announce:
            log("Нових роздач за критеріями немає.")
        return len(new_hits)

    def run_loop(self):
        kw = self.cfg.get("keywords") or "ВСІ"
        interval = max(1, int(self.cfg.get("check_interval_minutes", 15)))
        log("Запущено. Ключові слова: %s | інтервал: %d хв" % (kw, interval))
        first = len(self.seen) == 0
        if first:
            log("Перший запуск — знайомлюся з поточними роздачами; "
                "сповіщатиму лише про НОВІ після цього.")
        while not self.stop_event.is_set():
            self.check_once(announce=False if first else True)
            first = False
            # чекаємо інтервал, але реагуємо на «Вийти» одразу
            self.stop_event.wait(interval * 60)


# --------------------------- трей / запуск ---------------------------

def make_icon_image():
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # проста «подарункова коробка»
    d.rectangle([12, 28, 52, 56], fill=(220, 50, 50, 255))
    d.rectangle([12, 20, 52, 30], fill=(200, 30, 30, 255))
    d.rectangle([30, 20, 34, 56], fill=(255, 220, 90, 255))   # вертикальна стрічка
    d.rectangle([12, 26, 52, 30], fill=(255, 220, 90, 255))   # горизонтальна стрічка
    return img


def run_with_tray(tracker):
    import pystray
    from pystray import MenuItem as Item

    t = threading.Thread(target=tracker.run_loop, daemon=True)
    t.start()

    def on_check(icon, item):
        threading.Thread(
            target=lambda: _check_and_toast(tracker), daemon=True).start()

    def _check_and_toast(tr):
        n = tr.check_once(announce=True)
        if n == 0:
            notify("Ловець роздач", "Поки нових роздач немає. Стежу далі 👀")

    def on_open_last(icon, item):
        if tracker.last_url:
            webbrowser.open(tracker.last_url)
        else:
            notify("Ловець роздач", "Ще не було жодної роздачі для відкриття.")

    def on_open_folder(icon, item):
        try:
            os.startfile(BASE)  # noqa (Windows only)
        except Exception:
            webbrowser.open(BASE)

    def on_toggle_autostart(icon, item):
        new_state = not autostart_enabled()
        if set_autostart(new_state):
            notify("Ловець роздач",
                   "Автозапуск з Windows %s." % ("увімкнено" if new_state else "вимкнено"))

    def on_quit(icon, item):
        tracker.stop_event.set()
        icon.stop()

    # один раз автоматично вмикаємо автозапуск (якщо так налаштовано)
    marker = os.path.join(BASE, ".autostart_done")
    if tracker.cfg.get("autostart", True) and not os.path.exists(marker):
        if set_autostart(True):
            log("Автозапуск з Windows увімкнено.")
        try:
            open(marker, "w").close()
        except Exception:
            pass

    kw = tracker.cfg.get("keywords") or "усі ігри"
    menu = pystray.Menu(
        Item("Ловлю: %s" % (", ".join(kw) if isinstance(kw, list) else kw),
             None, enabled=False),
        Item("Перевірити зараз", on_check, default=True),
        Item("Відкрити останню роздачу", on_open_last),
        Item("Відкрити папку (лог/налаштування)", on_open_folder),
        Item("Автозапуск з Windows", on_toggle_autostart,
             checked=lambda i: autostart_enabled()),
        Item("Вийти", on_quit),
    )
    icon = pystray.Icon("giveaway", make_icon_image(),
                        "Ловець легальних роздач", menu)
    notify("Ловець роздач запущено",
           "Працюю у треї. Знайду роздачу — скопіюю посилання й сповіщу.")
    icon.run()


def main():
    tracker = Tracker()
    try:
        run_with_tray(tracker)
    except Exception as e:
        # якщо трей недоступний — працюємо просто фоном
        log("Трей недоступний (%s), працюю без іконки." % e)
        tracker.run_loop()


if __name__ == "__main__":
    main()
