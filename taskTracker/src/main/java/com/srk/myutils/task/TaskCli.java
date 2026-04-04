package com.srk.myutils.task;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "task", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "CLI Task Tracker with SQLite",
        subcommands = {TaskCli.Add.class, TaskCli.ListCmd.class, TaskCli.Get.class, TaskCli.Update.class, TaskCli.Delete.class})
public class TaskCli implements Runnable {

    @Option(names = "--db", description = "Path to SQLite DB", defaultValue = "${user.home}/.task-tracker/tasks.db", scope = CommandLine.ScopeType.INHERIT)
    String dbPath;

    TaskDao dao() { return new TaskDao(new DbHelper(dbPath)); }

    @Override
    public void run() { new CommandLine(this).usage(System.out); }

    public static void main(String[] args) { System.exit(new CommandLine(new TaskCli()).execute(args)); }

    @Command(name = "add", description = "Add a new task")
    static class Add implements Runnable {
        @CommandLine.ParentCommand TaskCli parent;
        @CommandLine.Parameters(index = "0", description = "Task title") String title;
        @Option(names = "--due", description = "Due date/time (ISO-8601)") String due;
        @Option(names = "--tags", description = "Comma-separated tags", split = ",") java.util.List<String> tags;

        @Override
        public void run() {
            try {
                Task t = parent.dao().add(title, due, tags != null ? tags : java.util.List.of());
                System.out.println(t.toJson());
            } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
        }
    }

    @Command(name = "list", description = "List tasks with optional filters")
    static class ListCmd implements Runnable {
        @CommandLine.ParentCommand TaskCli parent;
        @Option(names = "--status", description = "Filter by status (TODO, IN_PROGRESS, DONE)") String status;
        @Option(names = "--tag", description = "Filter by tag") String tag;
        @Option(names = "--due-before", description = "Filter tasks due before (inclusive)") String dueBefore;
        @Option(names = "--due-after", description = "Filter tasks due after (inclusive)") String dueAfter;

        @Override
        public void run() {
            try {
                var tasks = parent.dao().list(status, tag, dueBefore, dueAfter);
                System.out.println("[" + tasks.stream().map(Task::toJson).reduce((a, b) -> a + "," + b).orElse("") + "]");
            } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
        }
    }

    @Command(name = "get", description = "Get a task by ID")
    static class Get implements Runnable {
        @CommandLine.ParentCommand TaskCli parent;
        @CommandLine.Parameters(index = "0", description = "Task ID") int id;

        @Override
        public void run() {
            try {
                Task t = parent.dao().get(id);
                System.out.println(t == null ? "{\"error\":\"Task not found\"}" : t.toJson());
            } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
        }
    }

    @Command(name = "update", description = "Update a task")
    static class Update implements Runnable {
        @CommandLine.ParentCommand TaskCli parent;
        @CommandLine.Parameters(index = "0", description = "Task ID") int id;
        @Option(names = "--status", description = "New status") String status;
        @Option(names = "--due", description = "New due date/time") String due;
        @Option(names = "--tags", description = "Replace tags (comma-separated)", split = ",") java.util.List<String> tags;

        @Override
        public void run() {
            try {
                Task t = parent.dao().update(id, status, due, tags);
                System.out.println(t == null ? "{\"error\":\"Task not found\"}" : t.toJson());
            } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
        }
    }

    @Command(name = "delete", description = "Delete a task by ID")
    static class Delete implements Runnable {
        @CommandLine.ParentCommand TaskCli parent;
        @CommandLine.Parameters(index = "0", description = "Task ID") int id;

        @Override
        public void run() {
            try {
                boolean ok = parent.dao().delete(id);
                System.out.println(ok ? "{\"deleted\":" + id + "}" : "{\"error\":\"Task not found\"}");
            } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
        }
    }
}
