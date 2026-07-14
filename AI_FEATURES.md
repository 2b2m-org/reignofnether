# AI Features

This document describes the AI opponent implementation on the `feature/basic-ai-opponents` branch. It deliberately separates implemented behavior from validation still in progress.

## Design principles

- Bots obey the same resource costs, gathering rates, population limits, construction and production times, unit stats, combat command paths, and victory rules as human players. Fog uses a separate server-side visibility model described below; it is not yet identical to every client reveal rule.
- Difficulty changes decision quality and cadence, not game rules. Bot creation explicitly removes research cheats.
- Personalities change strategic timing and composition decisions without resource grants, stat modifiers, extra vision, or faster construction or production. Matchup balance is validated separately.
- Combat targeting and scouting are deterministic from the bot's visible or remembered state under fog. Building placement still checks authoritative collision state before issuing a legal placement.
- The implementation uses the mod's existing building, production, resource, unit-order, alliance, minimap-marker, and saved-data paths.

The main components are [BotController](src/main/java/com/solegendary/reignofnether/bot/BotController.java), [BotDecisionMaker](src/main/java/com/solegendary/reignofnether/bot/BotDecisionMaker.java), [BotArmy](src/main/java/com/solegendary/reignofnether/bot/BotArmy.java), [BotWorldView](src/main/java/com/solegendary/reignofnether/bot/BotWorldView.java), [BotBuildingPlanner](src/main/java/com/solegendary/reignofnether/bot/BotBuildingPlanner.java), and [BotServerEvents](src/main/java/com/solegendary/reignofnether/bot/BotServerEvents.java). These classes provide the useful separation that an older experimental `RonApi`/`RoNAi` project was aiming for without adding a second abstraction over the whole mod.

`BotStrategy`, `BotDifficulty`, and `BotPersonality` are built-in policy inputs, not a public add-on API or runtime AI-script registry. Adding a personality currently requires a code change.

## Difficulties

Army sizes are measured in population rather than unit count so the same budget applies across factions.

Production targets count the full military population. Normal attack launches count the active main group plus units reserved for fog scouting or beacon guarding, preventing a reserved unit from stranding the main group below its launch gate. Retreat and visible-advantage comparisons still use the active main group.

| Difficulty | Decision profile | Economy and production | Army behavior |
| --- | --- | --- | --- |
| Easy | strategic decisions every 60 ticks; army orders refreshed every 400 ticks | 4 workers; food-heavy split; plans supply 1 unit ahead; queues 1 item | 24-population target; attacks at 12; retreat threshold 1, which effectively means no voluntary retreat with a live army |
| Medium | decisions every 20 ticks; orders every 200 ticks | 5 workers; balanced split; plans supply 2 units ahead; queues up to 2 items | 36-population target; attacks at 24; may regroup below 12 when outmatched |
| Hard | decisions every 10 ticks; orders every 100 ticks | 9 workers; construction-aware split; plans supply 3 units ahead; queues up to 2 items | queues at least an 8/12/16-population opening by personality before resuming worker production; unit population can round the queued force upward; targets 48 population; attacks at 45 or earlier with a clear observed advantage; may regroup below 24 |

All three levels reserve enough resources to replace one worker before buying another military unit. Hard is intended to be the strongest deterministic heuristic profile, not a search-based or perfect-play AI. Establishing a reliable Hard > Medium > Easy match hierarchy remains an active validation item.

## Personalities

Personalities layer strategic tradeoffs over every difficulty.

| Personality | Economy and timing | Army composition and commitment | Targets, defense, and beacon behavior |
| --- | --- | --- | --- |
| Rusher | 1 fewer worker; army target and attack timing reduced by 4 population | targets roughly 25% ranged population when both unit types are affordable; smaller force; retreat threshold reduced by 4, with a minimum of 1 | attacks the nearest known valid structure; while attack-ready, recalls for critical Classic threats or its own threatened Wave base; reserves up to 3 population for beacon guards, subject to unit-population granularity; selects Strength |
| Steady | baseline worker, army, attack, and retreat values | targets roughly 40% ranged population when both unit types are affordable | prefers capitols, then production; balanced defense; reserves up to 6 population for beacon guards, subject to unit-population granularity; selects Regeneration |
| Turtle | 1 extra worker; army target and attack timing increased by 4 population | targets roughly 60% ranged population when both unit types are affordable; larger force; regroups sooner | prefers production, then capitols; responds to more allied threats; reserves up to 9 population for beacon guards, subject to unit-population granularity, and reinforces them aggressively; selects Resistance |

