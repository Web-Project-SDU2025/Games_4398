(() => {
  'use strict';

  const SUITS = { spades: '♠', hearts: '♥', clubs: '♣', diamonds: '♦' };
  const FACES = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A' };
  const HAND_SIZE = 8;
  const HANDS_PER_ROUND = 4;
  const JOKER_LIMIT = 5;
  const SHOP_REROLL_COST = 5;
  const PACK_PRICE = 4;
  const ANTE_TARGETS = [300, 800, 2000, 5000, 11000, 20000, 35000, 50000];
  const BOSSES = [
    { id: 'water', bossName: '水之壁', rule: '本轮不能弃牌。', discards: 0 },
    { id: 'head', bossName: '头之壁', rule: '红桃牌被削弱，仍可组成牌型。', debuffSuit: 'hearts' },
    { id: 'psychic', bossName: '通灵者', rule: '每手必须打出五张牌。', minPlay: 5 },
    { id: 'manacle', bossName: '镣铐', rule: '本轮手牌上限减少一张。', handSizeOffset: -1 },
    { id: 'plant', bossName: '植物', rule: 'J、Q、K 被削弱，仍可组成牌型。', debuffFaces: true },
    { id: 'tooth', bossName: '牙齿', rule: '每打出一张牌失去 $1。', costPerCard: 1 },
    { id: 'wall', bossName: '高墙', rule: '所需分数是小盲注的四倍。', multiplier: 4 },
    { id: 'leaf', bossName: '翠绿叶片', rule: '所有牌都被削弱。出售一张小丑即可解除。', debuffAll: true },
  ];
  const HANDS = {
    high: { name: '高牌', chips: 5, mult: 1, planet: '冥王星', chipGain: 10, multGain: 1 },
    pair: { name: '对子', chips: 10, mult: 2, planet: '水星', chipGain: 15, multGain: 1 },
    twoPair: { name: '两对', chips: 20, mult: 2, planet: '天王星', chipGain: 20, multGain: 1 },
    trips: { name: '三条', chips: 30, mult: 3, planet: '金星', chipGain: 20, multGain: 2 },
    straight: { name: '顺子', chips: 30, mult: 4, planet: '土星', chipGain: 30, multGain: 3 },
    flush: { name: '同花', chips: 35, mult: 4, planet: '木星', chipGain: 15, multGain: 2 },
    fullHouse: { name: '葫芦', chips: 40, mult: 4, planet: '地球', chipGain: 25, multGain: 2 },
    quads: { name: '四条', chips: 60, mult: 7, planet: '火星', chipGain: 30, multGain: 3 },
    straightFlush: { name: '同花顺', chips: 100, mult: 8, planet: '海王星', chipGain: 40, multGain: 4 },
  };
  const JOKERS = [
    {
      id: 'wildcard', name: '小丑', icon: '✦', color: '#e99e73', price: 2,
      description: '+4 倍率',
      handEffect: () => ({ mult: 4 }),
    },
    {
      id: 'echo', name: '悬挂选票', icon: '▤', color: '#eac379', price: 4,
      description: '首张计分牌额外触发两次',
      repeats: 2,
    },
    {
      id: 'vault', name: '杂技演员', icon: '✧', color: '#c4a7e7', price: 6,
      description: '本轮最后一次出牌时 ×3 倍率',
      handEffect: ({ state }) => state.handsLeft === 1 && { times: 3 },
    },
    {
      id: 'jolly', name: '开心小丑', icon: '☻', color: '#ffb97e', price: 3,
      description: '牌型包含对子时 +8 倍率',
      handEffect: ({ ranks }) => ranks.some(([, count]) => count >= 2) && { mult: 8 },
    },
    {
      id: 'banner', name: '旗帜', icon: '⚑', color: '#81cfbd', price: 5,
      description: '每剩余一次弃牌 +30 筹码',
      handEffect: ({ state }) => state.discardsLeft > 0 && { chips: 30 * state.discardsLeft },
    },
    {
      id: 'half', name: '半张小丑', icon: '◐', color: '#ed929c', price: 5,
      description: '打出至多 3 张牌时 +20 倍率',
      handEffect: ({ cards }) => cards.length <= 3 && { mult: 20 },
    },
    {
      id: 'odd', name: '奇数托德', icon: 'Ⅰ', color: '#83bedf', price: 4,
      description: '每张计分的 A、3、5、7、9 +31 筹码',
      cardEffect: ({ rank }) => [3, 5, 7, 9, 14].includes(rank) && { chips: 31 },
    },
    {
      id: 'even', name: '偶数史蒂文', icon: 'Ⅱ', color: '#c5baf0', price: 4,
      description: '每张计分的 2、4、6、8、10 +4 倍率',
      cardEffect: ({ rank }) => rank <= 10 && rank % 2 === 0 && { mult: 4 },
    },
    {
      id: 'lusty', name: '色欲小丑', icon: '♥', color: '#f392a1', price: 5,
      description: '每张计分红桃 +3 倍率',
      cardEffect: ({ suit }) => suit === 'hearts' && { mult: 3 },
    },
    {
      id: 'greedy', name: '贪婪小丑', icon: '♦', color: '#f7af78', price: 5,
      description: '每张计分方块 +3 倍率',
      cardEffect: ({ suit }) => suit === 'diamonds' && { mult: 3 },
    },
    {
      id: 'crafty', name: '狡猾小丑', icon: '♣', color: '#9ed5a4', price: 4,
      description: '牌型包含同花时 +80 筹码',
      handEffect: ({ flush }) => flush && { chips: 80 },
    },
    {
      id: 'abstract', name: '抽象小丑', icon: '◆', color: '#9bb4ed', price: 4,
      description: '每持有一张小丑 +3 倍率',
      handEffect: ({ state }) => ({ mult: 3 * state.jokers.length }),
    },
    {
      id: 'green', name: '绿色小丑', icon: '❧', color: '#96d28e', price: 4,
      description: '每次出牌使本牌 +1 倍率，弃牌则 −1',
      handEffect: ({ state }) => ({ mult: state.jokerProgress.green + 1 }),
    },
    {
      id: 'supernova', name: '超新星', icon: '✷', color: '#b89aee', price: 5,
      description: '本局每打过一次该牌型 +1 倍率，含本手',
      handEffect: ({ state, handId }) => ({ mult: state.handUses[handId] + 1 }),
    },
    {
      id: 'constellation', name: '星座', icon: '✧', color: '#91bdf0', price: 6,
      description: '初始 ×1，每使用一张星球牌额外累积 ×0.1 倍率',
      handEffect: ({ state }) => state.jokerProgress.constellation > 0 && { times: 1 + state.jokerProgress.constellation / 10 },
    },
    {
      id: 'stuntman', name: '特技演员', icon: '↯', color: '#e9a66d', price: 7,
      description: '+250 筹码，手牌上限减少两张',
      handEffect: () => ({ chips: 250 }),
      handSizeOffset: -2,
    },
    {
      id: 'cardSharp', name: '老千小丑', icon: '▣', color: '#e4c481', price: 6,
      description: '本轮已经打过该牌型时 ×3 倍率',
      handEffect: ({ state, handId }) => state.blindHandUses[handId] > 0 && { times: 3 },
    },
    {
      id: 'blackboard', name: '黑板', icon: '▩', color: '#a4c5b2', price: 6,
      description: '留在手中的牌全是黑桃或梅花时 ×3 倍率',
      handEffect: ({ cards, state }) => state.hand.every((card) => cards.includes(card) || ['spades', 'clubs'].includes(card.suit)) && { times: 3 },
    },
    {
      id: 'duo', name: '二重奏', icon: 'Ⅱ', color: '#e9a8ae', price: 8,
      description: '牌型包含对子时 ×2 倍率',
      handEffect: ({ ranks }) => ranks.some(([, count]) => count >= 2) && { times: 2 },
    },
    {
      id: 'trio', name: '三重奏', icon: 'Ⅲ', color: '#a5bcf4', price: 8,
      description: '牌型包含三条时 ×3 倍率',
      handEffect: ({ ranks }) => ranks[0][1] >= 3 && { times: 3 },
    },
    {
      id: 'tribe', name: '部落', icon: '✣', color: '#a5d6b9', price: 8,
      description: '牌型包含同花时 ×2 倍率',
      handEffect: ({ flush }) => flush && { times: 2 },
    },
    {
      id: 'order', name: '秩序', icon: '∴', color: '#eac379', price: 8,
      description: '牌型包含顺子时 ×3 倍率',
      handEffect: ({ straight }) => straight && { times: 3 },
    },
  ].map(Object.freeze);

  function roundsFor(ante) {
    const target = ANTE_TARGETS[ante - 1];
    const boss = BOSSES[ante - 1];
    return Object.freeze([
      { id: 'small', name: '小盲注', target, reward: 3, rule: '' },
      { id: 'big', name: '大盲注', target: target * 1.5, reward: 4, rule: '' },
      { name: '首领盲注', target: target * (boss.multiplier ?? 2), reward: 5, ...boss },
    ].map((round) => Object.freeze({ minPlay: 1, discards: 3, ...round })));
  }

  function handCounters(initial) {
    return Object.fromEntries(Object.keys(HANDS).map((id) => [id, initial]));
  }

  function handValues(id, level) {
    const hand = HANDS[id];
    return {
      id,
      name: hand.name,
      level,
      chips: hand.chips + (level - 1) * hand.chipGain,
      mult: hand.mult + (level - 1) * hand.multGain,
    };
  }

  function isDebuffed(card, state) {
    if (state.phase !== 'round') return false;
    const round = state.rounds[state.round];
    return Boolean(
      (round.debuffAll && !state.bossReleased) ||
      card.suit === round.debuffSuit ||
      (round.debuffFaces && card.rank >= 11 && card.rank <= 13),
    );
  }

  function classify(cards) {
    const counts = new Map();
    for (const card of cards) {
      counts.set(card.rank, (counts.get(card.rank) ?? 0) + 1);
    }
    const ranks = [...counts].sort(([rankA, countA], [rankB, countB]) => countB - countA || rankB - rankA);
    const [[firstRank, firstCount], [, secondCount] = []] = ranks;
    const five = cards.length === 5;
    const flush = five && cards.every((card) => card.suit === cards[0].suit);
    const ordered = [...counts.keys()].sort((a, b) => a - b);
    const straight = ordered.length === 5 && (
      ordered[4] - ordered[0] === 4 || ordered.join(',') === '2,3,4,5,14'
    );

    let handId = 'high';
    let scoringRanks = [firstRank];
    if (straight && flush) {
      handId = 'straightFlush';
      scoringRanks = ordered;
    } else if (firstCount === 4) {
      handId = 'quads';
    } else if (firstCount === 3 && secondCount === 2) {
      handId = 'fullHouse';
      scoringRanks = ordered;
    } else if (flush) {
      handId = 'flush';
      scoringRanks = ordered;
    } else if (straight) {
      handId = 'straight';
      scoringRanks = ordered;
    } else if (firstCount === 3) {
      handId = 'trips';
    } else if (firstCount === 2 && secondCount === 2) {
      handId = 'twoPair';
      scoringRanks = ranks.slice(0, 2).map(([rank]) => rank);
    } else if (firstCount === 2) {
      handId = 'pair';
    }
    return { handId, ranks, flush, straight, scoring: cards.filter(({ rank }) => scoringRanks.includes(rank)) };
  }

  function scoreHand(cards, state) {
    const { handId, ranks, flush, straight, scoring } = classify(cards);
    const hand = handValues(handId, state.levels[handId]);
    let { chips } = hand;
    // 只有星座能引入小数倍率。用十分位累计，防止整分被浮点误差舍去一分。
    let multTenths = hand.mult * 10;
    const steps = [{ label: hand.name, chips, mult: hand.mult }];

    function addStep(label, change, source) {
      if (!change) return;
      chips += change.chips ?? 0;
      multTenths = Math.round((multTenths + (change.mult ?? 0) * 10) * (change.times ?? 1));
      steps.push({ label, chips, mult: multTenths / 10, ...source });
    }

    function scoreCard(card, repeatJoker) {
      const label = (FACES[card.rank] ?? card.rank) + SUITS[card.suit];
      const value = card.rank === 14 ? 11 : Math.min(card.rank, 10);
      addStep(repeatJoker ? repeatJoker.name + ' · ' + label : label, { chips: value }, {
        cardId: card.id,
        ...(repeatJoker && { jokerId: repeatJoker.id }),
      });
      for (const joker of state.jokers) {
        addStep(joker.name + ' · ' + label, joker.cardEffect?.(card), { cardId: card.id, jokerId: joker.id });
      }
    }

    for (const [index, card] of scoring.entries()) {
      if (isDebuffed(card, state)) {
        const label = (FACES[card.rank] ?? card.rank) + SUITS[card.suit];
        steps.push({ label: label + ' · 削弱', chips, mult: multTenths / 10, cardId: card.id });
        continue;
      }
      scoreCard(card);
      if (index === 0) {
        for (const joker of state.jokers) {
          for (let count = 0; count < (joker.repeats ?? 0); count++) {
            scoreCard(card, joker);
          }
        }
      }
    }

    const context = { cards, state, ranks, flush, straight, handId };
    for (const joker of state.jokers) {
      addStep(joker.name, joker.handEffect?.(context), { jokerId: joker.id });
    }

    return {
      handId, name: hand.name, level: hand.level, chips, mult: multTenths / 10,
      total: Math.floor(chips * multTenths / 10),
      scoringIds: scoring.map(({ id }) => id), steps,
    };
  }

  function createGame({ random = Math.random } = {}) {
    const state = {
      phase: 'round',
      ante: 1,
      anteMax: ANTE_TARGETS.length,
      round: 0,
      rounds: roundsFor(1),
      hand: [],
      score: 0,
      totalScore: 0,
      handsLeft: HANDS_PER_ROUND,
      discardsLeft: 3,
      money: 4,
      jokers: [],
      offers: [],
      rerollCost: SHOP_REROLL_COST,
      packAvailable: false,
      packPrice: PACK_PRICE,
      planets: [],
      deckCount: 0,
      lastHand: null,
      bestHand: null,
      reward: null,
      bossReleased: false,
      levels: handCounters(1),
      handUses: handCounters(0),
      blindHandUses: handCounters(0),
      jokerProgress: {},
    };
    let deck = [];

    function currentRound() {
      return state.rounds[state.round];
    }

    function shuffled(items) {
      const result = items.slice();
      for (let index = result.length - 1; index > 0; index--) {
        const swap = Math.floor(random() * (index + 1));
        [result[index], result[swap]] = [result[swap], result[index]];
      }
      return result;
    }

    function refillHand() {
      const jokerOffset = state.jokers.reduce((sum, joker) => sum + (joker.handSizeOffset ?? 0), 0);
      const handSize = HAND_SIZE + (currentRound().handSizeOffset ?? 0) + jokerOffset;
      const drawCount = Math.min(deck.length, Math.max(0, handSize - state.hand.length));
      const draw = deck.splice(deck.length - drawCount);
      state.hand.push(...draw);
      state.deckCount = deck.length;
    }

    function startRound() {
      const cards = [];
      for (const suit of Object.keys(SUITS)) {
        for (let rank = 2; rank <= 14; rank++) {
          cards.push({ id: suit + rank, suit, rank });
        }
      }
      deck = shuffled(cards);
      state.hand = [];
      state.score = 0;
      state.handsLeft = HANDS_PER_ROUND;
      state.discardsLeft = currentRound().discards;
      state.offers = [];
      state.packAvailable = false;
      state.planets = [];
      state.lastHand = null;
      state.reward = null;
      state.bossReleased = false;
      state.blindHandUses = handCounters(0);
      state.phase = 'round';
      refillHand();
    }

    function selection(ids) {
      if (state.phase !== 'round' || !Array.isArray(ids) || ids.length < 1 || ids.length > 5) return null;
      if (new Set(ids).size !== ids.length) return null;
      const selected = Array.from(ids, (id) => state.hand.find((card) => card.id === id));
      return selected.every(Boolean) ? selected : null;
    }

    function useCards(ids) {
      state.hand = state.hand.filter((card) => !ids.includes(card.id));
      refillHand();
    }

    function clearBlind(result) {
      const blind = currentRound().reward;
      const hands = state.handsLeft;
      const interest = Math.min(5, Math.floor(state.money / 5));
      const reward = { blind, hands, interest, total: blind + hands + interest };
      state.reward = reward;
      result.reward = reward;
      state.phase = 'cashout';
    }

    function cashout() {
      if (state.phase !== 'cashout') return false;
      state.money += state.reward.total;
      if (state.ante === state.anteMax && state.round === state.rounds.length - 1) {
        state.phase = 'won';
      } else {
        stockShop();
        state.rerollCost = SHOP_REROLL_COST;
        state.packAvailable = true;
        state.phase = 'shop';
      }
      return true;
    }

    function stockShop() {
      const available = JOKERS.filter((joker) => !state.jokers.includes(joker) && !state.offers.includes(joker));
      state.offers = shuffled(available).slice(0, 3);
    }

    function preview(ids) {
      const cards = selection(ids);
      return cards && cards.length >= currentRound().minPlay ? scoreHand(cards, state) : null;
    }

    function play(ids) {
      const result = preview(ids);
      if (!result) return null;
      state.lastHand = result;
      state.score += result.total;
      state.totalScore += result.total;
      if (!state.bestHand || result.total > state.bestHand.total) state.bestHand = result;
      state.handUses[result.handId]++;
      state.blindHandUses[result.handId]++;
      if (state.jokerProgress.green !== undefined) state.jokerProgress.green++;
      state.handsLeft--;
      state.money = Math.max(0, state.money - (currentRound().costPerCard ?? 0) * ids.length);
      useCards(ids);
      if (state.score >= currentRound().target) {
        clearBlind(result);
      } else if (state.handsLeft === 0) {
        state.phase = 'lost';
      }
      return result;
    }

    function discard(ids) {
      if (state.discardsLeft === 0 || !selection(ids)) return false;
      state.discardsLeft--;
      if (state.jokerProgress.green > 0) state.jokerProgress.green--;
      useCards(ids);
      return true;
    }

    function buy(id) {
      if (state.phase !== 'shop' || state.jokers.length >= JOKER_LIMIT) return false;
      const index = state.offers.findIndex((joker) => joker.id === id);
      const joker = state.offers[index];
      if (!joker || state.money < joker.price) return false;
      state.money -= joker.price;
      state.jokers.push(joker);
      if (joker.id === 'green' || joker.id === 'constellation') state.jokerProgress[joker.id] = 0;
      state.offers.splice(index, 1);
      return true;
    }

    function sellPrice(id) {
      const joker = state.jokers.find((item) => item.id === id);
      return joker ? Math.max(1, Math.floor(joker.price / 2)) : null;
    }

    function sell(id) {
      if (state.phase !== 'round' && state.phase !== 'shop') return false;
      const price = sellPrice(id);
      if (price === null) return false;
      state.jokers = state.jokers.filter((joker) => joker.id !== id);
      state.money += price;
      delete state.jokerProgress[id];
      if (state.phase === 'round' && currentRound().debuffAll) state.bossReleased = true;
      return true;
    }

    function handTable() {
      return Object.keys(HANDS).map((id) => handValues(id, state.levels[id]));
    }

    function openPack() {
      if (state.phase !== 'shop' || !state.packAvailable || state.money < PACK_PRICE) return false;
      state.money -= PACK_PRICE;
      state.packAvailable = false;
      state.planets = shuffled(Object.keys(HANDS)).slice(0, 3).map((id) => {
        const { planet, name, chipGain, multGain } = HANDS[id];
        return { id, name: planet, handName: name, chips: chipGain, mult: multGain, level: state.levels[id] };
      });
      state.phase = 'pack';
      return true;
    }

    function skipPack() {
      if (state.phase !== 'pack') return false;
      state.planets = [];
      state.phase = 'shop';
      return true;
    }

    function choosePlanet(id) {
      if (state.phase !== 'pack' || !state.planets.some((planet) => planet.id === id)) return false;
      state.levels[id]++;
      if (state.jokerProgress.constellation !== undefined) state.jokerProgress.constellation++;
      return skipPack();
    }

    function reroll() {
      if (state.phase !== 'shop' || state.money < state.rerollCost) return false;
      state.money -= state.rerollCost;
      state.rerollCost++;
      stockShop();
      return true;
    }

    function moveJoker(id, toIndex) {
      if (state.phase !== 'round' && state.phase !== 'shop') return false;
      if (!Number.isInteger(toIndex) || toIndex < 0 || toIndex >= state.jokers.length) return false;
      const index = state.jokers.findIndex((joker) => joker.id === id);
      if (index === -1 || index === toIndex) return false;
      const [joker] = state.jokers.splice(index, 1);
      state.jokers.splice(toIndex, 0, joker);
      return true;
    }

    function nextRound() {
      if (state.phase !== 'shop') return false;
      state.round++;
      if (state.round === state.rounds.length) {
        state.ante++;
        state.round = 0;
        state.rounds = roundsFor(state.ante);
      }
      startRound();
      return true;
    }

    startRound();
    return {
      state, preview, play, discard, cashout, buy, sell, sellPrice, reroll, moveJoker, nextRound,
      handTable, openPack, choosePlanet, skipPack, isDebuffed: (card) => isDebuffed(card, state),
    };
  }

  const api = Object.freeze({ createGame });
  globalThis.JokerEngine = api;
})();
