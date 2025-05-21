# AuctionHouse

A passively replicated auction system with 2-way authentication and session management.

![screenshot](https://github.com/user-attachments/assets/a998e1a8-c839-491c-8ee9-863013bfa265)

---

## Setup

### Compile

From the root of the project:

```bash
(cd Server && javac *.java) && (cd Client && javac *.java)
```

---

## Key Exchange

The server's public key must be manually copied to the client.

Run in project root:
```bash
(cd Server && java backend & sleep 1 && cp serverKey.pub ../Client/)
```

**OR**

1. In `/Server`, generate the key:

   ```bash
   java backend
   ```

   This creates `serverKey.pub`.

2. Copy `serverKey.pub` into `/Client`.

This runs `backend`, waits a moment for the key to be created, then copies it to `/Client`.

---

## Running the Server

From the `/Server` directory:

1. Start the RMI registry:

   ```bash
   rmiregistry
   ```

2. In separate terminals, run one or more replicas:

   ```bash
   java Replica <id>
   ```

   The first started becomes the primary.

3. Run the frontend:

   ```bash
   java Frontend
   ```

---

## Running the Client

From the `/Client` directory (with `serverKey.pub` present):

```bash
java Client <email> <command> [args...]
```

### Commands

- `getSpec <itemID>` – get item details
- `newAuction <name> <reservePrice> <description>` – create auction
- `closeAuction <itemID>` – close auction
- `listItems` – list active auctions
- `bid <itemID> <amount>` – place a bid

### Examples

```bash
java Client person@domain.com newAuction chair 50 "an old chair"
java Client person@domain.com listItems
```
