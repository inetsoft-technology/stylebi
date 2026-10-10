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
package inetsoft.mv;

/**
 * Thrown when an MV build's query failed while streaming its rows (e.g. a database
 * error or dropped connection), as opposed to a real user cancel/interrupt. Unlike
 * {@link inetsoft.util.CancelledException}, this is a genuine failure and must be
 * reported as one instead of being silently treated as a cancel (Bug #78117).
 */
public class MVLoadFailedException extends RuntimeException {
   public MVLoadFailedException(String mvName, Throwable cause) {
      super("Failed to read the data of materialized view " + mvName + ": " +
               cause.getMessage(), cause);
   }
}
