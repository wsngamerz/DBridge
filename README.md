# DBridge
A simple bridge between Discord and a Minecraft GTNH server.

## Features
- Chat bridge between Discord and Minecraft
- Discord commands:
  - `/list` - List all online players
  - `/leaderboard <stat>` - Show the leaderboard for a stat (ServerUtilities)
- Global (Discord & In-game Chat) notifications of server events:
  - Player join/leave
  - Deaths
  - Achievements
  - Quests (BetterQuesting)
  - Server start/stop

## Installation
1. Download the latest release from the releases page
2. Add the plugin to your server's `mods` folder
3. Start the server to generate the configuration file, then stop it
4. Create a Discord bot and invite it to your server
5. Edit the configuration file (`config/dbridge.cfg`) with your bot token, guild ID, and channel ID to relay messages to
6. Create a webhook in the same channel as the bot and copy the URL
7. Edit the configuration file with the webhook URL
8. Start the server and the bot should be online
