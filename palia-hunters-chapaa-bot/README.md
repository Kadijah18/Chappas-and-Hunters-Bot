# Palia Hunters & Chapaa Discord Challenge Bot

A Java/JDA Discord bot for a 90-space Palia challenge race inspired by the in-game Hunters & Chapaa board.

## Features

- `/join` adds a player and creates a dedicated public Discord thread.
- `/roll` rolls a d6, moves the player, applies configured board shortcuts/setbacks, and shows the final-space challenge.
- `/complete` accepts up to five screenshot attachments and creates a **pending proof submission**.
- Players stay locked from rolling while proof is awaiting moderator verification.
- `/verify` lets a moderator approve or reject a proof submission.
- Approved proof marks the space verified/completed and unlocks the next roll.
- Rejected proof leaves the player on the same challenge so they can resubmit.
- `/proof-status` shows a player's latest submission status and moderator note.
- `/challenge`, `/position`, `/trail`, and `/leaderboard` show game progress.
- `/reset` is restricted to members with Discord's **Manage Server** permission.
- SQLite persists players, threads, completed spaces, proof submissions, screenshot metadata, and winners across restarts.
- Railway deployment files are included so the bot can stay online without your computer running.

## Requirements

- Java 21 for local development
- IntelliJ IDEA or Gradle
- A Discord application/bot token
- A Discord server where you can invite the bot
- For 24/7 hosting: a Railway account (or another Docker-compatible host)

The project currently uses JDA `6.5.0`.

## Discord permissions

Give the bot these permissions in the game channel:

- View Channel
- Send Messages
- Send Messages in Threads
- Create Public Threads
- Manage Threads
- Read Message History
- Attach Files
- Use Application Commands

Moderators who review proof need Discord's **Manage Server** permission because `/verify` and `/reset` check that permission.

# Local setup

## 1. Create the Discord bot

1. Open the Discord Developer Portal.
2. Create a new Application.
3. Open **Bot** and configure the bot user.
4. Copy its token.
5. Invite it to your Discord server with the `bot` and `applications.commands` scopes.
6. Give it the permissions listed above.

Never put the token directly in source code or commit it to GitHub.

## 2. Configure environment variables

In IntelliJ: **Run > Edit Configurations > Environment variables**.

Required:

```text
PALIA_BOT_TOKEN=your_discord_bot_token
```

Optional local database path:

```text
PALIA_BOT_DB=palia-hunters-chapaa.db
```

If `PALIA_BOT_DB` is omitted locally, the bot uses `palia-hunters-chapaa.db` in the working directory.

## 3. Configure the Discord game channel

Edit:

```text
src/main/resources/bot.properties
```

Set:

```properties
game.channel.id=123456789012345678
```

Leave it blank if `/join` should work in any normal text channel.

## 4. Proof rules

The default configuration requires at least one screenshot:

```properties
proof.required=true
proof.max.attachments=5
```

Set `proof.required=false` if you want moderators to allow screenshot-free/honor-system submissions.

## 5. Add Hunters & Chapaa board movement

Edit:

```text
src/main/resources/board-movement.json
```

Format:

```json
{
  "12": 25,
  "21": 36,
  "47": 28,
  "67": 48
}
```

Those numbers are examples only. Replace them with the real board's shortcut/setback positions.

## 6. Edit challenges

All 90 challenge descriptions are stored in:

```text
src/main/resources/challenges.json
```

# Proof verification workflow

## Player submits proof

Inside their assigned thread, the player runs:

```text
/complete proof1:<screenshot> proof2:<screenshot>
```

The bot responds with a submission ID, for example:

```text
Submission ID: 17
Status: awaiting moderator verification
```

The player **cannot roll again** while submission 17 is pending.

They can check it with:

```text
/proof-status
```

## Moderator reviews proof

The moderator views the screenshots in the player's `/complete` message and then runs:

```text
/verify submission:17 action:Approve
```

Optional note:

```text
/verify submission:17 action:Approve reason:Proof clearly shows the requested starred fish.
```

Approval does all of the following:

- changes the submission to `APPROVED`
- marks the board space completed
- adds it to the player's verified trail
- unlocks `/roll`
- if it is Space 90, declares the player the winner

To reject proof:

```text
/verify submission:17 action:Reject reason:The screenshot does not show the requested item clearly.
```

Rejection:

- changes the submission to `REJECTED`
- keeps the challenge active
- unlocks `/complete` so the player can submit replacement screenshots
- does **not** unlock `/roll`

# Main player commands

```text
/join
/roll
/challenge
/complete
/proof-status
/position
/trail
/leaderboard
```

Moderator commands:

```text
/verify
/reset
```

# Railway deployment — keep the bot online 24/7

The repository now contains:

```text
Dockerfile
railway.json
.dockerignore
```

You do not need to keep IntelliJ or your computer running after Railway is hosting it.

## 1. Put the project on GitHub

Create a GitHub repository and push this project to it.

Do **not** commit your Discord bot token.

## 2. Create a Railway project

In Railway:

1. Create a new project.
2. Choose **Deploy from GitHub repo**.
3. Select this repository.
4. Railway will detect the included `Dockerfile` and build the bot.

The bot is a Discord gateway process, so it does not need a public HTTP port or domain.

## 3. Add the Discord token

Under the Railway service's **Variables**, add:

```text
PALIA_BOT_TOKEN=your_real_discord_bot_token
```

## 4. Add persistent storage

This step is critical because SQLite is a file-based database.

Create a Railway **Volume** and mount it at:

```text
/data
```

The included Dockerfile defaults the database location to:

```text
/data/palia-hunters-chapaa.db
```

You can explicitly add this Railway variable too:

```text
PALIA_BOT_DB=/data/palia-hunters-chapaa.db
```

With the volume attached, player positions and verification history survive normal container redeploys/restarts.

Without a persistent volume, do not rely on SQLite data surviving a deployment.

## 5. Deploy

Deploy the Railway service and view its logs. A successful startup ends with a message similar to:

```text
Palia Hunters & Chapaa bot is online as YourBotName
```

The Discord bot should then appear online even when your personal computer is off.

# Suggested testing sequence

Use a test Discord server before putting the bot in your community server.

1. `/join`
2. Enter the created player thread.
3. `/roll`
4. Try `/roll` again — it should be blocked.
5. `/complete proof1:<image>`
6. Try `/roll` — it should still be blocked because verification is pending.
7. `/proof-status` — should show `PENDING`.
8. As a moderator, run `/verify submission:<id> action:Approve`.
9. `/proof-status` — should now show `APPROVED`.
10. `/roll` — should now work.
11. Submit another challenge and reject it.
12. Confirm `/roll` remains blocked but `/complete` allows replacement proof.
13. Restart the Java process and confirm `/position`, `/trail`, and `/proof-status` still show the same data.

# Database tables

The bot automatically creates/migrates these SQLite tables:

- `players`
- `completed_spaces`
- `proof_submissions`
- `proofs`

Existing databases from the earlier starter version are migrated by adding the new `proof_pending` and `submission_id` columns when missing.

# Important proof-storage note

The bot stores Discord attachment URLs and metadata. Discord attachment URLs may not be suitable as permanent archival storage forever. For a casual community challenge this is usually enough for immediate moderator review, but a future enhancement could copy each submitted screenshot into a dedicated Discord proof-review channel so the review record stays easier to browse.
