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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A query manager is used to track queries and provides API for cancelling
 * all pending queries.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class QueryManager {
   public QueryManager() {
   }

   public QueryManager(boolean cancelable) {
      this.cancelable = cancelable;
   }

   /**
    * Check if the statement is cancelled.
    */
   public static boolean isCancelled(Object stmt) {
      return cancelled.containsKey(stmt);
   }

   /**
    * Add a query statement to the manager. The statement object must have a
    * method called 'cancel' with no parameter. The cancel method is called
    * when attempting to cancel a pending query.
    */
   public synchronized void addPending(Object stmt) {
      if(stmt != null) {
         queries.add(new WeakReference(stmt));
         // @by stephenwebster, For Bug #641
         // The query should not be cancelled immediately upon adding it to
         // the query manager pending list.  In cases where a driver
         // implementation may have statement pooling, the WeakHashMap will not
         // be sufficient to remove already processed statements before they
         // are reused from the statement pool.
         cancelled.remove(stmt);
      }
   }

   /**
    * Removes a cancellable object from the manager.
    * @param stmt the object to remove from the manager
    */
   public synchronized void removePending(Object stmt) {
      if(stmt != null) {
         Iterator iter = queries.iterator();

         while(iter.hasNext()) {
            WeakReference ref = (WeakReference) iter.next();
            Object val = ref.get();

            if(val != null && val.equals(stmt)) {
               iter.remove();
               break;
            }
         }
      }
   }

   /**
    * Get the last cancelled moment.
    */
   public long lastCancelled() {
      return ts;
   }

   /**
    * Cancel a query
    * @param val  the query object to cancel
    * @return  <tt>1</tt> if object cancelled, <tt>0</tt> otherwise
    */
   private int cancel(Object val) {
      try {
         Method func = val.getClass().getMethod("cancel", new Class[0]);
         cancelled.put(val, null);
         func.invoke(val, new Object[0]);
         return 1;
      }
      catch(Throwable ex) {
         LOG.debug("Failed to cancel a query: " + val, ex);
      }

      return 0;
   }

   /**
    * Cancel all pending queries.
    * @return the cancelled query count.
    */
   public int cancel() {
      List<WeakReference> queries;

      synchronized(this) {
         queries = new ArrayList<>(this.queries);
         ts = System.currentTimeMillis();
      }

      int cnt = 0;

      for(WeakReference ref : queries) {
         Object val = ref.get();

         if(val != null) {
            cnt += cancel(val);
         }
      }

      synchronized(this) {
         this.queries.removeAll(queries);
      }

      return cnt;
   }

   /**
    * Clear out the pending queries.
    */
   public synchronized void reset() {
      queries.clear();
   }

   /**
    * Get the query count controlled by this query manager.
    */
   public synchronized int getQueryCount() {
      return queries.size();
   }

   public boolean isCancelable() {
      return this.cancelable;
   }

   private boolean cancelable = true;
   private static Map cancelled = Collections.synchronizedMap(new WeakHashMap());
   private List<WeakReference> queries = new ArrayList<>();
   private long ts = 0L;

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryManager.class);
}
