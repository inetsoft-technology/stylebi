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

package inetsoft.test;

import inetsoft.storage.BlobStorageTestSupport;
import inetsoft.util.ConfigurationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ConfigurationContextInitializer
   implements ApplicationContextInitializer<ConfigurableApplicationContext>
{
   @Override
   public void initialize(ConfigurableApplicationContext applicationContext) {
      // the change events still queued in a closed context must run before this context is
      // installed. A context per test method is closed without SreeHomeExtension.beforeAll(),
      // which waits at the class boundary. A timeout is not thrown from here: Spring would count
      // it as a failure to load this context configuration and skip every later load of it.
      // SreeHomeExtension fails the test instead
      String error = BlobStorageTestSupport.awaitEventBarrier();

      if(error != null) {
         String message = "Loading " + applicationContext.getDisplayName() + " for " +
            SreeHomeExtension.getCurrentTestClassName() + ": " + error;
         LOG.error(message);
         SreeHomeExtension.setPendingBarrierError(message);
      }

      ConfigurationContext.getContext().setApplicationContext(applicationContext);
   }

   private static final Logger LOG = LoggerFactory.getLogger(ConfigurationContextInitializer.class);
}
