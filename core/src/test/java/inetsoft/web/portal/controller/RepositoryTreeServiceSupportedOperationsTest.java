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
package inetsoft.web.portal.controller;

import inetsoft.sree.*;
import inetsoft.sree.security.*;
import inetsoft.web.portal.model.RepositoryEntryModelFactoryService;
import inetsoft.web.portal.model.RepositoryTreeAction;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RepositoryTreeService.getSupportedOperations with a per-listing context.
 *  - the entry-independent checks (MATERIALIZATION and VIEWSHEET on "*") run once per listing and
 *    only when a viewsheet entry needs them; without a context they run once per entry as before
 *  - the operations of every entry are identical with and without the context, over a mix of
 *    explicit, inherited, foreign, My Report, folder and administrator permissions
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class RepositoryTreeServiceSupportedOperationsTest {
   private static final String ALL = "*";

   private final AnalyticRepository repository = mock(AnalyticRepository.class);
   private final SecurityProvider provider = mock(SecurityProvider.class);
   private final RepositoryTreeService service = new RepositoryTreeService(
      repository, provider, mock(RepositoryEntryModelFactoryService.class),
      mock(RepletRegistryManager.class));

   private final Principal plain = principal("plain");
   private final Principal sysAdmin = principal("sysAdmin");
   private final Principal orgAdmin = principal("orgAdmin");
   private final Principal composerOnly = principal("composerOnly");

   // path -> {delete, write} for the plain user; the folder entry only has READ
   private final Map<String, boolean[]> grants = new HashMap<>();

   {
      grants.put("/", new boolean[] { false, false });
      grants.put("explicit", new boolean[] { true, true });
      grants.put("foreign", new boolean[] { false, false });
      grants.put("inherited", new boolean[] { false, true });
      grants.put("folder", new boolean[] { false, true });
      grants.put("My Reports/mine", new boolean[] { false, false });
   }

   private List<RepositoryEntry> entries() {
      List<RepositoryEntry> entries = new ArrayList<>();
      entries.add(new ViewsheetEntry("explicit"));
      entries.add(new ViewsheetEntry("foreign"));
      entries.add(new ViewsheetEntry("inherited"));
      entries.add(new ViewsheetEntry("My Reports/mine", new IdentityID("plain", "org1")));
      entries.add(new RepletFolderEntry("folder"));
      entries.add(new RepletFolderEntry("/"));
      return entries;
   }

   private void stubPermissions() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.REPORT), anyString(), any()))
         .thenAnswer(inv -> {
            Principal p = inv.getArgument(0);
            String path = inv.getArgument(2);
            ResourceAction action = inv.getArgument(3);

            if(p == sysAdmin) {
               return true;
            }

            boolean[] grant = grants.getOrDefault(path, new boolean[] { false, false });
            return action == ResourceAction.DELETE ? grant[0] : grant[1];
         });
      when(provider.checkPermission(any(), eq(ResourceType.MATERIALIZATION), eq(ALL), any()))
         .thenAnswer(inv -> inv.getArgument(0) != composerOnly);
      when(provider.checkPermission(any(), eq(ResourceType.VIEWSHEET), eq(ALL), any()))
         .thenAnswer(inv -> inv.getArgument(0) != orgAdmin);
   }

   @Test
   void context_operationsEqualTheOldPathForEveryEntryAndPrincipal() throws Exception {
      stubPermissions();
      int compared = 0;

      for(Principal principal : List.of(plain, sysAdmin, orgAdmin, composerOnly)) {
         RepositoryTreeService.SupportedOperationsContext ctx =
            service.new SupportedOperationsContext(principal);

         for(RepositoryEntry entry : entries()) {
            EnumSet<RepositoryTreeAction> old = service.getSupportedOperations(entry, principal);
            EnumSet<RepositoryTreeAction> now =
               service.getSupportedOperations(entry, principal, ctx);
            assertEquals(old, now, principal.getName() + " " + entry.getPath());
            compared++;
         }
      }

      assertEquals(24, compared);
   }

   @Test
   void context_expectedOperationsForThePlainUser() throws Exception {
      stubPermissions();
      RepositoryTreeService.SupportedOperationsContext ctx =
         service.new SupportedOperationsContext(plain);
      Map<String, EnumSet<RepositoryTreeAction>> ops = new HashMap<>();

      for(RepositoryEntry entry : entries()) {
         ops.put(entry.getPath(), service.getSupportedOperations(entry, plain, ctx));
      }

      assertEquals(EnumSet.of(RepositoryTreeAction.MATERIALIZE, RepositoryTreeAction.EDIT,
                              RepositoryTreeAction.RENAME, RepositoryTreeAction.CHANGE_FOLDER,
                              RepositoryTreeAction.DELETE), ops.get("explicit"));
      assertEquals(EnumSet.of(RepositoryTreeAction.EDIT), ops.get("foreign"));
      assertEquals(EnumSet.of(RepositoryTreeAction.MATERIALIZE, RepositoryTreeAction.EDIT),
                   ops.get("inherited"));
      assertEquals(EnumSet.of(RepositoryTreeAction.MATERIALIZE, RepositoryTreeAction.EDIT,
                              RepositoryTreeAction.RENAME, RepositoryTreeAction.CHANGE_FOLDER,
                              RepositoryTreeAction.DELETE), ops.get("My Reports/mine"));
      assertEquals(EnumSet.of(RepositoryTreeAction.NEW_FOLDER), ops.get("folder"));
      assertEquals(EnumSet.noneOf(RepositoryTreeAction.class), ops.get("/"));
   }

   @Test
   void context_entryIndependentChecksRunOncePerListing() throws Exception {
      for(int entries : new int[] { 10, 200 }) {
         reset(repository, provider);
         stubPermissions();
         RepositoryTreeService.SupportedOperationsContext ctx =
            service.new SupportedOperationsContext(plain);

         for(int i = 0; i < entries; i++) {
            service.getSupportedOperations(new ViewsheetEntry("vs" + i), plain, ctx);
         }

         verify(provider, times(1)).checkPermission(
            plain, ResourceType.MATERIALIZATION, ALL, ResourceAction.ACCESS);
         verify(provider, times(1)).checkPermission(
            plain, ResourceType.VIEWSHEET, ALL, ResourceAction.ACCESS);
         // the per-entry checks are not touched
         verify(repository, times(entries)).checkPermission(
            eq(plain), eq(ResourceType.REPORT), anyString(), eq(ResourceAction.DELETE));
         verify(repository, times(entries)).checkPermission(
            eq(plain), eq(ResourceType.REPORT), anyString(), eq(ResourceAction.WRITE));
      }
   }

   @Test
   void withoutContext_entryIndependentChecksRunPerEntry() throws Exception {
      for(int entries : new int[] { 10, 200 }) {
         reset(repository, provider);
         stubPermissions();

         for(int i = 0; i < entries; i++) {
            service.getSupportedOperations(new ViewsheetEntry("vs" + i), plain);
         }

         verify(provider, times(entries)).checkPermission(
            plain, ResourceType.MATERIALIZATION, ALL, ResourceAction.ACCESS);
         verify(provider, times(entries)).checkPermission(
            plain, ResourceType.VIEWSHEET, ALL, ResourceAction.ACCESS);
      }
   }

   @Test
   void context_checksAreLazyAndFoldersNeverRunThem() throws Exception {
      stubPermissions();
      RepositoryTreeService.SupportedOperationsContext ctx =
         service.new SupportedOperationsContext(plain);

      for(int i = 0; i < 20; i++) {
         service.getSupportedOperations(new RepletFolderEntry("folder"), plain, ctx);
      }

      verify(provider, never()).checkPermission(
         any(), eq(ResourceType.MATERIALIZATION), anyString(), any());
      verify(provider, never()).checkPermission(
         any(), eq(ResourceType.VIEWSHEET), anyString(), any());
   }

   @Test
   void context_ofAnotherPrincipalIsIgnored() throws Exception {
      stubPermissions();
      RepositoryTreeService.SupportedOperationsContext ctx =
         service.new SupportedOperationsContext(plain);
      RepositoryEntry entry = new ViewsheetEntry("explicit");

      // composerOnly may not materialize; the context built for the plain user must not answer
      EnumSet<RepositoryTreeAction> ops = service.getSupportedOperations(entry, composerOnly, ctx);

      assertEquals(service.getSupportedOperations(entry, composerOnly), ops);
      verify(provider, atLeastOnce()).checkPermission(
         composerOnly, ResourceType.MATERIALIZATION, ALL, ResourceAction.ACCESS);
   }

   private static Principal principal(String name) {
      return new SRPrincipal(new IdentityID(name, "org1"), new IdentityID[0], new String[0],
                             "org1", 1L);
   }
}
