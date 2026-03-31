# forloopsjava

## Roblox Rare Username Finder

A complete Java desktop application with a dark-themed GUI for finding available Roblox usernames.

### Features
- **Generate & Check** – create usernames by length, prefix, suffix, and character set (a-z, A-Z, 0-9, _)
- **From File** – load a `.txt` file (one username per line) and check all of them; also supports pasting usernames directly
- **Live Results table** – colour-coded: green = Available, red = Taken, yellow = Invalid
- **Filter** – show All / Available Only / Taken Only / Invalid Only
- **Export** – save all available usernames to a `.txt` file
- **Stop** – cancel the check at any time

### Requirements
- Java 11 or later (uses `java.net.http.HttpClient`)

### Run
```bash
javac RobloxUsernameFinder.java
java RobloxUsernameFinder
```

### How it works
Usernames are validated against the official Roblox auth endpoint:
`POST https://auth.roblox.com/v1/usernames/validate`

A 350 ms delay between requests avoids rate-limiting.
