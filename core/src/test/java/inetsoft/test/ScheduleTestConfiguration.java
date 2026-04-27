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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class ScheduleTestConfiguration {
   @Bean
   public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
      return new BlobIndexedStorage(blobStorageManager);
   }

   @Bean
   public ScheduleManager scheduleManager(SecurityEngine securityEngine, Cluster cluster) {
      return new ScheduleManager(securityEngine, cluster, mock(ScheduleClient.class), mock(DependencyHandler.class));
   }

   @Bean
   public ScheduleStatusDao scheduleStatusDao() {
      return mock(ScheduleStatusDao.class);
   }
}
