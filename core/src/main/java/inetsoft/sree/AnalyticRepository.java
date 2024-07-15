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
package inetsoft.sree;

import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.Scheduler;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.util.SingletonManager;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.concurrent.locks.Lock;

/**
 * This defines the server interface for the analytic component of the report
 * server. This API is likely to change in the future.
 *
 * @author InetSoft Technology Corp.
 * @version 7.0
 */
@SingletonManager.Singleton(AnalyticRepository.Reference.class)
public interface AnalyticRepository extends RepletRepository {
   /**
    * Get a data model with the specified name. The actual data model returned
    * may be a virtual private model that provides a view to the data model for
    * the assigned roles.
    * @param name the name of the data model.
    * @param principal the roles assigned to the user that is requesting the
    * data model.
    * @return the requested data model.
    * @throws RemoteException if an error occurs.
    */
   XLogicalModel getLogicalModel(String name, Principal principal)
      throws RemoteException;

   /**
    * Get a serializable Object from engine.
    * @param op the specified operation to process.
    * @return a serializable Object.
    */
   Serializable getObject(String op)
           throws RemoteException;

   /**
    * Factory that handles creating the shared instance of <tt>AnalyticRepository</tt>.
    */
   class Reference extends SingletonManager.Reference<AnalyticRepository> {
      @Override
      public AnalyticRepository get(Object ... parameters) {
         if(engine == null) {
            // avoid deadlock. (50572)
            Lock lock = Cluster.getInstance().getLock(Scheduler.INIT_LOCK);
            lock.lock();

            try {
               if(engine == null) {
                  engine = new AnalyticEngine();
                  AssetUtil.setAssetRepository(false, engine);
                  engine.init();
               }
            }
            finally {
               lock.unlock();
            }
         }

         return engine;
      }

      @Override
      public synchronized void dispose() {
         if(engine != null) {
            engine.dispose();
            engine = null;
         }
      }

      private AnalyticEngine engine;
   }
}
