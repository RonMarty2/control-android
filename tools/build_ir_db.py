import os, re, sys, collections

flipper, coreelec, out_path = sys.argv[1], sys.argv[2], sys.argv[3]

CATS = {"TVs": "T", "Universal_TV_Remotes": "T", "Streaming_Devices": "B", "Cable_Boxes": "B",
        "Converters": "B", "DVB-T": "B", "TV_Tuner": "B"}

POWER = re.compile(r"^(power|pwr|power[ _-]?toggle|power[ _-]?on|on[ _/-]?off|on|standby|stand[ _-]?by|"
                   r"power[ _-]?on[ _/-]?off|power[ _-]?button|turn[ _-]?on|tv[ _-]?power|stb[ _-]?power|power[ _-]?1|power1)$", re.I)
KEYMAP = [
    ("VOLUME_UP", re.compile(r"^vol(ume)?[ _-]?(up|\+|plus|inc|increase)$|^vol\+$", re.I)),
    ("VOLUME_DOWN", re.compile(r"^vol(ume)?[ _-]?(down|-|minus|dec|decrease)$|^vol-$", re.I)),
    ("MUTE", re.compile(r"^mute$", re.I)),
    ("CHANNEL_UP", re.compile(r"^(ch(annel)?|prog(ram)?)[ _-]?(up|\+|next|plus|nxt)$|^ch\+$", re.I)),
    ("CHANNEL_DOWN", re.compile(r"^(ch(annel)?|prog(ram)?)[ _-]?(down|-|prev|previous|minus|prv)$|^ch-$", re.I)),
    ("UP", re.compile(r"^(up|arrow[ _-]?up|cursor[ _-]?up|nav[ _-]?up|dpad[ _-]?up)$", re.I)),
    ("DOWN", re.compile(r"^(down|arrow[ _-]?down|cursor[ _-]?down|nav[ _-]?down|dpad[ _-]?down)$", re.I)),
    ("LEFT", re.compile(r"^(left|arrow[ _-]?left|cursor[ _-]?left|nav[ _-]?left|dpad[ _-]?left)$", re.I)),
    ("RIGHT", re.compile(r"^(right|arrow[ _-]?right|cursor[ _-]?right|nav[ _-]?right|dpad[ _-]?right)$", re.I)),
    ("SELECT", re.compile(r"^(ok|enter|select|center|centre|confirm|ok[ _-]?enter)$", re.I)),
    ("BACK", re.compile(r"^(back|return)$", re.I)),
    ("HOME", re.compile(r"^home$", re.I)),
    ("PLAY_PAUSE", re.compile(r"^(play|pause|play[ _/-]?pause|playpause)$", re.I)),
]
LINUX_KEYS = {"KEY_POWER": "POWER", "KEY_VOLUMEUP": "VOLUME_UP", "KEY_VOLUMEDOWN": "VOLUME_DOWN",
              "KEY_MUTE": "MUTE", "KEY_CHANNELUP": "CHANNEL_UP", "KEY_CHANNELDOWN": "CHANNEL_DOWN",
              "KEY_UP": "UP", "KEY_DOWN": "DOWN", "KEY_LEFT": "LEFT", "KEY_RIGHT": "RIGHT",
              "KEY_ENTER": "SELECT", "KEY_OK": "SELECT", "KEY_SELECT": "SELECT", "KEY_BACK": "BACK",
              "KEY_HOME": "HOME", "KEY_PLAYPAUSE": "PLAY_PAUSE", "KEY_PLAY": "PLAY_PAUSE"}
LINUX_NUM = {116: "POWER", 115: "VOLUME_UP", 114: "VOLUME_DOWN", 113: "MUTE", 402: "CHANNEL_UP",
             403: "CHANNEL_DOWN", 103: "UP", 108: "DOWN", 105: "LEFT", 106: "RIGHT", 97: "SELECT",
             28: "SELECT", 158: "BACK", 102: "HOME", 164: "PLAY_PAUSE", 207: "PLAY_PAUSE"}


def hx(b):
    return "%02x" % (b & 0xFF)


