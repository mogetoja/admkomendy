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
| `/ban <nick> [powód]` | ban na stałe |
| `/tempban <nick> <czas> [powód]` | ban na czas |
| `/tempbanip <nick\|ip> <czas> [powód]` | ban na adres IP na czas |
| `/unban <nick\|ip>` | zdjęcie bana |
| `/mute <nick> [powód]` | wyciszenie na stałe |
| `/tempmute <nick> <czas> [powód]` | wyciszenie na czas |
| `/tempmuteip <nick\|ip> <czas> [powód]` | wyciszenie adresu IP na czas |
| `/unmute <nick\|ip>` | zdjęcie wyciszenia |

Czas podajesz jako `30m`, `2h`, `7d`, `1d12h` (jednostki: `s` `m` `h` `d` `w`, maksymalnie 10 lat).
Kary zapisują się w `plugins/AdmKomendy/punishments.yml` i przetrwają restart serwera.
Wyciszony gracz nie może pisać na czacie ani używać `/msg`, `/tell`, `/w`, `/r`, `/me`, `/say`.

## Uprawnienia

`admkomendy.tp`, `admkomendy.gm`, `admkomendy.fly`, `admkomendy.flyspeed`, `admkomendy.ban`
(wszystkie bany i `/unban`), `admkomendy.mute` (wszystkie wyciszenia i `/unmute`) oraz
`admkomendy.others` (używanie `/gm` i `/fly` na innych graczach). Domyślnie dla OP.

## Budowanie

```
mvn package
```

Gotowy plik: `target/AdmKomendy.jar` → wrzuć do `plugins/`.
Jar buduje się też automatycznie w GitHub Actions (zakładka Actions → Artifacts).
