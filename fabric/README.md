# Tags per Fabric 26.2

Port del plugin Tags: proprietà di più tag, selezione salvata, menu paginato, tag forzati temporaneamente e livelli automatici basati sul tempo giocato.

## Installazione

Richiede **Minecraft Java 26.2**, **Java 25**, **Fabric Loader 0.19.5** e **Fabric API 0.161.0+26.2**. La mod si installa solo sul server; i giocatori possono usare il client vanilla 26.2.

1. Compila con `cd fabric` e `./gradlew build` (su Windows: `gradlew.bat build`).
2. Copia il JAR da `fabric/build/libs/` nella cartella `mods/` del server, insieme a Fabric API per 26.2. Usa il JAR senza `-sources` nel nome.
3. Avvia il server Fabric. I file della mod sono in `config/`.

Le versioni della toolchain sono fissate nel progetto; il wrapper Gradle verifica lo SHA-256 della distribuzione.

## Comandi

- `/tag` oppure `/tags`: menu dei tag; clic sinistro per selezionare o disattivare, frecce per le pagine.
- `/nametag add <giocatore> <tag>` e `set`: assegna un tag.
- `/nametag all <giocatore>` e `giveall`: assegna tutti i tag.
- `/nametag remove <giocatore> [tag]`: rimuove uno o tutti i tag.
- `/nametag force <giocatore> <tag> <durata>`: forza un tag (`10s`, `30m`, `2h`, `1d`).
- `/nametag unforce <giocatore>`: ripristina la selezione precedente.
- `/nametag get <giocatore>`, `/nametag list`, `/nametag reload`.
- `/nametag resetplaytime <giocatore|all>`: azzera il tempo, mantenendo i tag posseduti.
- `/minecraft:tag`: comando vanilla originale.

La console e gli operatori di livello almeno 2 possono amministrare i tag. Il nodo Fabric Permission API è `tags:admin`. I nodi Bukkit e i relativi plugin di permessi devono essere configurati separatamente sul server Fabric.

Il formato chat conserva titolo, colori e separatore. I messaggi formattati vengono trasmessi come messaggi di sistema, rispettando visibilità e filtri dei destinatari; non conservano il formato di chat firmata vanilla. Il livello di tempo giocato più alto ha precedenza sulla selezione manuale, come nel plugin originale; un tag forzato ha precedenza su entrambi.

## Salvataggi Paper

A server fermo, copia `plugins/Tags/config.yml` e `plugins/Tags/data.yml` in `config/Tags/`. Sono supportati sia il vecchio valore singolo per giocatore sia i campi `tags`, `selected`, `playtime`, `forced`, `forced-expiry` e `forced-previous`. Mantieni gli stessi UUID e la stessa modalità di autenticazione del server.

Il port legge correttamente i livelli di tempo giocato sia come lista YAML (configurazione predefinita) sia come sezioni. Per concedere un tag da Tebex: `nametag add {username} donator`.

## Verifica

Cinque test automatici verificano migrazione, proprietà, selezione, tag forzati, reset del tempo e protezione dei file YAML non validi. La prova con le tre mod sul server 26.2 verifica anche menu, alias e chat con tag.
