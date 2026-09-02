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
package inetsoft.sree.internal.cluster;

import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;

/**
 * {@code AffinityCallException} is thrown when a request could not be executed on the cluster node
 * that owns the target resource. This is a transient, retriable condition: it means the call was
 * never delivered or never answered, typically because the owning node left the cluster or the
 * call timed out during a topology change. It does not indicate that the work itself failed.
 *
 * <p>It extends {@link MessageException} so the user is shown a message saying the operation can
 * be retried, instead of the bare 500 that a plain runtime exception produces. It is logged at
 * WARN without a stack trace: during a rolling update this is expected, and the stack of a
 * timeout carries no useful information.
 */
public class AffinityCallException extends MessageException {
   public AffinityCallException(String message) {
      super(message, LogLevel.WARN, false);
   }

   public AffinityCallException(String message, Throwable cause) {
      this(message);
      initCause(cause);
   }
}
