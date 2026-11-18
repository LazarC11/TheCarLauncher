// The launcher's welcome-page globe (GlobeView.java) for the website: a dark see-through wireframe
// globe tilted like the Earth in the bottom-right corner, orbit streaks with green tails and thin
// horizontal light streaks. Pauses when the tab is hidden; still frame with reduced motion.
(function () {
  var canvas = document.getElementById("globe");
  if (!canvas || !canvas.getContext) return;
  var ctx = canvas.getContext("2d");
  var BLUE = [45, 140, 235], GREEN = [56, 232, 160];
  var ROLL = 23.4 * Math.PI / 180, PITCH = 16 * Math.PI / 180;
  var L = [-0.55, 0.62, 0.56];
  var MER = 24, PAR = 17, SEG = 72;
  var RINGS = [
    [1.10, 70, -30, 0.34, 1.8, 1.2], [1.22, 62, 30, -0.25, 2.1, 2.6], [1.05, 80, -6, 0.46, 1.4, 4.4],
    [1.34, 55, -48, 0.20, 2.3, 0.4], [1.16, 76, 52, -0.38, 1.6, 5.3], [1.28, 68, 10, 0.28, 1.9, 3.5]
  ];
  var STREAKS = [[0.86, 0.22, 0.50, 0], [0.70, 0.34, 0.35, 1.1], [0.55, 0.28, 0.42, 2.3], [0.40, 0.44, 0.22, 1.7],
    [0.27, 0.30, 0.31, 3.9], [0.14, 0.40, 0.28, 4.6], [0.04, 0.25, 0.45, 5.4]];
  var still = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var spin = 0.35, time = 0, last = 0, w = 0, h = 0, dpr = 1;

  function rgba(c, a) { return "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + Math.max(0, Math.min(1, a)).toFixed(3) + ")"; }
  function mix(a, b, f) { f = Math.max(0, Math.min(1, f)); return [a[0] + (b[0] - a[0]) * f | 0, a[1] + (b[1] - a[1]) * f | 0, a[2] + (b[2] - a[2]) * f | 0]; }

  function sphere(lat, lon) {
    var x = Math.cos(lat) * Math.sin(lon + spin), y = Math.sin(lat), z = Math.cos(lat) * Math.cos(lon + spin);
    var y1 = y * Math.cos(PITCH) - z * Math.sin(PITCH), z1 = y * Math.sin(PITCH) + z * Math.cos(PITCH);
    return [x * Math.cos(ROLL) - y1 * Math.sin(ROLL), x * Math.sin(ROLL) + y1 * Math.cos(ROLL), z1];
  }
  function ring(rr, ax, az, t) {
    ax *= Math.PI / 180; az *= Math.PI / 180;
    var x = rr * Math.cos(t), z = rr * Math.sin(t), y1 = -z * Math.sin(ax), z1 = z * Math.cos(ax);
    return [x * Math.cos(az) - y1 * Math.sin(az), x * Math.sin(az) + y1 * Math.cos(az), z1];
  }

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = canvas.clientWidth; h = canvas.clientHeight;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function frame(now) {
    if (!w) resize();
    var dt = last ? Math.min(0.1, (now - last) / 1000) : 0;
    last = now; spin += dt * 0.10; time += dt;
    ctx.clearRect(0, 0, w, h);
    var narrow = w < 720;
    var r = narrow ? Math.min(w * 0.62, h * 0.55) : Math.min(h * 0.80, w * 0.40);
    var cx = w - r * (narrow ? 0.35 : 0.10), cy = h + r * (narrow ? 0.18 : 0.02);
    var sw = Math.max(1.4, r / 190);

    // atmosphere
    var g = ctx.createRadialGradient(cx, cy, r * 0.66 * 1.45, cx, cy, r * 1.45);
    g.addColorStop(0, rgba(BLUE, 0.26)); g.addColorStop(0.4, rgba(BLUE, 0.10)); g.addColorStop(1, rgba(BLUE, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, r * 1.45, 0, 7); ctx.fill();
    // dark see-through sphere, lit from the upper left
    var hx = cx - r * 0.42, hy = cy - r * 0.46;
    g = ctx.createRadialGradient(hx, hy, 0, hx, hy, r * 1.55);
    g.addColorStop(0, "rgba(23,25,28,.55)"); g.addColorStop(0.38, "rgba(13,14,16,.5)");
    g.addColorStop(0.78, "rgba(5,5,6,.6)"); g.addColorStop(1, rgba(mix([3, 3, 4], BLUE, 0.3), 0.75));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, r, 0, 7); ctx.fill();

    orbits(cx, cy, r, sw, false);
    wireframe(cx, cy, r, sw);

    // rim
    ctx.save(); ctx.shadowColor = rgba(BLUE, 0.8); ctx.shadowBlur = sw * 10;
    ctx.strokeStyle = rgba(mix([74, 78, 86], BLUE, 0.45), 0.95); ctx.lineWidth = sw * 1.3;
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, 7); ctx.stroke(); ctx.restore();

    orbits(cx, cy, r, sw, true);
    streaks(cx, cy, r, sw);

    if (!still && !document.hidden) requestAnimationFrame(frame);
  }

  function wireframe(cx, cy, r, sw) {
    var buckets = 10, front = [], back = [], i, m, k, s;
    for (i = 0; i < buckets; i++) { front.push(new Path2D()); back.push(new Path2D()); }
    function add(p0, p1) {
      var z = (p0[2] + p1[2]) / 2, l = (p0[0] + p1[0]) / 2 * L[0] + (p0[1] + p1[1]) / 2 * L[1] + z * L[2], b;
      if (z >= 0) {
        b = Math.max(0, Math.min(buckets - 1, (0.15 + 0.55 * Math.max(0, l) + 0.30 * z) * buckets | 0));
        front[b].moveTo(cx + p0[0] * r, cy - p0[1] * r); front[b].lineTo(cx + p1[0] * r, cy - p1[1] * r);
      } else {
        b = Math.max(0, Math.min(buckets - 1, (1 + z) * buckets | 0));
        back[b].moveTo(cx + p0[0] * r, cy - p0[1] * r); back[b].lineTo(cx + p1[0] * r, cy - p1[1] * r);
      }
    }
    for (m = 0; m < MER; m++) {
      var lon = m * Math.PI * 2 / MER, prev = sphere(-Math.PI / 2, lon);
      for (s = 1; s <= SEG; s++) { var p = sphere(-Math.PI / 2 + s * Math.PI / SEG, lon); add(prev, p); prev = p; }
    }
    for (k = 1; k <= PAR; k++) {
      var lat = -Math.PI / 2 + k * Math.PI / (PAR + 1), pr = sphere(lat, 0);
      for (s = 1; s <= SEG; s++) { var q = sphere(lat, s * Math.PI * 2 / SEG); add(pr, q); pr = q; }
    }
    ctx.lineCap = "round"; ctx.lineJoin = "round";
    ctx.lineWidth = sw * 0.75;
    for (i = 0; i < buckets; i++) { ctx.strokeStyle = rgba([46, 49, 54], 0.25 + 0.25 * i / (buckets - 1)); ctx.stroke(back[i]); }
    ctx.lineWidth = sw;
    for (i = 0; i < buckets; i++) {
      var f = i / (buckets - 1);
      ctx.strokeStyle = rgba(mix([37, 40, 45], [70, 74, 82], f), 0.85 + 0.15 * f); ctx.stroke(front[i]);
    }
  }

  function visible(p, x, y, cx, cy, r, near) {
    var isNear = p[2] >= 0;
    if (near) return isNear;
    if (isNear) return false;
    return (x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r;
  }

  function orbits(cx, cy, r, sw, near) {
    RINGS.forEach(function (g) {
      var head = g[5] + time * g[3], s, p, x, y, px = 0, py = 0, pv = false;
      ctx.lineWidth = sw * 0.6; ctx.strokeStyle = rgba(BLUE, near ? 0.22 : 0.09);
      for (s = 0; s <= 120; s++) {
        p = ring(g[0], g[1], g[2], s * Math.PI * 2 / 120); x = cx + p[0] * r; y = cy - p[1] * r;
        var vis = visible(p, x, y, cx, cy, r, near);
        if (s > 0 && vis && pv) { ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(x, y); ctx.stroke(); }
        px = x; py = y; pv = vis;
      }
      pv = false;
      for (s = 0; s <= 44; s++) {
        var f = s / 44;
        p = ring(g[0], g[1], g[2], head - Math.sign(g[3]) * g[4] * (1 - f)); x = cx + p[0] * r; y = cy - p[1] * r;
        var v2 = visible(p, x, y, cx, cy, r, near);
        if (s > 0 && v2 && pv) {
          var col = f < 0.5 ? mix(GREEN, BLUE, f * 2) : mix(BLUE, [255, 255, 255], (f - 0.5) * 2), a = 0.12 + 0.8 * f * f;
          ctx.save(); ctx.shadowColor = rgba(f < 0.6 ? col : BLUE, a); ctx.shadowBlur = sw * (2 + 5 * f);
          ctx.lineWidth = sw * (0.5 + 1.2 * f); ctx.strokeStyle = rgba(col, a);
          ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(x, y); ctx.stroke(); ctx.restore();
        }
        px = x; py = y; pv = v2;
      }
      p = ring(g[0], g[1], g[2], head); x = cx + p[0] * r; y = cy - p[1] * r;
      if (visible(p, x, y, cx, cy, r, near)) {
        ctx.save(); ctx.shadowColor = rgba(BLUE, 1); ctx.shadowBlur = sw * 12;
        ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(x, y, sw * 1.8, 0, 7); ctx.fill(); ctx.restore();
      }
    });
  }

  function streaks(cx, cy, r, sw) {
    STREAKS.forEach(function (st) {
      var y = cy - st[0] * r, dy = y - cy;
      if (Math.abs(dy) >= r) return;
      var edge = cx - Math.sqrt(r * r - dy * dy), len = st[1] * w;
      // keep the streaks clear of the text column on the left
      var minX = w < 720 ? 0 : Math.min(w * 0.52, edge - 40);
      len = Math.min(len, edge - minX);
      if (len <= 0) return;
      var pulse = 0.5 + 0.5 * Math.sin(time * st[2] + st[3]), a = 0.22 + 0.58 * pulse, th = sw * (0.45 + 0.55 * pulse);
      var g = ctx.createLinearGradient(edge - len, y, edge + r * 0.22, y);
      g.addColorStop(0, rgba(GREEN, 0)); g.addColorStop(0.25, rgba(GREEN, a * 0.55)); g.addColorStop(0.62, rgba(BLUE, a * 0.8));
      g.addColorStop(0.86, rgba([255, 255, 255], a)); g.addColorStop(1, rgba([255, 255, 255], 0));
      ctx.fillStyle = g; ctx.fillRect(edge - len, y - th, len + r * 0.22, th * 2);
    });
  }

  window.addEventListener("resize", function () { resize(); if (still) requestAnimationFrame(frame); });
  document.addEventListener("visibilitychange", function () { if (!document.hidden && !still) { last = 0; requestAnimationFrame(frame); } });
  resize();
  requestAnimationFrame(frame);
})();
