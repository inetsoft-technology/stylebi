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
package inetsoft.sree;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.log.*;
import inetsoft.web.admin.properties.PropertiesController;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77006: removing a log level property must reset the running log level, both on the node
 * that removed it and on the nodes that pick up the removal through a reload.
 *
 * <p>A real {@link LogManager} replaces the mock of the test configuration, and the engine's
 * key-value storage is replaced by an in-memory fake, so that the test can play the part of
 * another cluster node removing a property from the shared storage.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineLogLevelResetTest {
   @BeforeEach
   void swap(TestInfo info) throws Exception {
      logger = "test77006." + info.getTestMethod().orElseThrow().getName().toLowerCase();
      engine = PropertiesEngine.getInstance();
      engine.clear();

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      ObjectProvider<SecurityEngine> securityEngineProvider =
         new StaticListableBeanFactory(Map.of("securityEngine", securityEngine))
            .getBeanProvider(SecurityEngine.class);
      logManager = new LogManager(securityEngineProvider, null, null);
      originalLogManagerProvider = getField("logManagerProvider");
      setField("logManagerProvider",
               new StaticListableBeanFactory(Map.of("logManager", logManager))
                  .getBeanProvider(LogManager.class));

      originalStorage = getField("kvStorage");
      storage = new InMemoryKeyValueStorage<>();
      setField("kvStorage", storage);
   }

   @AfterEach
   void restore() throws Exception {
      engine.clear();
      setField("kvStorage", originalStorage);
      setField("logManagerProvider", originalLogManagerProvider);
      engine.init();
   }

   /**
    * The real log manager configures the global Logback context whenever the logging framework
    * is reloaded (a root logger at ERROR, file appenders and the log context turbo filter).
    * Restore the test configuration, so that the WARN events that later tests capture from
    * their loggers are not dropped.
    */
   @AfterAll
   static void restoreLogback() throws Exception {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.reset();
      new ContextInitializer(context).autoConfig();
   }

   @Test
   void localRemoveOfLoggerLevelResetsIt() throws Exception {
      initEngine();
      engine.setLogLevel(LogContext.CATEGORY, logger, LogLevel.DEBUG);
      engine.save();
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger));

      engine.remove("log.level." + logger);

      assertNull(logManager.getLevel(logger), "the removed logger level is still running");
      assertFalse(isListed(LogContext.CATEGORY, logger));

      // the reload caused by the node's own save must not bring it back
      engine.save();
      engine.init(true);
      assertNull(logManager.getLevel(logger));
   }

   @Test
   void remoteRemoveOfLoggerLevelIsResetByReload() throws Exception {
      String key = "log.level." + logger;
      storage.remotePut(key, "debug", false);
      initEngine();
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger));

      // another node removes the property, the change event schedules the reload
      storage.remoteRemove(key, true);

      waitFor(() -> logManager.getLevel(logger) == null);
      assertNull(engine.getProperty(key));
      assertFalse(isListed(LogContext.CATEGORY, logger));
   }

   @Test
   void contextLevelIsResetByLocalRemoveAndReload() {
      String local = logger + ".local";
      String remote = logger + ".remote";
      storage.remotePut("log.USER.level." + local, "debug", false);
      storage.remotePut("log.USER.level." + remote, "debug", false);
      initEngine();
      assertTrue(isListed(LogContext.USER, local));
      assertTrue(isListed(LogContext.USER, remote));

      engine.remove("log.USER.level." + local);
      assertFalse(isListed(LogContext.USER, local), "the removed user level is still running");

      storage.remoteRemove("log.USER.level." + remote, false);
      engine.init(true);
      assertFalse(isListed(LogContext.USER, remote), "the removed user level is still running");
   }

   @Test
   void removedDetailLevelFallsBackToDefault() {
      initEngine();
      engine.setProperty("log.detail.level", "debug");
      assertEquals(LogLevel.DEBUG, logManager.getLevel());

      // defaults.properties has log.detail.level=INFO
      engine.remove("log.detail.level");
      assertEquals(LogLevel.INFO, logManager.getLevel());

      storage.remotePut("log.detail.level", "debug", false);
      engine.init(true);
      assertEquals(LogLevel.DEBUG, logManager.getLevel());

      storage.remoteRemove("log.detail.level", false);
      engine.init(true);
      assertEquals(LogLevel.INFO, logManager.getLevel());
   }

   @Test
   void removedBuiltInLoggerLevelFallsBackToBuiltInLevel() {
      String key = "log.level.org.apache.ignite";
      initEngine();
      assertEquals(LogLevel.WARN, logManager.getLevel("org.apache.ignite"));

      engine.setProperty(key, "debug");
      assertEquals(LogLevel.DEBUG, logManager.getLevel("org.apache.ignite"));
      engine.remove(key);
      assertEquals(LogLevel.WARN, logManager.getLevel("org.apache.ignite"));

      storage.remotePut(key, "debug", false);
      engine.init(true);
      assertEquals(LogLevel.DEBUG, logManager.getLevel("org.apache.ignite"));
      storage.remoteRemove(key, false);
      engine.init(true);
      assertEquals(LogLevel.WARN, logManager.getLevel("org.apache.ignite"));
   }

   @Test
   void controllerDeleteOfLoggerLevelResetsIt() throws Exception {
      // the reported path: DELETE /api/admin/properties/delete -> deleteProperty -> SreeEnv.remove
      String dotted = logger + ".sub";
      String orgScoped = logger + "^org77006";
      initEngine();
      engine.setLogLevel(LogContext.CATEGORY, logger, LogLevel.DEBUG);
      engine.setProperty("log.level." + dotted, "debug");
      engine.setProperty("log.level." + orgScoped, "debug");
      engine.save();
      assertTrue(isListed(LogContext.CATEGORY, logger));
      assertEquals(LogLevel.DEBUG, logManager.getLevel(dotted));
      assertEquals(LogLevel.DEBUG, logManager.getLevel(orgScoped));

      PropertiesController controller = new PropertiesController(mock(AssetRepository.class));
      controller.deleteProperty(null, "log.level." + logger);
      controller.deleteProperty(null, "log.level." + dotted);
      controller.deleteProperty(null, "log.level." + orgScoped);

      assertNull(engine.getProperty("log.level." + logger));
      assertNull(logManager.getLevel(logger), "the deleted logger level is still running");
      assertFalse(isListed(LogContext.CATEGORY, logger));
      assertNull(logManager.getLevel(dotted), "the deleted dotted logger level is still running");
      assertNull(logManager.getLevel(orgScoped), "the deleted org logger level is still running");

      // deleting a log level property that does not exist must not fail
      assertDoesNotThrow(() -> controller.deleteProperty(null, "log.level." + logger + ".none"));

      // the reload caused by the node's own save must not bring the levels back
      engine.init(true);
      assertNull(logManager.getLevel(logger));
      assertNull(logManager.getLevel(dotted));
      assertNull(logManager.getLevel(orgScoped));
   }

   @Test
   void offLevelStaysOffAndHidden() throws Exception {
      initEngine();
      engine.setLogLevel(LogContext.CATEGORY, logger, LogLevel.OFF);
      engine.save();
      engine.init(true);

      assertEquals(LogLevel.OFF, logManager.getLevel(logger));
      assertFalse(isListed(LogContext.CATEGORY, logger));
   }

   @Test
   void removedOrgScopedKeyDoesNotResetHostLogger() {
      storage.remotePut("log.level." + logger, "debug", false);
      storage.remotePut("inetsoft.org.org77006.log.level." + logger, "info", false);
      initEngine();
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger));

      engine.remove("inetsoft.org.org77006.log.level." + logger);
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger));

      storage.remoteRemove("inetsoft.org.org77006.log.level." + logger, false);
      engine.init(true);
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger));
   }

   @Test
   void orgRenameKeepsOrgLogLevels() throws Exception {
      // the rename path of AbstractEditableAuthenticationProvider.copyOrganizationInternal()
      // (replace = true) followed by IdentityService.syncIdentity() (Bug #71473)
      String oldOrg = "org77006old";
      String newOrg = "org77006new";
      String user = logger + ".user";
      String group = logger + ".group";
      initEngine();
      engine.setProperty("log.USER.level." + user + "^" + oldOrg, "debug");
      engine.setProperty("log.GROUP.level." + group + "^" + oldOrg, "error");
      engine.setProperty("log.level." + logger + "^" + oldOrg, "debug");
      engine.save();
      assertEquals(LogLevel.DEBUG, contextLevel(LogContext.USER, user + "^" + oldOrg));

      IdentityService identityService = createIdentityService();
      identityService.updateOrgProperties(oldOrg, newOrg);
      identityService.removeOrgProperties(oldOrg);
      logManager.renameOrgLogLevels(oldOrg, newOrg);

      assertOrgLogLevels(user, group, oldOrg, newOrg);

      // the renamed levels are stored, so a reload (or a restart) keeps them
      engine.save();
      engine.init(true);
      assertOrgLogLevels(user, group, oldOrg, newOrg);
   }

   @Test
   void orgDeleteClearsOrgLogLevels() throws Exception {
      String org = "org77006deleted";
      String user = logger + ".user";
      initEngine();
      engine.setProperty("log.USER.level." + user + "^" + org, "debug");
      engine.setProperty("log.level." + logger + "^" + org, "debug");
      engine.setProperty("log.level." + logger, "error");
      engine.save();

      // the delete path of IdentityService.syncIdentity()
      createIdentityService().removeOrgProperties(org);
      logManager.removeOrgLogLevels(org);

      assertNull(engine.getProperty("log.USER.level." + user + "^" + org));
      assertNull(contextLevel(LogContext.USER, user + "^" + org));
      assertNull(logManager.getLevel(logger + "^" + org));
      assertEquals(LogLevel.ERROR, logManager.getLevel(logger), "the host logger was reset");

      engine.save();
      engine.init(true);
      assertNull(contextLevel(LogContext.USER, user + "^" + org));
      assertNull(logManager.getLevel(logger + "^" + org));
   }

   private void assertOrgLogLevels(String user, String group, String oldOrg, String newOrg)
      throws Exception
   {
      assertEquals(LogLevel.DEBUG, contextLevel(LogContext.USER, user + "^" + newOrg),
                   "the renamed org's user level was lost");
      assertEquals(LogLevel.ERROR, contextLevel(LogContext.GROUP, group + "^" + newOrg),
                   "the renamed org's group level was lost");
      assertEquals(LogLevel.DEBUG, logManager.getLevel(logger + "^" + newOrg));
      assertNull(contextLevel(LogContext.USER, user + "^" + oldOrg));
      assertNull(contextLevel(LogContext.GROUP, group + "^" + oldOrg));
      assertNull(logManager.getLevel(logger + "^" + oldOrg));

      // stored, so that the other nodes and a restart see them too
      assertEquals("debug", engine.getProperty("log.USER.level." + user + "^" + newOrg));
      assertEquals("error", engine.getProperty("log.GROUP.level." + group + "^" + newOrg));
      assertEquals("debug", engine.getProperty("log.level." + logger + "^" + newOrg));
      assertNull(engine.getProperty("log.USER.level." + user + "^" + oldOrg));
      assertNull(engine.getProperty("log.GROUP.level." + group + "^" + oldOrg));
      assertNull(engine.getProperty("log.level." + logger + "^" + oldOrg));
   }

   private static IdentityService createIdentityService() {
      return new IdentityService(
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         Optional.empty(), null, null, null, null, null, null, null, null, null, null, null,
         null, null, Optional.empty());
   }

   @SuppressWarnings("unchecked")
   private LogLevel contextLevel(LogContext context, String name) throws Exception {
      Field field = LogManager.class.getDeclaredField("contextLevels");
      field.setAccessible(true);
      return ((Map<LogContext, Map<String, LogLevel>>) field.get(logManager))
         .get(context).get(name);
   }

   private boolean isListed(LogContext context, String name) {
      return logManager.getContextLevels().stream()
         .anyMatch(s -> s.getContext() == context && name.equals(s.getName()));
   }

   /**
    * Loads the fake storage and saves whatever was left pending by the test harness, so each
    * test starts with no pending properties.
    */
   private void initEngine() {
      engine.init();

      try {
         engine.save();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the reload");
         }

         Thread.sleep(50L);
      }
   }

   @SuppressWarnings("unchecked")
   private <T> T getField(String name) throws Exception {
      return (T) field(name).get(engine);
   }

   private void setField(String name, Object value) throws Exception {
      field(name).set(engine, value);
   }

   private static Field field(String name) throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private PropertiesEngine engine;
   private LogManager logManager;
   private ObjectProvider<LogManager> originalLogManagerProvider;
   private KeyValueStorage<String> originalStorage;
   private InMemoryKeyValueStorage<String> storage;
   private String logger;
}
