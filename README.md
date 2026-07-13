# Reign of Nether: RTS in Minecraft

Inspired by classic real-time strategy games of the early 2000s, including StarCraft, Warcraft, and Age of Empires, Reign of Nether transforms Minecraft into an RTS using the assets and models from the vanilla game.

Reign of Nether does not imitate any one of those games exactly. Its mechanics are built around Minecraft: a building's health is proportional to its placed blocks, and units are based on vanilla mobs such as Illagers, Creepers, and Piglins.

## Soft roadmap

These plans are not set in stone but are roughly what I plan to look at next in order of priority (last updated 28 June 2026):

1. Pathfinding improvements
2. Hero equippable items
3. Further API and Scenario improvement
4. Fog of war rework
5. New units for each faction
6. Third set of heroes for each faction

## Releases

Published releases are available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/reign-of-nether-rts-in-minecraft) and [Modrinth](https://modrinth.com/mod/reign-of-nether-rts).

## Minecraft 1.21.1 development build

This branch targets Minecraft 1.21.1 and NeoForge 21.1.230 or newer. Building and running it requires Java 21.

Build the mod with:

```sh
./gradlew clean build
```

The jar is written to `build/libs/reignofnether-1.3.8a-1.21.1.jar`. Copy that exact jar into the `mods` directory of every client and dedicated server, then launch them with NeoForge 21.1.230 or newer.

Published Minecraft 1.20.1 versions still use their matching Forge release. Mod versions 1.1.3 and earlier use Forge 1.19.2.

## Basic AI opponents

Operators can add AI-controlled RTS players from the server console or in-game chat:

```text
/rts-bot add <name> <villagers|monsters|piglins> [x y z]
/rts-bot list
/rts-bot remove <name>
```

If the position is omitted, the bot starts near the command source. The bot uses normal resource costs and the same gathering, construction, production, and combat command paths as a human player. It builds a small economy, trains a mixed basic army, and attacks enemy structures after reaching five military units.

For faster development matches, `/rts-bot speed <name> true` enables the existing accelerated build, production, and gathering timings for that bot. Set it back to `false` for normal match timing. Bot ownership and home positions are saved across server restarts; tutorial NPC bots remain separately scripted.

## License

GNU General Public License v3.0

See [LICENSE.txt](LICENSE.txt) for the full text.

## Other stuff

Please join the discord if you want to discuss the mod: https://discord.gg/erBen9CzbD

I also look at bug reports there much more than here on GitHub unless you have an actual PR fix.
