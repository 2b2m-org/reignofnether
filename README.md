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
/rts-bot personality <name> <steady|rusher|turtle>
/rts-bot wave-survival <beginner|easy|medium|hard|extreme>
/rts-bot remove <name>
```

On maps with configured start positions, bots can join the normal match lobby:

```text
/rts-bot lobby add <name> <villagers|monsters|piglins> <easy|medium|hard> <steady|rusher|turtle> <x y z>
/rts-bot lobby list
/rts-bot lobby remove <name>
```

Lobby bots ready automatically and use the seat's configured team.

Add one or more active bots and then use `rts-bot wave-survival` to start a bot-only or mixed human-and-bot run. All participants become allies and the selected Wave Survival difficulty is synchronized to clients. Bot-only runs lock out new RTS players; mixed runs do not automatically lock out late human allies. After the run, use `/rts-reset` or `/rts-hard-reset` before starting another match.

The difficulty defaults to `medium` and the personality defaults to `steady`. Either the difficulty, the position, or both may be omitted; without a position, the bot starts near the command source. Difficulty, personality, and home position are saved across server restarts.

All difficulties use identical starting resources, costs, gathering rates, build and production times, unit stats, and fog-of-war visibility rules. They differ only in decisions:

Army budgets use the mod's population costs rather than unit head counts, giving every faction the same military supply budget.

| Difficulty | Economy | Supply planning | Attack behavior |
| --- | --- | --- | --- |
| Easy | 4 workers, food-heavy split | 1 unit ahead | attacks at 12 population; smaller 24-population army |
| Medium | 5 workers, balanced split | 2 units ahead | attacks at 24 population; may regroup below 12 when pressured; balanced 36-population army |
| Hard | 9 workers, construction-aware split | 3 units ahead | queues at least an 8/12/16-population opening for Rusher/Steady/Turtle before resuming worker production, with unit population able to round it upward; attacks at 45 population or sooner with a clear observed advantage; 48-population army; may regroup below 24 |

Personalities change strategy without resource grants, stat modifiers, extra vision, or faster construction or production. Final matchup tuning and full difficulty-hierarchy validation are deferred until AI feature freeze:

| Personality | Economy and timing | Army style | Target and defense style |
| --- | --- | --- | --- |
| Rusher | 1 fewer worker; attacks 4 population earlier | targets roughly 25% ranged population when both types are affordable; 4-population-smaller army; retreat threshold reduced by 4, with a minimum of 1 | attacks the nearest known structure; while attack-ready, recalls for critical Classic threats or its own threatened Wave base |
| Steady | baseline economy and timing | targets roughly 40% ranged population when both types are affordable; baseline army size | prioritizes the enemy capitol, then production; uses the same attack-ready recall rule as Rusher |
| Turtle | 1 extra worker; attacks 4 population later | targets roughly 60% ranged population when both types are affordable; 4-population-larger army; regroups sooner when pressured | prioritizes production, then the capitol; while attack-ready, also recalls for remembered allied threats |

Bots use normal resource costs and the same gathering, construction, production, and combat command paths as human players. They defend recently damaged buildings, replace fallen builders, and fall back to nearby food sources while their farm regrows. With fog of war disabled, the map is visible to every player and bot. With fog enabled, bots remember structures discovered by their own buildings or their team's units, plus structures revealed by normal game rules. With at least two military units, the lowest-population unit is reserved as a scout; it explores unseen chunks and receives Move orders that clear incidental combat instead of stopping to fight.

## License

GNU General Public License v3.0

See [LICENSE.txt](LICENSE.txt) for the full text.

## Other stuff

Please join the discord if you want to discuss the mod: https://discord.gg/erBen9CzbD

I also look at bug reports there much more than here on GitHub unless you have an actual PR fix.