These are fixed strategic profiles, not cosmetic labels. They affect worker targets, the Hard opening, unit mix, attack and retreat thresholds, structure priorities, defense commitment, beacon garrisons, and beacon aura choice.

## Factions

Each faction follows the same high-level economy and combat loop using faction-native buildings and units defined in [BotStrategy](src/main/java/com/solegendary/reignofnether/bot/BotStrategy.java).

| Faction | Current build and production plan | Faction-specific behavior | Not implemented yet |
| --- | --- | --- | --- |
| Villagers | Town Centre, Villager House, Wheat Farm, Barracks; Villager workers; Vindicators and Pillagers | normal overworld placement; melee/ranged personality mix | additional production buildings, job management, militia, enchanting, upgrades, heroes, and spell use |
| Monsters | Mausoleum, Haunted House, Pumpkin Farm, Graveyard; Zombie Villager workers; Zombies and Skeletons | before daylight, unsheltered armies move to the nearest friendly night source, or home if none exists; units already under cover, in water or rain, in powder snow, or within a night source hold; Blood Moons disable recall, and the army resumes after dusk | advanced night timing, Sculk Sensor expansion, additional units/buildings, upgrades, heroes, and spell use |
| Piglins | Central Portal, Netherwart Farm, two Basic Portals transformed into civilian and military roles; Grunt workers; Brutes and Headhunters | placement enforces Nether-terrain requirements; civilian/military portal roles are tracked and reconciled after reload | portal networks, distant resource portals, expansion, additional units/buildings, upgrades, heroes, and spell use |

## Economy, construction, and production

Bots currently:

- start with three faction-native workers and normal starting resources;
- build or rebuild their capitol, forecast supply from the actual population cost of the next unit, add one farm and one military production building, and train toward their worker and army targets;
- assign workers between food and wood, rebalance them on a difficulty-specific cadence, use a completed farm while it has harvestable food, and fall back to world food while it regrows;
- for autonomously planned post-start buildings, require a loaded footprint on flat, clear ground inside current vision and the world border, with three-block separation and required Nether terrain; configured lobby capitols instead use the selected start-seat footprint after border and overlap checks;
- use a real worker for construction and reassign an available worker if an unfinished building loses its builder;
- repair damaged structures with one worker, prioritizing the capitol and production buildings, only while wood exceeds the largest configured farm, supply, or military construction package, including portal transforms, and not beside a visible military threat;
- queue real production items and respect production-queue and population budgets; military purchases preserve one worker's replacement cost and target the requested melee/ranged population mix, falling back to any affordable unit;
- reconcile Piglin civilian and military portal roles from saved origins, existing portal types, and active transform queues; and
- on graceful shutdown, save each owner's queued-production costs back into only that owner's resource balance before the building queues are cleared, allowing the restarted controller to queue replacements without duplicating or losing another player's resources.

Current economy scope is intentionally basic: there is no ore assignment, resource-node analysis, expansion economy, trading, research plan, multi-production build, or adaptive build order.

## Fog of war and scouting

With fog disabled, bots can consider all enemy buildings and units that normal server state exposes. With fog enabled:

- a bot's own and allied units reveal a one-chunk radius; its own ordinary buildings reveal one chunk and its own capitol reveals two. Allied buildings and the client's Ghast, occupied-garrison, and revealed-owner special cases do not currently extend bot vision;
- an enemy building is remembered when any chunk intersecting its footprint becomes visible or normal capitol-loss reveal rules expose its owner;
- a remembered structure is removed when any chunk intersecting its last-known footprint is visible and no matching structure remains;
- visible enemy combatants must be alive, ungarrisoned, hostile, and inside current bot-team vision; and
- when the army has at least two units, its lowest-population unit is reserved as a scout. It traverses expanding waypoints and replaces stalled waypoints after 600 ticks without four blocks of progress;
- an autonomous waypoint is complete only after the scout enters the waypoint's 16-by-16 chunk, ensuring that its one-chunk sight radius actually observes the target chunk and adjacent chunks; and
- scouts receive Move rather than Attack Move. A same-target Move is not discarded while the unit still has a combat, forced-target, or attack-move state, so the normal Move command can clear that state and continue exploration.

Unknown allied minimap markers can redirect the scout. Known targets near a marker instead receive strategic priority.

## Combat

Bots currently:

