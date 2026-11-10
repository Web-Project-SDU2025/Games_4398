(() => {
  'use strict';

  const canvas = document.getElementById('felt');
  if (!canvas) return;

  const palette = {
    blue: [0x0d172c, 0x20365b, 0x42658b, 0x615992],
    violet: [0x171321, 0x342443, 0x644b7d, 0x99759e],
    ember: [0x1c151d, 0x47272b, 0x8b4b40, 0xb57a53],
  };
  const colors = Object.fromEntries(Object.entries(palette).map(([name, tones]) => [name,
    tones.flatMap((tone) => [(tone >> 16) / 255, ((tone >> 8) & 255) / 255, (tone & 255) / 255]),
  ]));

  const vertex = `
    attribute vec2 point;
    void main() { gl_Position = vec4(point, 0.0, 1.0); }
  `;

  const fragment = `
    precision highp float;
    uniform vec2 size;
    uniform float time;
    uniform vec3 tones[4];

    float hash(vec2 p) {
      vec3 q = fract(vec3(p.xyx) * 0.1031);
      q += dot(q, q.yzx + 33.33);
      return fract((q.x + q.y) * q.z);
    }

    float noise(vec2 p) {
      vec2 i = floor(p);
      vec2 f = fract(p);
      vec2 u = f * f * (3.0 - 2.0 * f);
      return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
                 mix(hash(i + vec2(0.0, 1.0)), hash(i + 1.0), u.x), u.y);
    }

    float cloud(vec2 p) {
      const mat2 turn = mat2(1.54, -1.13, 1.13, 1.54);
      float value = noise(p) * 0.60;
      p = turn * p + vec2(7.0, 4.0);
      value += noise(p) * 0.27;
      p = turn * p + vec2(4.0, -5.0);
      return value + noise(p) * 0.13;
    }

    vec2 curl(vec2 p, vec2 center, float strength, float scale) {
      vec2 offset = p - center;
      float angle = strength / (1.0 + dot(offset, offset) * scale);
      float c = cos(angle);
      float s = sin(angle);
      return center + mat2(c, -s, s, c) * offset;
    }

    void main() {
      vec2 uv = gl_FragCoord.xy / size;
      vec2 p = (gl_FragCoord.xy - size * 0.5) / size.y * 3.2;
      float drift = time * 0.13;
      p = curl(p, vec2(0.9, 0.27), 2.55 + 0.24 * sin(drift), 1.3);
      p = curl(p, vec2(-0.85, -0.8), -3.15 + 0.21 * sin(drift + 1.0), 1.9);
      p += vec2(0.08 * sin(drift), 0.06 * cos(drift * 0.8));

      vec2 flow = vec2(cloud(p + vec2(0.7, 3.2)), cloud(p + vec2(5.5, -1.7))) - 0.5;
      vec2 ribbon = p + flow * 3.5;
      float depth = cloud(ribbon + vec2(2.9, 0.1));
      float contour = 0.5 + 0.5 * cos(depth * 28.0 + flow.y * 2.0);
      float streak = pow(0.5 + 0.5 * cos(depth * 55.0 - flow.x), 8.0);
      float light = smoothstep(0.27, 0.74, depth);

      vec3 ink = mix(tones[0], tones[1], 0.29 + light * 0.62);
      ink = mix(ink, tones[2], smoothstep(0.37, 0.75, depth) * (0.29 + contour * 0.45));
      ink = mix(ink, tones[3], streak * smoothstep(0.07, 0.36, length(flow)) * 0.21);

      float vignette = smoothstep(0.28, 0.77, length((uv - vec2(0.56, 0.53)) * vec2(0.92, 1.0)));
      float scan = 1.0 - mod(gl_FragCoord.y, 2.0) * 0.024;
      gl_FragColor = vec4(ink * (1.0 - vignette * 0.24) * scan, 1.0);
    }
  `;

  function compile(gl, type, source) {
    const shader = gl.createShader(type);
    if (!shader) return null;
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (gl.getShaderParameter(shader, gl.COMPILE_STATUS)) return shader;
    gl.deleteShader(shader);
    return null;
  }

  function createRenderer() {
    const gl = canvas.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' });
    if (!gl) return null;

    const vs = compile(gl, gl.VERTEX_SHADER, vertex);
    const fs = compile(gl, gl.FRAGMENT_SHADER, fragment);
    const program = gl.createProgram();
    if (!vs || !fs || !program) return null;
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      return null;
    }

    const triangle = gl.createBuffer();
    const point = gl.getAttribLocation(program, 'point');
    gl.useProgram(program);
    gl.bindBuffer(gl.ARRAY_BUFFER, triangle);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(point);
    gl.vertexAttribPointer(point, 2, gl.FLOAT, false, 0, 0);
    const size = gl.getUniformLocation(program, 'size');
    const time = gl.getUniformLocation(program, 'time');
    const tones = gl.getUniformLocation(program, 'tones[0]');

    return (elapsed) => {
      const width = Math.max(1, window.innerWidth);
      const height = Math.max(1, window.innerHeight);
      const scale = Math.min(1, 860 / width, 520 / height);
      const w = Math.max(1, Math.round(width * scale));
      const h = Math.max(1, Math.round(height * scale));
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
      }
      gl.viewport(0, 0, w, h);
      gl.uniform2f(size, w, h);
      gl.uniform1f(time, elapsed);
      gl.uniform3fv(tones, colors[document.body.dataset.felt] || colors.blue);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    };
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let draw = null;
  let frame = 0;
  let last = 0;
  let elapsed = 0;

  function animate(now) {
    if (now - last >= 40) {
      elapsed += Math.min(now - last, 70) / 1000;
      last = now;
      draw(elapsed);
    }
    frame = requestAnimationFrame(animate);
  }

  function refresh() {
    cancelAnimationFrame(frame);
    if (!draw || document.hidden) return;
    last = performance.now();
    draw(elapsed);
    if (!reduceMotion.matches) frame = requestAnimationFrame(animate);
  }

  function restore() {
    try {
      draw = createRenderer();
    } catch {
      draw = null;
    }
    canvas.hidden = !draw;
    refresh();
  }

  canvas.addEventListener('webglcontextlost', (event) => {
    event.preventDefault();
    draw = null;
    canvas.hidden = true;
    cancelAnimationFrame(frame);
  });
  canvas.addEventListener('webglcontextrestored', restore);
  document.addEventListener('visibilitychange', refresh);
  window.addEventListener('resize', refresh);
  reduceMotion.addEventListener('change', refresh);
  new MutationObserver(refresh).observe(document.body, { attributes: true, attributeFilter: ['data-felt'] });
  restore();
})();
