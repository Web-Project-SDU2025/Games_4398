(() => {
  'use strict';

  const ink = '#182335';
  const paper = '#fff3d4';
  const gold = '#ffd262';
  const svg = (content, width = 36, height = 48) =>
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" shape-rendering="crispEdges" aria-hidden="true" focusable="false">${content}</svg>`;
  const rect = (x, y, width, height, fill) => `<rect x="${x}" y="${y}" width="${width}" height="${height}" fill="${fill}"/>`;
  const path = (data, fill) => `<path d="${data}" fill="${fill}"/>`;
  const group = (content, transform) => `<g transform="${transform}">${content}</g>`;

  function spark(x, y, color) {
    return group(path('M2 0H3V2H5V3H3V5H2V3H0V2H2Z', color), `translate(${x} ${y})`);
  }

  function diamond(x, y, color) {
    return group(path('M3 0H4V1H5V2H6V3H7V4H6V5H5V6H4V7H3V6H2V5H1V4H0V3H1V2H2V1H3Z', color), `translate(${x} ${y})`);
  }

  function frame(fill, line) {
    return rect(0, 0, 36, 48, fill) + path('M1 0H35V1H36V47H35V48H1V47H0V1H1Z M2 2V46H34V2Z', line);
  }

  function jester(left, right, collar, skin = '#ffe2b7') {
    return path('M5 19V11H8V7H13V10H16V14H19V10H22V6H28V9H31V17H28V20H27V28H24V32H27V34H30V39H24V42H12V40H6V36H8V32H11V29H9V25H7V20Z', ink) +
      path('M7 18V11H9V8H12V11H14V14H17V19H11V17H8Z', left) +
      path('M18 19V14H21V10H23V7H27V10H29V17H26V20H22V18Z', right) +
      path('M6 11H10V15H6Z M24 6H28V9H24Z M28 17H32V21H28Z', gold) +
      path('M10 21H13V19H23V20H26V26H24V29H21V31H16V30H13V28H11V25H10Z', skin) +
      path('M12 22H16V25H13V24H12Z M20 22H24V24H22V25H20Z M16 27H22V28H20V30H17V29H16Z', ink) +
      path('M12 27H14V29H12Z M23 26H25V28H23Z', '#ef766b') +
      path('M10 32H13V34H16V36H20V35H23V33H27V35H29V38H24V40H12V38H8V36H10Z', collar) +
      path('M11 34H13V37H11Z M16 37H19V39H16Z M22 35H24V38H22Z M26 35H28V37H26Z', paper);
  }

  function playingTile(x, y, color, motif, turn = 0) {
    const tile = path('M1 0H12V1H13V18H12V19H1V18H0V1H1Z', ink) +
      rect(1, 1, 11, 17, paper) + rect(2, 2, 2, 2, color) + rect(9, 15, 2, 2, color) + motif;
    return group(tile, `translate(${x} ${y}) rotate(${turn} 6.5 9.5)`);
  }

  function heart(x, y, fill) {
    const shape = 'M1 0H4V1H5V2H6V1H7V0H10V1H11V5H10V6H9V7H8V8H7V9H6V10H5V9H4V8H3V7H2V6H1V5H0V1H1Z';
    return group(path(shape, fill), `translate(${x} ${y})`);
  }

  const stars = (light) => spark(4, 8, light) + spark(27, 32, light) +
    rect(26, 10, 2, 2, light) + rect(5, 31, 2, 2, light) + rect(17, 4, 1, 1, light) + rect(12, 41, 1, 1, light);

  function medallion(x, y, face, shadow, light) {
    return group(path('M5 0H12V1H15V3H17V6H18V12H17V15H15V17H12V18H5V17H2V15H1V12H0V6H1V3H3V1H5Z', ink) +
      path('M5 2H12V3H15V6H16V12H15V15H12V16H5V15H3V12H2V6H3V3H5Z', face) +
      path('M5 4H10V5H6V7H4V10H3V6H5Z', light) +
      path('M12 13H15V14H13V16H6V15H11V14H12Z', shadow), `translate(${x} ${y})`);
  }

  const pin = (color) => path('M17 8H19V40H17Z M9 9H17V11H24V12H30V18H26V20H20V22H10V20H6V15H7V11H9Z', ink) +
    path('M10 11H17V13H24V14H28V17H24V18H19V20H11V18H8V15H9Z', color) +
    path('M11 12H15V14H10V16H9V14H11Z M14 39H22V41H14Z', gold);

  function masks(color, light) {
    return path('M3 15H7V13H12V14H16V16H20V14H24V13H29V15H33V24H31V27H28V31H23V29H20V25H16V29H13V31H8V28H6V25H3Z', ink) +
      path('M5 17H9V15H12V17H16V19H20V17H24V15H28V17H31V23H29V26H27V29H24V27H22V23H14V27H12V29H9V26H7V23H5Z', color) +
      path('M7 18H10V19H14V22H9V21H7Z M22 19H26V18H29V21H27V22H22Z', ink) +
      path('M7 16H11V17H8V18H6V17H7Z M25 16H29V17H25Z M15 19H21V20H15Z', light);
  }

  const jokerDrawings = {
    wildcard: () => frame('#f4e7ce', '#d2ad78') + stars('#d99754') + jester('#c54455', '#347dc7', '#e7a53d'),
    jolly: () => frame('#fbe5a1', '#e7a03f') + stars('#f89149') + jester('#d65649', '#569bd7', '#3f8cb1', '#fff2c4'),
    half: () => frame('#f7c9c9', '#d87587') + jester('#9d3e7a', '#d46267', '#bc3455') +
      path('M19 3H34V45H19V42H21V39H19V36H21V33H19V30H21V27H19V24H21V21H19V18H21V15H19V12H21V9H19Z', '#692f54') +
      path('M21 8H23V10H21Z M21 14H23V16H21Z M21 20H23V22H21Z M21 26H23V28H21Z M21 32H23V34H21Z M21 38H23V40H21Z', '#e5a4c1'),
    odd: () => frame('#b3d7e7', '#558eb5') + jester('#2e6ea8', '#478ec7', '#75b7ce') +
      path('M10 21H17V24H19V21H26V25H23V27H19V25H17V27H13V26H10Z', ink) + rect(12, 22, 4, 2, '#f8ce63') + rect(21, 22, 4, 2, '#f8ce63'),
    even: () => frame('#ded1f2', '#9b77c6') + jester('#5a479d', '#9972ce', '#7e60c2') +
      path('M9 22H11V21H16V22H20V21H25V22H27V25H25V27H20V25H16V27H11V25H9Z', ink) + rect(12, 22, 4, 2, '#d2bafb') + rect(21, 22, 4, 2, '#d2bafb'),
    lusty: () => frame('#f4c2ce', '#d36b96') + heart(4, 6, '#df6a86') + heart(24, 31, '#df6a86') +
      jester('#e24f7b', '#98275a', '#d44974') + heart(13, 23, '#f15882'),
    greedy: () => frame('#f4d1a1', '#ce9559') + jester('#c55935', '#cb922f', '#d8a746') +
      medallion(15, 23, '#eda83d', '#b4652f', '#ffec8d') + diamond(20, 28, '#fff1b3'),
    crafty: () => frame('#c3e0bc', '#74ae8b') + stars('#75aa74') +
      path('M8 32H13V29H24V32H29V35H26V38H11V36H8Z', '#385e57') + masks('#378565', '#cce18f'),
    green: () => frame('#bfdfb3', '#72ac77') + jester('#3d8e60', '#68aa64', '#408664') +
      path('M21 35H24V33H26V29H31V26H33V32H31V36H27V38H23V40H21Z', '#226850') + rect(27, 31, 3, 3, '#a3d781'),
    banner: () => frame('#b3ddd8', '#559eaa') + stars('#6da8b3') + pin('#318889') + diamond(14, 12, gold),
    echo: () => frame('#f6d7a0', '#bc8950') +
      path('M9 8H28V10H30V30H27V33H22V37H19V42H10V39H7V11H9Z', ink) +
      path('M10 10H27V12H28V29H25V31H20V35H17V40H11V38H9V12H10Z', paper) +
      path('M12 14H24V16H12Z M12 20H15V23H12Z M17 20H20V23H17Z M22 20H25V23H22Z M12 27H15V30H12Z M17 27H20V30H17Z M22 27H25V30H22Z', '#b9804b') +
      path('M16 24H18V25H19V23H20V21H22V23H21V25H20V27H18V26H16Z', '#459975'),
    vault: () => frame('#695390', '#9b84be') + stars('#dbc4fd') +
      path('M6 37H29V40H6Z M9 12H14V10H23V12H28V14H31V20H28V15H23V13H14V15H9V20H6V15H9Z', '#e7bd64') +
      path('M16 16H20V19H21V23H19V26H23V27H27V29H30V32H25V31H20V30H17V27H14V29H11V32H7V34H4V31H6V29H10V26H12V23H15V20H14V18H16Z', ink) +
      path('M16 17H19V20H16Z M16 22H19V24H17V27H15V26H13V24H16Z M9 29H12V27H15V29H12V31H9Z M20 27H23V28H26V30H22V29H20Z', '#f1cc79'),
    abstract: () => frame('#27294c', '#777dc5') + stars('#b7a5e7') +
      path('M16 8H20V11H24V14H28V17H31V30H27V34H22V38H18V41H15V37H10V33H6V30H4V17H8V13H12V11H16Z', '#9c92dc') +
      path('M17 10H20V13H24V16H28V19H25V22H21V25H17V22H13V19H9V17H13V14H17Z', '#e6c8ef') +
      path('M17 25H20V28H23V31H27V34H22V37H18V40H16V36H12V33H8V30H12V28H16Z', '#565baa') +
      path('M4 17H8V20H12V23H16V28H12V30H8V33H6V30H4Z M31 17V30H27V33H25V30H22V28H20V25H23V22H27V19H29V17Z', '#7978ce'),
    supernova: () => frame('#331d53', '#9d64b9') + stars('#f8acd8') +
      path('M17 5H19V13H21V16H24V13H27V10H30V13H28V17H25V20H29V21H34V24H28V26H25V28H28V31H30V36H27V33H24V30H21V33H20V42H17V35H15V32H13V35H10V39H7V35H9V31H12V28H8V27H2V24H7V22H11V20H8V17H5V13H8V16H11V18H15V15H17Z', '#e358ae') +
      path('M17 15H20V18H23V21H27V24H23V27H20V31H17V28H14V26H10V23H14V20H17Z', '#fda978') +
      path('M17 20H20V22H22V25H20V27H17V25H15V22H17Z', '#fff4c6'),
    constellation: () => frame('#26345e', '#657cbf') + stars('#b4c5fc') +
      '<path d="M8 28L13 12L25 16L26 34L18 39L8 28L25 16" fill="none" stroke="#7293d1"/>' +
      spark(6, 25, '#e1e5ff') + spark(11, 10, gold) + spark(23, 14, '#e1e5ff') + spark(24, 32, '#e1e5ff') + spark(16, 37, gold),
    stuntman: () => frame('#df8b43', '#a94d34') +
      path('M5 10H10V12H7V15H4V12H5Z M26 8H31V10H29V13H26Z M5 30H8V33H11V37H7V34H4V32H5Z M29 31H32V35H30V39H26V36H29Z', '#ffce62') +
      path('M9 18H12V14H17V12H24V14H28V18H31V31H27V34H13V31H9Z', ink) +
      path('M12 18H15V15H18V14H24V16H27V19H29V29H25V32H14V30H11V21H12Z', '#edcb77') +
      path('M12 22H15V19H26V21H29V26H25V29H15V27H12Z', '#2b4c78') +
      path('M16 20H21V21H18V23H15V22H16Z M13 29H17V31H13Z', '#91c8ec'),
    cardSharp: () => frame('#b18c63', '#674f4b') + stars('#f3d49a') +
      playingTile(7, 13, '#276e80', diamond(3, 6, '#276e80'), -24) +
      playingTile(16, 13, '#b93757', diamond(3, 6, '#b93757'), 24) +
      playingTile(12, 12, '#222c4a', diamond(3, 6, '#222c4a')) +
      path('M8 32H12V34H15V35H20V33H24V34H26V38H23V41H15V40H11V38H8Z', '#f4cc9e'),
    blackboard: () => frame('#6b5144', '#8f735b') + rect(5, 10, 26, 28, '#d6b182') + rect(7, 12, 22, 23, '#22444a') +
      path('M9 17H11V19H13V17H15V19H14V21H15V23H13V21H11V23H9V21H10V19H9Z M18 16H25V18H23V20H25V25H18V23H23V22H20V20H22V18H18Z', '#f4e8c7') +
      path('M10 29H26V30H10Z M11 33H16V34H11Z M23 34H28V36H23Z', '#94c2b0'),
    duo: () => frame('#c884a5', '#986580') +
      playingTile(7, 13, '#aa315e', heart(1, 5, '#aa315e'), -13) + playingTile(17, 15, '#aa315e', heart(1, 5, '#aa315e'), 13) + spark(17, 7, gold),
    trio: () => frame('#7891d1', '#4e63a8') +
      playingTile(4, 16, '#344b88', diamond(3, 6, '#344b88'), -19) + playingTile(19, 16, '#a83b65', diamond(3, 6, '#a83b65'), 19) +
      playingTile(12, 12, '#344b88', diamond(3, 6, '#344b88')) + spark(16, 6, gold),
    tribe: () => frame('#82bba2', '#467c77') +
      path('M5 8H7V40H5Z M29 8H31V40H29Z M10 10H26V12H10Z M10 36H26V38H10Z', '#bbddab') + masks('#54755e', gold) + diamond(15, 7, '#f5d778'),
    order: () => frame('#d7aa69', '#a67250') +
      path('M4 15H7V13H11V11H15V9H21V11H25V13H29V15H32V18H29V35H32V38H35V41H1V38H4V35H7V18H4Z', '#5a4851') +
      path('M8 15H12V13H16V11H20V13H24V15H28V17H8Z M7 19H29V21H7Z M9 22H13V35H9Z M16 22H20V35H16Z M23 22H27V35H23Z M6 36H30V38H6Z M4 39H32V40H4Z', '#fbe4ab'),
  };

  const courtColors = {
    hearts: ['#b92f4d', '#4272ae'], diamonds: ['#cb4a36', '#308f9b'],
    spades: ['#405fa8', '#a84153'], clubs: ['#37856e', '#b87933'],
  };

  function court(rank, suit) {
    if (!Object.hasOwn(courtColors, suit) || ![11, 12, 13].includes(rank)) return '';
    const [main, trim] = courtColors[suit];
    const hat = rank === 13 ? 'M11 7V3H13V5H16V2H18V5H21V3H23V8H11Z' :
      rank === 12 ? 'M11 8V6H13V4H16V2H18V4H21V6H23V8H11Z' : 'M10 7H13V5H17V4H20V3H22V1H24V4H22V6H21V8H10Z';
    const staff = rank === 13 ? 'M27 4H29V18H32V20H29V24H27V20H24V18H27Z' :
      rank === 12 ? 'M27 5H29V7H31V11H29V24H27V11H25V7H27Z' : 'M28 4H30V15H29V18H31V20H28V24H26V20H23V18H26V15H28Z';
    const hair = rank === 12 ? path('M10 9H13V12H11V15H12V17H10V20H7V16H9V12H10Z M20 9H23V12H24V15H25V19H22V16H21V12H20Z', '#a25d46') : '';
    const beard = rank === 13 ? path('M12 14H15V15H20V13H22V16H21V19H19V21H16V20H14V18H12Z', ink) : '';
    const attire = rank === 12 ? path('M10 19H12V21H14V22H22V19H24V23H21V24H12V23H10Z', trim) :
      path('M7 22H10V24H7Z M23 22H25V24H23Z M15 21H18V23H15Z', trim);
    const half = path('M7 12H9V8H12V5H21V7H23V10H25V14H23V16H22V18H25V20H27V24H5V20H7V18H11V16H9V14H7Z', ink) +
      path(hat, rank === 11 ? main : gold) + path('M10 8H23V9H10Z M13 6H15V8H13Z M17 5H19V7H17Z M21 6H22V8H21Z', trim) + hair +
      path('M12 10H20V11H22V14H20V16H19V18H15V17H13V15H12V13H11V11H12Z', '#f7d7ac') +
      path('M13 11H16V12H14V13H13Z M18 11H21V13H20V12H18Z M16 15H20V16H17V17H16Z', ink) +
      path('M10 17H13V18H15V19H19V18H22V17H24V20H27V24H6V21H8V19H10Z', main) +
      path('M10 18H12V20H14V21H20V20H22V18H24V21H22V23H13V22H11V21H9Z', paper) +
      attire + beard +
      path(staff, ink) + path('M27 7H29V17H28V21H27Z M25 18H31V19H25Z', gold);
    return svg(rect(1, 1, 34, 46, ink) + rect(3, 3, 30, 42, '#f6eedf') +
      rect(4, 4, 28, 40, trim) + rect(5, 5, 26, 38, '#fff4df') + half + group(half, 'rotate(180 18 24)'));
  }

  const planetColors = {
    high: ['#a090c4', '#625489', '#e0c5e4'], pair: ['#bbad9f', '#7c7180', '#e7d9b5'],
    twoPair: ['#6ed2db', '#387faf', '#b9f4ee'], trips: ['#f1aa62', '#a36767', '#ffe2a1'],
    straight: ['#d8b887', '#aa775f', '#fff1ba'], flush: ['#d98c61', '#a74f5f', '#ffd5a0'],
    fullHouse: ['#4382c1', '#344d9c', '#7acc9f'], quads: ['#dc795b', '#983954', '#ffc282'],
    straightFlush: ['#6388dc', '#3e4caa', '#a3baf0'],
  };

  function planet(id) {
    if (!Object.hasOwn(planetColors, id)) return '';
    const [body, shadow, light] = planetColors[id];
    const ringBack = id === 'straight' ? path('M3 24H6V20H11V17H15V14H24V12H31V14H34V18H31V20H28V18H31V15H25V16H20V18H15V20H10V23H6V26H3Z', light) : '';
    const ringFront = id === 'straight' ? path('M3 24H6V26H12V24H18V22H24V20H28V18H31V21H28V23H23V25H18V27H11V29H5V27H3Z', light) : '';
    return svg(stars('#b9bcf0') + ringBack + medallion(9, 11, body, shadow, light) +
      path(id === 'fullHouse' ? 'M14 13H19V15H22V17H20V20H17V22H13V19H11V17H14Z M23 22H25V25H22V27H19V25H20V23H23Z' :
        'M12 18H17V16H24V18H22V20H15V21H11V19H12Z M11 22H16V23H21V21H26V23H22V25H15V24H11Z', shadow) +
      ringFront, 36, 40);
  }

  function joker(id) {
    const draw = Object.hasOwn(jokerDrawings, id) && jokerDrawings[id];
    return draw ? svg(draw()) : '';
  }

  const suitPaths = {
    spades: 'M7 1H9V3H11V5H13V7H15V11H13V13H10V12H9V14H11V15H5V14H7V12H6V13H3V11H1V7H3V5H5V3H7Z',
    hearts: 'M2 2H6V3H7V5H9V3H10V2H14V3H16V8H15V10H13V12H11V14H9V15H7V14H5V12H3V10H1V8H0V3H2Z',
    clubs: 'M6 1H10V2H12V6H11V7H14V8H16V12H14V13H11V12H9V14H11V15H5V14H7V12H5V13H2V12H0V8H2V7H5V6H4V2H6Z',
    diamonds: 'M7 0H9V2H11V4H13V6H15V10H13V12H11V14H9V16H7V14H5V12H3V10H1V6H3V4H5V2H7Z',
  };
  const suitIcons = Object.fromEntries(Object.entries(suitPaths).map(([name, outline]) =>
    [name, svg(path(outline, 'currentColor'), 16, 16)]));

  function suit(id) {
    return Object.hasOwn(suitIcons, id) ? suitIcons[id] : '';
  }

  const art = Object.freeze({ joker, court, planet, suit });
  globalThis.JokerArt = art;
})();
