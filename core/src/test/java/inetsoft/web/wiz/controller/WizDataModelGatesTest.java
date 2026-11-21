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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The gates deny with the Portal's SecurityException text and ask the same services it asks. */
@Tag("core")
class WizDataModelGatesTest {
   private final Principal bob = () -> "bob";
   private DataSourceService dss;
   private LogicalModelService lms;
   private WizDataModelGates gates;

   @BeforeEach
   void setUp() {
      dss = mock(DataSourceService.class);
      lms = mock(LogicalModelService.class);
      gates = new WizDataModelGates(dss, lms);
   }

   @Test
   void dataSourceDenialNamesTheSourceAndUser() throws Exception {
      when(dss.checkPermission("D", ResourceAction.WRITE, bob)).thenReturn(false);

      SecurityException e = assertThrows(SecurityException.class,
         () -> gates.requireDataSourcePermission("D", ResourceAction.WRITE, bob));
      assertEquals("Unauthorized access to resource \"D\" by user " + bob, e.getMessage());

      when(dss.checkPermission("D", ResourceAction.READ, bob)).thenReturn(true);
      assertDoesNotThrow(() -> gates.requireDataSourcePermission("D", ResourceAction.READ, bob));
   }

   @Test
   void folderGateNamesTheFolderPathOrTheSourceForTheRoot() throws Exception {
      when(dss.checkPermission(anyString(), any(), any(ResourceAction.class), any(Principal.class)))
         .thenReturn(false);

      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireFolderPermission("D", "F", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D/F\""));
      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireFolderPermission("D", "", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D\""));
   }

   @Test
   void modelGateUsesTheStoredFolderOfTheModel() {
      when(lms.checkPermission("D", "F", "M", null, ResourceAction.DELETE, bob)).thenReturn(false);
      when(lms.checkPermission("D", "F", "M", null, ResourceAction.READ, bob)).thenReturn(true);

      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireModelPermission("D", "F", "M", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D/F/M\""));
      assertDoesNotThrow(
         () -> gates.requireModelPermission("D", "F", "M", ResourceAction.READ, bob) );
   }

   @Test
   void editAccessIsTheServicesOwnCheck() throws Exception {
      doThrow(new SecurityException("x")).when(dss).checkDataModelEditPermission("D", "c", bob);

      assertThrows(SecurityException.class, () -> gates.requireEditAccess("D", "c", bob));
      assertDoesNotThrow(() -> gates.requireEditAccess("D", null, bob));
   }

   // -- a not-visible item is answered like a missing one (review R1-4) --------------------------

   @Test
   void aSourceWithoutReadAnswersExactlyLikeAMissingSource() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSource("D")).thenReturn(mock(JDBCDataSource.class));
      when(repository.getDataSource("X")).thenReturn(mock(inetsoft.uql.xmla.XMLADataSource.class));
      WizDataModelSource source = new WizDataModelSource(repository);
      when(dss.checkPermission(anyString(), eq(ResourceAction.READ), any(Principal.class)))
         .thenReturn(false);

      String missing = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> gates.requireVisibleJdbcDataModel(source, "nope", bob)).getMessage();
      WizDataModelErrors.DmFailure existing = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> gates.requireVisibleJdbcDataModel(source, "D", bob));
      WizDataModelErrors.DmFailure notJdbc = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> gates.requireVisibleJdbcDataModel(source, "X", bob));

      assertEquals("NOT_FOUND", existing.reason());
      assertEquals("NOT_FOUND", notJdbc.reason(), "the type of a source is not revealed");
      assertEquals(missing.replace("nope", "D"), existing.getMessage());
      assertEquals(missing.replace("nope", "X"), notJdbc.getMessage());
   }

   @Test
   void visibilityIsTheRuleThatListsTheItemInTheDataTab() throws Exception {
      XDataModel dm = mock(XDataModel.class);
      XLogicalModel model = mock(XLogicalModel.class);
      when(model.getFolder()).thenReturn("F");
      XPartition view = mock(XPartition.class);
      when(view.getFolder()).thenReturn("F");
      when(dm.getLogicalModel("lm")).thenReturn(model);
      when(dm.getPartition("pv")).thenReturn(view);
      when(dm.getVirtualPrivateModel("vp")).thenReturn(mock(VirtualPrivateModel.class));
      when(dm.getFolders()).thenReturn(new String[] { "F" });
      when(lms.checkPermission("D", "F", "lm", null, ResourceAction.READ, bob)).thenReturn(true);
      when(dss.checkPermission("D", "F", ResourceAction.WRITE, bob)).thenReturn(true);
      when(dss.checkPermission("D", ResourceAction.WRITE, bob)).thenReturn(false);
      when(dss.checkDataModelFolderPermission("D/F", ResourceAction.READ, bob)).thenReturn(true);

      assertTrue(gates.maySee(dm, "D", "logicalModel", "lm", bob));
      assertTrue(gates.maySee(dm, "D", "physicalView", "pv", bob));
      assertFalse(gates.maySee(dm, "D", "vpm", "vp", bob), "a VPM needs WRITE on the source");
      assertTrue(gates.maySee(dm, "D", "folder", "F", bob));
      assertFalse(gates.maySee(dm, "D", "logicalModel", "ghost", bob));
      assertEquals("logicalModel", gates.visibleKindOfName(dm, "D", "lm", bob));
      assertNull(gates.visibleKindOfName(dm, "D", "vp", bob),
                 "an item the caller may not see is answered like a missing one");
   }

   @Test
   void aClashWithAnInvisibleItemDoesNotNameItsKind() {
      XDataModel dm = mock(XDataModel.class);
      when(dm.getDataSource()).thenReturn("D");
      when(dm.getVirtualPrivateModel("vp")).thenReturn(mock(VirtualPrivateModel.class));
      when(dm.getLogicalModel("lm")).thenReturn(mock(XLogicalModel.class));

      WizDataModelErrors.DmFailure hidden = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> WizDataModelNames.requireUniqueInSource(dm, "vp", (kind, name) -> false));
      WizDataModelErrors.DmFailure shown = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> WizDataModelNames.requireUniqueInSource(dm, "lm", (kind, name) -> true));

      assertEquals("DUPLICATE_NAME", hidden.reason());
      assertNull(hidden.toBody().existingKind());
      assertFalse(hidden.getMessage().contains("vpm"), hidden.getMessage());
      assertEquals("logicalModel", shown.toBody().existingKind());
   }
}
