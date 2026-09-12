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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pviewsheet scriptable in viewsheet scope.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class PViewsheetScriptable implements ScriptScope {
   /**
    * Create a viewsheet assembly scriptable.
    */
   public PViewsheetScriptable() {
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   public String getClassName() {
      return "PViewsheetVSA";
   }

   /**
    * Get a named property from the object. Auto-vivifies a child attribute
    * scriptable on first access.
    */
   @Override
   public Object getMember(String name) {
      Object obj = members.get(name);

      if(obj == null) {
         obj = new AttributeScriptable();
         members.put(name, obj);
      }

      return obj;
   }

   @Override
   public boolean hasMember(String name) {
      return true;
   }

   @Override
   public void putMember(String name, Object value) {
      members.put(name, value);
   }

   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray();
   }

   private final Map<String, Object> members = new LinkedHashMap<>();

   // NOTE (Feature #75423): the inner AttributeScriptable previously implemented
   // Rhino's Callable (returning itself when invoked as a function, to support
   // arbitrary method chains via __noSuchMethod__). The callable-as-function
   // behavior is a Rhino-specific feature; under GraalJS it would be modeled with
   // a ProxyExecutable in the Milestone 4 native-binding work. For now it is a
   // plain auto-vivifying ScriptScope (member access still chains).
   private class AttributeScriptable implements ScriptScope {
      /**
       * Get the name of the set of objects implemented by this Java class.
       */
      public String getClassName() {
         return "AttributeVSA";
      }

      /**
       * Get a named property from the object.
       */
      @Override
      public Object getMember(String name) {
         Object obj = members.get(name);

         if(obj == null || "__noSuchMethod__".equals(name)) {
            obj = new AttributeScriptable();
            members.put(name, obj);
         }

         return obj;
      }

      @Override
      public boolean hasMember(String name) {
         return true;
      }

      @Override
      public void putMember(String name, Object value) {
         members.put(name, value);
      }

      @Override
      public Object[] getMemberKeys() {
         return members.keySet().toArray();
      }

      private final Map<String, Object> members = new LinkedHashMap<>();
   }
}
