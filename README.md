# Game of Life

Conway's Game of Life with a bit-packed engine, an HTTP API that streams generations as
Server-Sent Events, and a small canvas page to watch them on. It replaces an earlier Swing
version, and it is plain JDK with no runtime dependencies.

A live cell with two or three live neighbours survives, a dead cell with exactly three comes
alive, and every other cell dies or stays dead. The board's edges are either hard, with nothing
beyond them, or wrap round to the opposite side, making the board a torus.

## Running it

You need Docker or Podman with Compose. Add these to your hosts file:

```
127.0.0.1 game-of-life.showcases.chipomho.local
127.0.0.1 game-of-life-api.showcases.chipomho.local
```

and run `docker compose up --build -d`. The first start creates a local CA in `.certs/` and a
wildcard certificate for `*.showcases.chipomho.local` that Traefik serves on 443. Trust
`.certs/ca.crt` in your browser, then open https://game-of-life.showcases.chipomho.local, pick a
pattern and press Start. `SHOWCASES_CA_DIR` (an existing directory) shares one CA between the
showcase projects, and `SHOWCASES_HTTP_PORT`/`SHOWCASES_HTTPS_PORT` move Traefik off 80 and 443.

Both hostnames reach the same container: the page on one and the API on the other, so the page's
calls are cross-origin as they would be deployed apart. The page reads the API's address from
`/config.json` (`API_BASE_URL`), and the API allows the page's origin (`ALLOWED_ORIGINS`).
Without containers, `./gradlew run` serves both on http://localhost:8080.

## The API

| Endpoint                                                  | Does                                         |
|-----------------------------------------------------------|----------------------------------------------|
| `GET /v1/patterns`                                        | Lists the bundled patterns                   |
| `POST /v1/simulations?pattern=glider&width=120&height=80&topology=TOROIDAL` | Starts a simulation; or send a pattern as the body |
| `GET /v1/simulations/{id}`                                | The current generation                       |
| `GET /v1/simulations/{id}/events?speed=10&generations=…`  | Streams generations, up to 30 a second       |
| `DELETE /v1/simulations/{id}`                             | Removes it                                   |

A pattern sent as the body can be [RLE](https://conwaylife.com/wiki/Run_Length_Encoded), the
format most Life software uses, or the original project's list of `[x, y]` cells. The bundled
ones are the glider, Gosper glider gun, pulsar, R-pentomino and the original project's data.
Each generation's `cells` is the board, base64-encoded, a row at a time with `ceil(width / 8)`
bytes per row and cell x at bit `x % 8` of byte `x / 8`.

```
$ curl --cacert .certs/ca.crt -X POST \
    "https://game-of-life-api.showcases.chipomho.local/v1/simulations?pattern=glider&width=8&height=6"
{"id":"c10ce212a5d060aaa31a1fb4","width":8,"height":6,"topology":"TOROIDAL","generation":0,"population":5,"cells":"AAgQHAAA"}

$ curl --cacert .certs/ca.crt -N \
    "https://game-of-life-api.showcases.chipomho.local/v1/simulations/{id}/events?generations=4"
event: generation
data: {"generation":0,"population":5,"cells":"AAgQHAAA"}
...
event: generation
data: {"generation":4,"population":5,"cells":"AAAQIDgA"}

event: end
data: {"generation":4,"reason":"limit"}
```

Without `generations` a stream runs until the client leaves, and a new one carries on from where
it stopped. A second stream takes over from the first, which gets an `end` event, so reopening it
to change speed never runs the game twice. Idle simulations go after 15 minutes. (Windows' own
curl also needs `--ssl-no-revoke`, because a local CA has no revocation list.)

## Design notes

Each row is a `long[]`, one bit per cell. The next generation is worked out 64 cells at a time:
the eight neighbours are eight shifted copies of the rows above, beside and below, with bits
carried across word boundaries and, on a torus, round the edges, and they are added with full and
half adders into a count per cell. Edges are a sealed `Topology`, `Bounded` or `Toroidal`, that
the engine switches on.

Generations are immutable: each step builds a new board, so the one being streamed can be handed
to another thread without copying or locking. On a busy laptop a 1,000 x 1,000 step takes about
0.7 ms on one thread, about the same as base64-encoding it, and a small fraction of the gap
between frames at 30 a second, so the engine stays single-threaded. The server is the JDK's
`HttpServer` with a virtual thread per request, which suits streams that stay open while someone
watches.

## Tests

`./gradlew check` fails the build unless line and branch coverage are both 100%. The engine is
checked against a deliberately plain implementation, a grid of booleans with a loop over each
cell's neighbours, on 130 random boards of awkward sizes (1 wide, either side of 8, 64 and 128)
with both topologies for 25 generations; breaking the edge wrap or the carry between words fails
dozens of them. Known shapes are checked too: blinker, block, a glider crossing a torus and coming
home, pulsar, and the Gosper gun's glider every 30 generations. The API tests run the real server
and compare responses and streamed events byte for byte with the engine's output.
