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

import inetsoft.util.ResourceCache;

import java.util.function.Consumer;

/**
 * This is the wrapper for native java objects.
 */
public class ScriptCache {
   public ScriptCache(int max, long timeout) {
      cache = new ResourceCache(max, timeout) {
         @Override
         protected Object create(Object key) throws Exception {
            return compile((String) key);
         }
      };
   }

   public Object get(String script, ScriptEnv senv, Consumer<Exception> handler) {
      this.senv.set(senv);
      this.handler.set(handler);

      try {
         return cache.get(script);
      }
      catch(ScriptException ex) {
         throw ex;
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
      finally {
         this.senv.remove();
         this.handler.remove();
      }
   }

   private Object compile(String script) {
      ScriptEnv senv = this.senv.get();

      try {
         return senv.compile(script, true);
      }
      catch(Exception ex) {
         this.handler.get().accept(ex);
         return null;
      }
   }

   private ResourceCache cache;
   private static final ThreadLocal<ScriptEnv> senv = new ThreadLocal<>();
   private static final ThreadLocal<Consumer<Exception>> handler = new ThreadLocal<>();
}