- hold near home until an attack threshold, known objective, visible Hard-difficulty advantage, active pursuit, or beacon objective makes the army attack-ready;
- choose known enemy structures by team-marker priority, personality priority, distance, and stable coordinate ordering;
- use Attack Move through the normal unit command path;
- track progress by army distance and destroyed building blocks, abandon a stalled objective, and temporarily cool it down before choosing another target;
- defend recently damaged friendly structures in Classic and intercept nearby Wave Survival enemies before damage;
- evacuate workers from visible military threats, use hysteresis to avoid order thrashing, then return them to work;
- engage threatening or substantial nearby armies instead of blindly base-racing;
- have ranged units prioritize flying threats while ground units continue their attack move;
- let Hard press a sufficiently large, clearly observed population advantage before its normal attack threshold;
- while attack-ready, let Rusher and Steady recall for critical Classic threats or their own threatened Wave base, while Turtle also recalls for remembered allied threats;
- pursue a locally observed retreating ground army only while ahead, then expire the pursuit; and
- retreat an outmatched committed army to its home for a short regroup period according to difficulty and personality.

Reserve-inclusive readiness is scoped to launch order selection. A reserved fog scout does not by itself make Rusher or Steady ignore normal defense or broaden local enemy engagement; owned-beacon garrisons retain their existing special defense behavior.

The bot commands the main army as one group, but it does not yet have a true staging point, formation/cohesion check, straggler regrouping, separate defense/harass groups, choke-point routing, focus-fire controller beyond the ranged anti-air target, pullback micro, or spellcasting.

## Teams and communication

Lobby bots inherit their start seat's color/team and ready automatically. Alliance checks are used consistently for vision, targeting, defense, beacon ownership, and advice.

Coordination uses the mod's minimap-marker channel rather than text chat:

- an allied human marker becomes the preferred advice for 600 ticks and overrides bot advice;
- with fog enabled and at least two military units, an otherwise unknown marker redirects the reserved scout; without an available scout, the marker remains advice but does not move the main army;
- a marker near a known enemy building, threatened friendly building, or beacon prioritizes that objective;
- bots publish deduplicated attack, defense, and beacon intents to allied bots and online human allies; and
- advice expires and is rejected if the sender is no longer allied; receiving bot advice never inserts an unknown building into fog memory, so an unknown coordinate must still be scouted.

Current coordination is positional only. Bots do not assign complementary team roles, negotiate who attacks or defends, reserve targets for one another, acknowledge player orders, or send text-chat status.

Lobby bots receive teams from their configured start seats, and Wave Survival creates the co-op alliances it needs. Ad-hoc `/rts-bot add` bots have no team or alliance argument; the normal `/ally` command targets online human players rather than bot identities. Human coordination with an allied bot therefore currently uses minimap markers.

## Game modes

| Mode | Current support | Important limits |
| --- | --- | --- |
| Classic | Bots participate as normal RTS players in FFA or lobby-defined teams, use normal building-destruction defeat rules, scout under fog, attack opponents, defend allies, and contribute to match results. | the full difficulty hierarchy, all official map/team layouts, and larger player counts are not yet validated end to end |
| Wave Survival | `/rts-bot wave-survival` starts bot-only or mixed human/bot co-op at any Wave difficulty; participants become allies; bots recognize Wave enemies, defend friendly bases with a larger interception radius, and attack known enemy portals/buildings. Bot-only sessions lock late RTS joins. | mixed sessions do not automatically lock late human allies; human defense-marker precedence and multi-bot role allocation need stronger validation; no current-head natural multi-wave client playthrough is complete |
| King of the Beacon | Bots know the broadcast beacon location, capture neutral or favorable beacons, contest when combat-ready, remember recently observed defenders, back off stalled assaults, reserve personality-sized guards, reinforce threatened guards, and select personality-specific auras. | source-level decisions are covered, but current-head FFA/team capture handoffs, aura application, timers, victory, and client presentation still require end-to-end validation |
| Sandbox | No dedicated AI behavior. | intentionally outside the bot scope |

## Commands and UI

Operators can manage active bots with:

```text
/rts-bot add <name> <villagers|monsters|piglins> [easy|medium|hard] [x y z]
/rts-bot list
/rts-bot difficulty <name> <easy|medium|hard>
/rts-bot personality <name> <steady|rusher|turtle>
/rts-bot wave-survival <beginner|easy|medium|hard|extreme>
/rts-bot remove <name>
```

Configured maps can place bots into normal start seats with:

```text
/rts-bot lobby add <name> <villagers|monsters|piglins> <easy|medium|hard> <steady|rusher|turtle> <x y z>
/rts-bot lobby list
/rts-bot lobby remove <name>
```

Lobby bots appear in the match-start screen with a bot icon, faction, difficulty, personality, team color, and ready state. There are no client-side spawn buttons or difficulty/personality dropdowns yet; creation is operator-command driven.

