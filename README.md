# Tags

## Download di riferimento per Minekeep

Scarica **[Tags.jar](https://github.com/Gekojr/Tags/releases/latest/download/Tags.jar)** dalla **[release Latest](https://github.com/Gekojr/Tags/releases/latest)**. Questa è la build Paper di riferimento.

Ogni build riuscita di `main` pubblica una nuova release Latest con JAR, SHA-256 e commit sorgente. Le build delle pull request producono artefatti di verifica senza aggiornare Latest.

Per Minekeep, arresta il server Paper 26.3, sostituisci la vecchia copia del plugin nella cartella `plugins/` con questo JAR e riavvia. Conserva una sola copia del plugin e mantieni la sua cartella di configurazione. Per i repository privati è necessario accedere a GitHub per scaricare.

Paper 26.3 plugin for multiple selectable chat nametags.

## Player usage

Use **/tag** to open the nametag inventory.

- Every nametag you own appears as a Minecraft **Name Tag** item.
- Hovering an item shows its title and selection information.
- **Right-click** a Name Tag to select it.
- You can own multiple nametags at the same time.
- Only the selected nametag appears in chat.
- Your selected nametag is saved after a restart.

## Admin commands

- `/nametag add <player> <tag>` — give a nametag without removing other nametags.
- `/nametag remove <player> [tag]` — remove one nametag, or all if no tag is specified.
- `/nametag get <player>` — show owned and selected nametags.
- `/nametag list` — list configured nametags.
- `/nametag reload` — reload configuration.

## Tebex

For a package that grants a nametag, use the console command:

`nametag add {username} donator`

The player can later use **/tag** to choose the nametag.

## Default nametags

- OWNER — dark red
- CO-OWNER — light red
- ADMIN — orange
- DONATOR — cyan

The plugin stores ownership and the selected nametag in `plugins/Tags/data.yml`.
