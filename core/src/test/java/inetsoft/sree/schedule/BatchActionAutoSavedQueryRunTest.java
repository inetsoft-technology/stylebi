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
package inetsoft.sree.schedule;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.Drivers;
import inetsoft.util.*;
import inetsoft.web.AutoSaveUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77549, end to end through BatchAction.run: a stored batch action whose query entry
 * carries openAutoSaved/autoFileName (a task stored before the fix, or changed in place) must
 * not read another user's auto-saved worksheet. The victim's draft is written to the real
 * auto-save blob storage of the organization, and the query is read by a real
 * AbstractAssetEngine.getSheet (with the real AbstractIndexedStorage.getAutoSavedSheet), which
 * skips the permission check for an openAutoSaved entry.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  BatchActionAutoSavedQueryRunTest.LocaleConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BatchActionAutoSavedQueryRunTest {
   private static final String ORG = "baqorg";
   private static final String VICTIM_TABLE = "VictimDraftTable";
   private static SecurityTestDataBuilder builder;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("baq", ORG)
         .addUser("baqAttacker", ORG, "password")
         .addUser("baqVictim", ORG, "password")
         .setup();
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void run_storedAutoSaveProperties_doNotReadAnotherUsersAutoSavedWorksheet() throws Throwable {
      SRPrincipal attacker = builder.principalOf("baqAttacker", ORG);
      SRPrincipal victim = builder.principalOf("baqVictim", ORG);
      ThreadContext.setContextPrincipal(attacker);

      // the victim's unsaved worksheet in the organization's auto-save storage
      String victimFile = "4^WORKSHEET^" + victim.getName() + "^Private^~";
      Worksheet draft = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(draft, VICTIM_TABLE);
      draft.addAssembly(table);
      draft.setPrimaryAssembly(table);
      AutoSaveUtils.writeAutoSaveFile(
         AbstractIndexedStorage.encodeXMLSerializable(draft, victimFile), victimFile, victim);

      AbstractIndexedStorage storage = mock(AbstractIndexedStorage.class);
      when(storage.getAutoSavedSheet(any(), any())).thenCallRealMethod();
      ReflectionTestUtils.setField(storage, "listeners", new ArrayList<TransformListener>());
      List<Object> read = new ArrayList<>();
      AbstractAssetEngine engine = spy(new StubAssetEngine(storage));
      doAnswer(inv -> {
         try {
            read.add(inv.callRealMethod());
         }
         catch(Exception e) {
            read.add(e);
         }

         // stop here, the query of the sheet is not part of this test
         throw new StopException();
      }).when(engine).getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class));

      // the child task the batch runs, owned by the attacker
      ScheduleTask child = new ScheduleTask("Child");
      child.setOwner(IdentityID.getIdentityIDFromKey(attacker.getName()));

      // a stored action, the entry changed in place after it was set (or read before the fix)
      BatchAction action = new BatchAction();
      action.setTaskId(child.getTaskId());
      action.setQueryEntry(AssetEntry.createAssetEntry("1^2^__NULL__^Sales^" + ORG));
      action.getQueryEntry().setProperty("openAutoSaved", "true");
      action.getQueryEntry().setProperty("autoFileName", victimFile);
      action.setQueryParameters(Map.of("p1", "col1"));

      ScheduleManager manager = mock(ScheduleManager.class);
      when(manager.getScheduleTask(child.getTaskId())).thenReturn(child);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager =
             mockStatic(ScheduleManager.class, CALLS_REAL_METHODS);
          MockedStatic<Drivers> drivers = mockStatic(Drivers.class))
      {
         // the sheet class of a stored asset is loaded by the driver class loader, which has no
         // core classes in a unit test
         Drivers driverClasses = mock(Drivers.class);
         when(driverClasses.getDriverClass(anyString(), any()))
            .thenAnswer(inv -> Class.forName(inv.getArgument(0)));
         drivers.when(Drivers::getInstance).thenReturn(driverClasses);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);
         scheduleManager.when(ScheduleManager::getScheduleManager).thenReturn(manager);

         assertThrows(StopException.class, () -> action.run(attacker));
      }
      finally {
         AutoSaveUtils.deleteAutoSaveFile(victimFile, victim);
      }

      assertEquals(1, read.size(), "the query is read once: " + read);
      Object sheet = read.get(0);
      assertFalse(sheet instanceof Worksheet ws && ws.getAssembly(VICTIM_TABLE) != null,
                  "the batch query read the victim's auto-saved worksheet");
      verify(storage, never()).getAutoSavedSheet(any(), any());
   }

   // the child principal gets the task's locale (BatchAction.getChildPrincipal)
   @org.springframework.context.annotation.Configuration
   static class LocaleConfiguration {
      @org.springframework.context.annotation.Bean
      LocaleService localeService(SecurityEngine securityEngine) {
         return new LocaleService(securityEngine);
      }
   }

   private static class StopException extends RuntimeException {
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, (Cluster) null);
         this.istore = storage;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return false;
      }
   }
}
