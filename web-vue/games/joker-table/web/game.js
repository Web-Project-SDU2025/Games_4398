(() => {
  'use strict';

  const get = (id) => document.getElementById(id);
  const ui = Object.fromEntries([
    'ante-label', 'round-name', 'round-number', 'round-track', 'round-rule', 'boss-preview', 'blind-reward',
    'target', 'score', 'progress', 'formula', 'hands-left', 'discards-left', 'money', 'deck-count',
    'jokers', 'joker-count', 'stage', 'hand', 'hand-count', 'play', 'discard', 'options', 'hand-guide',
    'sort-rank', 'sort-suit', 'overlay', 'status',
  ].map((id) => [id, get(id)]));
  const number = new Intl.NumberFormat('zh-CN');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const AudioContext = window.AudioContext || window.webkitAudioContext;
  const deck = document.querySelector('.deck-counter i');
  const suits = {
    spades: ['♠', '黑桃'], hearts: ['♥', '红桃'],
    clubs: ['♣', '梅花'], diamonds: ['♦', '方块'],
  };
  const ranks = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A' };
  const suitOrder = Object.fromEntries(Object.keys(suits).map((suit, index) => [suit, index]));
  const pipRows = {
    2: [[22, 50], [78, 50]],
    3: [[22, 50], [50, 50], [78, 50]],
    4: [[22, 28, 72], [78, 28, 72]],
    5: [[22, 28, 72], [50, 50], [78, 28, 72]],
    6: [[22, 28, 72], [50, 28, 72], [78, 28, 72]],
    7: [[22, 28, 72], [36, 50], [50, 28, 72], [78, 28, 72]],
    8: [[22, 28, 72], [36, 50], [50, 28, 72], [64, 50], [78, 28, 72]],
    9: [[21, 28, 72], [40, 28, 72], [50, 50], [60, 28, 72], [79, 28, 72]],
    10: [[21, 28, 72], [31, 50], [40, 28, 72], [60, 28, 72], [69, 50], [79, 28, 72]],
  };

  const storage = globalThis.JokerPlatformStorage;
  let run = JokerRunStore.open(JokerEngine, storage);
  let game = run.game;
  const selected = new Set();
  let recentCards = [];
  let busy = false;
  let modal = '';
  let draggingJoker = '';
  let sortMode = 'rank';
  const soundVolumeKey = 'midnight-draw.sound-volume.v1';
  let sound = Boolean(AudioContext);
  let soundVolume = readSoundVolume();
  let audio;
  let soundOutput;
  let dealSample;
  let dealSampleReady;
  let deal;

  const format = (value) => number.format(value);
  const isRed = (card) => card.suit === 'hearts' || card.suit === 'diamonds';
  const cardName = (card) => suits[card.suit][1] + (ranks[card.rank] ?? card.rank);
  const currentBlind = () => game.state.rounds[game.state.round];
  const blindName = (blind) => blind.bossName ?? blind.name;
  const anteLabel = () => `第 ${game.state.ante} 注`;

  function announce(message) {
    ui.status.textContent = message;
  }

  function readSoundVolume() {
    try {
      const saved = Number(storage?.getItem(soundVolumeKey)?.trim() || '100');
      if (Number.isInteger(saved) && saved >= 0 && saved <= 100) return saved;
    } catch {
      // The browser may block local settings.
    }
    return 100;
  }

  function updateSoundGain() {
    if (soundOutput) soundOutput.gain.value = sound ? soundVolume / 100 : 0;
  }

  function tone(frequency, duration = 0.07, volume = 0.035) {
    if (!sound || !soundVolume || !audio || audio.state !== 'running') return;
    const oscillator = audio.createOscillator();
    const gain = audio.createGain();
    const start = audio.currentTime;
    oscillator.type = 'triangle';
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(volume, start);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(gain).connect(soundOutput);
    oscillator.start(start);
    oscillator.stop(start + duration);
  }

  function playDealSound() {
    if (!sound || !soundVolume || !dealSample || audio.state !== 'running') return;
    const source = audio.createBufferSource();
    source.buffer = dealSample;
    source.playbackRate.value = 1.8;
    source.connect(soundOutput);
    source.start();
  }

  async function startAudio() {
    if (!audio) {
      audio = new AudioContext();
      soundOutput = audio.createGain();
      updateSoundGain();
      soundOutput.connect(audio.destination);
      const sample = Uint8Array.from(atob(JokerDealSound), (char) => char.charCodeAt(0));
      dealSampleReady = audio.decodeAudioData(sample.buffer).then((buffer) => { dealSample = buffer; }, () => {});
    }
    await audio.resume();
  }

  function unlockAudio(event) {
    if (event.isTrusted && sound && audio?.state !== 'running' && navigator.userActivation?.isActive !== false) {
      startAudio().catch(() => {});
    }
  }

  function prepareOpeningAudio() {
    if (audio || !sound || !soundVolume || window.top === window || !window.innerWidth || !window.innerHeight) return;
    try {
      // The platform launch click happens before this iframe is loaded.
      if (window.top.navigator.userActivation?.hasBeenActive) startAudio().catch(() => {});
    } catch {
      // Cross-origin embeds still unlock audio through their own controls.
    }
  }

  function previewDealSound(control) {
    if (!sound || !soundVolume) return;
    startAudio().then(() => dealSampleReady).then(() => {
      if (control.isConnected && modal === 'options') playDealSound();
    }).catch(() => {});
  }

  function toggleSound() {
    if (!AudioContext) return;
    const button = ui.overlay.querySelector('[data-action="sound"]');
    sound = !sound;
    updateSoundGain();
    previewDealSound(button);
    button.setAttribute('aria-pressed', String(sound));
    button.textContent = `音效 ${sound ? '开' : '关'}`;
  }

  function updateSoundVolume(event) {
    const input = event.target;
    if (input.id !== 'sound-volume') return;
    soundVolume = input.valueAsNumber;
    updateSoundGain();
    const percentage = `${soundVolume}%`;
    input.setAttribute('aria-valuetext', percentage);
    get('sound-volume-value').textContent = percentage;
  }

  function saveSoundVolume(event) {
    if (event.target.id !== 'sound-volume') return;
    try {
      storage?.setItem(soundVolumeKey, String(soundVolume));
    } catch {
      // The setting still applies in this session.
    }
    previewDealSound(event.target);
  }

  function jokerMarkup(joker, held = false) {
    const controls = held && game.state.jokers.length > 1
      ? 'draggable="true" aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight"'
      : '';
    const tooltipId = `joker-effect-${joker.id}`;
    return `<div class="joker-card" style="--joker-color:${joker.color}" data-joker="${joker.id}" tabindex="0"
      aria-label="${joker.name}" aria-describedby="${tooltipId}" ${controls}>
      <div class="joker-face" aria-hidden="true">
        <span class="joker-corner">JOKER</span>
        <span class="joker-art">${JokerArt.joker(joker.id) || joker.icon}</span>
        <span class="joker-corner joker-corner-bottom">JOKER</span>
      </div>
      <div class="joker-tooltip" id="${tooltipId}">
        <strong class="joker-name">${joker.name}</strong>
        <span class="joker-description">${joker.description}</span>
      </div>
      ${held && game.state.phase === 'round' ? sellButton(joker) : ''}
    </div>`;
  }

  function sellButton(joker, shop = false) {
    const price = game.sellPrice(joker.id);
    return `<button type="button" class="joker-sell" data-sell="${joker.id}"
      aria-label="出售${joker.name}，获得 $${price}">${shop ? joker.name : '出售'} · $${price}</button>`;
  }

  function renderJokers() {
    const jokers = game.state.jokers;
    ui['joker-count'].textContent = `${jokers.length} / 5`;
    ui.jokers.innerHTML = jokers.map((joker) => jokerMarkup(joker, true)).join('') +
      Array.from({ length: Math.max(0, 5 - jokers.length) }, () =>
        '<div class="joker-empty" aria-hidden="true"><span>✦</span></div>').join('');
  }

  function renderBoard() {
    const state = game.state;
    const current = currentBlind();
    const boss = state.rounds[2];
    document.body.dataset.blind = state.round === 2 ? 'boss' : state.round === 1 ? 'big' : 'small';
    document.body.dataset.felt = current.id === 'tooth' ? 'ember' : state.round === 2 ? 'violet' : 'blue';
    ui['ante-label'].textContent = `${state.ante} / ${state.anteMax}`;
    ui['round-number'].textContent = (state.ante - 1) * state.rounds.length + state.round + 1;
    ui['blind-reward'].textContent = '$'.repeat(current.reward);
    ui['blind-reward'].setAttribute('aria-label', `奖励 ${current.reward} 金币`);
    ui['round-name'].textContent = blindName(current);
    ui['round-rule'].textContent = state.bossReleased ? '翠绿叶片已解除弱化。' : current.rule;
    ui['boss-preview'].textContent = state.round === 2 ? '' : `首领 · ${boss.bossName}`;
    ui['boss-preview'].title = boss.rule;
    ui['round-track'].innerHTML = state.rounds.map((round, index) => {
      const cleared = index < state.round || (index === state.round && ['cashout', 'shop', 'pack', 'won'].includes(state.phase));
      return `<span class="round-marker ${index === state.round ? 'is-current' : ''} ${cleared ? 'is-cleared' : ''}"
        title="${blindName(round)}" aria-label="${blindName(round)}${cleared ? '，已通过' : ''}">${cleared ? '✓' : index + 1}</span>`;
    }).join('');
    ui.target.textContent = format(current.target);
    ui.score.textContent = format(state.score);
    ui.progress.max = current.target;
    ui.progress.value = Math.min(state.score, current.target);
    ui['hands-left'].textContent = state.handsLeft;
    ui['discards-left'].textContent = state.discardsLeft;
    ui.money.textContent = `$${state.money}`;
    ui['deck-count'].textContent = state.deckCount;
  }

  function faceMarkup(card) {
    const rank = ranks[card.rank] ?? card.rank;
    const suit = JokerArt.suit(card.suit);
    let design;
    if (card.rank === 14) {
      design = `<span class="card-suit">${suit}</span>`;
    } else if (card.rank >= 11) {
      design = `<span class="card-court">${JokerArt.court(card.rank, card.suit)}</span>`;
    } else {
      design = `<span class="card-pips">${pipRows[card.rank].map(([y, ...columns]) => columns.map((x) =>
        `<span class="card-pip${y > 50 ? ' is-inverted' : ''}" style="--x:${x}%;--y:${y}%">${suit}</span>`).join('')).join('')}</span>`;
    }
    return `<span class="card-rank" aria-hidden="true">${rank}<small>${suit}</small></span>
      <span class="card-design" aria-hidden="true">${design}</span>
      <span class="card-bottom" aria-hidden="true">${rank}<small>${suit}</small></span>`;
  }

  function cardMarkup(card, index, hand) {
    const debuffed = game.isDebuffed(card);
    const spread = index - (hand.length - 1) / 2;
    return `<button type="button" class="playing-card ${isRed(card) ? 'is-red' : ''} ${debuffed ? 'is-debuffed' : ''}"
      style="--tilt:${spread * 1.9}deg;--offset:${Math.abs(spread) ** 2 * 1.05}px"
      data-card="${card.id}" data-suit="${card.suit}" aria-label="${cardName(card)}${debuffed ? '，已弱化' : ''}" aria-keyshortcuts="${index + 1}" aria-pressed="false">
      ${faceMarkup(card)}
    </button>`;
  }

  function renderHand() {
    const hand = visibleHand();
    ui.hand.innerHTML = hand.map(cardMarkup).join('');
    ui['hand-count'].textContent = `${hand.length} 张手牌`;
  }

  function finishDeal() {
    if (!deal) return;
    const { layer, flights } = deal;
    deal = null;
    delete document.body.dataset.dealing;
    for (const { card, animation } of flights) {
      animation.cancel();
      card.classList.remove('is-dealing');
    }
    layer.remove();
    ui['deck-count'].textContent = game.state.deckCount;
  }

  function dealHand(kind) {
    if (reducedMotion.matches || document.hidden || !deck?.animate) return;
    const cards = [...ui.hand.querySelectorAll('button')];
    if (!cards.length) return;
    if (kind === 'opening') prepareOpeningAudio();

    const source = deck.getBoundingClientRect();
    const layer = document.createElement('div');
    layer.className = 'deal-layer';
    layer.setAttribute('aria-hidden', 'true');

    // Keep the copies in hand order, even though the cards fly in right to left.
    const flights = cards.map((card) => {
      const bounds = card.getBoundingClientRect();
      const style = getComputedStyle(card);
      const x = bounds.left + bounds.width / 2;
      const y = bounds.top + bounds.height / 2;
      const copy = document.createElement('span');
      copy.className = `${card.className} deal-card`;
      copy.dataset.suit = card.dataset.suit;
      copy.innerHTML = card.innerHTML;
      copy.style.width = style.width;
      copy.style.height = style.height;
      copy.style.left = `${x - parseFloat(style.width) / 2}px`;
      copy.style.top = `${y - parseFloat(style.height) / 2}px`;
      copy.style.zIndex = style.zIndex;
      copy.style.outline = style.outline;
      copy.style.outlineOffset = style.outlineOffset;
      layer.append(copy);
      return {
        card, copy,
        dx: source.left + source.width / 2 - x,
        dy: source.top + source.height / 2 - y,
        tilt: parseFloat(style.getPropertyValue('--tilt')),
      };
    });

    const active = { layer, flights: [], remaining: flights.length };
    deal = active;
    document.body.dataset.dealing = kind;
    document.body.append(layer);
    ui['deck-count'].textContent = game.state.deckCount + active.remaining;

    const start = kind === 'opening' ? 265 : 18;
    const pose = (x, y, tilt, scale) => `translate(${x}px, ${y}px) rotate(${tilt}deg) scale(${scale})`;
    for (const [index, { card, copy, dx, dy, tilt }] of flights.reverse().entries()) {
      card.classList.add('is-dealing');
      const animation = copy.animate([
        { opacity: 0, transform: pose(dx, dy, 6, 0.83) },
        { opacity: 1, transform: pose(dx * 0.78, dy * 0.78 - 10, 4, 0.93), offset: 0.17 },
        { opacity: 1, transform: pose(0, -5, tilt, 1.015), offset: 0.88 },
        { opacity: 1, transform: pose(0, 0, tilt, 1) },
      ], {
        delay: start + index * 50,
        duration: 300,
        easing: 'cubic-bezier(.24,.64,.32,1)',
        fill: 'both',
      });
      active.flights.push({ card, animation });
      animation.onfinish = () => {
        if (deal !== active) return;
        // Keep landed copies in the same stack until the whole hand is ready.
        active.remaining--;
        ui['deck-count'].textContent = game.state.deckCount + active.remaining;
        if (!active.remaining) finishDeal();
      };
    }

    if (sound && audio) {
      let sounded = 0;
      const playAppearingCards = () => {
        if (deal !== active) return;
        while (active.flights[sounded]?.animation.effect.getComputedTiming().progress > 0) {
          playDealSound();
          sounded++;
        }
        if (sounded < active.flights.length) requestAnimationFrame(playAppearingCards);
      };
      requestAnimationFrame(playAppearingCards);
    }
  }

  function visibleHand() {
    return [...game.state.hand].sort((a, b) => {
      const byRank = b.rank - a.rank;
      const bySuit = suitOrder[a.suit] - suitOrder[b.suit];
      return sortMode === 'suit' ? bySuit || byRank : byRank || bySuit;
    });
  }

  function sortHand(mode) {
    if (busy || modal || sortMode === mode) return;
    finishDeal();
    sortMode = mode;
    ui['sort-rank'].setAttribute('aria-pressed', String(mode === 'rank'));
    ui['sort-suit'].setAttribute('aria-pressed', String(mode === 'suit'));
    renderHand();
    renderSelection();
  }

  function stageCards(cards, scoringIds = [], activeId) {
    if (!cards.length) return '';
    return `<div class="stage-cards">${cards.map((card) =>
      `<span class="playing-card played-card ${isRed(card) ? 'is-red' : ''} ${card.debuffed ? 'is-debuffed' : ''} ${scoringIds.includes(card.id) ? 'is-scoring' : ''} ${card.id === activeId ? 'is-active' : ''}"
        data-suit="${card.suit}" aria-label="${cardName(card)}">${faceMarkup(card)}</span>`).join('')}</div>`;
  }

  function renderFormula(result, { step, preview = false } = {}) {
    const { chips = 0, mult = 0 } = step ?? result ?? {};
    ui.formula.innerHTML = `
      <h2 class="hud-hand">${result?.name ?? '牌型'}${result ? `<small class="hud-level">Lv.${result.level}</small>` : ''}</h2>
      <div class="hud-formula">
        <span class="score-chip" aria-label="筹码 ${format(chips)}">${format(chips)}</span>
        <span aria-hidden="true">×</span>
        <span class="score-mult" aria-label="倍率 ${format(mult)}">${format(mult)}</span>
      </div>
      <p class="hud-estimate">${preview ? `预计 ${format(result.total)} 分` : '&nbsp;'}</p>`;
  }

  function scoringStage(result, { step, previousStep, preview = false, final = false } = {}) {
    renderFormula(result, { step, preview });
    if (preview) {
      ui.stage.replaceChildren();
      return;
    }
    const chipGain = previousStep ? step.chips - previousStep.chips : 0;
    const multGain = previousStep ? step.mult - previousStep.mult : 0;
    let pop = '';
    if (final) pop = `<span class="score-total">${format(result.total)}</span>`;
    else if (chipGain > 0) pop = `<span class="pop-chips">+${format(chipGain)}</span>`;
    else if (multGain > 0) pop = `<span class="pop-mult">+${format(multGain)} 倍率</span>`;
    ui.stage.innerHTML = `
      <div class="stage-pop">${pop}</div>
      ${stageCards(recentCards, result.scoringIds, step?.cardId)}
      <p class="stage-detail">${step?.label ?? '本手得分'}</p>`;
  }

  function idleStage(needsMoreCards = false) {
    renderFormula();
    ui.stage.innerHTML = `
      <p class="stage-idle">${needsMoreCards ? '本轮须打出五张牌' : '选择手牌'}</p>`;
  }

  function renderSelection() {
    const playable = !busy && game.state.phase === 'round';
    const ids = visibleHand().filter((card) => selected.has(card.id)).map((card) => card.id);
    const result = playable && ids.length ? game.preview(ids) : null;
    for (const card of ui.hand.querySelectorAll('[data-card]')) {
      const isSelected = selected.has(card.dataset.card);
      card.disabled = !playable;
      card.setAttribute('aria-pressed', String(isSelected));
      card.classList.toggle('is-selected', isSelected);
      card.classList.toggle('is-scoring', result?.scoringIds.includes(card.dataset.card) ?? false);
    }
    ui.play.disabled = !result || game.state.handsLeft === 0;
    ui.discard.disabled = !playable || selected.size === 0 || game.state.discardsLeft === 0;
    ui.options.disabled = busy;
    ui['hand-guide'].disabled = busy;
    for (const button of ui.jokers.querySelectorAll('[data-sell]')) button.disabled = !playable;
    if (ui['sort-rank']) ui['sort-rank'].disabled = busy;
    if (ui['sort-suit']) ui['sort-suit'].disabled = busy;

    if (busy) return;
    if (result) scoringStage(result, { preview: true });
    else if (playable && ids.length && ids.length < currentBlind().minPlay) idleStage(true);
    else if (game.state.lastHand && recentCards.length) scoringStage(game.state.lastHand, { final: true });
    else idleStage();
  }

  function render() {
    renderBoard();
    renderJokers();
    renderHand();
    renderSelection();
  }

  function select(id) {
    if (busy || modal || game.state.phase !== 'round') return;
    finishDeal();
    if (selected.has(id)) {
      selected.delete(id);
      tone(225);
    } else if (selected.size < 5) {
      selected.add(id);
      tone(260 + selected.size * 35);
    } else {
      announce('每次最多选 5 张牌。');
      return;
    }
    renderSelection();
    announce(selected.size ? `已选 ${selected.size} 张牌。` : '已取消选牌。');
  }

  const pause = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

  async function play() {
    if (busy || modal || !selected.size) return;
    const cards = visibleHand().filter((card) => selected.has(card.id))
      .map((card) => ({ ...card, debuffed: game.isDebuffed(card) }));
    const result = run.perform('play', cards.map((card) => card.id));
    if (!result) return;
    recentCards = cards;
    busy = true;
    selected.clear();
    renderSelection();

    if (!reducedMotion.matches) {
      for (const [index, step] of result.steps.entries()) {
        scoringStage(result, { step, previousStep: result.steps[index - 1] });
        const active = [...ui.jokers.querySelectorAll('[data-joker]')]
          .find((joker) => joker.dataset.joker === step.jokerId);
        active?.classList.add('is-active');
        tone(240 + index * 33, 0.095);
        await pause(step.jokerId ? 230 : 125);
        active?.classList.remove('is-active');
      }
    }

    scoringStage(result, { final: true });
    renderBoard();
    tone(530, 0.16);
    if (!reducedMotion.matches && game.state.phase !== 'round') await pause(650);

    busy = false;
    render();
    announce(`${result.name}，本手 ${format(result.total)} 分，本轮 ${format(game.state.score)} 分。`);
    if (game.state.phase === 'cashout') showCashout();
    if (game.state.phase === 'lost') showResult();
    if (game.state.phase === 'round') ui.hand.querySelector('button')?.focus({ preventScroll: true });
  }

  function discard() {
    if (busy || modal || !selected.size) return;
    const count = selected.size;
    if (!run.perform('discard', [...selected])) return;
    selected.clear();
    recentCards = [];
    render();
    tone(195, 0.12);
    announce(`已弃掉 ${count} 张牌，剩余 ${game.state.discardsLeft} 次弃牌。`);
    ui.hand.querySelector('button')?.focus({ preventScroll: true });
  }

  function openDialog(kind, contents) {
    finishDeal();
    modal = kind;
    ui.overlay.dataset.kind = kind;
    ui.overlay.innerHTML = ['shop', 'pack', 'cashout'].includes(kind)
      ? `<div class="table-dialog-content">${contents}</div>` : contents;
    ui.overlay.querySelector('.dialog-title').id = 'dialog-title';
    ui.overlay.setAttribute('aria-labelledby', 'dialog-title');
    if (!ui.overlay.open) ui.overlay.showModal();
    ui.overlay.querySelector('[autofocus]')?.focus();
  }

  function closeDialog() {
    modal = '';
    delete ui.overlay.dataset.kind;
    ui.overlay.close();
  }

  function showCashout() {
    const { blind, hands, interest, total } = game.state.reward;
    openDialog('cashout', `
      <div class="dialog-kicker">${anteLabel()} · ${blindName(currentBlind())}</div>
      <h2 class="dialog-title">盲注达成</h2>
      <dl class="cashout-lines">
        <div><dt>盲注奖励</dt><dd>$${blind}</dd></div>
        <div><dt>剩余出牌奖励</dt><dd>$${hands}</dd></div>
        <div><dt>利息</dt><dd>$${interest}</dd></div>
      </dl>
      <div class="dialog-actions">
        <button type="button" class="primary-button" data-action="cashout" autofocus>领取 $${total}</button>
      </div>`);
  }

  function showShop({ focusReroll = false, afterBuy = false } = {}) {
    const state = game.state;
    const round = currentBlind();
    const next = state.rounds[state.round + 1];
    const offers = state.offers.map((joker) => {
      const disabled = state.money < joker.price || state.jokers.length >= 5;
      const label = state.jokers.length >= 5 ? '栏位已满' : disabled ? '余额不足' : `购买 · $${joker.price}`;
      return `<div class="shop-offer">${jokerMarkup(joker)}
        <button type="button" class="shop-buy" data-buy="${joker.id}" ${disabled ? 'disabled' : ''}>${label}</button>
      </div>`;
    }).join('');
    const held = state.jokers.map((joker) => sellButton(joker, true)).join('');
    openDialog('shop', `
      <header class="shop-header">
        <h2 class="dialog-title">商店</h2>
        <div class="dialog-kicker">${anteLabel()} · ${blindName(round)}已通过 · 持有 $${state.money}</div>
      </header>
      <div class="shop-offers">${offers || '<p>货架已售空。</p>'}</div>
      ${state.packAvailable ? `<div class="shop-pack">
        <div class="shop-pack-copy"><strong>天体补充包</strong><span>从三张星球牌中选择一张，提升对应牌型的等级。</span></div>
        <button type="button" class="shop-buy" data-action="open-pack" ${state.money < state.packPrice ? 'disabled' : ''}>开包 · $${state.packPrice}</button>
      </div>` : ''}
      ${held ? `<div class="shop-held" aria-label="出售小丑牌"><span>出售小丑</span>${held}</div>` : ''}
      <div class="dialog-actions">
        <button type="button" class="secondary-button" data-action="guide">牌型等级</button>
        <button type="button" class="secondary-button" data-action="reroll" ${state.money < state.rerollCost ? 'disabled' : ''}>重掷 · $${state.rerollCost}</button>
        <button type="button" class="primary-button" data-action="next" autofocus>${next?.bossName ? `迎战${next.bossName}` : `进入${next?.name ?? `第 ${state.ante + 1} 注`}`}</button>
      </div>`);
    if (focusReroll) ui.overlay.querySelector('[data-action="reroll"]:enabled')?.focus();
    if (afterBuy) ui.overlay.querySelector('[data-buy]:enabled')?.focus();
  }

  function showPack() {
    const planets = game.state.planets.map((planet, index) => `
      <div class="shop-offer">
        <div class="planet-card">
          <span class="planet-icon" aria-hidden="true">${JokerArt.planet(planet.id) || '◉'}</span>
          <strong class="planet-name">${planet.name}</strong>
          <span class="planet-effect">
            <span>${planet.handName}升 1 级</span>
            <span class="planet-bonuses">
              <span>+${planet.chips} 筹码</span>
              <span>+${planet.mult} 倍率</span>
            </span>
          </span>
        </div>
        <button type="button" class="shop-buy" data-planet="${planet.id}" ${index === 0 ? 'autofocus' : ''}>使用${planet.name}</button>
      </div>`).join('');
    openDialog('pack', `
      <div class="dialog-kicker">${anteLabel()} · 商店</div>
      <h2 class="dialog-title">天体补充包</h2>
      <p class="dialog-description">选择一张星球牌，立即升级对应牌型。</p>
      <div class="shop-offers">${planets}</div>
      <div class="dialog-actions">
        <button type="button" class="secondary-button" data-action="guide">牌型等级</button>
        <button type="button" class="secondary-button" data-action="skip-pack">跳过选择</button>
      </div>`);
  }

  function showHandGuide() {
    const rows = game.handTable().map((hand) => `<tr>
      <th scope="row">${hand.name}</th><td>${hand.level}</td><td>${format(hand.chips)}</td><td>${format(hand.mult)}</td>
    </tr>`).join('');
    openDialog('guide', `
      <div class="dialog-kicker">${anteLabel()}</div>
      <h2 class="dialog-title">牌型等级</h2>
      <p class="dialog-description">这里显示每种牌型的基础筹码和倍率。星球牌能提高两项数值。</p>
      <table class="hand-levels">
        <thead><tr><th scope="col">牌型</th><th scope="col">等级</th><th scope="col">筹码</th><th scope="col">倍率</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      <div class="dialog-actions"><button type="button" class="primary-button" data-action="back" autofocus>返回</button></div>`);
  }

  function showResult() {
    const state = game.state;
    const won = state.phase === 'won';
    openDialog('result', `
      <div class="dialog-kicker">${anteLabel()} · ${blindName(currentBlind())}</div>
      <h2 class="dialog-title">${won ? '盲注全破' : '本局结束'}</h2>
      <p class="dialog-description">${won ? '你打过了全部八注。' : '本轮出牌次数已用完。'}</p>
      <dl class="cashout-lines">
        <div><dt>本轮得分</dt><dd>${format(state.score)} / ${format(currentBlind().target)}</dd></div>
        <div><dt>最高单手</dt><dd>${format(state.bestHand?.total ?? 0)}</dd></div>
        <div><dt>累计得分</dt><dd>${format(state.totalScore)}</dd></div>
      </dl>
      <div class="dialog-actions">
        <button type="button" class="primary-button" data-action="restart" autofocus>再来一局</button>
      </div>`);
  }

  function showHelp() {
    openDialog('help', `
      <div class="dialog-kicker">${anteLabel()}</div>
      <h2 class="dialog-title">怎么玩</h2>
      <div class="dialog-description">
        <p>点击手牌，或按数字键 1–8 选牌。每次可以打出或弃掉 1–5 张。</p>
        <p>同一手牌只结算最高牌型。构成牌型的牌增加筹码，小丑牌从左到右依次生效，最终得分是筹码乘以倍率。</p>
        <p>每注包含小盲注、大盲注和首领盲注。首领有额外规则，打完第八注即可获胜。</p>
        <p>过关后可以在商店买卖小丑和开天体补充包。星球牌会提升对应牌型的基础筹码与倍率。</p>
      </div>
      <div class="dialog-actions"><button type="button" class="primary-button" data-action="options" autofocus>返回选项</button></div>`);
  }

  function showOptions() {
    const disabled = AudioContext ? '' : ' disabled';
    openDialog('options', `
      <h2 class="dialog-title">选项</h2>
      <div class="sound-options">
        <div class="sound-options-heading">
          <label for="sound-volume">音效音量</label>
          <output id="sound-volume-value" for="sound-volume" aria-hidden="true">${soundVolume}%</output>
        </div>
        <div class="sound-options-controls">
          <input type="range" id="sound-volume" min="0" max="100" step="1" value="${soundVolume}"
            aria-valuetext="${soundVolume}%"${disabled}>
          <button type="button" class="secondary-button" id="sound" data-action="sound"
            aria-pressed="${sound}"${disabled}>音效 ${sound ? '开' : '关'}</button>
        </div>
      </div>
      <div class="options-grid">
        <button type="button" class="secondary-button" id="help" data-action="help">怎么玩</button>
        <button type="button" class="secondary-button" id="reset" data-action="reset-options">重新开局</button>
      </div>
      <div class="dialog-actions"><button type="button" class="primary-button" data-action="close" autofocus>回到牌桌</button></div>`);
  }

  function confirmSale(id) {
    const joker = game.state.jokers.find((item) => item.id === id);
    if (!joker || busy) return;
    openDialog('sell', `
      <div class="dialog-kicker">小丑牌</div>
      <h2 class="dialog-title">出售${joker.name}？</h2>
      <p class="dialog-description">出售后获得 $${game.sellPrice(id)}，并腾出一个小丑栏位。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-button" data-action="back" autofocus>保留</button>
        <button type="button" class="primary-button" data-confirm-sell="${id}">确认出售</button>
      </div>`);
  }

  function returnToPhase() {
    if (game.state.phase === 'shop') showShop();
    else if (game.state.phase === 'pack') showPack();
    else closeDialog();
  }

  function confirmReset() {
    openDialog('reset', `
      <div class="dialog-kicker">牌桌</div>
      <h2 class="dialog-title">重新开局？</h2>
      <p class="dialog-description">当前对局会结束，并重新洗牌。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-button" data-action="options" autofocus>保留对局</button>
        <button type="button" class="primary-button" data-action="restart">重新开局</button>
      </div>`);
  }

  function reset() {
    closeDialog();
    run = JokerRunStore.reset(JokerEngine, storage);
    game = run.game;
    selected.clear();
    recentCards = [];
    render();
    announce('新对局开始。');
    ui.hand.querySelector('button')?.focus({ preventScroll: true });
    dealHand('opening');
  }

  ui.hand.addEventListener('click', (event) => {
    const card = event.target.closest('[data-card]');
    if (card) select(card.dataset.card);
  });
  function moveJoker(id, index) {
    if (busy || modal || !run.perform('moveJoker', id, index)) return;
    renderJokers();
    renderSelection();
    ui.jokers.querySelector(`[data-joker="${id}"]`)?.focus({ preventScroll: true });
    announce(`已将小丑移到第 ${index + 1} 位。`);
  }

  function clearDrag() {
    draggingJoker = '';
    for (const card of ui.jokers.querySelectorAll('.is-dragging, .is-drop-target')) {
      card.classList.remove('is-dragging', 'is-drop-target');
    }
  }

  ui.jokers.addEventListener('click', (event) => {
    const sell = event.target.closest('[data-sell]');
    if (sell && !modal) confirmSale(sell.dataset.sell);
  });
  ui.jokers.addEventListener('dragstart', (event) => {
    const card = event.target.closest('[data-joker][draggable="true"]');
    if (!card || busy || modal) {
      event.preventDefault();
      return;
    }
    draggingJoker = card.dataset.joker;
    event.dataTransfer.setData('text/plain', draggingJoker);
    event.dataTransfer.effectAllowed = 'move';
    card.classList.add('is-dragging');
  });
  ui.jokers.addEventListener('dragover', (event) => {
    const card = event.target.closest('[data-joker]');
    if (!draggingJoker || !card) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    for (const target of ui.jokers.querySelectorAll('[data-joker]')) {
      target.classList.toggle('is-drop-target', target === card && card.dataset.joker !== draggingJoker);
    }
  });
  ui.jokers.addEventListener('drop', (event) => {
    const card = event.target.closest('[data-joker]');
    if (!draggingJoker || !card) return;
    event.preventDefault();
    const index = game.state.jokers.findIndex((joker) => joker.id === card.dataset.joker);
    const id = draggingJoker;
    clearDrag();
    moveJoker(id, index);
  });
  ui.jokers.addEventListener('dragend', clearDrag);
  ui.jokers.addEventListener('keydown', (event) => {
    const card = event.target.closest('[data-joker]');
    if (!card || !event.altKey || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
    event.preventDefault();
    const index = game.state.jokers.findIndex((joker) => joker.id === card.dataset.joker);
    moveJoker(card.dataset.joker, index + (event.key === 'ArrowLeft' ? -1 : 1));
  });
  ui.play.addEventListener('click', play);
  ui.discard.addEventListener('click', discard);
  ui.options.addEventListener('click', showOptions);
  ui['hand-guide'].addEventListener('click', showHandGuide);
  ui['sort-rank']?.addEventListener('click', () => sortHand('rank'));
  ui['sort-suit']?.addEventListener('click', () => sortHand('suit'));
  ui.overlay.addEventListener('cancel', (event) => {
    event.preventDefault();
    if (modal === 'help' || modal === 'reset') showOptions();
    else if (['options', 'guide', 'sell'].includes(modal)) returnToPhase();
  });

  const dialogActions = {
    reroll() {
      if (!run.perform('reroll')) return;
      renderBoard();
      showShop({ focusReroll: true });
      tone(285, 0.12);
    },
    cashout() {
      if (!run.perform('cashout')) return;
      renderBoard();
      if (game.state.phase === 'won') showResult();
      else showShop();
    },
    next() {
      if (!run.perform('nextRound')) return;
      closeDialog();
      recentCards = [];
      render();
      announce(`${anteLabel()}，${blindName(currentBlind())}开始。`);
      ui.hand.querySelector('button')?.focus({ preventScroll: true });
      dealHand('blind');
    },
    'open-pack'() {
      if (!run.perform('openPack')) return;
      renderBoard();
      showPack();
      tone(370, 0.15);
    },
    'skip-pack'() {
      if (run.perform('skipPack')) showShop();
    },
    guide: showHandGuide,
    options: showOptions,
    help: showHelp,
    sound: toggleSound,
    'reset-options': confirmReset,
    restart: reset,
    close: closeDialog,
    back: returnToPhase,
  };

  ui.overlay.addEventListener('input', updateSoundVolume);
  ui.overlay.addEventListener('change', saveSoundVolume);
  ui.overlay.addEventListener('click', (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    const { action, buy, sell, confirmSell, planet } = button.dataset;
    if (buy && run.perform('buy', buy)) {
      renderBoard();
      renderJokers();
      showShop({ afterBuy: true });
      tone(420, 0.12);
      announce('已购买小丑牌。');
    } else if (sell) {
      confirmSale(sell);
    } else if (confirmSell && run.perform('sell', confirmSell)) {
      render();
      returnToPhase();
      announce('已出售小丑牌。');
      if (!modal) ui.hand.querySelector('button')?.focus({ preventScroll: true });
    } else if (planet && run.perform('choosePlanet', planet)) {
      showShop();
      announce('牌型已升级。');
      tone(610, 0.18);
    } else if (action) {
      dialogActions[action]?.();
    }
  });
  for (const type of ['pointerdown', 'click', 'keydown']) document.addEventListener(type, unlockAudio, true);
  document.addEventListener('pointerdown', finishDeal, true);
  window.addEventListener('resize', finishDeal);
  window.addEventListener('scroll', finishDeal, { passive: true });
  reducedMotion.addEventListener('change', finishDeal);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) finishDeal();
  });
  document.addEventListener('keydown', (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.repeat || modal || busy) return;
    finishDeal();
    if (/^[1-8]$/.test(event.key)) {
      const card = visibleHand()[Number(event.key) - 1];
      if (card) select(card.id);
    }
  });

  render();
  if (game.state.phase === 'cashout') showCashout();
  else if (['lost', 'won'].includes(game.state.phase)) showResult();
  else if (['shop', 'pack'].includes(game.state.phase)) returnToPhase();
  else if (!run.resumed) dealHand('opening');
  if (run.resumed) announce(`已恢复${anteLabel()}的牌局。`);
})();
