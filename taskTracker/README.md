# Task Tracker CLI

A lightweight CLI task tracker built with Java, SQLite, and Picocli. Stateless per invocation — each run connects to a local SQLite DB file, performs the operation, and exits.

## Build

```bash
mvn clean package
```

Produces a fat JAR at `target/task-tracker-1.0.0.jar`.

## Usage

```bash
alias task="java -jar /path/to/task-tracker-1.0.0.jar"
```

### Add a task

```bash
task add "Fix login bug" --due "2026-04-10T17:00" --tags "backend,urgent" --priority HIGH --notes "Blocks release"
```

### List tasks

```bash
task list                                        # all tasks
task list --status TODO                          # by status (TODO, IN_PROGRESS, DONE)
task list --tag "backend"                        # by tag
task list --priority URGENT                      # by priority (LOW, MEDIUM, HIGH, URGENT)
task list --due-before "2026-04-10T00:00"        # due on or before
task list --due-after "2026-04-05T00:00"         # due on or after
task list --status TODO --tag "urgent"           # combine filters
```

### Get a task

```bash
task get 1
```

### Update a task

```bash
task update 1 --status DONE
task update 1 --title "New title"
task update 1 --due "2026-04-12T10:00"
task update 1 --priority LOW
task update 1 --notes "Completed via slack-companion"
task update 1 --tags "backend,p1"                # replaces all tags
```

### Delete a task

```bash
task delete 1
```

### Custom DB path

```bash
task --db /path/to/custom.db list
```

Default DB location: `~/.task-tracker/tasks.db`

## Output

All commands output JSON for easy parsing and tool integration.

## Tech Stack

- Java 17
- [Picocli](https://picocli.info/) — CLI framework
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) — embedded database
- Maven Shade Plugin — fat JAR packaging
