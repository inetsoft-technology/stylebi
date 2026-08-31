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
package inetsoft.uql.jdbc;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the cluster-sync gap fixed by PropertiesEngine.initEngine()'s
 * mysql.server.timezone/mysql.local.timezone listeners: a peer node that learns of a property
 * change via PropertiesEngine's cluster-sync PropertyChangeEvent path (rather than its own
 * direct SreeEnv.setProperty call, which is already covered by applySqlHelperProperty) must
 * still invalidate SQLHelper's cached CONVERT_TZ SQL template.
 *
 * <p>Uses a real Spring-managed PropertiesEngine bean (as ScheduleTaskTest/DerbyHelperTest do)
 * rather than a hand-built mock, because PropertiesEngine.initEngine() is a @PostConstruct
 * instance method that (re)registers these listeners on every fresh bean instance -- unlike a
 * static-initializer-based fix, this is not a once-per-JVM registration whose target instance
 * depends on which test happens to touch the class first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SQLHelperMysqlTimezoneCacheTest {

   @Autowired private PropertiesEngine propertiesEngine;

   private static final String[] CACHE_FIELD_NAMES =
      { "helperTable", "unsupported", "afuncs", "dfuncs" };

   private MockedStatic<SreeEnv> sreeEnvStatic;
   private Object[] savedCacheFields;

   @BeforeEach
   void setUp() throws Exception {
      // Other test classes in this JVM (e.g. WindowFrameCapabilityTest) read SQLHelper's cache
      // fields directly without ever calling getHelperClass() themselves, trusting that some
      // earlier test already warmed the cache. SQLHelper.resetCache() nulls those fields (not
      // just this test's own view of them), so save and restore the real static field values
      // around this test instead of leaving them null/reset for whichever test runs next.
      savedCacheFields = new Object[CACHE_FIELD_NAMES.length];

      for(int i = 0; i < CACHE_FIELD_NAMES.length; i++) {
         Field field = SQLHelper.class.getDeclaredField(CACHE_FIELD_NAMES[i]);
         field.setAccessible(true);
         savedCacheFields[i] = field.get(null);
      }

      SQLHelper.resetCache();
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() throws Exception {
      sreeEnvStatic.close();

      for(int i = 0; i < CACHE_FIELD_NAMES.length; i++) {
         Field field = SQLHelper.class.getDeclaredField(CACHE_FIELD_NAMES[i]);
         field.setAccessible(true);
         field.set(null, savedCacheFields[i]);
      }
   }

   @Test
   void peerNodePropertyChangeEvent_invalidatesCache_soNextLoadUsesNewTimezone() throws Exception {
      // locale_tz is stubbed as blank in both loads, so it always falls back to the JVM default
      // (America/New_York, set by core/pom.xml's surefire argLine) -- pick server_tz values that
      // don't collide with that fallback, so the assertions below test the server_tz baked value
      // specifically, not the constant locale_tz fallback.
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.server.timezone"))
         .thenReturn("Asia/Tokyo");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.local.timezone")).thenReturn("");

      String initialCmd = loadMysqlYearCommand();
      assertTrue(initialCmd.contains("Asia/Tokyo"),
                 "sanity check: initial load must bake in the configured server timezone: " +
                 initialCmd);

      // Simulate a peer node's cluster-sync PropertyChangeEvent: the writing node already
      // invalidates its own cache synchronously via PropertiesEngine.applySqlHelperProperty
      // (called from setProperty -- not exercised here); a peer node instead learns of the
      // change only via PropertiesEngine's PropertyChangeSupport, fired exactly this way from
      // the private onChange() method on every KeyValueStorage entryAdded/Updated/Removed
      // callback. Firing it directly on the real, Spring-managed PropertiesEngine bean exercises
      // the actual listener registered by initEngine(), without needing to fake a whole
      // KeyValueStorage/cluster round-trip.
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.server.timezone")).thenReturn("UTC");
      firePropertyChange("mysql.server.timezone", "Asia/Tokyo", "UTC");

      String cmdAfterSync = loadMysqlYearCommand();
      assertTrue(cmdAfterSync.contains("UTC") && !cmdAfterSync.contains("Asia/Tokyo"),
                 "after the peer-node listener fires, the next load must rebuild the cache using " +
                 "the new timezone instead of returning the stale cached template: " + cmdAfterSync);
   }

   @Test
   void peerNodePropertyChangeEvent_forLocalTimezone_alsoInvalidatesCache() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.server.timezone")).thenReturn("UTC");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.local.timezone"))
         .thenReturn("Asia/Tokyo");

      String initialCmd = loadMysqlYearCommand();
      assertTrue(initialCmd.contains("Asia/Tokyo"),
                 "sanity check: initial load must bake in the configured local timezone: " +
                 initialCmd);

      sreeEnvStatic.when(() -> SreeEnv.getProperty("mysql.local.timezone"))
         .thenReturn("Europe/London");
      firePropertyChange("mysql.local.timezone", "Asia/Tokyo", "Europe/London");

      String cmdAfterSync = loadMysqlYearCommand();
      assertTrue(cmdAfterSync.contains("Europe/London") && !cmdAfterSync.contains("Asia/Tokyo"),
                 "after the peer-node listener fires for mysql.local.timezone, the next load " +
                 "must rebuild the cache using the new locale timezone: " + cmdAfterSync);
   }

   /** Fires a PropertyChangeEvent on the real PropertiesEngine bean's PropertyChangeSupport,
    *  matching exactly what PropertiesEngine's private onChange() does on a cluster-sync
    *  KeyValueStorage callback -- the only way to trigger initEngine()'s registered listeners
    *  without standing up a real clustered KeyValueStorage. */
   private void firePropertyChange(String name, String oldValue, String newValue) throws Exception {
      Field supportField = PropertiesEngine.class.getDeclaredField("support");
      supportField.setAccessible(true);
      PropertyChangeSupport support = (PropertyChangeSupport) supportField.get(propertiesEngine);
      support.firePropertyChange(name, oldValue, newValue);
   }

   /**
    * Triggers SQLHelper's private getHelperClass() load and returns the mysql "year" CONVERT_TZ
    * command it bakes into dfuncs, so tests can distinguish a fresh load from a stale one.
    */
   private static String loadMysqlYearCommand() throws Exception {
      Method getHelperClass = SQLHelper.class.getDeclaredMethod("getHelperClass", String.class);
      getHelperClass.setAccessible(true);
      getHelperClass.invoke(null, "mysql");

      Field dfuncsField = SQLHelper.class.getDeclaredField("dfuncs");
      dfuncsField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, String> dfuncs = (Map<String, String>) dfuncsField.get(null);
      String cmd = dfuncs.get("mysql:year");
      assertNotNull(cmd, "mysql:year should be populated by getHelperClass()");

      return cmd;
   }
}
