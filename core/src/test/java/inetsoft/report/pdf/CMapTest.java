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
package inetsoft.report.pdf;

import inetsoft.sree.SreeEnv;
import inetsoft.util.DataSpace;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * getCMapData reuses one StringTokenizer across a local-filesystem loop and a data-space
 * fallback loop; once the first loop drains it, the second loop's body never executes,
 * silently skipping the data-space tier entirely.
 */
@Tag("core")
class CMapTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<FileSystemService> fileSystemServiceStatic;
   private MockedStatic<DataSpace> dataSpaceStatic;
   private FileSystemService fileSystemService;
   private DataSpace dataSpace;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("font.cmap.path")).thenReturn("dirA;dirB");

      fileSystemService = mock(FileSystemService.class);
      fileSystemServiceStatic = mockStatic(FileSystemService.class);
      fileSystemServiceStatic.when(FileSystemService::getInstance).thenReturn(fileSystemService);

      File missingA = mock(File.class);
      when(missingA.exists()).thenReturn(false);
      File missingB = mock(File.class);
      when(missingB.exists()).thenReturn(false);
      when(fileSystemService.getFile("dirA", "cmap.name")).thenReturn(missingA);
      when(fileSystemService.getFile("dirB", "cmap.name")).thenReturn(missingB);

      dataSpace = mock(DataSpace.class);
      dataSpaceStatic = mockStatic(DataSpace.class);
      dataSpaceStatic.when(DataSpace::getDataSpace).thenReturn(dataSpace);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      fileSystemServiceStatic.close();
      dataSpaceStatic.close();
   }

   @Test
   void getCMapData_localMiss_fallsBackToDataSpace() throws Exception {
      InputStream dataSpaceStream = new ByteArrayInputStream(new byte[0]);
      when(dataSpace.getInputStream("dirA", "cmap.name")).thenReturn(null);
      when(dataSpace.getInputStream("dirB", "cmap.name")).thenReturn(dataSpaceStream);

      InputStream result = CMap.getCMapData("cmap.name");

      assertSame(dataSpaceStream, result);
   }

   @Test
   void getCMapData_localAndDataSpaceMiss_fallsThroughToClasspathResource() throws Exception {
      when(dataSpace.getInputStream("dirA", "cmap.name")).thenReturn(null);
      when(dataSpace.getInputStream("dirB", "cmap.name")).thenReturn(null);

      InputStream result = CMap.getCMapData("cmap.name");

      assertNull(result);
      verify(dataSpace).getInputStream("dirA", "cmap.name");
      verify(dataSpace).getInputStream("dirB", "cmap.name");
   }
}
