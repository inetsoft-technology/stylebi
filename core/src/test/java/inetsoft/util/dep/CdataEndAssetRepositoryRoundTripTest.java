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
package inetsoft.util.dep;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.ws.RenameColumnController;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77850: user text holding {@code ]]>} saved and reopened through the real asset
 * repository (AssetRepository.setSheet/getSheet), as the composer and deployment do:
 * a dashboard named/aliased with ]]> embedded by another viewsheet, a worksheet column renamed
 * through the server rename path (which has no character check), and a deployment export and
 * import of a viewsheet whose alias holds ]]>.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CdataEndAssetRepositoryRoundTripTest {
   private static final String DASH = "Q1]]>Q2";
   private AssetRepository repository;
   private SRPrincipal admin;
   private String orgId;
   private final List<AssetEntry> cleanup = new ArrayList<>();

   @BeforeEach
   void setUp() {
      // permission checks are not under test; the test principal has no session
      AssetRepository.IGNORE_PERM.set(true);
      repository = AssetUtil.getAssetRepository(false);
      orgId = OrganizationManager.getInstance().getCurrentOrgID();
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(AssetEntry entry : cleanup) {
            if(repository.containsEntry(entry)) {
               repository.removeSheet(entry, null, true);
            }
         }
      }
      finally {
         cleanup.clear();
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   @Test
   void viewsheetEmbeddingDashboardNamedWithCdataEndReopens() throws Exception {
      AssetEntry dash = viewsheetEntry(DASH);
      dash.setAlias(DASH);
      Viewsheet dashboard = new Viewsheet();
      dashboard.addAssembly(new TextVSAssembly(dashboard, "Inner1"));
      save(dash, dashboard);

      AssetEntry parentEntry = viewsheetEntry("vs77850embedding");
      save(parentEntry, new Viewsheet());
      Viewsheet parent = (Viewsheet) open(parentEntry);

      // what ComposerObjectService.addEmbeddedViewsheet does
      Viewsheet embedded = ((Viewsheet) repository.getSheet(dash, admin, true, AssetContent.ALL))
         .createVSAssembly("Viewsheet1");
      embedded.setEntry(dash);
      embedded.initDefaultFormat();
      parent.addAssembly(embedded);
      repository.setSheet(parentEntry, parent, admin, true);

      Viewsheet back = (Viewsheet) open(parentEntry);
      Viewsheet eback = (Viewsheet) back.getAssembly("Viewsheet1");
      assertNotNull(eback);
      assertEquals(DASH, eback.getEntry().getPath());
      assertEquals(DASH, eback.getEntry().getAlias());
      assertNotNull(eback.getAssembly("Inner1"), "embedded content resolved from the dashboard");
   }

   @Test
   void worksheetColumnRenamedThroughServerPathReopens() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER, XSchema.STRING },
                                               new Object[][] { { "id", "name" }, { 1, "a" } }));
      ws.addAssembly(table);
      ws.addAssembly(new MirrorTableAssembly(ws, "M1", table));
      ColumnRef column = (ColumnRef) table.getColumnSelection(false).getAttribute("name");
      String alias = "Sales]]>2026 & <x>";

      // RenameColumnService.renameColumn -> this, no server-side character check
      assertFalse(RenameColumnController.renameColumn(ws, mock(CommandDispatcher.class), table,
                                                      column, alias));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws77850rename", null, orgId);
      save(entry, ws);

      Worksheet back = (Worksheet) open(entry);
      ColumnSelection columns = ((TableAssembly) back.getAssembly("T1")).getColumnSelection(false);
      ColumnRef cback = (ColumnRef) columns.getAttribute(alias);
      assertNotNull(cback, "renamed column " + columns);
      assertEquals(alias, cback.getAlias());
      assertNotNull(back.getAssembly("M1"));
   }

   @Test
   void deploymentExportImportOfViewsheetAliasedWithCdataEnd() throws Exception {
      String alias = "Exp]]>Alias";
      AssetEntry entry = viewsheetEntry("vs77850deploy");
      entry.setAlias(alias);
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      save(entry, vs);

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      JarOutputStream jar = new JarOutputStream(bytes);
      assertTrue(new ViewsheetAsset(entry).writeContent(jar));
      jar.finish();
      repository.removeSheet(entry, null, true);

      byte[] content;

      try(JarInputStream in = new JarInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
         assertNotNull(in.getNextJarEntry());
         content = in.readAllBytes();
      }

      ViewsheetAsset imported = new ViewsheetAsset(viewsheetEntry("vs77850deploy"));
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      imported.parseContent(new ByteArrayInputStream(content), config, true, false);

      assertEquals(alias, imported.getAssetEntry().getAlias());
      assertNotNull(((Viewsheet) open(entry)).getAssembly("Text1"));
   }

   private void save(AssetEntry entry, AbstractSheet sheet) throws Exception {
      cleanup.add(entry);
      repository.setSheet(entry, sheet, admin, true);
   }

   private AbstractSheet open(AssetEntry entry) throws Exception {
      AbstractSheet sheet = repository.getSheet(entry, admin, false, AssetContent.ALL);
      assertNotNull(sheet, "sheet reopens: " + entry);
      return sheet;
   }

   private AssetEntry viewsheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            orgId);
   }
}
