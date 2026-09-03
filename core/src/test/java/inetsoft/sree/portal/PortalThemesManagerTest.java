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
import inetsoft.util.DataChangeEvent;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
   /** What the data space serves as the persisted themes file; swap per test. */
   private Supplier<InputStream> persisted;
   /** The bytes the manager last wrote, served back on the next read. */
   private ByteArrayOutputStream written;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = Mockito.mockStatic(SreeEnv.class);
      setThemesFile(DEFAULT_THEMES_FILE);
      when(cluster.getLock(anyString())).thenReturn(new ReentrantLock());
      // serve the bundled configuration as the persisted file, so loadThemes() takes the
      // normal path rather than the classpath fallback (which needs a Spring context)
      persisted = this::bundledThemes;
      // a fake that round-trips: once save() has written the file, reading it back
      // yields those same bytes, which is what the self-write digest fence compares
      when(dataSpace.getInputStream(any(), anyString()))
         .thenAnswer(invocation -> written == null
            ? persisted.get() : new ByteArrayInputStream(written.toByteArray()));
      when(dataSpace.beginTransaction()).thenReturn(transaction);
      when(transaction.newStream(any(), anyString()))
         .thenAnswer(invocation -> written = new ByteArrayOutputStream());
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

   /*
    * The data space delivers change notifications asynchronously, so the listener
    * de-registration saveUnderLock() does around its own write cannot suppress the
    * notification for that write: it lands after the write has committed and the
    * listener has been re-registered, at a point where a later in-memory change (an
    * addLogoEntry() whose own save() has not run yet) is still pending. Reloading there
    * replaces the entry maps wholesale from the last-saved file and drops it.
    */
   @Test
   void changeListener_notificationForOwnWrite_doesNotClobberUnsavedEntry() throws Exception {
      persisted = () -> themesWithLogoEntry("orgB", "portal/orgB/logo.png");
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();
      manager.save();

      // a change made in memory that has not been saved yet
      manager.addLogoEntry("orgA", "portal/orgA/logo.png");

      // the notification for the save above, delivered late -- the file still holds
      // exactly what that save wrote
      registeredListener().dataChanged(notification());

      assertEquals("portal/orgA/logo.png", manager.getLogoEntries().get("orgA"),
                   "a notification for this instance's own write must not reload over " +
                      "an entry added since that write");
   }

   @Test
   void changeListener_notificationForRemoteWrite_reloads() throws Exception {
      persisted = () -> themesWithLogoEntry("orgB", "portal/orgB/logo.png");
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();
      manager.save();

      manager.addLogoEntry("orgA", "portal/orgA/logo.png");

      // another node replaced the file with different content
      written = null;
      persisted = () -> themesWithLogoEntry("orgC", "portal/orgC/logo.png");
      registeredListener().dataChanged(notification());

      assertNull(manager.getLogoEntries().get("orgA"),
                 "a notification for content this instance did not write must still reload");
      assertEquals("portal/orgC/logo.png", manager.getLogoEntries().get("orgC"),
                   "the reload must have repopulated the map from the persisted file");
   }

   /*
    * A remote write can carry an *older* blob timestamp than this instance's own last
    * write -- the mtime is stamped by whichever node wrote it, and pod clocks drift.
    * The fence must therefore key off content, not the event time.
    */
   @Test
   void changeListener_remoteWriteWithOlderTimestamp_stillReloads() throws Exception {
      persisted = () -> themesWithLogoEntry("orgB", "portal/orgB/logo.png");
      PortalThemesManager manager = new PortalThemesManager(cluster, dataSpace);
      manager.loadThemes();
      manager.save();

      written = null;
      persisted = () -> themesWithLogoEntry("orgC", "portal/orgC/logo.png");
      registeredListener().dataChanged(
         new DataChangeEvent(null, DEFAULT_THEMES_FILE, 1L));

      assertEquals("portal/orgC/logo.png", manager.getLogoEntries().get("orgC"),
                   "a remote change must not be discarded because its node stamped it " +
                      "with an earlier timestamp than our own last write");
   }

   private DataChangeEvent notification() {
      return new DataChangeEvent(null, DEFAULT_THEMES_FILE, System.currentTimeMillis());
   }

   private DataChangeListener registeredListener() {
      ArgumentCaptor<DataChangeListener> listener =
         ArgumentCaptor.forClass(DataChangeListener.class);
      verify(dataSpace, atLeastOnce())
         .addChangeListener(isNull(), eq(DEFAULT_THEMES_FILE), listener.capture());

      return listener.getValue();
   }

   private InputStream themesWithLogoEntry(String identityName, String logoFile) {
      String xml = """
         <?xml version="1.0"?>
         <PortalThemes>
         <Version>9.5</Version>
         <logoEntries>
         <logoEntry>
         <identityName><![CDATA[%s]]></identityName>
         <logoFile><![CDATA[%s]]></logoFile>
         </logoEntry>
         </logoEntries>
         </PortalThemes>
         """.formatted(identityName, logoFile);

      return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
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
