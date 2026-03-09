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
package inetsoft.web;

import inetsoft.util.DataSpace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataSpaceProtocolResolverTest {
   /**
    * A directory entry must not be considered readable — trying to open an InputStream on a
    * directory path throws an IOException. isReadable() should return false so that Spring's
    * ResourceLoader skips attempting to read the resource.
    */
   @Test
   void isReadable_returnsFalse_forDirectoryEntry() {
      DataSpace mockDataSpace = mock(DataSpace.class);
      when(mockDataSpace.exists("config", "reports")).thenReturn(true);
      when(mockDataSpace.getPath("config", "reports")).thenReturn("config/reports");
      when(mockDataSpace.isDirectory("config/reports")).thenReturn(true);

      DataSpaceProtocolResolver.DataSpaceResource resource =
         new DataSpaceProtocolResolver.DataSpaceResource("config", "reports", mockDataSpace);

      assertFalse(resource.isReadable());
   }

   /**
    * A regular file that exists should be readable.
    */
   @Test
   void isReadable_returnsTrue_forRegularFile() {
      DataSpace mockDataSpace = mock(DataSpace.class);
      when(mockDataSpace.exists("config", "file.xml")).thenReturn(true);
      when(mockDataSpace.getPath("config", "file.xml")).thenReturn("config/file.xml");
      when(mockDataSpace.isDirectory("config/file.xml")).thenReturn(false);

      DataSpaceProtocolResolver.DataSpaceResource resource =
         new DataSpaceProtocolResolver.DataSpaceResource("config", "file.xml", mockDataSpace);

      assertTrue(resource.isReadable());
   }

   /**
    * A path that does not exist should not be readable.
    */
   @Test
   void isReadable_returnsFalse_whenEntryDoesNotExist() {
      DataSpace mockDataSpace = mock(DataSpace.class);
      when(mockDataSpace.exists("config", "missing.xml")).thenReturn(false);

      DataSpaceProtocolResolver.DataSpaceResource resource =
         new DataSpaceProtocolResolver.DataSpaceResource("config", "missing.xml", mockDataSpace);

      assertFalse(resource.isReadable());
   }
}