Bots use unique internal owner IDs, so a bot and a human may share a display name without sharing units, buildings, resources, or commands. Bot display names remain unique among bots.

## Persistence and reload behavior

[RTSPlayerSaveData](src/main/java/com/solegendary/reignofnether/player/RTSPlayerSaveData.java) persists bot ownership, display name, faction, home position, difficulty, personality, and Piglin portal-role origins. Controllers are recreated from saved AI players after server startup and reconnect to the units, buildings, and resources restored by the mod's existing saved-data systems. Piglin portal roles are then reconciled against the reloaded buildings.

Production queue progress itself is not serialized. On graceful shutdown, [ResourcesServerEvents](src/main/java/com/solegendary/reignofnether/resources/ResourcesServerEvents.java) saves before building shutdown and refunds only the queued items owned by each resource account. The restarted controller can then requeue work through the normal production rules. A two-owner fixture confirms that distinct 100-wood and 75-wood Piglin transforms refund and requeue without resource loss, duplication, or cross-owner leakage.

Tactical state is intentionally transient: fog memory, scout waypoint, current target, pursuit, defense memory, team advice, and beacon assault backoff are rebuilt after a restart. The same current-head fixture confirms controller recreation, unchanged bot identity and portal-role origins, same-owner reassignment and progress on two unfinished farms, and successful completion of the requeued Military and Civilian Portal transforms.

## Validation status

Implementation reviewed through commit `fff6b0cd`; runtime evidence below is pinned to the commit named in each result:

- The latest local Gradle test run passed all 43 tests: 36 decision-policy tests, 2 bot command/identity tests, 2 RTS player identity tests, and 3 existing utility tests. The bot tests cover difficulty and personality bands, build goals, supply planning, resource reserves, worker safety, repairs, army orders, target priorities, anti-air behavior, scouting progress, Monster shelter timing, and beacon decisions.
- A controlled, fog-enabled, adjacent-base hierarchy run at commit `39d470a2` recorded three expected higher-difficulty wins: Piglin Medium over Easy, Monster Hard over Medium, and Villager Medium over Easy.
- The fourth controlled trial at that commit, Monster Medium over Easy, timed out without a winner. The run stopped at the first non-win, leaving 20 planned side/order/faction trials pending.
- Commit `d7bba3a9` fixes both defects found from that timeout: autonomous scouts must enter their waypoint chunk before advancing, and a same-target Move can no longer be discarded while combat or Attack Move state needs clearing. The exact fog-enabled trial then passed: Medium discovered the enemy capitol, survived Easy's opening attack, issued 10 attack events, and won at tick 45,883 while sustaining 199.5 effective TPS.
- Commit `545dd103` lets Hard press a clearly observed 5:4 army advantage when at least 12 enemy population is visible and its main army is at or above its personality-adjusted retreat threshold. In the exact Monster Hard-versus-Medium seed/side/order that had timed out after 96,018 simulated ticks, Medium attacked first with 24 units, Hard counterattacked below its normal 45-population timing, destroyed every opposing building, and won after 71,818 simulated ticks. The run sustained 186.7 effective TPS with no grants, refills, watchdog, or crash. The reciprocal side and creation order also passed, with Hard winning after 95,495 simulated ticks at 182.3 effective TPS.
- Commit `70015c02` prevents a fog scout or beacon guard from leaving an otherwise ready army permanently below its launch threshold. Commit `fff6b0cd` confines the general reserve signal to launch selection so ordinary defense and local-engagement policy remain unchanged. In reciprocal fresh-world Villager Medium-versus-Easy runs at `fff6b0cd`, Medium won from both sides and creation orders after 29,653 and 29,325 simulated ticks at 172.3 and 177.0 effective TPS, with no resource grants or refills. The second run also directly inspected `fog enabled=true` from live server state.
- A current-head graceful-restart fixture passed for commit `2aa8cee7`: two owners retained identity and role origins, requeued different transform costs to exactly zero remaining resources, reassigned their own workers to unfinished farms, advanced both farms, and completed Military and Civilian Portal transforms.
- These targeted reruns validate the fixes, but the complete multi-seed, side/order-controlled difficulty hierarchy is still **not proven**.
- An earlier Wave persistence fixture preserved an active wave portal across restart and preserved the wave-30 cap. It predates the current head and does not replace current-head mixed-team or natural-wave validation.
- An earlier Monster daylight fixture passed natural dawn recall, weather-clear recall, survival under cover/night sources, and night resumption. It predates the latest scouting changes.
- King of the Beacon has deterministic policy tests, but no current-head client-visible FFA/team victory run has passed yet.
- Official maps, 3/4/6-player layouts, FFA and team variants, a real client LAN match, and the user's manual jar audit remain pending.

