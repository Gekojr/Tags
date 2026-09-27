# Tags

Paper 26.3 chat nametag plugin for the Geko server.

## Included tags

- OWNER — dark red
- CO-OWNER — light red
- ADMIN — orange
- DONATOR — cyan

## Commands

Console or players with `tags.admin`:

```
/nametag set <player> <tag>
/nametag remove <player>
/nametag get <player>
/nametag list
/nametag reload
```

Examples:

```
/nametag set Gekojr18 owner
/nametag set Player donator
/nametag remove Player
```

## Tebex

No Tebex API key is required.

In a Tebex package, add the server command:

```
nametag set {username} donator
```

For the other tags:

```
nametag set {username} admin
nametag set {username} co-owner
nametag set {username} owner
```

This is intended to be run by the server console when Tebex delivers the package.

## Build

The GitHub Actions workflow builds the plugin with Maven and uploads the resulting `Tags.jar` artifact.
