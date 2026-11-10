# Online play (Firebase)

"Play online" lets two people play on their own phones. One creates a game and gets a
6-character code (for example `K7PX2M`). They send it to the other person, who joins with it.
The games are stored in Firebase Realtime Database. Players sign in anonymously, so nobody needs an
account. Everything fits in the free **Spark** plan. There is no server code (Cloud Functions
need a paid plan).

The Firebase config file (`app/google-services.json`) is **not** in git. Without it the app still
builds, and "Play online" says that online mode is not configured.

## Setup

1. Open <https://console.firebase.google.com>, choose **Create a project** and give it a name, e.g. `satranc`.
   Google Analytics is not needed.
2. In the project overview choose **Add app → Android**:
   - Package name: `com.thunderbascraft.satranc`
   - App nickname: optional. The SHA-1 is not needed (anonymous sign-in does not use it).
3. **Build → Authentication → Get started → Sign-in method**: enable **Anonymous**.
4. **Build → Realtime Database → Create database**:
   - Location: **Belgium (europe-west1)**
   - Start in **locked mode**
5. In the database's **Rules** tab, replace everything with the contents of
   [`database.rules.json`](../database.rules.json) from this repository and choose **Publish**.
6. Go to **Project settings → Your apps** and download `google-services.json`. Put it in `app/`:
   `app/google-services.json` (it is listed in `.gitignore`). If the file contains no database URL
   (`firebase_url`), the app uses the default database
   `https://<project-id>-default-rtdb.europe-west1.firebasedatabase.app`. If you picked a different
   location, download the file again after creating the database.
7. Build the app again. The Google Services Gradle plugin is applied automatically when the file is there.

## Data model

```
/games/{CODE}
  white, black     uid of each player (the empty slot is filled by whoever joins)
  status           "waiting" | "active" | "finished"
  moves            the moves in UCI, separated by spaces ("e2e4 e7e5 g1f3")
  moveCount        number of moves
  result           "1-0" | "0-1" | "1/2-1/2"   (only when finished)
  reason           checkmate | stalemate | insufficient | repetition | fifty |
                   resign | agreement | abandoned | cancelled
  drawOffer        uid of the player offering a draw, if any
  createdAt, lastMoveAt   server timestamps
  presence/{uid}   { online: bool, since: timestamp }  (set to offline by onDisconnect)
```

The moves are one string, not a list, so that the rules can check that moves are only added at
the end, one at a time.

Each client checks moves with chess-core. A move is written in a transaction that also checks the
number of moves and whose turn it is, so two clients cannot both add a move. The client that makes
the last move also writes the result (checkmate, stalemate, insufficient material, threefold
repetition, 50-move rule).

If a player is offline for 3 minutes, the other player can claim the win.

## Security rules

[`database.rules.json`](../database.rules.json):

- Only signed-in users can read or write. A game can only be read by its code; nobody can list the games.
- A new game must start as `waiting`. The creator can only put their own uid in it.
- Anyone signed in can join a `waiting` game. They can only fill the empty colour with their own uid,
  and the game becomes `active`.
- After that, only the two players can write. Players, codes and `createdAt` cannot be changed.
- Moves can only be added at the end, one per write, and only by the player whose turn it is
  (white on odd `moveCount`, black on even). The move format is checked.
- A finished game stays finished, and its result cannot be changed.
- Each player can only change their own presence. Unknown fields are rejected.

The rules cannot check chess legality. A modified client could write a wrong result or an illegal move.
This mode is for playing with friends, not for rated games.

## Old games

Games that have not changed for 7 days are treated as expired: they cannot be joined and are not
offered for resuming. Nothing deletes them automatically (that would need a server). Delete
`/games` in the Firebase console from time to time if you want.