def convert(proto, address, command):
    """Flipper parsed signal -> (normalized code, address-group key) or None if unsupported."""
    a = [int(x, 16) for x in address.split()]
    c = [int(x, 16) for x in command.split()]
    if proto == "NEC":
        return "nec:" + hx(a[0]) + hx(~a[0]) + hx(c[0]) + hx(~c[0]), "nec:" + hx(a[0]) + hx(~a[0])
    if proto == "NECext":
        return "nec:" + hx(a[0]) + hx(a[1]) + hx(c[0]) + hx(c[1]), "nec:" + hx(a[0]) + hx(a[1])
    if proto == "Samsung32":
        if c[1] != 0:
            return None
        return "samsung:" + hx(a[0]) + hx(a[0]) + hx(c[0]) + hx(~c[0]), "samsung:" + hx(a[0])
    if proto in ("SIRC", "SIRC15", "SIRC20"):
        bits = {"SIRC": 12, "SIRC15": 15, "SIRC20": 20}[proto]
        addr = a[0] | (a[1] << 8)
        return "sirc:%d:%s:%x" % (bits, hx(c[0]), addr), "sirc:%d:%x" % (bits, addr)
    if proto == "RC5":
        return "rc5:%s:%s" % (hx(a[0]), hx(c[0])), "rc5:" + hx(a[0])
    if proto == "RC6":
        return "rc6:%s:%s" % (hx(a[0]), hx(c[0])), "rc6:" + hx(a[0])
    return None


class Entry:
    def __init__(self, code, group):
        self.code, self.group = code, group
        self.flags, self.n = set(), 0
        self.brands = collections.Counter()


entries = collections.OrderedDict()
buttons = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))


def add_entry(code, group, flag, brand, weight=1):
    e = entries.setdefault(code, Entry(code, group))
    e.flags.add(flag)
    e.n += weight
    e.brands[brand] += weight


