

# MSR Telegram Chat Bridge

Production-ready plugin for Paper / Purpur (1.21.x) that synchronizes Minecraft chat with Telegram.

---

## Overview

MSR Telegram Chat Bridge provides a stable and scalable integration between a Minecraft server and a Telegram group.

The plugin is designed with production concerns in mind:
- non-blocking architecture (no server freezes)
- message queue buffering
- rate-safe Telegram communication
- configurable batching and retry logic
- clean separation between event handling and network I/O

---

## Features

- Minecraft → Telegram chat synchronization
- Telegram → Minecraft chat synchronization (polling-based)
- Asynchronous message processing
- Internal queue to prevent lag spikes
- Configurable formatting
- Configurable rate limits and batching
- Graceful handling of Telegram API errors
- Lightweight and dependency-minimal

---

## Architecture

### Outgoing Flow (Minecraft → Telegram)

1. Player sends a message in Minecraft
2. Plugin captures AsyncChatEvent
3. Message is placed into a thread-safe queue
4. Background worker sends messages to Telegram in batches

### Incoming Flow (Telegram → Minecraft)

1. Plugin polls Telegram Bot API (`getUpdates`)
2. Filters updates by chat_id
3. Extracts valid messages
4. Sends them into Minecraft chat on main thread

---

## Installation

1. Build the plugin:
```bash
mvn clean package
```

2. Place the generated `.jar` into:
```
/plugins/
```

3. Start the server

4. Configure `config.yml`

---

## Configuration

```yaml
telegram:
  enabled: true
  bot-token: "YOUR_BOT_TOKEN"
  chat-id: "-100XXXXXXXXXX"

  sender-interval-ms: 250
  sender-batch-size: 3
  send-timeout-seconds: 8
  max-queue-size: 500
  requeue-failed-messages: false

polling:
  enabled: true
  interval-ticks: 40

format:
  to-telegram: "[MC] {player}: {message}"
  to-minecraft: "[TG] {user}: {message}"
```

---

## Telegram Setup

### Bot Token
Create a bot using BotFather and obtain a token.

### Chat ID
Use tools like:
- @userinfobot
- @RawDataBot

Add the bot to your group and send a message to retrieve the chat_id.

---

## Commands

```
/msrtg reload  - Reload configuration
/msrtg test    - Send test message to Telegram
```

---

## Performance & Stability

- No blocking operations on main thread
- Message queue prevents lag spikes
- Batch sending reduces API overhead
- Configurable retry logic
- Safe under high chat load

---

## Known Limitations

- Uses polling instead of webhook
- Text messages only
- No backend integration (planned)

---

## Future Improvements

- Backend integration (Spring API bridge)
- Webhook-based Telegram updates
- Message moderation & filtering
- Multi-channel support
- Attachments & media support

---

## Troubleshooting

Check:
- Bot token correctness
- Chat ID validity
- Bot permissions in group
- Server logs for Telegram responses

---

## License

MIT / Free to use
