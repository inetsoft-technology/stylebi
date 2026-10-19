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
package inetsoft.util.script;

import java.util.*;

/**
 * Marker cause attached to the {@link ScriptException} of a guest ReferenceError, a script
 * that read a name that is not defined (Bug #78685). Inert for callers that do not look for
 * it; a strict calc table range condition looks for it to report the missing name.
 */
public class UndefinedNameException extends RuntimeException {
   public UndefinedNameException(String name) {
      super("Name \"" + name + "\" is not defined");
      this.name = name;
   }

   public String getName() {
      return name;
   }

   /**
    * Find this exception in the cause chain of {@code failure}.
    */
   public static UndefinedNameException find(Throwable failure) {
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof UndefinedNameException) {
            return (UndefinedNameException) t;
         }
      }

      return null;
   }

   private final String name;
}
