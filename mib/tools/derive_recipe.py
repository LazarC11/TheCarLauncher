"""Works out mib/skin_recipe.json: for every ui_* picture in the app, which skin1 picture it was
made from and how (copy, cropped to its edges, crop then turned 90°, …). Run from the repo root:

    python3 mib/tools/derive_recipe.py
"""
import glob, hashlib, json, os
from PIL import Image

RES = 'app/src/main/res/drawable-nodpi'
SKIN = 'mib/skin1'


def key(im):
    im = im.convert('RGBA')
    return im.size, hashlib.md5(im.tobytes()).hexdigest()


def ops(im):
    """The ways a launcher picture was cut from a skin picture."""
    im = im.convert('RGBA')
    bb = im.getbbox()
    yield 'copy', im
    if bb:
        c = im.crop(bb)
        yield 'bbox', c
        yield 'bbox_rot90', c.rotate(90, expand=True)
        yield 'bbox_skip1', im.crop((bb[0], bb[1] + 1, bb[2], bb[3]))


index = {}
for f in sorted(glob.glob(os.path.join(SKIN, 'img_*.png')), key=lambda p: int(os.path.basename(p)[4:-4])):
    n = int(os.path.basename(f)[4:-4])
    for op, im in ops(Image.open(f)):
        index.setdefault(key(im), (n, op))

recipe, missing = {}, []
for f in sorted(glob.glob(os.path.join(RES, 'ui_*.png'))):
    name = os.path.basename(f)[:-4]
    hit = index.get(key(Image.open(f)))
    if hit:
        recipe[name] = {'src': hit[0], 'op': hit[1]}
    else:
        missing.append(name)

# Pictures cut in ways the search above does not try (kept as notes for the build tool).
special = {
    'ui_skin_eq_knob_line': {'src': 2114, 'op': 'crop_rot90', 'box': [2, 21, 114, 82]},
    'ui_src_fm': {'src': 2618, 'op': 'center_square_148'},
}
for k, v in special.items():
    if k in missing:
        recipe[k] = v
        missing.remove(k)
json.dump({'skin': 'mhi2_jp_p1101', 'recipe': recipe}, open('mib/skin_recipe.json', 'w'), indent=1, sort_keys=True)
print(len(recipe), 'pictures in the recipe; not from the skin:', ' '.join(missing))
