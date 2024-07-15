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

import org.mozilla.javascript.*;

/**
 * The pviewsheet scriptable in viewsheet scope.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class PViewsheetScriptable extends ScriptableObject {
   /**
    * Create a viewsheet assembly scriptable.
    */
   public PViewsheetScriptable() {
      super();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "PViewsheetVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Object obj = null;
      boolean exist = has(name, start);

      if(!exist) {
         put(name, start, obj = new AttributeScriptable());
      }
      else {
         obj = super.get(name, start);
      }

      return obj;
   }

   private class AttributeScriptable extends ScriptableObject
      implements Callable
   {
      /**
       * Get the name of the set of objects implemented by this Java class.
       */
      @Override
      public String getClassName() {
         return "AttributeVSA";
      }

      /**
       * Get a named property from the object.
       */
      @Override
      public Object get(String name, Scriptable start) {
         Object obj = null;
         boolean exist = has(name, start);

         if(!exist || "__noSuchMethod__".equals(name)) {
            put(name, start, obj = new AttributeScriptable());
         }
         else {
            obj = super.get(name, start);
         }

         return obj;
      }

      /**
       * Get the default value of the object with a given hint.
       */
      @Override
      public Object getDefaultValue(java.lang.Class hint) {
         return new Object();
      }

      /**
       * Perform the call.
       *
       * @param cx the current Context for this thread
       * @param scope the scope to use to resolve properties.
       * @param thisObj the JavaScript <code>this</code> object
       * @param args the array of arguments
       * @return the result of the call
       */
      @Override
      public Object call(Context cx, Scriptable scope,
                         Scriptable Obj, Object[] args)
      {
         return this;
      }
   }
}
