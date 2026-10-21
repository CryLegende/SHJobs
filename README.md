<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHJobs

*Plugin du serveur SkyHand.*

<img src="https://img.shields.io/badge/Paper-1.20.2-f2a900?style=for-the-badge" alt="Paper 1.20.2"> <img src="https://img.shields.io/badge/Java-17-e76f00?style=for-the-badge" alt="Java 17"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Plugin du serveur SkyHand.

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

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`
- `custom_items.yml`
- `menus.yml`
- `messages.yml`

## Compilation

Java 17 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
