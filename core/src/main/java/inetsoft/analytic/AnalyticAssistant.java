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
package inetsoft.analytic;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.*;
import inetsoft.util.ItemList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;

/**
 * AnalyticAssistant provides all non-presentation logic to support the
 * analytic services. Unlike AnalyticEngine, the AnalyticAssistant is not
 * distributable, which means it would always run in the same processing
 * as the web service and handlers. This is not a real limitation since
 * the AnalyticAssistant is on top of the AnalyticEngine layer. In a cluster
 * environment, the majority of the processing would be done in distributed
 * AnalyticEngine instances, and the AnalyticAssistant would serve more
 * as a single front-end to the cluster
 *
 * @author InetSoft Technology Corp
 * @version 6.0 10/23/2003
 */
public class AnalyticAssistant extends SreeAssistant {
   /**
    * Get singleton analytic assistant.
    */
   public static AnalyticAssistant getAnalyticAssistant() {
      if(analyticAssistant == null) {
         analyticAssistant = new AnalyticAssistant();
      }

      return analyticAssistant;
   }

   /**
    * Create an analytic assistant.
    */
   private AnalyticAssistant() {
      try {
         engine = SUtil.getRepletRepository();
      }
      catch(Throwable e) {
         LOG.warn("Repository initialization error, using local engine", e);
         engine = new AnalyticEngine();
         ((AnalyticEngine) engine).init();
      }
   }

   /**
    * Create an analytic assistant.
    */
   public AnalyticAssistant(AnalyticRepository engine) {
      this.engine = engine;
   }

   /**
    * Get embedded values.
    */
   public ItemList getEmbeddedValues(String col) throws RemoteException {
      String req = "getEmbeddedValues?column=" + col;
      return (ItemList) engine.getObject(req);
   }

   private static AnalyticAssistant analyticAssistant = null;
   private static final Logger LOG = LoggerFactory.getLogger(AnalyticAssistant.class);
}
