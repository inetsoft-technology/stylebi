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
package inetsoft.mv.util;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class MVRule {
   /**
    * Check if the freshness exceeded.
    */
   public static boolean isStale(long ts) {
      return isExpired(ts, "mv.freshness");
   }

   /**
    * Check if the max age exceeded.
    */
   public static boolean isExpired(long ts) {
      return isExpired(ts, "mv.maxAge");
   }

   /**
    * Check if mv should be generated automatically.
    */
   public static boolean isAuto() {
      return SreeEnv.getProperty("mv.freshness") != null ||
         SreeEnv.getProperty("mv.maxAge") != null;
   }

   // check if ts passed threshold
   private static boolean isExpired(long ts, String durationProp) {
      long duration = parseDuration(SreeEnv.getProperty(durationProp));

      if(duration <= 0) {
         return false;
      }

      long now = System.currentTimeMillis();
      return ts < now - duration;
   }

   // parse duration spec to long (ms)
   public static long parseDuration(String str) {
      if(str != null) {
         try {
            return Long.parseLong(str);
         }
         catch(Exception ex) {
            return Duration.parse(str).toMillis();
         }
      }

      return 0;
   }

   // remove expired MVs
   public static void cleanup() {
      MVManager mgr = MVManager.getManager();

      for(MVDef mv : mgr.list(false)) {
         if(isExpired(mv.getLastUpdateTime())) {
            ClusterUtil.deleteClusterMV(mv.getName());
            mgr.remove(mv);

            if(LOG.isDebugEnabled()) {
               LOG.debug("MV expired and deleted: " + mv.getName());
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVRule.class);
}
