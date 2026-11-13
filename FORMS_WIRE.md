# minuit wire format

The bytes minuit sends between client and server. Read this to write your own client or server.
The Java codec (`dev.dryaides.minuit.wire`) is internal and may change in any release; these
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
features add a capability; the protocol number changes only in a major release. 2.x defines `hello/1` and
`form/1` (the node kinds below and the three form kinds). `form.shop` adds the `product`, `balance`
and `cart` kinds; a client without it draws their fallback text.

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
| `product` | label = name, 16 price `varlong`, 17 `image`, 18 sale price `varlong`, 19 stock `svarint` (-1 no limit), 20 category `str` (64), 21 quantity `varint` (0 to 999, at most the stock) on a cart line | a press (card), quantity `varint` (line) |
| `balance` | label = currency name, 16 amount `varlong`, 17 currency `image` | |
| `cart` | label = checkout button, 16 purchases `varint` (checkouts accepted; the client shows a purchase toast when it grows), 17 refusals `varint` (checkouts refused; the client shows field 18 when it grows), 18 reason `text` (why the last checkout was refused, empty once the cart moves on); children are `product` lines with a quantity | a press with the confirmed total (checkout, after the client's confirm dialog) |

A `grid` whose children are `product` cards lays them out as cards, with category tabs (All first) when any card has a category.

`varlong` is a varint over 64 bits, at most 9 bytes, never negative. A press on a product card asks
the server to add one to the cart; the server then patches the cart's children. The server, not the
client, checks stock and balance on checkout, then patches the balance, the cards' price and stock
and an empty cart. The checkout press carries the total the confirm dialog showed; when the
catalogue's total for the same lines differs (a sale ended, a price moved), the server charges
nothing, sends the lines and cards at their new prices and raises refusals with the reason. A mod
whose payment fails gets the same refused path: no purchase is counted. Product card ids are at
most 27 characters so that the line id `line.<id>` fits; the builder throws above that. In the
vanilla dialog fallback the checkout opens a `minecraft:confirmation` dialog (lines, total, balance
before and after) whose yes button sends the press with that total.

The root is a `column`. If its last child is a `row` with id `footer`, the client pins that row
below the scrolling body.

## Input

Slider `volume` moved to 50, session 1, input 3:

```
01 03 00 06 "volume" 64
```

Each message is `varint session`, `varint seq` (counts up per session), `byte op`, then:

- `0 change`: `str nodeId` (32), the value in the kind's "sent back" type.
- `1 press`: `str nodeId` (32), then on a cart's checkout `varlong confirmed`, the total the player confirmed (absent on any other press).
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
