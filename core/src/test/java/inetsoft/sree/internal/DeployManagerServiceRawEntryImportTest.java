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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.dep.XAssetConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81246: the template, sub-report and embedded table entries of an import package are
 * written before any per-asset permission check, so an importer without administrator permission
 * could write them. Such an entry is refused for a non-administrator and reported in the failed
 * list.
 */
@Tag("core")
class DeployManagerServiceRawEntryImportTest {
   @BeforeEach
   void setUp() {
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      embeddedTables = mock(EmbeddedTableStorage.class);
      space = mock(DataSpace.class);

      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), mock(DataSourceRegistry.class),
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         space, embeddedTables, mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("dave", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void refusesTemplateEntryForNonAdmin() throws Exception {
      List<String> failed = importEntry("__TEMPLATE_perm.srt");

      assertRejected(failed, "__TEMPLATE_perm.srt");
      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void refusesEmbeddedTableEntryForNonAdmin() throws Exception {
      List<String> failed = importEntry("__WS_EMBEDDED_TABLE_dir/embp.tdat");

      assertRejected(failed, "__WS_EMBEDDED_TABLE_dir/embp.tdat");
      verify(embeddedTables, never()).writeTable(anyString(), any());
   }

   @Test
   void refusesSubreportEntryForNonAdmin() throws Exception {
      List<String> failed = importEntry("__SUBREPORT_/templates/subreports/subp.srt");

      assertRejected(failed, "__SUBREPORT_/templates/subreports/subp.srt");
      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void siteAdminMayImportEmbeddedTable() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      List<String> failed = importEntry("__WS_EMBEDDED_TABLE_dir/embp.tdat");

      assertTrue(failed.isEmpty(), failed.toString());
      verify(embeddedTables).writeTable(eq("embp.tdat"), any());
   }

   @Test
   void orgAdminMayImportEmbeddedTable() throws Exception {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);

      List<String> failed = importEntry("__WS_EMBEDDED_TABLE_dir/embp.tdat");

      assertTrue(failed.isEmpty(), failed.toString());
      verify(embeddedTables).writeTable(eq("embp.tdat"), any());
   }

   @Test
   void anyImporterAllowedWhenSecurityIsDisabled() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      List<String> failed = importEntry("__WS_EMBEDDED_TABLE_dir/embp.tdat");

      assertTrue(failed.isEmpty(), failed.toString());
      verify(embeddedTables).writeTable(eq("embp.tdat"), any());
   }

   private void assertRejected(List<String> failed, String filename) {
      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.noPermission", filename);
      assertEquals(List.of(expected), failed);
   }

   private List<String> importEntry(String entryName) throws Exception {
      File file = tempDir.resolve("f" + (++fileCount)).toFile();
      Files.writeString(file.toPath(), "<template>MARK</template>", StandardCharsets.UTF_8);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), entryName);
      DeploymentInfo info = mock(DeploymentInfo.class);
      when(info.getNames()).thenReturn(names);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      List<String> failed = new ArrayList<>();

      Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
         .findFirst().orElseThrow();
      method.setAccessible(true);
      method.invoke(service, file, null, new ArrayList<>(), failed, embeddedTables,
                    new ArrayList<>(), new ArrayList<>(), true, null, info, false, config,
                    space, principal);
      return failed;
   }

   @TempDir
   Path tempDir;
   private int fileCount;
   private DeployManagerService service;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private EmbeddedTableStorage embeddedTables;
   private DataSpace space;
   private XPrincipal principal;
}
