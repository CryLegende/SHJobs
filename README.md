<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHJobs

*Métiers (/jobs) : jobs journaliers, niveaux, exp et points de métier à dépenser en boutique.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Métiers (/jobs) : jobs journaliers, niveaux, exp et points de métier à dépenser en boutique.

Les achats et reventes de la boutique sont écrits dans le fichier `boutique` de SHLogs (`plugins/SHLogs/SHJobs/`), les niveaux gagnés dans le fichier `niveaux`.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `PlaceholderAPI`
- Optionnelle : `SHLogs` (journal de la boutique et des niveaux, sinon écrits dans la console)
- Optionnelle : `SHNetwork` (plusieurs serveurs de jeu, voir plus bas)

## Plusieurs serveurs de jeu

Sans SHNetwork, le plugin tourne comme avant : stockage de `database.type`, rotation du jour dans `shjobs_server_state.yml`, limites de la boutique dans `daily_limits.yml`.

Avec SHNetwork (et `reseau.actif: true`, par défaut), tout est partagé dans la base MariaDB du bloc `reseau.base-de-donnees` (`database.type` est alors ignoré) :

- **Joueurs** : points, niveaux et quêtes du jour (tables `shjobs_player_data`, `shjobs_player_jobs`, `shjobs_player_daily_jobs`). Un joueur est lu à sa connexion, une fois que le serveur qu'il quitte a fini de le sauvegarder, et écrit à son départ.
- **Rotation du jour** : une seule ligne (`shjobs_state`) avec un numéro de version. Le serveur meneur la prend à l'heure de rotation, les autres l'appliquent quand le bus leur annonce la nouvelle version (ou à la relecture de la ligne toutes les `reseau.relecture-rotation-secondes`). Un joueur qui change de serveur garde donc ses quêtes du jour.
- **Limites de la boutique** : compteurs par jour, joueur et article (`shjobs_daily_limits`), incrémentés de façon atomique. Changer de serveur ne les remet pas à zéro.
- **Commandes d'admin** : `/sja exp`, `level` et `points` sur un joueur connecté sur un autre serveur sont exécutés sur son serveur. `/sja resetdaily` tourne sur n'importe quel serveur et la rotation est la même partout.

`/sja import` (permission `shjobs.admin.import`) reprend dans la base partagée les joueurs de `database/shjobs_data.db` et les limites de `daily_limits.yml`. Un joueur déjà en base n'est pas touché, la commande peut être relancée. La rotation du jour de `shjobs_server_state.yml` est reprise toute seule au premier démarrage en mode réseau.

Le bloc `reseau` demande un redémarrage.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/jobs` | `/shjobs` | Opens the main jobs menu. | `shjobs.player.menu` |
| `/shjobsadmin` | `/sja` | Admin commands for SHJobs. | `shjobs.admin.base` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shjobs.player.menu` | Allows opening the main jobs menu. | `true` |
| `shjobs.admin.base` | Base permission for admin commands. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHJobs/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/custom_items.yml">custom_items.yml</a>
├── <a href="src/main/resources/menus.yml">menus.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
