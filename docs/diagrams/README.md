# HelpDesk diagrams: start here

Read the folders **in number order**. Each one answers the question the one before it raises: what the pieces are, where they run, what they store, how a request moves through them, what rules apply, and finally what the code looks like.

| # | Folder | Diagram type | The question it answers |
|---|---|---|---|
| 1 | [`01-system-context`](01-system-context/README.md) | Context and container | Who uses HelpDesk, what does it talk to, and what are its running parts? |
| 2 | [`02-deployment`](02-deployment/README.md) | Deployment | Where does each part run, and which ports are open? |
| 3 | [`03-data-model`](03-data-model/README.md) | ER diagram | What data is stored, and how do the tables link? |
| 4 | [`04-sequence-diagrams`](04-sequence-diagrams/README.md) | Sequence | What happens, step by step, when someone logs in, an email arrives, an agent replies…? |
| 5 | [`05-state-machines`](05-state-machines/README.md) | State | What statuses can a ticket, token, key or account be in, and what moves it on? |
| 6 | [`06-flowcharts`](06-flowcharts/README.md) | Flowchart | How are the tricky decisions made (who may see what, when the AI may answer alone)? |
| 7 | [`07-class-diagram`](07-class-diagram/README.md) | Class | Which classes exist, with which fields and methods, and who depends on whom? |

Folder 4 has its own numbered list inside. Read the sequence diagrams in that order too: login first, because every other flow assumes a signed-in user.

## How to read each type

**Context, container, deployment and flowcharts** are boxes and arrows. A box is a thing (a person, a program, a decision); an arrow is "talks to" or "goes next", labelled with how.

**ER diagram** (entity-relationship). Each box is a table with its columns. `PK` is the primary key, `FK` a foreign key pointing at another table, `UK` a unique column. The line ends show how many:

| End | Means |
|---|---|
| `\|\|` | exactly one |
| `o\|` | zero or one |
| `o{` | zero or many |
| `\|{` | one or many |

So `USERS ||--o{ REFRESH_TOKENS` reads "one user has zero or many refresh tokens".

**Sequence diagram.** Time runs top to bottom. Each column is a participant (a person, a class, the database).

| Arrow | Means |
|---|---|
| `──▶` solid | a call or a request |
| `- - ▶` dashed | the answer coming back |
| `alt` / `else` box | only one of these branches happens |
| `loop` box | repeated |
| `Note` | an explanation, not a step |

**State diagram.** Each rounded box is a status; each arrow is a change, labelled with what causes it. `●` is where things start; `◉` is where they end.

**Class diagram.** Each box is a class: its name, then fields, then methods. A solid arrow means "uses / depends on"; a hollow triangle means "implements / extends".

## The same order works for any project

1. **Context:** who uses it and what it talks to.
2. **Containers or deployment:** the running pieces.
3. **Data model:** what it stores.
4. **Sequences for the main flows:** how requests move. Start with login, then the main business flow.
5. **State machines:** for anything with a status column.
6. **Flowcharts:** for complicated decisions.
7. **Class diagrams:** only for the module you're about to change.

Outside → pieces → data → behaviour → rules → code. If a project has no diagrams, drawing these yourself, in this order, is one of the fastest ways to learn it.

All diagrams here are [Mermaid](https://mermaid.js.org/): plain text that GitHub draws. They were written from the code as it is on `main`; if the code changes, update the diagram in the same commit.
