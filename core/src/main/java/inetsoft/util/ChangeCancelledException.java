/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * A query of an assembly that only newer changes of the viewsheet or newer queries of the
 * assembly cancelled, and that the request running it should skip, going on with its other
 * assemblies: a newer change reset the assembly or a newer query runs it, so the newer request
 * loads it, or it was given up on with a warning. Any other cancel, e.g. the user's, stops the
 * request (#78024).
 */
public class ChangeCancelledException extends CancelledException {
   /**
    * Create the exception.
    * @param cause the cancel of the query.
    */
   public ChangeCancelledException(CancelledException cause) {
      super(cause.getMessage());
      initCause(cause);
   }
}
