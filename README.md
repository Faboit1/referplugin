# ReferPlugin

A production-ready **referral system** for Paper 1.21+ Minecraft servers. Players share a personalised join link (subdomain) and earn configurable rewards whenever a new player joins through it.

---

## Features

- **Subdomain-based referral detection** — players join via `<name>.yourdomain.com` and the plugin automatically attributes the referral
- **Reward profiles** — money (Vault), XP, items, console commands, and chance-based bonus rewards per referral event
- **Permission-based reward multipliers** — give VIP players bigger bonuses without touching profiles
- **Milestones** — one-time rewards triggered at referral count thresholds (5, 10, 25, 50, …)
- **Anti-abuse engine** — self-referral block, same-IP block, cooldowns, daily/weekly caps; all configurable
- **Offline referrer support** — rewards are queued and delivered when the referrer next logs in
- **Admin panel** — in-game GUI showing leaderboard, latest referrals, and suspicious activity
- **PlaceholderAPI** support for scoreboards and chat
- **Velocity / BungeeCord** companion plugin included for correct IP extraction under modern forwarding
- **SQLite** (default) or **MySQL** storage with HikariCP connection pooling
- **Redis** cross-server IP cache (optional)
- **Log files** — `referrals.log` and `blocked-referrals.log` written to the plugin folder

---

## Requirements

| Dependency | Required? | Notes |
|---|---|---|
| Paper 1.21+ | ✅ Yes | Spigot is **not** supported |
| Java 21 | ✅ Yes | |
| Vault + economy plugin | ⚠ Soft | Money rewards only |
| PlaceholderAPI | ⚠ Soft | Placeholders in messages / scoreboards |
| LuckPerms | ⚠ Soft | Not required; permission checks use Bukkit API |

---

## Installation

1. Drop `ReferPlugin-<version>.jar` into your `plugins/` folder.
2. If you use Velocity, also drop `ReferPlugin-velocity-<version>.jar` into your Velocity `plugins/` folder.
3. Start the server once to generate `plugins/ReferPlugin/config.yml` and `messages.yml`.
4. Configure `hostname.base-domain` to match your server's domain (e.g. `cheesesmp.top`).
5. Reload with `/referral reload` or restart the server.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/referral` or `/refer` | Show help | `referplugin.use` (default: everyone) |
| `/referral stats` | View your own referral stats | `referplugin.use` |
| `/referral stats <player>` | View another player's stats | `referplugin.admin` |
| `/referral gui` | Open the player referral GUI | `referplugin.gui` (default: everyone) |
| `/referral admin` | Open the admin panel GUI | `referplugin.admin` |
| `/referral info [player]` | Show a player's full profile | `referplugin.admin` |
| `/referral setprofile <player> <profile>` | Override reward profile | `referplugin.admin` |
| `/referral reload` | Reload config and messages | `referplugin.reload` |

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `referplugin.use` | everyone | Use `/referral` commands |
| `referplugin.gui` | everyone | Open the player GUI |
| `referplugin.admin` | op | Admin commands and GUI |
| `referplugin.reload` | op | Reload the configuration |
| `referplugin.bypass.cooldown` | op | Bypass referral cooldowns |
| `referplugin.bypass.cap` | op | Bypass daily/weekly referral caps |
| `referral.boost.1` | false | +50 % reward multiplier |
| `referral.boost.2` | false | +100 % reward multiplier |

---

## How referral links work

A player named `faboit` shares the link **`faboit.cheesesmp.top`**.  
When a new player connects to that address the plugin detects the subdomain, looks up `faboit`, and processes the referral automatically.

Every player who joins the server is automatically registered as a potential referrer — **no setup is needed from the player**.

---

## Admin GUI

Open with `/referral admin`. The panel is divided into three sections:

| Slots | Content |
|---|---|
| 0–8 | **Top Referrers** leaderboard |
| 18–26 | **Suspicious activity** (blocked/flagged referrals) |
| 27–35 | **Latest Referrals** (most recent successful referrals) |
| 49 | Close button |

---

## Log files

Two plain-text log files are written inside `plugins/ReferPlugin/`:

- **`referrals.log`** — one line per successful referral:
  ```
  [2025-01-15 12:34:56] SUCCESS | referrer=faboit | joiner=Steve | ip=1.2.3.4 | host=faboit.cheesesmp.top
  ```
- **`blocked-referrals.log`** — one line per blocked attempt:
  ```
  [2025-01-15 12:35:10] BLOCKED_SAME_IP | referrer=faboit | joiner=AltAccount | ip=1.2.3.4
  ```

---

## PlaceholderAPI placeholders

| Placeholder | Description |
|---|---|
| `%referplugin_count%` | Player's successful referral count |
| `%referplugin_total%` | Player's total referral attempts |
| `%referplugin_blocked%` | Player's blocked referral count |
| `%referplugin_rewards%` | Total money earned from referrals |
| `%referplugin_profile%` | Active reward profile name |
| `%referplugin_top_N_name%` | Name of Nth leaderboard player |
| `%referplugin_top_N_count%` | Referral count of Nth leaderboard player |

---

## Configuration overview

Key settings in `config.yml`:

```yaml
hostname:
  base-domain: "cheesesmp.top"   # your server domain

anti-abuse:
  self-referral-block: true
  same-ip-block: true
  cooldown-hours: 24             # hours between referrals per referrer
  daily-cap: 10                  # max referrals per day per referrer

reward-profiles:
  default_referrer:
    money: 500.0
    xp: 100
  default_joiner:
    money: 250.0
    xp: 50
```

Full documentation is inside the generated `config.yml`.

---

## Building from source

```bash
mvn clean package -pl . -am
# Output: target/ReferPlugin-<version>.jar
# Velocity plugin: velocity-plugin/target/ReferPlugin-velocity-<version>.jar
```

Requires Java 21 and Maven 3.8+.

---

## License

MIT — see [LICENSE](LICENSE) for details.
