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
package inetsoft.util.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching of class methods.
 */
public class MethodCache {
   public Method getMethod(Class cls, String name, Class ...params) {
      if(name == null) {
         return null;
      }

      final Key key = new Key(cls, name, params);
      return methods.computeIfAbsent(key, k -> key.getMethod());
   }

   private static class Key {
      public Key(Class cls, String name, Class ...params) {
         this.cls = cls;
         this.name = name;
         this.params = params;
      }

      public Method getMethod() {
         try {
            return cls.getMethod(name, params);
         }
         catch(NoSuchMethodException e) {
            LOG.error("Failed to get method: " + name + " in " + cls, e);
            return null;
         }
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         Key key = (Key) o;
         return Objects.equals(name, key.name) && cls == key.cls &&
            Arrays.equals(params, key.params);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, cls);
      }

      private final Class cls;
      private final String name;
      private final Class[] params;
   }

   private static final Logger LOG = LoggerFactory.getLogger(JSObject.class);
   private final Map<Object, Method> methods = new ConcurrentHashMap<>();
}
