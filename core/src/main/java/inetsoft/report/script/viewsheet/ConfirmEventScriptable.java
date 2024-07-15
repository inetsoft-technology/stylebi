/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.viewsheet;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * The confirm event scriptable in viewsheet scope.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class ConfirmEventScriptable extends ScriptableObject {
   /**
    * Create a confirm event scriptable.
    */
   public ConfirmEventScriptable() {
      super();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ConfirmEvent";
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      super.put(name, start, value);

      if("confirmed".equals(name)) {
         confirmed = "true".equals(value.toString());
      }
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if("confirmed".equals(name)) {
         return confirmed;
      }

      return super.get(name, start);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      return "confirmed".equals(name);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      return new String[]{"confirmed"};
   }

   private boolean confirmed = false;
}
