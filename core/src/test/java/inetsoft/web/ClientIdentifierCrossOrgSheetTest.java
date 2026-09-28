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
package inetsoft.web;

/*
 * Bug #77063: four web endpoints build an AssetEntry from a client-supplied identifier (whose
 * 5th component is kept as the entry's orgID) and loaded it with getSheet(..., false, ...),
 * which skipped the READ check, the only place a foreign org is rejected. Another org's sheet
 * metadata was returned:
 *   S1 /api/composer/vs/hyperlink-parameters          (HyperlinkDialogController)
 *   S2 /api/data/logicalModel/vs/autoDrill-parameters (LogicalModelController)
 *   S3 /api/portal/data/autodrill/worksheet/fields    (DataAutoDrillController)
 *   S4 /api/vs/route-data                             (OpenViewsheetController)
 *
 * The sheets are seeded into org A's and org B's storage through a real BlobIndexedStorage and
 * read through the real AbstractAssetEngine.getSheet(). StubAssetEngine inherits
 * AbstractAssetEngine.checkPermission(), which always grants, so the same-org and site admin
 * controls here only show that the org check lets them through; real READ ACLs for same-org
 * callers are the same check the viewer open path (WorksheetEngine.openSheet) already uses.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogController;
import inetsoft.web.portal.controller.database.DataAutoDrillController;
import inetsoft.web.portal.controller.database.LogicalModelController;
import inetsoft.web.viewsheet.controller.OpenViewsheetController;
import inetsoft.web.viewsheet.model.ViewsheetRouteDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ClientIdentifierCrossOrgSheetTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static final String ORG_B_VS = "1^128^__NULL__^SecretVS77063^" + ORG_B;
   private static final String ORG_B_WS = "1^2^__NULL__^SecretWS77063^" + ORG_B;
   private static final String ORG_A_VS = "1^128^__NULL__^OwnVS77063^" + ORG_A;
   private static final String ORG_A_WS = "1^2^__NULL__^OwnWS77063^" + ORG_A;
   private static final String SECRET_VAR = "orgBSecretVar";
   private static final String SECRET_COL = "OrgBSecretCol";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;
   private static SRPrincipal siteAdmin;

   @Autowired
   private BlobStorageManager blobStorageManager;

   private AbstractAssetEngine engine;
   private MockedStatic<SUtil> sutil;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser("orgAUser", ORG_A, "password")
         .addUser("siteAdmin77063", ORG_A, "password")
         .addSysAdminRole("siteAdmins77063", ORG_A)
         .addUserToRole("siteAdmin77063", "siteAdmins77063", ORG_A)
         .setup();
      orgAUser = builder.principalOf("orgAUser", ORG_A);
      siteAdmin = builder.principalOf("siteAdmin77063", ORG_A);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      BlobIndexedStorage storage = new BlobIndexedStorage(blobStorageManager);
      engine = new StubAssetEngine(storage);
      storage.putXMLSerializable(ORG_B_VS, createViewsheet(SECRET_VAR));
      storage.putXMLSerializable(ORG_B_WS, createWorksheet(SECRET_COL));
      storage.putXMLSerializable(ORG_A_VS, createViewsheet("ownVar"));
      storage.putXMLSerializable(ORG_A_WS, createWorksheet("OwnCol"));

      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      if(sutil != null) {
         sutil.close();
      }
   }

   @Test
   void createAssetEntry_keepsClientOrg() {
      assertEquals(ORG_B, AssetEntry.createAssetEntry(ORG_B_VS).getOrgID());
   }

   // S1
   @Test
   void hyperlinkParameters_otherOrgViewsheet_returnsNothing() throws Exception {
      assertArrayEquals(new String[0], hyperlink().getViewsheetParameters(ORG_B_VS, orgAUser));
   }

   @Test
   void hyperlinkParameters_ownOrgAndSiteAdmin_stillReturnVariables() throws Exception {
      assertArrayEquals(new String[] { "ownVar" },
                        hyperlink().getViewsheetParameters(ORG_A_VS, orgAUser));
      assertArrayEquals(new String[] { SECRET_VAR },
                        hyperlink().getViewsheetParameters(ORG_B_VS, siteAdmin));
   }

   // S2
   @Test
   void autoDrillParameters_otherOrgViewsheet_returnsNothing() throws Exception {
      assertArrayEquals(new String[0], logicalModel().getViewsheetParameters(ORG_B_VS, orgAUser));
      assertArrayEquals(new String[] { "ownVar" },
                        logicalModel().getViewsheetParameters(ORG_A_VS, orgAUser));
   }

   // S3
   @Test
   void worksheetFields_otherOrgWorksheet_rejected() throws Exception {
      DataAutoDrillController controller = autoDrill();
      assertThrows(MessageException.class,
                   () -> controller.getWorksheetFields(ORG_B_WS, orgAUser));
      // a nonexistent foreign sheet is rejected the same way (no existence oracle)
      assertThrows(MessageException.class, () -> controller.getWorksheetFields(
         "1^2^__NULL__^NoSuchWS77063^" + ORG_B, orgAUser));
      assertEquals(List.of("OwnCol"), controller.getWorksheetFields(ORG_A_WS, orgAUser));
      assertEquals(List.of(SECRET_COL),
                   controller.getWorksheetFields(ORG_B_WS, siteAdmin));
   }

   // S4
   @Test
   void routeData_otherOrgViewsheet_returnsDefaults() throws Exception {
      ViewsheetRouteDataModel model = openViewsheet().getRouteData(ORG_B_VS, orgAUser);
      assertFalse(model.scaleToScreen());
      assertFalse(model.fitToWidth());
      assertFalse(model.hasBaseEntry());

      assertTrue(openViewsheet().getRouteData(ORG_A_VS, orgAUser).scaleToScreen());
      assertTrue(openViewsheet().getRouteData(ORG_B_VS, siteAdmin).scaleToScreen());
   }

   private HyperlinkDialogController hyperlink() {
      return new HyperlinkDialogController(null, engine, null, null);
   }

   private LogicalModelController logicalModel() {
      return new LogicalModelController(engine, null, null, null, null, null);
   }

   private DataAutoDrillController autoDrill() {
      DataAutoDrillController controller = new DataAutoDrillController();
      ReflectionTestUtils.setField(controller, "engine", engine);
      return controller;
   }

   private OpenViewsheetController openViewsheet() {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(engine);
      return new OpenViewsheetController(null, null, null, null, null, viewsheetService, null, null);
   }

   private static Viewsheet createViewsheet(String variable) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);
      vs.getViewsheetInfo().setFitToWidth(true);
      TableVSAssembly table = new TableVSAssembly(vs, "T1");
      ConditionList conditions = new ConditionList();
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(new UserVariable(variable));
      conditions.append(new ConditionItem(new AttributeRef("col"), condition, 0));
      table.setPreConditionList(conditions);
      vs.addAssembly(table);
      return vs;
   }

   private static Worksheet createWorksheet(String column) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "E1");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { "string" }, new Object[][] { { column }, { "v" } }));
      ws.addAssembly(table);
      ws.setPrimaryAssembly("E1");
      return ws;
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, (Cluster) null);
         istore = storage;
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
