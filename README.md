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

## Quêtes du jour

- Chaque jour, une quête par métier (case 1 Bûcheron, 2 Mineur, 3 Agriculteur, 4 Chasseur, 5 Artisan, 6 Pêcheur), tirée selon `selection_weight` parmi les quêtes du métier (`general.one_job_per_type`).
- Paliers (`general.level_tiers`) : à partir d'un niveau dans le métier, les objectifs et l'XP de la quête sont multipliés, les points ne changent pas. Le palier est fixé à l'attribution de la quête et affiché dans le menu (`daily_job_tier_lore` de `menus.yml`).
- Un bloc posé par un joueur ne fait pas avancer un objectif de casse quand il le recasse (`general.placed_blocks_count: false`). La position est gardée dans les données du chunk, suit les pistons et survit aux redémarrages.
- Types d'objectif : `BREAK`, `PLACE`, `STRIP`, `CRAFT`, `SMELT`, `FISH`, `KILL` (avec `projectile: true` pour les tirs à l'arc, à l'arbalète ou au trident), `BREW` (potion comptée à la fin de l'infusion, pour le dernier joueur à avoir cliqué dans l'alambic), `SMITH` (garniture `TRIM` ou netherite `UPGRADE`), `HARVEST` (cueillette au clic droit), `WALK`. Le détail est en tête de `job_templates` dans `config.yml`.

## Dépendances

- Obligatoires : `PlaceholderAPI`
- Optionnelle : `SHLogs` (journal de la boutique et des niveaux, sinon écrits dans la console)
- Optionnelle : `SHNetwork` (plusieurs serveurs de jeu, voir plus bas)
- Optionnelle : `SHSkins` (têtes des joueurs avec leur skin, sinon têtes sans texture : Steve ou Alex)

## Plusieurs serveurs de jeu

Sans SHNetwork, le plugin tourne comme avant : stockage de `database.type`, rotation du jour dans `shjobs_server_state.yml`, limites de la boutique dans `daily_limits.yml`.

Avec SHNetwork (et `reseau.actif: true`, par défaut), tout est partagé dans la base MariaDB du bloc `reseau.base-de-donnees` (`database.type` est alors ignoré) :

- **Joueurs** : points, niveaux et quêtes du jour (tables `shjobs_player_data`, `shjobs_player_jobs`, `shjobs_player_daily_jobs`). Un joueur est lu à sa connexion, une fois que le serveur qu'il quitte a fini de le sauvegarder, et écrit à son départ.
- **Rotation du jour** : une seule ligne (`shjobs_state`) avec un numéro de version. Le serveur meneur la prend à l'heure de rotation, les autres l'appliquent quand le bus leur annonce la nouvelle version (ou à la relecture de la ligne toutes les `reseau.relecture-rotation-secondes`). Un joueur qui change de serveur garde donc ses quêtes du jour.
- **Limites de la boutique** : compteurs par jour, joueur et article (`shjobs_daily_limits`), incrémentés de façon atomique. Changer de serveur ne les remet pas à zéro.
- **Commandes d'admin** : `/shjobs exp`, `level` et `points` sur un joueur connecté sur un autre serveur sont exécutés sur son serveur. `/shjobs resetdaily` tourne sur n'importe quel serveur et la rotation est la même partout.

`/shjobs import` (permission `shjobs.admin.import`) reprend dans la base partagée les joueurs de `database/shjobs_data.db` et les limites de `daily_limits.yml`. Un joueur déjà en base n'est pas touché, la commande peut être relancée. La rotation du jour de `shjobs_server_state.yml` est reprise toute seule au premier démarrage en mode réseau.

Le bloc `reseau` demande un redémarrage.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/jobs` | `/job` (ajouté par le serveur) | Ouvre le menu des métiers. | `shjobs.use` |
| `/jobs shop` | | Ouvre la boutique des métiers. | `shjobs.shop` |
| `/shjobs` | `/shjobsadmin`, `/sja` | Administration. Sans droit d'administration, se comporte comme `/jobs`. | voir ci-dessous |

Sous-commandes de `/shjobs`, utilisables aussi en console sauf `customitem save` :

| Sous-commande | Description | Permission |
|---|---|---|
| `reload` | Recharge `config.yml`, `messages.yml`, `menus.yml` et `custom_items.yml` ; une erreur de syntaxe annule le rechargement. | `shjobs.admin.reload` |
| `list [jobs\|shop]` | Rotation du jour et temps avant la suivante ; avec `jobs` ou `shop`, tous les ids. | `shjobs.admin.list` |
| `exp <give\|remove\|set> <joueur> <métier> <montant>` | Gère l'XP de métier. | `shjobs.admin.exp.give`, `.remove`, `.set` |
| `level set <joueur> <métier> <niveau>` | Définit le niveau de métier. | `shjobs.admin.level.set` |
| `points <give\|remove\|set> <joueur> <montant>` | Gère les points de métier ; `give` annonce le gain au joueur connecté (`admin.points.give.received`). | `shjobs.admin.points.give`, `.remove`, `.set` |
| `playerinfo <joueur>` (`info`) | Affiche les informations de métier d'un joueur. | `shjobs.admin.playerinfo` |
| `job change <slot 1-6> [id_job]` | Change un job journalier. | `shjobs.admin.job.change` |
| `shopitem change <slot 1-6> [id_article]` | Change un article de la boutique du jour. | `shjobs.admin.shop.changeitem` |
| `resetdaily` | Force la rotation des jobs et de la boutique. | `shjobs.admin.resetdaily` |
| `resetlimits <joueur> [id_article]` | Remet à zéro les limites d'achat et de revente du jour d'un joueur, pour tous les articles ou pour un seul. | `shjobs.admin.resetlimits` |
| `import` | Reprend les données de ce serveur dans la base partagée (mode réseau). | `shjobs.admin.import` |
| `customitem save <id>` | Enregistre l'objet en main dans `custom_items.yml`. | `shjobs.admin.customitem` et `.customitem.save` |
| `customitem list` | Liste les objets enregistrés. | `shjobs.admin.customitem` et `.customitem.list` |
| `customitem give <id> [joueur]` | Donne une copie d'un objet enregistré. | `shjobs.admin.customitem` et `.customitem.give` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shjobs.use` | Ouvrir le menu des métiers (`/jobs`). | `true` |
| `shjobs.shop` | Ouvrir la boutique avec `/jobs shop`. | `op` |
| `shjobs.admin` | Toute l'administration (parent de toutes les permissions `shjobs.admin.*`). | `op` |
| `shjobs.admin.reload`, `.list`, `.playerinfo`, `.resetdaily`, `.resetlimits`, `.import` | Les sous-commandes du même nom. | `op` |
| `shjobs.admin.exp.give`, `.exp.remove`, `.exp.set` | `exp give`, `remove`, `set`. | `op` |
| `shjobs.admin.level.set` | `level set`. | `op` |
| `shjobs.admin.points.give`, `.points.remove`, `.points.set` | `points give`, `remove`, `set`. | `op` |
| `shjobs.admin.job.change` | `job change`. | `op` |
| `shjobs.admin.shop.changeitem` | `shopitem change`. | `op` |
| `shjobs.admin.customitem`, `.customitem.save`, `.customitem.list`, `.customitem.give` | `customitem` et ses actions. | `op` |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

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

SHSkins doit être installé dans le dépôt Maven local (`mvn install` dans `SHSkins-Velocity`).

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
