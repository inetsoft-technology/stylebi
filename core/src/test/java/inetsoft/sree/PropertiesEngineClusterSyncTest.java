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

import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76954: SreeEnv properties saved on one cluster node must take effect on every node.
 *
 * <p>The engine's key-value storage is replaced by an in-memory fake, so that the test can play
 * the part of another cluster node writing to the shared storage. {@code init(true)} is what the
 * debounced change task runs when a storage change event arrives, so the tests call it directly
 * to simulate that reload deterministically.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineClusterSyncTest {
   @BeforeEach
   void swapStorage(TestInfo info) throws Exception {
      prefix = "test76954." + info.getTestMethod().orElseThrow().getName().toLowerCase() + ".";
      engine = PropertiesEngine.getInstance();
      engine.clear();
      originalStorage = getStorage();
      storage = new InMemoryKeyValueStorage<>();
      setStorage(storage);
   }

   @AfterEach
   void restoreStorage() throws Exception {
      engine.clear();
      setStorage(originalStorage);
      engine.init();
   }

   @Test
   void remoteChangeDuringOwnSaveIsReceived() throws Exception {
      String mine = prefix + "mine";
      String theirs = prefix + "theirs";
      initEngine();

      List<Object> received = new CopyOnWriteArrayList<>();
      PropertyChangeListener listener = evt -> received.add(evt.getNewValue());
      engine.addPropertyChangeListener(theirs, listener);

      try {
         engine.setProperty(mine, "local");
         // another node stores its change while this node is saving
         storage.runDuringNextWrite(() -> storage.remotePut(theirs, "remote", true));
         engine.save();

         assertEquals("local", storage.get(mine));
         assertEquals(List.of("remote"), received,
                      "the change event of the other node was dropped during save()");
         // and the reload scheduled by that event applies it to the in-memory properties
         waitFor(() -> "remote".equals(engine.getProperty(theirs)));
         // let the change task finish before the storage is restored
         Thread.sleep(1000L);
      }
      finally {
         engine.removePropertyChangeListener(theirs, listener);
      }
   }

   @Test
   void remoteRemoveIsAppliedByReload() {
      String name = prefix + "key";
      storage.remotePut(name, "value", false);
      initEngine();
      assertEquals("value", engine.getProperty(name));

      storage.remoteRemove(name, false);
      engine.init(true);

      assertNull(engine.getProperty(name), "a property removed from the storage was kept");
   }

   @Test
   void remoteUpdateIsAppliedByReload() {
      String name = prefix + "key";
      storage.remotePut(name, "old", false);
      initEngine();

      storage.remotePut(name, "new", false);
      engine.init(true);

      assertEquals("new", engine.getProperty(name));
   }

   @Test
   void earlyLoadedPropertiesAreKeptByReload() {
      String name = prefix + "key";
      storage.remotePut(name, "value", false);
      initEngine();

      storage.remoteRemove(name, false);
      engine.init(true);

      assertEquals("SreeBundle", engine.getProperty("sree.bundle"));
      assertNotNull(engine.getProperty("sree.home"));
      assertNull(engine.getProperty(name, true), "early-loaded properties kept a removed key");
   }

   @Test
   void pendingUpdateSurvivesUnrelatedReload() throws Exception {
      String name = prefix + "key";
      storage.remotePut(name, "stored", false);
      initEngine();

      engine.setProperty(name, "edit");
      storage.remotePut(prefix + "other", "remote", false);
      engine.init(true);

      assertEquals("edit", engine.getProperty(name));
      assertEquals("remote", engine.getProperty(prefix + "other"));
      engine.save();
      assertEquals("edit", storage.get(name));
   }

   @Test
   void pendingRemoveSurvivesUnrelatedReload() throws Exception {
      String name = prefix + "key";
      storage.remotePut(name, "stored", false);
      initEngine();

      engine.remove(name);
      storage.remotePut(prefix + "other", "remote", false);
      engine.init(true);

      assertNull(engine.getProperty(name));
      engine.save();
      assertFalse(storage.contains(name));
   }

   @Test
   void pendingAddSurvivesReloadAndIsNotDeletedOnSave() throws Exception {
      String name = prefix + "key";
      initEngine();

      engine.setProperty(name, "added");
      engine.init(true);

      assertEquals("added", engine.getProperty(name));
      engine.save();
      assertEquals("added", storage.get(name));
   }

   @Test
   void unsavedLocalValueDoesNotOverwriteRemoteChange() throws Exception {
      // e.g. PropertyAccessedExpiryPolicy/Mailer set a property and never save it themselves
      String name = prefix + "key";
      storage.remotePut(name, "stored", false);
      initEngine();

      engine.setProperty(name, "unsaved");
      storage.remotePut(name, "remote", false);
      engine.init(true);

      assertEquals("remote", engine.getProperty(name));
      // an unrelated save on this node must not write the stale local value back
      engine.setProperty(prefix + "other", "x");
      engine.save();
      assertEquals("remote", storage.get(name));
      assertEquals("x", storage.get(prefix + "other"));
   }

   @Test
   void unsavedLocalRemoveDoesNotOverwriteRemoteChange() throws Exception {
      String name = prefix + "key";
      storage.remotePut(name, "stored", false);
      initEngine();

      engine.remove(name);
      storage.remotePut(name, "remote", false);
      engine.init(true);

      assertEquals("remote", engine.getProperty(name));
      engine.setProperty(prefix + "other", "x");
      engine.save();
      assertEquals("remote", storage.get(name));
   }

   @Test
   void unsavedLocalAddDoesNotOverwriteRemoteAdd() throws Exception {
      String name = prefix + "key";
      initEngine();

      engine.setProperty(name, "unsaved");
      storage.remotePut(name, "remote", false);
      engine.init(true);

      assertEquals("remote", engine.getProperty(name));
      engine.setProperty(prefix + "other", "x");
      engine.save();
      assertEquals("remote", storage.get(name));
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
   private KeyValueStorage<String> getStorage() throws Exception {
      return (KeyValueStorage<String>) storageField().get(engine);
   }

   private void setStorage(KeyValueStorage<String> value) throws Exception {
      storageField().set(engine, value);
   }

   private static Field storageField() throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      return field;
   }

   private PropertiesEngine engine;
   private KeyValueStorage<String> originalStorage;
   private InMemoryKeyValueStorage<String> storage;
   private String prefix;
}
