/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/*
 * XDataCacheManager manages the life cycle of cached data.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public final class XDataCacheManager {
   /**
    * Register one data cache.
    */
   public static void register(XDataCache cache) {
      WeakReference ref = new WeakReference(cache);

      synchronized(list) {
         list.add(ref);
      }
   }

   /**
    * Clear cached data.
    */
   public static void clearCache() {
      synchronized(list) {
         int cnt = list.size();

         for(int i = cnt - 1; i >= 0; i--) {
            WeakReference ref = list.get(i);
            XDataCache cache = ref == null ? null : (XDataCache) ref.get();

            if(cache == null) {
               list.remove(i);
               continue;
            }

            cache.clear();
         }
      }
   }

   /**
    * XDataCache caches data.
    */
   public static interface XDataCache {
      public void clear();
   }

   private static final List<WeakReference> list = new ArrayList();
}
