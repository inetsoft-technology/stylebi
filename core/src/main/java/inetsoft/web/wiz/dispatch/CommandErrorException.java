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
package inetsoft.web.wiz.dispatch;

import java.util.Collections;
import java.util.List;

/**
 * Raised when a composer service reported failure by dispatching one or more
 * {@code MessageCommand}s of {@code Type.ERROR} and then returning normally.
 *
 * <p>Without this, such a failure is invisible to an agent-driven caller: the service returns,
 * nothing throws, and the tool reports success while nothing changed.
 */
public class CommandErrorException extends Exception {
   public CommandErrorException(List<String> errors) {
      super(String.join("; ", errors));
      this.errors = List.copyOf(errors);
   }

   /** The individual error messages, in the order the service reported them. */
   public List<String> getErrors() {
      return Collections.unmodifiableList(errors);
   }

   private final List<String> errors;
}
