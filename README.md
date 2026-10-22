<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHJobs

*Métiers (/jobs) : jobs journaliers, niveaux, exp et points de métier à dépenser en boutique.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Métiers (/jobs) : jobs journaliers, niveaux, exp et points de métier à dépenser en boutique.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `PlaceholderAPI`

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
