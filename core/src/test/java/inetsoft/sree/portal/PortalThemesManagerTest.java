/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.portal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The name of the portal themes file must be resolved once and used by every path -- the
 * load, the save and the change listener registration. Re-reading portal.themes.file on
 * each save let a change to the property on a running server silently point the save path
 * at a different file than the one the configuration was loaded from.
 *
 * Tier: [mockStatic] -- Mockito.mockStatic intercepts SreeEnv.getPath(), the manager's only
 * static dependency on the property; no Spring application context required.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PortalThemesManagerTest {
   @Mock private Cluster cluster;
   @Mock private DataSpace dataSpace;
   @Mock private DataSpace.Transaction transaction;

   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = Mockito.mockStatic(SreeEnv.class);
      setThemesFile(DEFAULT_THEMES_FILE);
      when(cluster.getLock(anyString())).thenReturn(new ReentrantLock());
      // serve the bundled configuration as the persisted file, so loadThemes() takes the
      // normal path rather than the classpath fallback (which needs a Spring context)
      when(dataSpace.getInputStream(any(), anyString()))
         .thenAnswer(invocation -> bundledThemes());
      when(dataSpace.beginTransaction()).thenReturn(transaction);
      when(transaction.newStream(any(), anyString())).thenReturn(new ByteArrayOutputStream());
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   @Test
   void save_afterPropertyChanged_writesToTheFileThatWasLoaded() throws Exception {
      setThemesFile("portalthemes-a.xml");
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();

      setThemesFile("portalthemes-b.xml");
      manager.save();

      List<String> written = capturedWrites();
      assertFalse(written.isEmpty(), "expected at least one write");
      assertTrue(written.stream().allMatch("portalthemes-a.xml"::equals),
                 "save must keep writing the file the configuration was loaded from, but wrote " +
                    written);
   }

   @Test
   void save_afterPropertyChanged_unregistersTheListenerItRegistered() throws Exception {
      setThemesFile("portalthemes-a.xml");
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();

      setThemesFile("portalthemes-b.xml");
      manager.save();

      // the remove/add pair around the write must target the same file, otherwise the
      // listener on the old file is stranded and a second one is added on the new file
      ArgumentCaptor<String> added = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
      verify(dataSpace, atLeastOnce())
         .addChangeListener(isNull(), added.capture(), any(DataChangeListener.class));
      verify(dataSpace, atLeastOnce())
         .removeChangeListener(isNull(), removed.capture(), any(DataChangeListener.class));

      assertTrue(added.getAllValues().stream().allMatch("portalthemes-a.xml"::equals),
                 "listener registered on " + added.getAllValues());
      assertTrue(removed.getAllValues().stream().allMatch("portalthemes-a.xml"::equals),
                 "listener removed from " + removed.getAllValues());
   }

   @Test
   void propertyUnset_usesTheDefaultFileName() throws Exception {
      setThemesFile(DEFAULT_THEMES_FILE);
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();
      manager.save();

      List<String> written = capturedWrites();
      assertFalse(written.isEmpty(), "expected at least one write");
      assertTrue(written.stream().allMatch("portalthemes.xml"::equals),
                 "expected the default file name, but wrote " + written);
   }

   private InputStream bundledThemes() {
      InputStream input =
         getClass().getResourceAsStream("/inetsoft/sree/portal/portalthemes.xml");
      assertNotNull(input, "bundled portalthemes.xml is missing from the classpath");

      return input;
   }

   private List<String> capturedWrites() throws Exception {
      ArgumentCaptor<String> file = ArgumentCaptor.forClass(String.class);
      verify(transaction, atLeastOnce()).newStream(isNull(), file.capture());

      return file.getAllValues();
   }

   private void setThemesFile(String value) {
      sreeEnv.when(() -> SreeEnv.getPath(THEMES_FILE_PROPERTY, DEFAULT_THEMES_FILE))
         .thenReturn(value);
   }

   private static final String THEMES_FILE_PROPERTY = "portal.themes.file";
   private static final String DEFAULT_THEMES_FILE = "portalthemes.xml";
}
