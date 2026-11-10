(() => {
  'use strict';

  const key = 'midnight-draw.run.v1';
  const range = 2 ** 32;
  const actions = new Set([
    'play', 'discard', 'cashout', 'buy', 'sell', 'reroll',
    'openPack', 'choosePlanet', 'skipPack', 'moveJoker', 'nextRound',
  ]);

  function start(engine, seed, history, storage) {
    let value = seed;
    const game = engine.createGame({ random: () => {
      value = (Math.imul(1664525, value) + 1013904223) >>> 0;
      return value / range;
    } });

    for (const entry of history) {
      if (!Array.isArray(entry) || !actions.has(entry[0]) || !game[entry[0]](...entry.slice(1))) {
        return null;
      }
    }

    function save() {
      try {
        storage?.setItem(key, JSON.stringify({ seed, history }));
      } catch {
        // Local play is available when the browser blocks storage.
      }
    }

    function perform(action, ...args) {
      if (!actions.has(action)) return null;
      const result = game[action](...args);
      if (result) {
        history.push([action, ...args]);
        save();
      }
      return result;
    }

    return { game, perform, save, resumed: false };
  }

  function open(engine, storage) {
    try {
      const saved = JSON.parse(storage?.getItem(key) ?? 'null');
      if (Number.isInteger(saved?.seed) && saved.seed >= 0 && saved.seed < range &&
          Array.isArray(saved.history) && saved.history.length <= 20000) {
        const run = start(engine, saved.seed, saved.history, storage);
        // 平台再次打开尚未操作的牌局时沿用牌序，并播放开场。
        if (run) return { ...run, resumed: saved.history.length > 0 };
      }
    } catch {
      // A damaged or outdated save falls back to a fresh run.
    }
    return reset(engine, storage);
  }

  function reset(engine, storage) {
    const run = start(engine, Math.floor(Math.random() * range), [], storage);
    run.save();
    return run;
  }

  const api = Object.freeze({ open, reset });
  globalThis.JokerRunStore = api;
})();
