package xyz.stackpancakes.shell.core;

/**
 * Enumeration representing the result of executing a command.  Each value
 * corresponds to a specific outcome and the {@link #toString()} method
 * provides a human‑readable description for user feedback.
 */
public enum CommandResult
{
    Success,
    Exit,
    Failure,
    InvalidSyntax,
    AlreadyExists,
    PermissionDenied,
    AccessDenied,
    PathNotFound,
    UnknownOption,
    UnknownError;

    @Override
    public String toString()
    {
        return switch (this)
        {
            case InvalidSyntax -> "Invalid syntax";
            case AlreadyExists -> "Already exists";
            case PermissionDenied -> "Permission denied";
            case AccessDenied -> "Access denied";
            case PathNotFound -> "Path not found";
            case UnknownOption -> "Unknown option";
            default -> name();
        };
    }
}