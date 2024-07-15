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

import org.mozilla.javascript.*;

import java.util.*;

/**
 * This is the wrapper for native java objects.
 */
public class JSFactory extends WrapFactory {
   @Override
   public Scriptable wrapAsJavaObject(Context cx, Scriptable scope,
                                      Object javaObject, Class staticType)
   {
      if(javaObject instanceof java.util.Date) {
         CachedObjects objs = null;

         if(javaObject.getClass() == java.util.Date.class) {
            objs = JSFactory.jdateObjs.get();
         }
         else if(javaObject.getClass() == java.sql.Date.class) {
            objs = JSFactory.dateObjs.get();
         }
         else if(javaObject.getClass() == java.sql.Timestamp.class) {
            objs = JSFactory.tsObjs.get();
         }
         else if(javaObject.getClass() == java.sql.Time.class) {
            objs = JSFactory.timeObjs.get();
         }

         if(objs != null) {
            if(objs.size() > objs.usedCount) {
               JSObject jsobj = objs.next();
               jsobj.setJavaObject(javaObject);
               return jsobj;
            }

            JSObject jsobj = new JSObject(scope, javaObject, staticType);
            objs.add(jsobj);
            objs.usedCount++;
            return jsobj;
         }
      }

      return new JSObject(scope, javaObject, staticType);
   }

   /**
    * Called to enable caching.
    */
   public static void startCache() {
      if(jdateObjs.get() == null) {
         jdateObjs.set(new CachedObjects());
      }

      if(dateObjs.get() == null) {
         dateObjs.set(new CachedObjects());
      }

      if(tsObjs.get() == null) {
         tsObjs.set(new CachedObjects());
      }

      if(timeObjs.get() == null) {
         timeObjs.set(new CachedObjects());
      }

      jdateObjs.get().reset();;
      dateObjs.get().reset();;
      tsObjs.get().reset();;
      timeObjs.get().reset();;
   }

   /**
    * Called at end of execution cycle.
    */
   public static void resetCache() {
      Arrays.asList(JSFactory.jdateObjs.get(), JSFactory.dateObjs.get(),
                    JSFactory.tsObjs.get(), JSFactory.timeObjs.get())
         .stream().filter(a -> a != null).forEach(a -> a.reset());
   }

   /**
    * Called to disable caching.
    */
   public static void stopCache() {
      jdateObjs.remove();
      dateObjs.remove();
      tsObjs.remove();
      timeObjs.remove();
   }

   private static class CachedObjects {
      private List<JSObject> objs = new ArrayList<>();
      private int usedCount = 0;

      public int size() {
         return objs.size();
      }

      public JSObject next() {
         return objs.get(usedCount++);
      }

      public void add(JSObject obj) {
         objs.add(obj);
      }

      public void reset() {
         usedCount = 0;
      }
   }

   private static ThreadLocal<CachedObjects> jdateObjs = new ThreadLocal<>();
   private static ThreadLocal<CachedObjects> dateObjs = new ThreadLocal<>();
   private static ThreadLocal<CachedObjects> tsObjs = new ThreadLocal<>();
   private static ThreadLocal<CachedObjects> timeObjs = new ThreadLocal<>();
}
