// The calm look (bravia-custom): drift: false, date_pill, focus_style "lift" and overlay "cinematic".
// Each part is off unless config.json's home section asks for it, adds no animation, and wins over
// Lighter home where it has to (Lighter's 4% photo scale, its white focus ring).
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const js = fs.readFileSync(path.join(__dirname, '..', 'tv-home', 'web', 'app.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '..', 'tv-home', 'web', 'app.css'), 'utf8');

// --- app.js: calmLook() turns each class on only for the exact value that asks for it ---
const fn = js.match(/  function calmLook\(\) \{[\s\S]*?\n  \}\n/);
assert(fn, 'calmLook() is in app.js');
const calmLook = (home) => {
  const on = new Set();
  const root = { classList: { toggle: (c, v) => { if (v) on.add(c); else on.delete(c); } } };
  new Function('document', 'HOME', fn[0] + '; calmLook();')({ documentElement: root }, home);
  return on;
};
assert.deepStrictEqual([...calmLook({})], [], 'nothing changes without the keys (upstream look unchanged)');
assert.deepStrictEqual([...calmLook({ drift: true, date_pill: false, focus_style: 'ring', overlay: 'default' })], [],
  'the default values change nothing');
assert.deepStrictEqual([...calmLook({ drift: false, date_pill: true, focus_style: 'lift', overlay: 'cinematic' })].sort(),
  ['date-pill', 'focus-lift', 'no-drift', 'overlay-cinematic'], 'the BRAVIA profile switches all four on');
assert.deepStrictEqual([...calmLook({ drift: 'false', date_pill: 'yes', focus_style: 'LIFT', overlay: 'dark' })], [],
  'only the exact values count');
// it runs with the other look settings, so a config change applies at once
assert(/movingTiles\(\);\n\s*calmLook\(\);/.test(js), 'readConfig() calls calmLook() right after movingTiles()');

// --- app.css: the calm-look block ---
const at = css.indexOf('the calm look (bravia-custom');
assert(at > 0, 'the calm-look block is in app.css');
const block = css.slice(at);
const rule = (sel) => { const m = block.match(new RegExp(sel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[^{]*\\{([^}]*)\\}')); return m && m[1]; };

// no continuous motion anywhere in it
assert(!/@keyframes|animation\s*:(?!\s*none\b)/.test(block), 'the calm look adds no animation');
assert(/animation:\s*none/.test(block), '(the pattern above does see "animation: none" as stopping motion)');
assert(!/transition\s*:/.test(block), 'the calm look adds no transition of its own (the tile one is reused)');

// drift: false holds the photo still at its own framing, also against Lighter home's scale(1.04)
const still = rule('html.no-drift .bg img.on, html.no-drift.lighter .bg img.on');
assert(still && /animation:\s*none/.test(still) && /transform:\s*none/.test(still), 'no-drift: no animation, no scale');
assert(css.indexOf('html.lighter .bg img.on { animation: none; transform: scale(1.04)') < at,
  'the no-drift rule comes after Lighter home\'s scale, so it wins');

// date pill: rounded, readable without blur (Lighter home), light on the Fog scene
const pill = rule('html.date-pill.theme-night .date .dline');
assert(pill && /border-radius/.test(pill) && /background:\s*rgba\(/.test(pill), 'the date pill is a rounded tinted shape');
assert(/blur\(0\.75rem\)/.test(pill), 'the pill blur is light');
const lighterPill = rule('html.date-pill.lighter.theme-night .date .dline');
assert(lighterPill && /rgba\(14, 12, 18, 0\.5\)/.test(lighterPill), 'under Lighter home the pill is darker to stay readable');
assert(rule('html.date-pill.theme-night.scene-fog .date .dline'), 'the Fog scene gets a light pill');

// focus lift: transform only for the movement, one fixed shadow, no white ring, also under Lighter home
const lift = rule('html.focus-lift.theme-night .app.focus');
assert(lift && /translateY\(-0\.875rem\)/.test(lift) && !/box-shadow/.test(lift), 'the lift moves the tile by transform only');
const face = css.slice(at).match(/html\.focus-lift\.theme-night \.app\.focus \.face,\s*\nhtml\.focus-lift\.lighter\.theme-night \.app\.focus \.face \{([^}]*)\}/);
assert(face, 'the lift shadow also applies under Lighter home');
assert(!/0 0 0 0\.25rem #fff/.test(face[1]), 'no white ring with the lift');
assert(/0 1\.125rem 2\.25rem rgba\(0, 0, 0, 0\.5\)/.test(face[1]), 'one soft drop shadow');
assert(css.indexOf('html.lighter.theme-night .app.focus .face { box-shadow: 0 0 0 0.25rem #fff; }') < at,
  'the lift comes after Lighter home\'s ring, so it wins');

// cinematic overlay: one static background on the scrim, gentle at the top
const scrim = rule('html.overlay-cinematic.theme-night .scrim');
assert(scrim && /linear-gradient/.test(scrim) && /radial-gradient/.test(scrim), 'the overlay is gradients on the scrim');
const alphas = (scrim.match(/rgba\(0, 0, 0, ([\d.]+)\)/g) || []).map((s) => parseFloat(s.match(/([\d.]+)\)$/)[1]));
assert(Math.max(...alphas) <= 0.6, 'never darker than 60% anywhere, so the photo stays visible');

console.log('Calm look tests passed.');
