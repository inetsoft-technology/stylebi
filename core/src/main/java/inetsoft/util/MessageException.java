/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.log.LogLevel;

/**
 * Exception implementation that holds information about how it is to be logged.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public class MessageException extends RuntimeException implements LogException {
   public MessageException(Exception cause) {
      this(cause.getMessage(), cause);
   }

   public MessageException(String msg, Exception cause) {
      this(msg);
      initCause(cause);
   }

   /**
    * Creates a new instance of MessageException. The log level is
    * Level.SEVERE, the warning level is MessageCommand.WARNING and
    * the stack trace is printed by default.
    *
    * @param message the error message.
    */
   public MessageException(String message) {
      this(message, LogLevel.INFO, true);
   }

   /**
    * Creates a new instance of MessageException. The warning level is
    * MessageCommand.WARNING and the stack trace is printed by default.
    *
    * @param message the error message.
    * @param logLevel the logging level at which to print the exception.
    */
   public MessageException(String message, LogLevel logLevel) {
      this(message, logLevel, true);
   }

   /**
    * Creates a new instance of MessageException.
    *
    * @param message the error message.
    * @param logLevel the logging level at which to print the exception.
    * @param dumpStack <code>true</code> if the stack trace should be printed to
    *                  the log; <code>false</code> otherwise.
    */
   public MessageException(String message, LogLevel logLevel, boolean dumpStack) {
      this(message, logLevel, dumpStack, ConfirmException.WARNING);
   }

   /**
    * Creates a new instance of MessageException.
    *
    * @param cause the root cause of this exception.
    * @param logLevel the logging level at which to print the exception.
    * @param dumpStack <code>true</code> if the stack trace should be printed to
    *                  the log; <code>false</code> otherwise.
    */
   public MessageException(Throwable cause, LogLevel logLevel, boolean dumpStack) {
      this(cause.getMessage(), logLevel, dumpStack, ConfirmException.WARNING);
      initCause(cause);
   }

   /**
    * Creates a new instance of MessageException.
    *
    * @param message the error message.
    * @param logLevel the logging level at which to print the exception.
    * @param dumpStack <code>true</code> if the stack trace should be printed to
    *                  the log; <code>false</code> otherwise.
    * @param warningLevel the warning level at which to warn the user.
    */
   public MessageException(String message, LogLevel logLevel, boolean dumpStack,
                           int warningLevel) {
      super(message);
      this.logLevel = logLevel;
      this.dumpStack = dumpStack;
      this.warningLevel = warningLevel;
   }

   /**
    * Gets the warning level at which to warn the user.
    *
    * @return the warning level. The level be one of the constants defined in
    *         {@link inetsoft.uql.asset.ConfirmException}.
    */
   public int getWarningLevel() {
      return warningLevel;
   }

   /**
    * Gets the logging level at which to print this exception.
    *
    * @return the logging level..
    */
   @Override
   public LogLevel getLogLevel() {
      return logLevel;
   }

   /**
    * Determines if the stack trace should be printed to the log.
    *
    * @return <code>true</code> if the stack trace should be printed;
    *         <code>false</code> otherwise.
    */
   public boolean isDumpStack() {
      return dumpStack;
   }

   /**
    * Get the keywords.
    * @return the keywords.
    */
   public String getKeywords() {
      return keywords;
   }

   /**
    * Set the keyword.
    */
   public void setKeywords(String keywords) {
      this.keywords = keywords;
   }

   private boolean dumpStack = true;
   private LogLevel logLevel = LogLevel.ERROR;
   private int warningLevel = ConfirmException.WARNING;
   private String keywords;
}
