# Mafia Online — TODO

Update this file whenever a feature is completed or a new task is identified.
Cross off items with ~~strikethrough~~ when done, or remove them entirely.

## Completed

- ~~Detective UI~~ — frontend calls `/night/investigate`, shows result
- ~~Doctor UI~~ — frontend calls `/night/protect` during NIGHT_DOCTOR phase
- ~~Game end screen~~ — winner announcement + role reveal on ENDED phase
- ~~Night timer~~ — auto-advance or countdown for night phases
- ~~Tests~~ — GameService (43 tests) and GameController (26 tests) fully covered
- ~~WebSocket~~ — replaces 2s polling; server broadcasts `"refresh"` on every state change
- ~~Public/private servers~~ — toggle on create-room form; private rooms hidden from browse list, joinable by code only

- ~~Security~~ — roles hidden from room state JSON (`@JsonIgnoreProperties`); math CAPTCHA required to create/join rooms (server-side HMAC, 10-min window)

## Remaining

— Nothing critical. Ship it.
