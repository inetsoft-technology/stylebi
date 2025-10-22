/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.storage;

import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ServiceLoader;

@SingletonManager.Singleton(WizStorageService.Reference.class)
public interface WizStorageService extends AutoCloseable {

   void write(String path, String content) throws IOException;

   void delete(String path) throws IOException;

   @Override
   default void close() throws Exception {
   }

   static WizStorageService getInstance() {
      return SingletonManager.getInstance(WizStorageService.class);
   }

   class Reference extends SingletonManager.Reference<WizStorageService> {
      private WizStorageService service;

      @Override
      public WizStorageService get(Object... parameters) {
         if(service == null) {
            InetsoftConfig config = InetsoftConfig.getInstance();

            if(config.getWiz() != null) {
               String type = config.getWiz().getType();

               for(WizStorageServiceFactory factory : ServiceLoader.load(WizStorageServiceFactory.class)) {
                  if(factory.getType().equals(type)) {
                     service = factory.createWizStorageService(config);
                     break;
                  }
               }
            }
         }

         return service;
      }

      @Override
      public void dispose() {
         if(service != null) {
            try {
               service.close();
            }
            catch(Exception e) {
               LoggerFactory.getLogger(WizStorageService.class).warn("Failed to close Wiz storage", e);
            }
            finally {
               service = null;
            }
         }
      }
   }
}