## Prioritized TODO

### P0: correctness and release evidence

- [x] Rerun the exact fog-enabled Monster Medium-versus-Easy timeout after the deterministic scout fix. Require enemy discovery, an attack order, and a win before accepting the fix as runtime-proven.
- [ ] Complete the repeatable side/order-controlled Easy < Medium < Hard matrix across all three factions and multiple seeds. Record timeouts and draws as failures, not wins.
- [ ] Exercise every personality at every difficulty, including adversarial matchups, without weakening the difficulty bands or introducing resource/stat cheats.
- [x] Pass a current-head graceful-restart fixture for multiple owners with queued production, unfinished construction, Piglin portal transforms, resource accounting, and replacement builders.
- [ ] Run current-head Classic, bot-only and mixed Wave Survival, and King of the Beacon end to end with real victory conditions.
- [ ] Validate official 2/3/4/6-player maps, FFA, 2v2, 3v3, and 2v2v2 seats/teams. Check 1.21.1 world upgrade, fog, terrain, start footprints, and path reachability.
- [ ] Build the audit jar, run the minimal-mod dedicated/LAN environment, perform a real client visual/play test, and leave the final manual audit to the user before opening a PR.

### P1: highest-value gameplay additions

- [ ] Add army staging and cohesion: a safe gather point, readiness based on assembled population, straggler recovery, attack-angle selection, and regrouping that does not drag an entire army back unnecessarily.
- [ ] Split military roles into the smallest useful groups: main attack, minimal local defense, short-lived harass, beacon guard, and Wave response. Coordinate these roles between allied bots instead of duplicating every response.
- [ ] Expand tactical micro with bounded focus fire, damaged-unit pullback, better anti-air allocation, Creeper target selection for garrisons/crowds, and faction-native spells. Keep actions within human-legal command and information limits.
- [ ] Add map analysis for reachable regions, routes, choke points, safe staging sides, and attack angles before adding more build-order complexity.
- [ ] Add fog-legal resource-node awareness and expansion: incrementally cluster visible resources, track capacity and depletion, avoid over-assigning a farm or node, choose safe outposts, place Stockpiles or Piglin portals near distant resources, and preserve valid worker assignments across reload. If repeated global scans become a measured hotspot, maintain a small per-owner index rather than a whole-mod wrapper API.
- [ ] Make base layout semantic and compact: keep farms near the capitol, score production positions by front/rear intent, preserve movement lanes, and permit proxy or resource placements only from fog-legal information.
- [ ] Add urgency-based multi-builder construction and deterministic replacement-builder selection while retaining normal worker commands and costs.
- [ ] Add prerequisite-aware research, upgrades, building transformations, and additional production buildings. Then add hero training, resurrection, skill selection, creeping, and spell use faction by faction.
- [ ] Deepen faction plans: Villager jobs/militia/enchanting, Monster night attacks and Sculk expansion, and Piglin portal-network expansion, while retaining the shared fair-policy core.
- [ ] Add a conservative, team-aware surrender decision only for mathematically hopeless states. Never use surrender to hide navigation bugs, timeouts, or poor play.
- [ ] Improve team communication with typed attack/defend/scout/beacon markers, multiple remembered human instructions, acknowledgements, target ownership, and explicit bot role status. Text chat can remain optional flavor rather than a control dependency.

### P2: authoring and quality of life

- [ ] Add lobby UI controls for bot faction, difficulty, personality, start seat, and team after the command workflow is stable.
- [ ] Add pre-match team assignment for command-created bots while respecting alliance locks and co-op rules; do not add an unrestricted mid-match force-alliance command.
- [ ] Add more personalities only when each one changes observable strategy across factions and remains fair—for example, an expansion-focused macro player or a harassment-focused skirmisher.
- [ ] Make build, unit, research, harvest, and attack priorities data-driven if multiple independently authored AI scripts create a real need. Do not introduce a broad `RonApi`, integer type-ID layer, or generic timer framework while the existing native classes remain sufficient.
- [ ] Profile 4- and 6-bot matches at normal and accelerated tick rates, then optimize measured server-thread hotspots without weakening decisions or replacing end-to-end tests with synthetic benchmarks.

The strongest ideas to carry forward from the abandoned project are army staging/cohesion, role-based attack and defense, prerequisite-aware tech and heroes, resource-aware expansion, faction-specific micro, map routing, and conservative surrender. Its full compatibility abstraction and script registry should wait until there are multiple real AI implementations that need them.
