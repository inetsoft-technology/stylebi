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
package inetsoft.sree.internal;

import inetsoft.sree.*;
import inetsoft.util.GroupedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AnalyticRegistry stores temporary reports, which are not yet saved.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class AnalyticRegistry extends RepletRegistry {
   /**
    * Create a new registry.
    */
   public AnalyticRegistry() throws Exception {
      super();

      String timeout = SreeEnv.getProperty("analytic.registry.timeout");

      try {
         if(timeout != null) {
            TIMEOUT_PERIOD = Integer.parseInt(timeout);
         }
      }
      catch(Exception ex) {
         LOG.error("Invalid numeric value for analytic registry " +
            "timeout (analytic.registry.timeout): " + timeout, ex);
      }
   }

   /**
    * Init the registry.
    */
   @Override
   protected void init() throws Exception {
      // do nothing
   }

   /**
    * Check if should always keep uptodate, true to add data change listener.
    */
   @Override
   protected boolean uptodate() {
      return false;
   }

   /**
    * Access a temporary replet name.
    * @param name the specified temporary replet name.
    */
   public void access(String name) {
      if(accessmap.containsKey(name)) {
         accessmap.put(name, Long.valueOf(System.currentTimeMillis()));
      }
   }

   /**
    * Recycle.
    */
   protected void recycle() {
      long ctime = System.currentTimeMillis();

      if(ctime - lrtime < (TIMEOUT_PERIOD / 3)) {
         return;
      }

      lrtime = ctime;
   }

   // last recycle time
   private static long lrtime = System.currentTimeMillis();
   // time out period
   private static int TIMEOUT_PERIOD = 1000 * 60 * 10;
   // replet access map
   private Map accessmap = new Hashtable();

   private static final Logger LOG =
      LoggerFactory.getLogger(AnalyticRegistry.class);
}
