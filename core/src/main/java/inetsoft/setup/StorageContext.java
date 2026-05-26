/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.setup;

import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.InetsoftConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import java.nio.file.Paths;

public class StorageContext implements AutoCloseable {
   public StorageContext(String home) {
      System.setProperty("sree.home", home);
      ConfigurationContext.getContext().setHome(home);
      InetsoftConfig.BOOTSTRAP_INSTANCE = InetsoftConfig.load(Paths.get(home, "inetsoft.yaml"));

      applicationContext = new AnnotationConfigApplicationContext(DirectStorageConfig.class);
      ConfigurationContext.getContext().setApplicationContext(applicationContext);
   }

   @Override
   public void close() throws Exception {
      try {
         applicationContext.close();
      }
      finally {
         ConfigurationContext.getContext().setApplicationContext(null);
         InetsoftConfig.BOOTSTRAP_INSTANCE = null;
      }
   }

   @SuppressWarnings("unused") // accessed via reflection
   public PropertiesService getPropertiesService() {
      return applicationContext.getBean(PropertiesService.class);
   }

   @SuppressWarnings("unused") // accessed via reflection
   public StorageService getStorageService() {
      return applicationContext.getBean(StorageService.class);
   }

   private final AbstractApplicationContext applicationContext;
}