def parse_ir(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    for block in text.split("#"):
        d = {}
        for line in block.strip().splitlines():
            if ":" in line:
                k, v = line.split(":", 1)
                d[k.strip()] = v.strip()
        if "name" in d and "type" in d:
            yield d


# ---- Flipper-IRDB (CC0) ----
for cat, flag in CATS.items():
    base = os.path.join(flipper, cat)
    for dp, _, fns in os.walk(base):
        for fn in fns:
            if not fn.endswith(".ir") or fn == "UNIVERSAL_POWER_OFF_DEVICES.ir":
                continue
            rel = os.path.relpath(os.path.join(dp, fn), base).replace("\\", "/").split("/")
            brand = (rel[0] if len(rel) > 1 else os.path.splitext(rel[0])[0]).replace("_", " ")
            for d in parse_ir(os.path.join(dp, fn)):
                name = d["name"]
                if d["type"] == "parsed":
                    try:
                        conv = convert(d.get("protocol", ""), d.get("address", ""), d.get("command", ""))
                    except Exception:
                        conv = None
                    if not conv:
                        continue
                    code, group = conv
                    if POWER.match(name):
                        add_entry(code, group, flag, brand)
                    else:
                        for key, rx in KEYMAP:
                            if rx.match(name):
                                buttons[group][key][code] += 1
                                break
                elif d["type"] == "raw" and POWER.match(name):
                    try:
                        freq = int(d["frequency"])
                        data = [int(x) for x in d["data"].split()]
                    except Exception:
                        continue
                    # keep the first frame only: cut at the first long gap (inter-frame space)
                    frame = []
                    for i, v in enumerate(data):
                        if i % 2 == 1 and v > 20000 and i >= 8:
                            break
                        frame.append(v)
                    if len(frame) % 2 == 0:
                        frame = frame[:-1]
                    if not (15 <= len(frame) <= 200):
                        continue
                    add_entry("raw:%d:%s" % (freq, ",".join(map(str, frame))), None, flag, brand)

# ---- CoreELEC AmRemote (Amlogic factory remotes, CC0) ----
am = os.path.join(coreelec, "AmRemote")
for brand in sorted(os.listdir(am)):
    p = os.path.join(am, brand, "remote.conf")
    if not os.path.isfile(p):
        continue
    text = open(p, encoding="utf-8", errors="replace").read()
    m = re.search(r"^factory_code\s*=\s*0x([0-9a-fA-F]+)", text, re.M)
    kb = re.search(r"^key_begin(.*?)^key_end", text, re.M | re.S)
    if not m or not kb:
        continue
    custom = (int(m.group(1), 16) >> 16) & 0xFFFF
    lo, hi = custom & 0xFF, custom >> 8
    group = "nec:" + hx(lo) + hx(hi)
    for line in kb.group(1).splitlines():
        parts = line.split("#")[0].split()
        if len(parts) < 2:
            continue
        try:
            sc, lk = int(parts[0], 16), int(parts[1])
        except ValueError:
            continue
        key = LINUX_NUM.get(lk)
        if not key:
            continue
        code = "nec:" + hx(lo) + hx(hi) + hx(sc) + hx(~sc)
        if key == "POWER":
            add_entry(code, group, "B", brand + " (Amlogic)", 3)
        else:
            buttons[group][key][code] += 1

# ---- CoreELEC MesonIR (NEC tables) ----
mi = os.path.join(coreelec, "MesonIR")
for brand in sorted(os.listdir(mi)):
    bd = os.path.join(mi, brand)
    if not os.path.isdir(bd):
        continue
    for fn in os.listdir(bd):
        p = os.path.join(bd, fn)
        if fn.endswith(".png") or fn == "rc_maps.cfg" or not os.path.isfile(p):
            continue
        text = open(p, encoding="utf-8", errors="replace").read()
        if not re.search(r"type:\s*NEC\b", text[:200]):
            continue
        for line in text.splitlines():
            mm = re.match(r"\s*0x([0-9a-fA-F]{1,4})\s+(KEY_\w+)", line)
            if not mm:
                continue
            sc = int(mm.group(1), 16)
            key = LINUX_KEYS.get(mm.group(2))
            if not key:
                continue
            a, c = (sc >> 8) & 0xFF, sc & 0xFF
            code = "nec:" + hx(a) + hx(~a) + hx(c) + hx(~c)
            group = "nec:" + hx(a) + hx(~a)
            if key == "POWER":
                add_entry(code, group, "B", brand + " (Meson)", 3)
            else:
                buttons[group][key][code] += 1

# ---- write ----
rows = []
for e in entries.values():
    extras = {}
    if e.group:
        for key, ctr in buttons.get(e.group, {}).items():
            extras[key] = ctr.most_common(1)[0][0]
    flags = "".join(sorted(e.flags))
    brands = ";".join(b for b, _ in e.brands.most_common(6)).replace("|", " ")
    ex = ";".join("%s=%s" % (k, v) for k, v in sorted(extras.items()))
    rows.append((e.n, "%s|%d|%s|%s|%s" % (flags, e.n, e.code, brands, ex)))
rows.sort(key=lambda r: -r[0])
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8", newline="\n") as f:
    f.write("# Codigos de encendido IR: flags(B=TV box/decodificador,T=televisor)|popularidad|codigo|marcas|botones extra\n")
    f.write("# Fuentes (CC0): Lucaslhm/Flipper-IRDB y CoreELEC/remotes.\n")
    for _, line in rows:
        f.write(line + "\n")
print(len(rows), "codes;", sum(1 for _, l in rows if l.split("|")[4]), "with extra buttons", file=sys.stderr)
print(collections.Counter(l.split("|")[2].split(":")[0] for _, l in rows), file=sys.stderr)


# ================= irdb (probonopd) -> separate optional file =================
if len(sys.argv) > 5:
    import csv
    irdb_root, irdb_out = sys.argv[4], sys.argv[5]
    main_codes = set(entries.keys())
    entries2 = collections.OrderedDict()
    buttons2 = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
    IR_POWER = re.compile(r"^(key_)?(power|pwr|power[ _-]?on|on|standby|power[ _-]?toggle|on[ _/]off|power[ _-]?on[ _/-]?off)$", re.I)
    TV_DIR = re.compile(r"(^|\b)(tv|plasma|lcd|hdtv|monitor|crt)\b", re.I)
    BOX_DIR = re.compile(r"(cable box|satellite|^sat$|dss|dvr|set top|tuner|tivo|decoder|iptv|streaming|media player)", re.I)

    def irdb_convert(proto, d, s, f):
        d = int(d); f = int(f); s = int(s) if s not in ("", None) else -1
        if proto in ("NEC", "NEC1", "NEC2"):
            if d < 0 or d > 255 or f < 0 or f > 255:
                return None
            if s < 0:
                return "nec:" + hx(d) + hx(~d) + hx(f) + hx(~f), "nec:" + hx(d) + hx(~d)
            if s > 255:
                return None
            return "nec:" + hx(d) + hx(s) + hx(f) + hx(~f), "nec:" + hx(d) + hx(s)
        if proto in ("Sony12", "Sony15", "Sony20"):
            bits = int(proto[4:])
            if f < 0 or f > 127 or d < 0:
                return None
            if bits == 12:
                addr = d
            elif bits == 15:
                addr = d
            else:
                addr = d | ((s if s >= 0 else 0) << 5)
            if addr >= (1 << (bits - 7)):
                return None
            return "sirc:%d:%s:%x" % (bits, hx(f), addr), "sirc:%d:%x" % (bits, addr)
        if proto == "RC5":
            if s != -1 and s != 0 or d < 0 or d > 31 or f < 0 or f > 63:
                return None
            return "rc5:%s:%s" % (hx(d), hx(f)), "rc5:" + hx(d)
        if proto == "RC6":
            if d < 0 or d > 255 or f < 0 or f > 255:
                return None
            return "rc6:%s:%s" % (hx(d), hx(f)), "rc6:" + hx(d)
        if proto == "JVC":
            if d < 0 or d > 255 or f < 0 or f > 255:
                return None
            return "jvc:%s:%s" % (hx(d), hx(f)), "jvc:" + hx(d)
        return None

    for brand in sorted(os.listdir(irdb_root)):
        bdir = os.path.join(irdb_root, brand)
        if not os.path.isdir(bdir):
            continue
        for dev in sorted(os.listdir(bdir)):
            ddir = os.path.join(bdir, dev)
            if not os.path.isdir(ddir):
                continue
            if TV_DIR.search(dev):
                flag = "T"
            elif BOX_DIR.search(dev):
                flag = "B"
            elif dev.lower().startswith("unknown"):
                flag = "U"
            else:
                continue
            for fn in os.listdir(ddir):
                if not fn.endswith(".csv"):
                    continue
                with open(os.path.join(ddir, fn), encoding="utf-8", errors="replace", newline="") as fh:
                    for row in csv.reader(fh):
                        if len(row) < 5 or row[0] == "functionname":
                            continue
                        try:
                            conv = irdb_convert(row[1].strip(), row[2], row[3], row[4])
                        except Exception:
                            conv = None
                        if not conv:
                            continue
                        code, group = conv
                        fname = row[0].strip()
                        if IR_POWER.match(fname):
                            if code in main_codes:
                                continue
                            e = entries2.setdefault(code, Entry(code, group))
                            e.flags.add(flag); e.n += 1; e.brands[brand] += 1
                        else:
                            base = re.sub(r"^key_", "", fname, flags=re.I)
                            for key, rx in KEYMAP:
                                if rx.match(base):
                                    buttons2[group][key][code] += 1
                                    break
    rows2 = []
    for e in entries2.values():
        extras = {}
        for key, ctr in buttons2.get(e.group, {}).items():
            extras[key] = ctr.most_common(1)[0][0]
        brands = ";".join(b for b, _ in e.brands.most_common(6)).replace("|", " ")
        ex = ";".join("%s=%s" % (k, v) for k, v in sorted(extras.items()))
        rows2.append((e.n, "%s|%d|%s|%s|%s" % ("".join(sorted(e.flags)), e.n, e.code, brands, ex)))
    rows2.sort(key=lambda r: -r[0])
    with open(irdb_out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Codigos de encendido IR de irdb (probonopd). Contains/accesses irdb by Simon Peter and contributors, used under permission.\n")
        f.write("# See https://github.com/probonopd/irdb for licensing details. Delete this file to exclude irdb data.\n")
        for _, line in rows2:
            f.write(line + "\n")
    print("irdb:", len(rows2), "extra codes", collections.Counter(l.split("|")[2].split(":")[0] for _, l in rows2), file=sys.stderr)
