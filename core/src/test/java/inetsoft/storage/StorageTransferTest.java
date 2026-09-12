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
package inetsoft.storage;

import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.KeyValueConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

@Tag("core")
class StorageTransferTest {
   @Test
   void createReturnsClusterTransferForMapdb() {
      assertTransferType("mapdb", ClusterStorageTransfer.class);
   }

   @ParameterizedTest
   @ValueSource(strings = {"database", "s3"})
   void createReturnsDirectTransferForSharedBackends(String keyValueType) {
      assertTransferType(keyValueType, DirectStorageTransfer.class);
   }

   @Test
   void createReturnsDirectTransferForNullType() {
      // unconfigured type falls through to the shared backend via "mapdb".equals(null) == false
      assertTransferType(null, DirectStorageTransfer.class);
   }

   private void assertTransferType(String keyValueType,
                                   Class<? extends StorageTransfer> expected)
   {
      KeyValueEngine keyValueEngine = mock(KeyValueEngine.class);
      BlobEngine blobEngine = mock(BlobEngine.class);

      KeyValueConfig keyValueConfig = mock(KeyValueConfig.class);
      when(keyValueConfig.getType()).thenReturn(keyValueType);
      InetsoftConfig config = mock(InetsoftConfig.class);
      when(config.getKeyValue()).thenReturn(keyValueConfig);

      try(MockedStatic<InetsoftConfig> mocked = mockStatic(InetsoftConfig.class)) {
         mocked.when(InetsoftConfig::getInstance).thenReturn(config);
         StorageTransfer transfer = StorageTransfer.create(keyValueEngine, blobEngine);
         assertInstanceOf(expected, transfer);
      }
   }
}
