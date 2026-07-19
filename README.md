# CML Item Physic

A [BBS CML Edition](https://modrinth.com/mod/bbs-cml-edition) addon that adds
realistic, keyframable item physics to item forms.

## Features

- **Physic Item form** — an item form with a keyframable `Physics` channel
  (`0 → 1`) that plays a real, baked rigid-body drop simulation (fall, tumble,
  physically-decaying bounces, friction). Drop height, spins, bounce and overlap
  are configurable.
- **Item Rain form** — spawns a configurable number of items that fall using the
  same simulation. Items are picked from a user-defined pool or from every game
  item, and fall at **randomly staggered times** so they rain down progressively
  (never all at once). Two modes:
  - **Heap** — items land tightly clustered and pile up.
  - **Rain** — items scatter over a configurable radius.

Both forms live in the form palette's **Miscellaneous** category.

## Supported versions

The same source builds for two Minecraft versions, each against the matching
BBS CML Edition `2.0-beta-1` jar:

| Minecraft | BBS CML Edition          |
|-----------|--------------------------|
| 1.21.4    | `2.0-beta-1-1.21.4`      |
| 1.21.1    | `2.0-beta-1-1.21.1`      |

## Building

This project depends on the BBS CML Edition mod jars, which are **not** committed
to the repository. Download them from
[Modrinth](https://modrinth.com/mod/bbs-cml-edition) and place them in `libs/`:

```
libs/bbs-cml-edition-2.0-beta-1-1.21.4.jar
libs/bbs-cml-edition-2.0-beta-1-1.21.1.jar
```

Then build with Gradle, selecting the target with `-Pmc`:

```bash
# 1.21.4 (default)
./gradlew build -Pmc=1.21.4

# 1.21.1
./gradlew build -Pmc=1.21.1
```

The output jars are written to `build/libs/`:

- `cml-item-physic-<version>+mc1.21.4.jar`
- `cml-item-physic-<version>+mc1.21.1.jar`

Requires JDK 21.

## License

MIT
