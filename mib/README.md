# MIB 2 skin graphics (reference)

The factory images of a VW MIB 2 High unit (firmware `MHI2_JP_VW48x_P1101`,
`AW7-MON20.06.1900060699`), supplied by the unit's owner for private use. This folder
is reference material only: nothing here is built into the app. Pictures the launcher
uses are copied from here into `app/src/main/res/drawable-nodpi/` as `ui_*.png`.

## Contents

- `skin1/img_<n>.png` — all 2836 images of the skin's `images.mcf` (the menus, keys,
  status bar, flags, progress bars …). This skin's `images.res` has no file names, so
  the images are numbered in the order they are stored.
- `index/skin1_sheet_<0-6>.jpg` — overview sheets of `skin1`, each picture labelled with
  its number.
- `skin0_images.txt` — the names of the 3014 pictures of `skin0` (car pictures per
  model code, climate, seats, ambient lighting, traffic signs `VZA/*`). These are 152 MB,
  so they are not kept in the repository; extract them from the firmware archive with
  the tool below.
- `tools/extract_mcf.py` — the extractor from
  [jilleb/mib2-toolbox](https://github.com/jilleb/mib2-toolbox) (`Tools/extract-mcf.py`),
  changed to run on Linux/macOS and to take folders as arguments:

  ```sh
  python3 mib/tools/extract_mcf.py <folder with images.mcf [and imageidmap.res]> <output folder>
  ```

## Used in the launcher

| Launcher picture | skin1 image |
| --- | --- |
| `ui_flag_<language>.png` (24 languages) | 685–719 (flags with a dark frame; 721–761 are the same with a light frame) |
| Media progress knob (drawn in `MibProgress` to its proportions) | 1398–1415 |
| `ui_skin_tile` — menu tiles | 2426 |
| `ui_skin_row` / `ui_skin_row_pressed` — settings and list rows | 1459 / 1458 |
| `ui_skin_key` / `ui_skin_key_on` — framed keys (normal / pressed or selected) and popups | 1804 / 1803 |
| `ui_skin_notch` — popup pointer | 251 |
| `ui_key_repeat`, `ui_key_repeat_one`, `ui_key_shuffle` — media repeat / shuffle keys | 1162, 1158, 1166 |
| `ui_skin_popup` — the Source popup's body (black, grey frame, shadow) | 244 |
| `ui_skin_dot_on` / `ui_skin_dot_off` — page dots | 67 / 65 |
| `ui_skin_scroll` / `ui_skin_scroll_track` — scroll bar | 1439 / 28 |
| `ui_skin_eq`, `ui_skin_eq_knob`, `ui_skin_eq_knob_line` — sound sliders (knob turned 90°) | 2439, 2115, 2114 |

| `ui_skin_key_pressed` — pressed soft keys, list rows and popup cells | 2174 |
| `ui_skin_tile_glow` — glow under the menu tiles' line | 2415 |
| `ui_skin_key_light` — sound page + / − keys | 1099 |
| `ui_nav_*` — navigation manoeuvre arrows | 1573–1606 |
| `ui_lamp_*` — vehicle status lights (tick, warning, oil, service, registration, tyres, fuel) | 271, 272, 337, 274, 325, 326, 1617, 868, 311 |

`Mib.skin()` stretches these like nine-patches: the edges stay as drawn, only the middle grows.

Irish and Maltese have no flag in this skin; `FlagDrawable` draws those.
