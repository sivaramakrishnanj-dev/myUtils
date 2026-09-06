package com.imagegen;

/**
 * Every failure the CLI reports deliberately. Carries the machine-readable
 * {@code code}, the exit status, and a {@code hint} naming the next action so a
 * calling agent can recover without guessing.
 */
public class CliException extends RuntimeException {

    private final ExitCode exitCode;
    private final String code;
    private final String hint;

    public CliException(ExitCode exitCode, String code, String message, String hint) {
        super(message);
        this.exitCode = exitCode;
        this.code = code;
        this.hint = hint;
    }

    public CliException(ExitCode exitCode, String code, String message, String hint, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.code = code;
        this.hint = hint;
    }

    public static CliException usage(String message, String hint) {
        return new CliException(ExitCode.USAGE, "USAGE", message, hint);
    }

    public static CliException config(String message, String hint) {
        return new CliException(ExitCode.CONFIG, "CONFIG", message, hint);
    }

    public static CliException io(String message, String hint, Throwable cause) {
        return new CliException(ExitCode.IO, "IO", message, hint, cause);
    }

    public ExitCode exitCode() {
        return exitCode;
    }

    public String code() {
        return code;
    }

    public String hint() {
        return hint;
    }
}
