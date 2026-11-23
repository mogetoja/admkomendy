# Komendy

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
| `/flyspeed <1-10> [nick]` | prędkość latania sobie lub wskazanemu graczowi (liczby całkowite) |
| `/msg <nick> <wiadomość>` | prywatna wiadomość (aliasy: `/tell`, `/w`, `/m`, `/whisper`, `/pm`) |
| `/r <wiadomość>` | odpowiedź ostatniemu rozmówcy (alias: `/reply`) |
| `/repair` | naprawia przedmiot trzymany w ręce |
| `/repair all` | naprawia wszystkie przedmioty (ekwipunek, zbroja, druga ręka) |
| `/ban <nick> [powód]` | ban na stałe |
| `/banip <nick\|ip> [powód]` | ban na adres IP na stałe (alias: `/ban-ip`) |
| `/tempban <nick> <czas> [powód]` | ban na czas |
| `/tempbanip <nick\|ip> <czas> [powód]` | ban na adres IP na czas |
| `/unban <nick\|ip>` | zdjęcie bana |
| `/mute <nick> [powód]` | wyciszenie na stałe |
| `/tempmute <nick> <czas> [powód]` | wyciszenie na czas |
| `/tempmuteip <nick\|ip> <czas> [powód]` | wyciszenie adresu IP na czas |
| `/unmute <nick\|ip>` | zdjęcie wyciszenia |

Czas podajesz jako `30m`, `2h`, `7d`, `1d12h` (jednostki: `s` `m` `h` `d` `w`, maksymalnie 10 lat).
Kary zapisują się w `plugins/Komendy/punishments.yml` i przetrwają restart serwera.
Wyciszony gracz nie może pisać na czacie ani używać `/msg`, `/tell`, `/w`, `/r`, `/me`, `/say`.

## Konflikty z Essentials

Komendy banów i wyciszeń (`/ban`, `/banip`, `/ban-ip`, `/tempban`, `/unban`, `/unbanip`, `/pardon`,
`/pardon-ip`, `/mute`, `/tempmute`, `/unmute`) też zawsze idą przez ten plugin, także w formie
`/minecraft:ban`, `/essentials:ban` itd.

`/gm`, `/msg` (i aliasy) oraz `/r` zawsze uruchamiają wersję z tego pluginu, nawet gdy Essentials też je ma:
plugin przekierowuje je na `/komendy:gm`, `/komendy:msg` i `/komendy:r`, więc nic się nie kłóci.
Zmiana trybu gry przez **F3+F4** też idzie przez ten plugin (klient wysyła wtedy `/gamemode`,
które plugin zamienia na `/gm`), więc widzisz jego komunikaty, a nie z Essentials.
Opcjonalnie możesz też wyłączyć `/gm` w Essentials, dopisując w jego `config.yml`:

```yaml
disabled-commands:
  - gm
```

## Uprawnienia

Każda komenda ma własne uprawnienie (domyślnie dla OP):

| Uprawnienie | Do czego |
|---|---|
| `komendy.tp` | `/tp` |
| `komendy.gm` / `komendy.gm.others` | `/gm` / `/gm` na innych graczach |
| `komendy.fly` / `komendy.fly.others` | `/fly` / `/fly` na innych graczach |
| `komendy.flyspeed` / `komendy.flyspeed.others` | `/flyspeed` / `/flyspeed` na innych graczach |
| `komendy.msg` | `/msg` (domyślnie dla wszystkich) |
| `komendy.r` | `/r` (domyślnie dla wszystkich) |
| `komendy.repair` | `/repair` |
| `komendy.repair.all` | `/repair all` |
| `komendy.ban` | `/ban` |
| `komendy.banip` | `/banip` |
| `komendy.tempban` | `/tempban` |
| `komendy.tempbanip` | `/tempbanip` |
| `komendy.unban` | `/unban` |
| `komendy.mute` | `/mute` |
| `komendy.tempmute` | `/tempmute` |
| `komendy.tempmuteip` | `/tempmuteip` |
| `komendy.unmute` | `/unmute` |

## Budowanie

```
mvn package
```

Gotowy plik: `target/Komendy.jar` → wrzuć do `plugins/`.
Jar buduje się też automatycznie w GitHub Actions (zakładka Actions → Artifacts).
