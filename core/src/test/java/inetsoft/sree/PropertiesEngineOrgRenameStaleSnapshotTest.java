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
import inetsoft.test.*;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81618: an organization property that another node saved is not in this node's in-memory
 * properties until the debounced reload, so renaming the organization right after the save must
 * still move it to the new organization ID.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineOrgRenameStaleSnapshotTest {
   @BeforeEach
   void setUp() throws Exception {
      engine = PropertiesEngine.getInstance();
      storage = new InMemoryKeyValueStorage<>();
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      field.set(engine, storage);
      engine.init();
   }

   @Test
   void renameMovesPropertyStoredByAnotherNodeBeforeReload() throws Exception {
      // the other node's save; this node's memory only catches up after the 500 ms debounce
      storage.remotePut("inetsoft.org.a.max.row.count", "18", true);
      assertNull(engine.getProperties().getProperty("inetsoft.org.a.max.row.count"));

      IdentityService service = mock(IdentityService.class, withSettings()
         .defaultAnswer(CALLS_REAL_METHODS));
      service.updateOrgProperties("A", "A2");
      engine.save();

      assertEquals("18", storage.get("inetsoft.org.a2.max.row.count"));
      assertNull(storage.get("inetsoft.org.a.max.row.count"));
   }

   @Test
   void syncKeepsPendingLocalChange() throws Exception {
      engine.setProperty("inetsoft.org.b.max.row.count", "5");
      storage.remotePut("inetsoft.org.b.max.row.count", "7", true);
      storage.remotePut("inetsoft.org.b.max.col.count", "9", true);

      engine.syncOrgScopedProperties("B");

      assertEquals("5", engine.getProperties().getProperty("inetsoft.org.b.max.row.count"));
      assertEquals("9", engine.getProperties().getProperty("inetsoft.org.b.max.col.count"));
      // the merged property is already stored, so it is not a pending change
      engine.save();
      assertEquals("5", storage.get("inetsoft.org.b.max.row.count"));
   }

   private PropertiesEngine engine;
   private InMemoryKeyValueStorage<String> storage;
}
