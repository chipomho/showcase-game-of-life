'use strict';

// The API may be on another origin; config.json says where.
const page = {
  api: '',
  simulation: null,
  stream: null,
};

const $ = (id) => document.getElementById(id);

async function start() {
  try {
    const config = await (await fetch('config.json')).json();
    page.api = config.apiBase || '';
    const patterns = await (await fetch(`${page.api}/v1/patterns`)).json();
    for (const pattern of patterns) {
      $('pattern').add(new Option(`${pattern.name} (${pattern.width} x ${pattern.height})`, pattern.id));
    }
    $('pattern').add(new Option('Your own...', 'custom'));
    $('pattern').value = 'gosper-glider-gun';
    await newBoard();
  } catch (error) {
    say(`Could not reach the API: ${error.message}`);
  }
}

async function newBoard(event) {
  event?.preventDefault();
  stop();
  if (page.simulation) {
    fetch(`${page.api}/v1/simulations/${page.simulation.id}`, { method: 'DELETE' }).catch(() => {});
  }
  const query = new URLSearchParams({ width: $('width').value, height: $('height').value, topology: $('topology').value });
  const options = { method: 'POST' };
  if ($('pattern').value === 'custom') {
    options.body = $('custom').value;
    options.headers = { 'Content-Type': 'text/plain' };
  } else {
    query.set('pattern', $('pattern').value);
  }
  const response = await fetch(`${page.api}/v1/simulations?${query}`, options);
  const body = await response.json();
  if (!response.ok) {
    page.simulation = null;
    $('toggle').disabled = true;
    say(body.detail);
    return;
  }
  say('');
  page.simulation = body;
  $('toggle').disabled = false;
  draw(body);
}

function run() {
  const speed = $('speed').value;
  page.stream = new EventSource(`${page.api}/v1/simulations/${page.simulation.id}/events?speed=${speed}`);
  page.stream.addEventListener('generation', (event) => draw(JSON.parse(event.data)));
  page.stream.addEventListener('end', () => {
    stop();
    say('This board is being run from another window.');
  });
  page.stream.onerror = () => {
    stop();
    say('The stream stopped. Start again, or make a new board.');
  };
  $('toggle').textContent = 'Stop';
}

function stop() {
  page.stream?.close();
  page.stream = null;
  $('toggle').textContent = 'Start';
}

// Each row is ceil(width / 8) bytes; cell x is bit x % 8 of byte x / 8.
function draw(frame) {
  const { width, height } = page.simulation;
  const canvas = $('board');
  const size = Math.max(1, Math.floor(Math.min(1060 / width, 640 / height)));
  canvas.width = width * size;
  canvas.height = height * size;
  const context = canvas.getContext('2d');
  context.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--cell');
  const bytes = Uint8Array.from(atob(frame.cells), (c) => c.charCodeAt(0));
  const bytesPerRow = Math.ceil(width / 8);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (bytes[y * bytesPerRow + (x >> 3)] >> (x & 7) & 1) {
        context.fillRect(x * size, y * size, size, size);
      }
    }
  }
  $('generation').textContent = frame.generation;
  $('population').textContent = frame.population;
}

function say(message) {
  $('message').textContent = message;
}

$('setup').addEventListener('submit', newBoard);
$('pattern').addEventListener('change', () => {
  document.querySelector('.custom').hidden = $('pattern').value !== 'custom';
});
$('toggle').addEventListener('click', () => (page.stream ? stop() : run()));
$('speed').addEventListener('input', () => {
  $('speed-value').textContent = $('speed').value;
  if (page.stream) {
    stop();
    run();
  }
});

start();
