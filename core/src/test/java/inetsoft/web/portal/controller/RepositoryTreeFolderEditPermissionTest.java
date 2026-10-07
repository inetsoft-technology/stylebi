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
package inetsoft.web.portal.controller;

import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.web.RecycleBin;
import inetsoft.web.portal.model.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77838, editing a portal repository folder without changing its name only writes the
 * alias and description, and that branch checked no permission while a rename of the same folder
 * needs WRITE on it. Runs against the real repository tree controller, replet engine and
 * registry, with a security engine that refuses only WRITE on the folder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryTreeFolderEditPermissionTest {
   private String orgId;

   @BeforeEach
   void setUp() {
      orgId = Organization.getDefaultOrganizationID();
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void editWithUnchangedNameWithoutWriteIsRefused() throws Exception {
      String folder = "F77838a";
      addFolder(folder, "OldAlias", "OldDesc");
      List<List<Object>> calls = new ArrayList<>();

      MessageCommand[] result = new MessageCommand[1];
      withWriteRefusedOn(folder, calls, () -> result[0] = controller().addRepositoryFolder(
         editEvent(folder, folder, "NewAlias", "NewDesc"), user()));

      assertNotNull(result[0], "the edit was not refused");
      assertEquals(MessageCommand.Type.ERROR, result[0].getType());
      assertEquals(Catalog.getCatalog().getString("common.writeAuthority", folder),
                   result[0].getMessage());
      assertTrue(calls.contains(List.of(ResourceType.REPORT, folder, ResourceAction.WRITE)),
                 () -> "WRITE on the folder was not checked: " + calls);

      RepletRegistryManager.getInstance().clearOrgCache(orgId);
      assertEquals("OldAlias", registry().getFolderAlias(folder));
      assertEquals("OldDesc", registry().getFolderDescription(folder));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void editWithChangedNameWithoutWriteIsRefusedAndKeepsAlias() throws Exception {
      String folder = "F77838b";
      addFolder(folder, "OldAlias", "OldDesc");

      MessageCommand[] result = new MessageCommand[1];
      withWriteRefusedOn(folder, new ArrayList<>(), () -> result[0] = controller()
         .addRepositoryFolder(editEvent(folder, folder + "x", "NewAlias", "NewDesc"), user()));

      assertNotNull(result[0], "the rename was not refused");
      assertEquals(MessageCommand.Type.ERROR, result[0].getType());
      assertTrue(registry().isFolder(folder));
      assertFalse(registry().isFolder(folder + "x"));
      assertEquals("OldAlias", registry().getFolderAlias(folder));
      assertEquals("OldDesc", registry().getFolderDescription(folder));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void editWithUnchangedNameWithWriteStillWorks() throws Exception {
      String folder = "F77838c";
      addFolder(folder, "OldAlias", "OldDesc");
      List<List<Object>> calls = new ArrayList<>();

      // WRITE is refused on another folder only
      MessageCommand[] result = new MessageCommand[1];
      withWriteRefusedOn("Other77838c", calls, () -> result[0] = controller().addRepositoryFolder(
         editEvent(folder, folder, "NewAlias", "NewDesc"), user()));

      assertNull(result[0], () -> "edit refused: " + result[0].getMessage());
      RepletRegistryManager.getInstance().clearOrgCache(orgId);
      assertEquals("NewAlias", registry().getFolderAlias(folder));
      assertEquals("NewDesc", registry().getFolderDescription(folder));
   }

   // ---- helpers ----

   private void addFolder(String path, String alias, String description) throws Exception {
      RepletRegistry reg = registry();
      reg.addFolder(path);
      reg.setFolderAlias(path, alias);
      reg.setFolderDescription(path, description);
      reg.save();
   }

   private RepletRegistry registry() throws Exception {
      return RepletRegistryManager.getInstance().getRegistry(orgId);
   }

   private static AddRepositoryFolderEvent editEvent(String path, String name, String alias,
                                                     String description)
   {
      return new AddRepositoryFolderEvent.Builder()
         .entry(new RepositoryEntryModel<>(new RepositoryEntry(path, RepositoryEntry.FOLDER)))
         .name(name)
         .alias(alias)
         .description(description)
         .edit(true)
         .confirmed(false)
         .build();
   }

   private SRPrincipal user() {
      return new SRPrincipal(new IdentityID("u77838", orgId), new IdentityID[0], new String[0],
                             orgId, 1L);
   }

   private static RepositoryTreeController controller() {
      return new RepositoryTreeController(SUtil.getRepletRepository(), null, null,
         mock(ScheduleManager.class), mock(RecycleBin.class), RepletRegistryManager.getInstance());
   }

   private interface Body {
      void run() throws Exception;
   }

   /**
    * Runs the body with a security engine that refuses WRITE on the given report folder, allows
    * every other permission check and records each (type, resource, action) it is asked.
    */
   private static void withWriteRefusedOn(String folder, List<List<Object>> calls, Body body)
      throws Exception
   {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            if(!inv.getMethod().getName().equals("checkPermission")) {
               return inv.callRealMethod();
            }

            Object[] args = inv.getArguments();
            calls.add(Arrays.asList(args[1], args[2], args[3]));
            return !(args[1] == ResourceType.REPORT && folder.equals(args[2]) &&
               args[3] == ResourceAction.WRITE);
         }));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         body.run();
      }
   }
}
