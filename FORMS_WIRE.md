# minuit wire format

The bytes minuit sends between client and server. Read this to write your own client or server.
The Java codec (`dev.dryaides.minuit.wire`) is internal and may change in any 1.x release; these
bytes do not.

## Channels

```
minuit:hello    both ways          handshake
minuit:screen   server to client   open, patch, close
minuit:input    client to server   change, press, closed
```

All three are optional, so vanilla clients and servers still connect. A server sends a form on
`screen` only to a client whose hello lists `form/1`; other players get the form as a vanilla
dialog.

## Primitives

- `varint`: Minecraft's varint. `svarint`: zigzag varint, for signed values. `bool`: one byte.
- `str`: varint byte length, then UTF-8. Each field gives its cap in characters; a reader drops
  the message if the string is longer.
- `Text`: tag 0 is a literal `str`. Tag 1 is a translation key: `str` key, `str` fallback, varint
  argument count, each argument a `Text`. Tag 2 is tag 1 with a style byte after the tag
  (0 plain, 1 muted, 2 accent, 3 warn). At most 8 arguments, 4 levels deep.
- `image`: tag 0 is an item (`str` id), tag 1 a GUI sprite (`str` id, `varint` w, `varint` h).

## Hello

The 1.0 client's hello:

```
01                       protocol 1
05 31 2E 30 2E 30        "1.0.0"
02                       two capabilities
07 68 65 6C 6C 6F 2F 31  "hello/1"
06 66 6F 72 6D 2F 31     "form/1"
```

The client sends it once the server accepts `minuit:hello`, at join or after a later
`minecraft:register`, and answers any server hello with it. A server hello has the same shape;
no build sends one yet. The server keeps each player's hello until they leave: a mod reads it with
`MinuitServer.client(player)`, a Paper plugin with `PaperForms.client(player)`.

Capabilities are named `area/level` or `area.feature`, and a level includes the lower ones. New
features add a capability; the protocol number changes only in 2.0. 1.x defines `hello/1` and
`form/1` (the node kinds below and the three form kinds).

## Screen

A custom screen titled "Shop" with a toggle and a button:

```
00 01                          open, session 1
0C "example:shop"              screen id
02                             custom
00 04 "Shop"                   title, a literal
01                             normal
06 "column" 00 37              root column, static, 55 bytes of fields
  02 35 02                     children, 53 bytes, two nodes
    06 "toggle" 06 "sounds" 0D
      06 08 00 06 "Sounds"     label
      10 01 01                 value true
    06 "button" 04 "save" 0B
      06 06 00 04 "Save"       label
      10 01 00                 role primary
```

Each message starts with `varint op`; an unknown op drops it.

- `0 open`: `varint session`, `str screenId` (64), `byte kind` (0 notice, 1 menu, 2 custom),
  `Text title`, `byte size` (0 narrow, 1 normal, 2 wide), the root `Node`.
- `1 patch`: `varint session`, `varint ack` (last input `seq` applied), varint count of sets, each
  `str nodeId` (32), `varint tag`, `varint length`, bytes. A `children` set replaces that subtree.
- `2 close`: `varint session`, `byte reason` (0 player, 1 invalid, 2 replaced, 3 server).

A `Node` is `str kind` (32), `str id` (empty when static, else `[a-z0-9_.-]{1,32}`), `varint
length`, then fields: `varint tag`, `varint length`, bytes. Writers send tags in ascending order;
readers accept any order but reject a repeated tag. Clients skip unknown fields, and draw an unknown
kind as its fallback text plus its children.

| Tag | Field, every kind |
|---|---|
| 1 | fallback `Text` |
| 2 | children: varint count, each `Node` |
| 3 | visible `bool` |
| 4 | enabled `bool` |
| 5 | tooltip `Text` |
| 6 | label `Text` |
| 7 | weight `varint`, share of a row |

| Kind | Fields from tag 16 | Sent back |
|---|---|---|
| `column`, `row` | 16 gap `varint` | |
| `section` | 16 open `bool` | |
| `tabs` | 16 selected `varint`; children are `page` nodes | page index `varint` |
| `page` | | |
| `text` | 16 lines shown before the cut `varint` | |
| `toggle` | 16 value `bool` | `bool` |
| `slider` | 16 value, 17 min, 18 max, 19 step, all `svarint` | `svarint` |
| `field` | 16 value `str`, 17 placeholder `Text`, 18 maxLength `varint`, 19 charset (0 any, 1 digits, 2 id-safe) | `str` |
| `dropdown` | 16 index `varint`, 17 options: varint count, each `Text` (128 max) | `varint` |
| `button` | 16 role (0 primary, 1 secondary, 2 destructive, 3 hero), 17 `image` | a press |
| `progress` | 16 per mille `svarint`, negative when unknown | |
| `image` | 17 `image` | |
| `grid` | 16 columns `varint`; children are image buttons | |

The root is a `column`. If its last child is a `row` with id `footer`, the client pins that row
below the scrolling body.

## Input

Slider `volume` moved to 50, session 1, input 3:

```
01 03 00 06 "volume" 64
```

Each message is `varint session`, `varint seq` (counts up per session), `byte op`, then:

- `0 change`: `str nodeId` (32), the value in the kind's "sent back" type.
- `1 press`: `str nodeId` (32).
- `2 closed`: `byte reason`, the `close` codes. Clients send 0 to 2.

## Size caps

A reader drops the whole message past any cap (`Limits` in the codec).

| Cap | Value |
|---|---|
| `minuit:screen` message | 256 KiB |
| `minuit:input` message | 4 KiB |
| nodes in an open or one `children` set | 512 |
| node depth (root is 1, a patched `children` set starts at 2) | 8 |
| `tabs` pages | 12 |
| characters per `Text` string | 1024 |
| sprite width or height | 1024 |
| `field` value and `maxLength` | 256 |
| image ids | 256 characters |
| capabilities in a hello | 64, each 64 characters |
