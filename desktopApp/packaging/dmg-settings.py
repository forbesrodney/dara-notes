# The macOS .dmg's contents and window, for dmgbuild (run by scripts/macos-dmg.sh, which
# passes app, volume_icon and background with -D). The positions match the arrow in
# dmg-background.tiff, drawn by scripts/app-icons.py (DMG_SIZE, DMG_ICON_Y, DMG_ARROW).
import os.path

app = defines["app"]  # noqa: F821 (dmgbuild provides defines)
app_name = os.path.basename(app)

format = "ULFO"  # LZFSE compression, macOS 10.11 and later
files = [app]
symlinks = {"Applications": "/Applications"}
icon = defines["volume_icon"]  # noqa: F821
background = defines["background"]  # noqa: F821

window_rect = ((200, 200), (640, 400))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
icon_size = 128
text_size = 13
icon_locations = {app_name: (160, 190), "Applications": (480, 190)}
hide_extensions = [app_name]
