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
package inetsoft.report.internal;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.SingletonManager;

import java.util.Date;

/**
 * {@code MVInfoClient} is an interface for classes that provide information about materialization.
 *
 * @since 13.5
 */
@SingletonManager.Singleton(MVInfoClient.Reference.class)
public interface MVInfoClient {
   /**
    * Gets the date and time at which a worksheet's data was refreshed.
    *
    * @param entry the worksheet entry.
    *
    * @return the refresh time or {@code null} if not materialized.
    */
   Date getDataRefreshedTime(AssetEntry entry);

   /**
    * Gets the {@code MVInfoClient} instance.
    *
    * @return the singleton instance.
    */
   static MVInfoClient getInstance() {
      return SingletonManager.getInstance(MVInfoClient.class);
   }

   class Reference extends SingletonManager.Reference<MVInfoClient> {
      @Override
      public MVInfoClient get(Object... parameters) {
         if(client == null) {
            client = new LocalMVInfoClient();
         }

         return client;
      }

      @Override
      public void dispose() {
         if(client != null) {
            client = null;
         }
      }

      private MVInfoClient client;
   }
}
