package com.ziv.util;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;

/** Logs exception locations and causes without exposing request values or SQL parameters. */
public final class ErrorLogUtil {
    private ErrorLogUtil() {
    }

    public static void log(Logger logger, String context, Throwable exception) {
        StringBuilder detail = new StringBuilder(context);
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        append(detail, exception, visited, "\n");
        logger.error("{}", detail);
    }

    private static void append(StringBuilder detail, Throwable exception, Set<Throwable> visited, String prefix) {
        if (exception == null || !visited.add(exception)) return;
        detail.append(prefix).append(exception.getClass().getName());
        if (exception instanceof SQLException sql) {
            detail.append(" [SQLState=").append(sql.getSQLState())
                    .append(", errorCode=").append(sql.getErrorCode()).append(']');
        }
        for (StackTraceElement frame : exception.getStackTrace()) {
            detail.append("\n\tat ").append(frame);
        }
        for (Throwable suppressed : exception.getSuppressed()) {
            append(detail, suppressed, visited, "\nSuppressed: ");
        }
        append(detail, exception.getCause(), visited, "\nCaused by: ");
        if (exception instanceof SQLException sql) {
            append(detail, sql.getNextException(), visited, "\nNext SQL exception: ");
        }
    }
}
