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
package inetsoft.report.script.viewsheet;

import inetsoft.util.script.graal.ScriptScope;

/**
 * The confirm event scriptable in viewsheet scope.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class ConfirmEventScriptable implements ScriptScope {
   /**
    * Create a confirm event scriptable.
    */
   public ConfirmEventScriptable() {
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   public String getClassName() {
      return "ConfirmEvent";
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void putMember(String name, Object value) {
      if("confirmed".equals(name)) {
         confirmed = "true".equals(value.toString());
      }
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      if("confirmed".equals(name)) {
         return confirmed;
      }

      return null;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean hasMember(String name) {
      return "confirmed".equals(name);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getMemberKeys() {
      return new String[]{"confirmed"};
   }

   private boolean confirmed = false;
}
