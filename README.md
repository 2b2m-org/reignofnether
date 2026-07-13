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
/rts-bot add <name> <villagers|monsters|piglins> [easy|medium|hard] [x y z]
/rts-bot list
/rts-bot difficulty <name> <easy|medium|hard>
/rts-bot remove <name>
```

The difficulty defaults to `medium`. Either the difficulty, the position, or both may be omitted; without a position, the bot starts near the command source. Difficulty and home position are saved across server restarts.

All difficulties use identical starting resources, costs, gathering rates, build and production times, unit stats, and map information. They differ only in decisions:

| Difficulty | Economy | Supply planning | Attack behavior |
| --- | --- | --- | --- |
| Easy | 4 workers, food-heavy split | 1 unit ahead | attacks at 4 units; smaller 8-unit army |
| Medium | 5 workers, balanced split | 2 units ahead | attacks at 8 units; regroups below 4; balanced 12-unit army |
| Hard | 9 workers, construction-aware split | 3 units ahead | masses 16 units, prioritizes strategic targets, retreats below 8 |

Bots use normal resource costs and the same gathering, construction, production, and combat command paths as human players. With fog of war disabled, the map is visible to every player and bot. With fog enabled, bots remember only structures discovered by their own units and buildings and scout when they have no known target.

For development tests only, `/rts-bot test-speed <name> true` enables the existing build, production, and gathering speed cheats. This is separate from difficulty and is visibly marked in `/rts-bot list`; set it back to `false` for normal match timing. Tutorial NPC bots remain separately scripted.

## License

GNU General Public License v3.0

See [LICENSE.txt](LICENSE.txt) for the full text.

## Other stuff

Please join the discord if you want to discuss the mod: https://discord.gg/erBen9CzbD

I also look at bug reports there much more than here on GitHub unless you have an actual PR fix.
