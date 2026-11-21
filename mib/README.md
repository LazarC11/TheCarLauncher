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

Irish and Maltese have no flag in this skin; `FlagDrawable` draws those.
