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
package inetsoft.analytic.composition.command;

import inetsoft.util.Tool;

/**
 * Pending command.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class PendingCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public PendingCommand() {
      super();
   }

   /**
    * Constructor.
    * @param name the assembly entry name.
    */
   public PendingCommand(String name) {
      this();
      put("name", name);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof PendingCommand)) {
         return false;
      }

      PendingCommand pcmd = (PendingCommand) obj;
      return Tool.equals(get("name"), pcmd.get("name"));
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + '(' + get("name") + ')';
   }
}
