(() => {
  'use strict';

  const key = 'midnight-draw.run.v1';
  const range = 2 ** 32;
  const actions = new Set([
    'play', 'discard', 'cashout', 'buy', 'sell', 'reroll',
    'openPack', 'choosePlanet', 'skipPack', 'moveJoker', 'nextRound',
  ]);
  let runSerial = 0;

  function createRunId(seed) {
    try {
      const values = globalThis.crypto.getRandomValues(new Uint32Array(4));
      return 'run-' + Array.from(values, (value) => value.toString(16).padStart(8, '0')).join('');
    } catch {
      runSerial++;
      return `run-${Date.now().toString(36)}-${seed.toString(36)}-${runSerial.toString(36)}`;
    }
  }

  function start(engine, saved, storage, recordBattle) {
    const { seed, history, runId } = saved;
    let newPersonalBest = saved.newPersonalBest === true;
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
        storage?.setItem(key, JSON.stringify({ seed, history, runId, newPersonalBest }));
      } catch {
        // Local play is available when the browser blocks storage.
      }
    }

    function recordSettlement() {
      const { phase, ante, bestHand } = game.state;
      try {
        const report = recordBattle?.({ runId, ante, score: bestHand?.total ?? 0, outcome: phase });
        newPersonalBest = report?.newPersonalBest === true;
      } catch {
        // A failed report must not interrupt settlement or saving the run.
        newPersonalBest = false;
      }
    }

    function hasNewPersonalBest() {
      return newPersonalBest;
    }

    function perform(action, ...args) {
      if (!actions.has(action)) return null;
      const previousPhase = game.state.phase;
      const result = game[action](...args);
      if (result) {
        history.push([action, ...args]);
        const phase = game.state.phase;
        if (phase !== previousPhase && (phase === 'won' || phase === 'lost')) {
          recordSettlement();
        }
        save();
      }
      return result;
    }

    return { game, perform, save, hasNewPersonalBest, resumed: false };
  }

  function open(engine, storage, recordBattle) {
    try {
      const saved = JSON.parse(storage?.getItem(key) ?? 'null');
      if (Number.isInteger(saved?.seed) && saved.seed >= 0 && saved.seed < range &&
          Array.isArray(saved.history) && saved.history.length <= 20000) {
        const runId = typeof saved.runId === 'string' && /^[a-z0-9-]{1,64}$/.test(saved.runId)
          ? saved.runId : 'legacy-' + saved.seed.toString(36);
        const run = start(engine, { ...saved, runId }, storage, recordBattle);
        // 平台再次打开尚未操作的牌局时沿用牌序，并播放开场。
        if (run) return { ...run, resumed: saved.history.length > 0 };
      }
    } catch {
      // A damaged or outdated save falls back to a fresh run.
    }
    return reset(engine, storage, recordBattle);
  }

  function reset(engine, storage, recordBattle) {
    const seed = Math.floor(Math.random() * range);
    const run = start(engine, { seed, history: [], runId: createRunId(seed) }, storage, recordBattle);
    run.save();
    return run;
  }

  const api = Object.freeze({ open, reset });
  globalThis.JokerRunStore = api;
})();
