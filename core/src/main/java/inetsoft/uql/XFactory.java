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
package inetsoft.uql;

import inetsoft.uql.service.*;
import inetsoft.util.SingletonManager;

import java.rmi.RemoteException;

/**
 * XFactory is a singlton object that can be used obtain instances of the
 * query engine. The instances are reused, so consecutive calls to get
 * a query engine returns the same object.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XFactory {
   /**
    * Get a specific repository object.
    */
   public static synchronized XRepository getRepository() throws RemoteException {
      return SingletonManager.getInstance(XRepository.class);
   }

   /**
    * Get a specific runtime query engine object.
    */
   public static synchronized XDataService getDataService() throws RemoteException {
      return SingletonManager.getInstance(XRepository.class);
   }

   /**
    * Clear the cached service objects to key. The next call to a 'get' method
    * will instantiate a new service object.
    */
   public static synchronized void clear() {
      SingletonManager.reset(XRepository.class);
      SingletonManager.reset(DataSourceRegistry.class);
   }

   public static final class Reference extends SingletonManager.Reference<XRepository>
   {
      @Override
      public synchronized XRepository get(Object ... parameters) {
         if(engine == null) {
            engine = new XEngine();
         }

         return engine;
      }

      @Override
      public void dispose() {
         if(engine != null) {
            try {
               engine.close();
            }
            catch(Exception ignore) {
            }

            engine = null;
         }
      }

      private XEngine engine;
   }
}
