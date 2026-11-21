# AdmKomendy

Prosty plugin administracyjny na Paper/Spigot 1.21 (Java 21) z ładnymi komunikatami na czacie.

## Komendy

| Komenda | Opis |
|---|---|
| `/tp <nick>` | teleportuje Cię do gracza |
| `/tp <nick> <nick2>` | teleportuje gracza do innego gracza |
| `/tp <x> <y> <z>` | teleportuje Cię na koordynaty (działa `~`) |
| `/tp <nick> <x> <y> <z>` | teleportuje gracza na koordynaty |
| `/gm <0-3> [nick]` | 0 survival, 1 creative, 2 adventure, 3 spectator |
| `/fly [nick]` | włącza/wyłącza latanie |
| `/flyspeed <1-10>` | prędkość latania (liczby całkowite) |

## Uprawnienia

`admkomendy.tp`, `admkomendy.gm`, `admkomendy.fly`, `admkomendy.flyspeed` oraz `admkomendy.others`
(używanie `/gm` i `/fly` na innych graczach). Domyślnie dla OP.

## Budowanie

```
mvn package
```

Gotowy plik: `target/AdmKomendy.jar` → wrzuć do `plugins/`.
Jar buduje się też automatycznie w GitHub Actions (zakładka Actions → Artifacts).
