# -*- coding: utf-8 -*-
"""Probes which Tabler outline icon names actually exist upstream.

Never guess an icon name: a 404 at runtime means a blank glyph, which is exactly the class of bug
that produced the deformed icons earlier. Run this to validate a candidate list against the real
repository, then import only the names it confirms.
"""
import json
import urllib.request
import urllib.error

BASE = "https://raw.githubusercontent.com/tabler/tabler-icons/main/icons/outline/%s.svg"

# Semantic slot -> candidate Tabler names, in preference order.
CANDIDATES = {
    "modules":      ["layout-grid", "grid-dots", "category"],
    "list":         ["list", "list-details"],
    "compact-list": ["menu-2", "list-tree", "layout-list"],
    "zap":          ["bolt", "zap", "flash"],
    "folder":       ["folder", "folders"],
    "keyboard":     ["keyboard", "keyboard-show"],
    "settings":     ["settings", "adjustments"],
    "sliders":      ["adjustments-horizontal", "adjustments-alt", "sliders-horizontal"],
    "palette":      ["palette", "color-swatch"],
    "sparkles":     ["sparkles", "star", "wand"],
    "search":       ["search", "zoom"],
    "arrow-left":   ["arrow-left", "arrow-narrow-left"],
    "arrow-right":  ["arrow-right", "arrow-narrow-right"],
    "chevron-down": ["chevron-down", "caret-down"],
    "chevron-right":["chevron-right", "caret-right"],
    "check":        ["check", "checks"],
    "plus":         ["plus", "square-plus"],
    "minus":        ["minus", "square-minus"],
    "square":       ["square", "rectangle"],
    "close":        ["x", "circle-x"],
    "download":     ["download", "file-download"],
    "upload":       ["upload", "file-upload"],
    "refresh":      ["refresh", "reload"],
    "trash":        ["trash", "trash-x"],
    "reset":        ["arrow-back-up", "restore", "rotate-ccw", "history"],
    "copy":         ["copy", "copy-plus"],
    "clipboard":    ["clipboard", "clipboard-text"],
    "pick":         ["color-picker", "eyedropper", "droplet"],
    "crosshair":    ["crosshair", "focus-2", "target"],
    "swords":       ["swords", "sword", "axe"],
    "wind":         ["wind", "run", "shoe"],
    "eye":          ["eye", "eye-check"],
    "eye-off":      ["eye-off", "eye-closed"],
    "user":         ["user", "user-circle"],
    "globe":        ["world", "globe", "earth"],
    "boxes":        ["box-multiple", "stack-2", "packages"],
    "activity":     ["activity", "pulse", "wave-sine"],
    "shield":       ["shield", "shield-check"],
    "lock":         ["lock", "lock-square"],
    "unlock":       ["lock-open", "lock-open-2"],
    "edit-hud":     ["layout-dashboard", "panels-top-left", "dashboard"],
    "filter":       ["filter", "filter-2"],
    "pin":          ["pin", "pinned"],
    "edit":         ["edit", "pencil"],
    "external":     ["external-link", "arrow-up-right"],
    "minimize":     ["minimize", "minus"],
    "maximize":     ["maximize", "arrows-maximize"],
    "info":         ["info-circle", "info-square"],
    "warning":      ["alert-triangle", "triangle"],
    "danger":       ["alert-octagon", "alert-circle"],
    "success":      ["circle-check", "check-circle"],
    "drag":         ["grip-vertical", "dots-vertical"],
    "sort":         ["arrows-sort", "sort-ascending"],
}

resolved = {}
missing = []
for slot, names in CANDIDATES.items():
    hit = None
    for name in names:
        try:
            with urllib.request.urlopen(BASE % name, timeout=45) as resp:
                body = resp.read().decode("utf-8", "replace")
            if "<svg" in body:
                hit = name
                break
        except urllib.error.HTTPError:
            continue
        except Exception as exc:  # network hiccup
            missing.append("%s/%s (%s)" % (slot, name, exc))
            continue
    if hit:
        resolved[slot] = hit
    else:
        missing.append(slot)

print("resolved %d/%d slots" % (len(resolved), len(CANDIDATES)))
for slot in sorted(resolved):
    print("  %-15s -> %s" % (slot, resolved[slot]))
if missing:
    print("\nUNRESOLVED:")
    for m in missing:
        print("  " + m)

with open("tools/tabler_map.json", "w", encoding="utf-8") as fh:
    json.dump(resolved, fh, indent=2, sort_keys=True)
print("\nwrote tools/tabler_map.json")
