"""Builds a launcher skin from another MIB software train.

1. Extract the train's skin with extract_mcf.py (the images.mcf of the skin folder) so that a
   folder of img_<n>.png pictures exists.
2. From the repository root run:

       python3 mib/tools/build_skin.py <folder with img_N.png> <skin id> "<name shown in Setup>"

   For every launcher picture in mib/skin_recipe.json the tool finds the picture of the new train
   that looks most like the one it was made from in the reference skin (mib/skin1), cuts it the same
   way and writes app/src/main/assets/skins/<skin id>/<launcher picture>.png plus skin.json.
3. Check mib/skins/<skin id>_report.txt: matches below the threshold are listed first. Fix any
   wrong one in mib/skins/<skin id>.json ({"ui_key_play": 1356, ...}) and run the tool again.

Pictures that have no good match are left out, so the launcher keeps its built-in one for them.
"""
import glob, json, os, sys
from PIL import Image

REF = 'mib/skin1'
RECIPE = 'mib/skin_recipe.json'
OUT = 'app/src/main/assets/skins'
FEAT = 16
GOOD = 0.90  # similarity (0..1) needed to use a match without a manual override


def feature(im):
    """Small RGBA thumbnail on black and white, as numbers 0..1 (shape, colour and transparency)."""
    im = im.convert('RGBA')
    t = im.resize((FEAT, FEAT), Image.BILINEAR)
    out = []
    for bg in ((0, 0, 0, 255), (255, 255, 255, 255)):
        b = Image.new('RGBA', t.size, bg)
        b.alpha_composite(t)
        out.extend(v / 255.0 for px in list(b.convert('RGB').getdata()) for v in px)
    return out


def similarity(a, sa, b, sb):
    d = sum((x - y) ** 2 for x, y in zip(a, b)) / len(a)
    s = 1.0 - min(1.0, d * 6)
    # Prefer pictures of the same size and shape.
    if sa != sb:
        ra, rb = sa[0] / max(1, sa[1]), sb[0] / max(1, sb[1])
        s -= 0.05 + min(0.3, abs(ra - rb))
    return s


def cut(im, spec):
    im = im.convert('RGBA')
    op = spec['op']
    bb = im.getbbox() or (0, 0, im.width, im.height)
    if op == 'copy':
        return im
    if op == 'bbox':
        return im.crop(bb)
    if op == 'bbox_rot90':
        return im.crop(bb).rotate(90, expand=True)
    if op == 'bbox_skip1':
        return im.crop((bb[0], bb[1] + 1, bb[2], bb[3]))
    if op == 'crop_rot90':
        return im.crop(tuple(spec['box'])).rotate(90, expand=True)
    if op == 'center_square_148':
        w, h = im.size
        s = min(w, h)
        sq = im.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((128, 128), Image.LANCZOS)
        out = Image.new('RGBA', (148, 148), (0, 0, 0, 0))
        out.paste(sq, (10, 10))
        return out
    raise ValueError(op)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    src, sid, name = sys.argv[1], sys.argv[2], sys.argv[3]
    recipe = json.load(open(RECIPE))['recipe']
    over_path = os.path.join('mib/skins', sid + '.json')
    overrides = json.load(open(over_path)) if os.path.exists(over_path) else {}
    pics = {}
    for f in glob.glob(os.path.join(src, 'img_*.png')):
        n = int(os.path.basename(f)[4:-4])
        im = Image.open(f)
        pics[n] = (feature(im), im.size)
    out = os.path.join(OUT, sid)
    os.makedirs(out, exist_ok=True)
    report = []
    for ui, spec in sorted(recipe.items()):
        if ui in overrides:
            n, score, how = overrides[ui], 1.0, 'override'
        else:
            ref = Image.open(os.path.join(REF, 'img_%d.png' % spec['src']))
            rf, rs = feature(ref), ref.size
            # Near-equal matches (normal / dimmed twins): the one nearest the reference's place in
            # the file, as trains keep their pictures in much the same order.
            best = max(pics.items(), key=lambda kv: (round(similarity(rf, rs, kv[1][0], kv[1][1]), 2),
                                                     -abs(kv[0] - spec['src'])))
            n, score, how = best[0], similarity(rf, rs, best[1][0], best[1][1]), 'match'
        ok = how == 'override' or score >= GOOD
        if ok:
            cut(Image.open(os.path.join(src, 'img_%d.png' % n)), spec).save(os.path.join(out, ui + '.png'), optimize=True)
        elif os.path.exists(os.path.join(out, ui + '.png')):
            os.remove(os.path.join(out, ui + '.png'))
        report.append((score, ui, spec['src'], n, how, ok))
    json.dump({'name': name, 'source': os.path.basename(os.path.normpath(src))}, open(os.path.join(out, 'skin.json'), 'w'),
              ensure_ascii=False, indent=1)
    os.makedirs('mib/skins', exist_ok=True)
    with open(os.path.join('mib/skins', sid + '_report.txt'), 'w') as r:
        r.write('similarity  launcher picture  reference img  new img  how  used\n')
        for score, ui, ref, n, how, ok in sorted(report):
            r.write('%.3f  %-28s  %5d  %5d  %-8s  %s\n' % (score, ui, ref, n, how, 'yes' if ok else 'NO (built-in kept)'))
    used = sum(1 for x in report if x[5])
    print('%s: %d of %d pictures, report in mib/skins/%s_report.txt' % (sid, used, len(report), sid))


if __name__ == '__main__':
    main()
