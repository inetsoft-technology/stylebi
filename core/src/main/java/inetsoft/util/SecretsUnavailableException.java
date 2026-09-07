/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.util.log.LogLevel;

/**
 * Thrown when a credential that is referenced by a secret id cannot be resolved from the
 * configured secrets manager, either because the secret could not be fetched, because it does
 * not exist, or because its content is not a valid credential payload.
 * <p>
 * This is distinct from a credential that is simply not configured, which is represented by an
 * empty credential id and never raises this exception.
 */
public class SecretsUnavailableException extends MessageException {
   /**
    * Creates a new instance of SecretsUnavailableException. The stack trace is not dumped
    * because the condition is already logged with the full context at the point of failure.
    *
    * @param message the error message.
    */
   public SecretsUnavailableException(String message) {
      super(message, LogLevel.ERROR, false);
   }

   /**
    * Creates a new instance of SecretsUnavailableException.
    *
    * @param message the error message.
    * @param cause   the root cause of this exception.
    */
   public SecretsUnavailableException(String message, Throwable cause) {
      this(message);
      initCause(cause);
   }
}
