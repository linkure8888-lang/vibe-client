# Vibe Client — Plugin API (apiVersion 1)

Plugins extend the client without touching its code: they can draw HUD lines,
keep their own config, read/write a private data folder, and make HTTP requests
through a daily budget.

## Where plugins live

```
<game>/plugins/
    my-plugin/                <- folder plugin
        vibe-plugin.json      <- manifest (required)
        plugin.jar            <- code (optional)
        data/                 <- private files for fs.read / fs.write
    other-plugin.jar          <- single-jar plugin (manifest at the jar root)
```

To disable a plugin without deleting it, press its state in the **Plugins** tab
(folder plugins get `disabled.marker`, jar plugins get `<name>.disabled`).

## Manifest

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "author": "you",
  "description": "What it does",
  "apiVersion": 1,
  "mainClass": "com.example.MyPlugin",
  "permissions": ["hud", "config"],
  "requestsPerDay": 300
}
```

| Field | Required | Notes |
|---|---|---|
| `id` | yes | `[a-z0-9][a-z0-9_-]{0,31}`, also the folder name |
| `name` | no | display name, defaults to `id` |
| `version` | no | free text, shown in the Plugins tab |
| `author` | no | free text |
| `description` | no | free text |
| `apiVersion` | no | defaults to `1`; anything else shows as UNSUPPORTED |
| `mainClass` | no | omit for a declarative (JSON-only) plugin |
| `permissions` | no | unknown entries are ignored and logged |
| `requestsPerDay` | no | defaults to **300**, capped at 300 |
| `lines` | no | declarative HUD lines (requires `hud`) |

### Declarative lines

```json
"lines": [
  { "id": "hi", "text": "Hello!", "x": 4, "y": 120, "color": -1 }
]
```

`color` is an ARGB int (`-1` = white). No code needed — a plugin folder with
just a manifest works. See `examples/hello-world/`.

## Permissions

| Permission | Grants |
|---|---|
| `hud` | `hud.addLine`, `hud.setLine`, `hud.removeLine`, manifest `lines` |
| `config` | `config.get`, `config.set` (own JSON file) |
| `network` | `net.get` (spends the request budget) |
| `filesystem` | `fs.read`, `fs.write` inside `plugins/<id>/data/` |

Anything not listed is denied with `permission_denied`. Plugins never get the
Minecraft client object, a class loader, or arbitrary file access.

## Tool calls

One shape in, one shape out — same idea as a tool-calling API:

```kotlin
val args = JsonObject().apply { addProperty("text", "Hi") }
val result = host.call("hud.addLine", args)
// result.success, result.result, result.error, result.message
```

| Tool | Args | Result |
|---|---|---|
| `hud.addLine` | `id`, `text`, `x?`, `y?`, `color?` | `{ id }` |
| `hud.setLine` | `id`, `text` | — |
| `hud.removeLine` | `id` | — |
| `config.get` | `key` | stored JSON value or null |
| `config.set` | `key`, `value` | — |
| `net.get` | `url` (http/https) | `{ body, remaining }` |
| `fs.read` | `path` | `{ content }` |
| `fs.write` | `path`, `content` | — |
| `log.info` | `message` | — |

Errors: `unknown_tool`, `bad_arguments`, `permission_denied`, `budget_exceeded`,
`network_error`, `not_found`, `invalid_path`, `too_large`, `internal_error`.

## Request budget

`net.get` is the only tool that spends budget: **300 requests per plugin per
UTC day**, stored in `config/plugins/<id>.usage.json`. The day is compared as a
date string, so it resets on its own with no timers. When the budget is gone the
call fails with `budget_exceeded` and no network request is made.

## Lifecycle

`onLoad` → `onEnable` → (`onDisable` → `onEnable`)* → `onUnload`

A callback that throws disables the plugin and is written to the log; the game
keeps running. HUD lines of a disabled or broken plugin disappear immediately.

## Writing a code plugin

Implement `com.vibe.client.plugin.Plugin`, compile against the client jar, put
the classes into `plugin.jar` next to the manifest. See
`examples/server-ping/` for a complete example.