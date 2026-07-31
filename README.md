# MSR Telegram Chat

A lightweight Paper/Purpur plugin that bridges your Minecraft server chat with a Telegram group.

Players can chat with Telegram users in real time, receive death notifications, and fully customize message formats using configurable placeholders.

## Features

* 💬 Minecraft → Telegram chat bridge
* 📥 Telegram → Minecraft chat bridge
* ☠️ Player death messages sent to Telegram
* ⚡ Asynchronous message sending and polling
* 🎨 Fully configurable message formats
* 🧩 Placeholder support for Telegram user information
* 🪶 Lightweight with minimal dependencies

## Requirements

* Java 21+
* Paper / Purpur 1.21+

## Installation

1. Download the latest release.
2. Place the plugin into your `plugins` folder.
3. Start the server.
4. Edit the generated `config.yml`.
5. Restart or reload the server.

## Configuration

Example configuration:

```yaml
telegram:
  enabled: true
  bot-token: "YOUR_BOT_TOKEN"
  chat-id: "YOUR_CHAT_ID"

  sender-interval-ms: 250
  sender-batch-size: 3
  send-timeout-seconds: 8
  max-queue-size: 500
  requeue-failed-messages: false

  receive-enabled: true
  poll-interval-ticks: 40

format:
  message: "[MC] {player}: {message}"
  telegram-message: "[TG] {user}: {message}"
```

## Minecraft Placeholders

### `format.message`

| Placeholder | Description           |
| ----------- | --------------------- |
| `{player}`  | Minecraft player name |
| `{message}` | Chat message          |

### `format.telegram-message`

| Placeholder    | Description                                                      |
| -------------- | ---------------------------------------------------------------- |
| `{user}`       | Best available Telegram display name (full name → username → ID) |
| `{username}`   | Telegram username (`@username`)                                  |
| `{first_name}` | Telegram first name                                              |
| `{last_name}`  | Telegram last name                                               |
| `{full_name}`  | Telegram full name                                               |
| `{message}`    | Telegram message                                                 |

If a requested Telegram field is unavailable, the plugin automatically falls back to the best available display name, ensuring that messages are always displayed correctly.

## Permissions

No permissions are required.

## Building

Using Maven Wrapper (recommended):

```bash
./mvnw clean package
```

Or with a local Maven installation:

```bash
mvn clean package
```

The compiled plugin will be generated in:

```
target/
```

## License

This project is licensed under the MIT License.
