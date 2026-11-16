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
package inetsoft.mv;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81422, the analysis of a viewsheet that shares a registered MV returns that registered
 * definition, and the analysis job then clears the worksheet from its container
 * ({@link MVDef#persistAndClearWorksheet()}). The worksheet snapshot the expression columns are
 * compared against must still be available afterwards, reloaded from the storage it was
 * persisted to.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDefSnapshotReloadTest {
   @Test
   void registeredDefinitionKeepsItsSnapshotAfterTheAnalysisClearedIt() throws Exception {
      MVDef def = def(mirrorWorksheet("13"));
      Map<String, Worksheet> storage = new HashMap<>();

      try(Mocks mocks = new Mocks(storage, true)) {
         // what the analysis job does to the registered definition it shares
         def.persistAndClearWorksheet();
         assertNull(def.container.ws, "the analysis clears the worksheet from memory");

         assertTrue(def.isExpressionChanged(mirrorWorksheet("17")));
         assertFalse(def.isExpressionChanged(mirrorWorksheet("13")));
         assertNotNull(def.getWorksheet());
         // reloaded once and kept in the container, not read from storage per call
         verify(mocks.storage, times(1)).getWorksheet(anyString(), anyString());
      }
   }

   @Test
   void unregisteredDefinitionIsNotReloaded() throws Exception {
      MVDef def = def(mirrorWorksheet("13"));
      Map<String, Worksheet> storage = new HashMap<>();

      try(Mocks mocks = new Mocks(storage, false)) {
         def.persistAndClearWorksheet();

         assertNull(def.getWorksheet());
         assertFalse(def.isExpressionChanged(mirrorWorksheet("17")));
         verify(mocks.storage, never()).getWorksheet(anyString(), anyString());
      }
   }

   @Test
   void missingStoredWorksheetIsNotChanged() {
      MVDef def = def(null);

      try(Mocks mocks = new Mocks(new HashMap<>(), true)) {
         assertNull(def.getWorksheet());
         assertFalse(def.isExpressionChanged(mirrorWorksheet("17")));
      }
   }

   private MVDef def(Worksheet snapshot) {
      MVDef def = new MVDef();
      ReflectionTestUtils.setField(def, "otname", "ORDERS");
      ReflectionTestUtils.setField(def, "tname", "ORDERS");
      ReflectionTestUtils.setField(def, "mvname", "stale_mv_test_ORDERS_1");
      def.container = new MVDef.MVContainer(new ArrayList<>(), snapshot);
      return def;
   }

   /** The worksheet of a viewsheet: the table is renamed ORDERS_O under the mirror ORDERS. */
   private Worksheet mirrorWorksheet(String expression) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "ORDERS_O");
      ColumnSelection cols = new ColumnSelection();
      ColumnRef id = new ColumnRef(new AttributeRef(null, "ID"));
      id.setDataType(XSchema.INTEGER);
      cols.addAttribute(id);
      ExpressionRef eref = new ExpressionRef(null, "Flag");
      eref.setExpression(expression);
      ColumnRef flag = new ColumnRef(eref);
      flag.setSQL(true);
      flag.setDataType(XSchema.INTEGER);
      cols.addAttribute(flag);
      table.setColumnSelection(cols);
      ws.addAssembly(table);
      ws.addAssembly(new MirrorTableAssembly(ws, "ORDERS", table));
      return ws;
   }

   /** The MV worksheet storage (a map), the MV manager and the organization. */
   private static final class Mocks implements AutoCloseable {
      Mocks(Map<String, Worksheet> stored, boolean registered) {
         storage = mock(MVWorksheetStorage.class);
         MVManager manager = mock(MVManager.class);
         OrganizationManager orgManager = mock(OrganizationManager.class);

         try {
            doAnswer(inv -> stored.put(inv.getArgument(0), inv.getArgument(1)))
               .when(storage).putWorksheet(anyString(), any(Worksheet.class), anyString());
            when(storage.getWorksheet(anyString(), anyString()))
               .thenAnswer(inv -> stored.get(inv.<String>getArgument(0)));
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }

         when(manager.containsMV(any(MVDef.class))).thenReturn(registered);
         when(orgManager.getCurrentOrgID()).thenReturn("host-org");
         storageStatic = mockStatic(MVWorksheetStorage.class);
         storageStatic.when(MVWorksheetStorage::getInstance).thenReturn(storage);
         managerStatic = mockStatic(MVManager.class);
         managerStatic.when(MVManager::getManager).thenReturn(manager);
         orgStatic = mockStatic(OrganizationManager.class);
         orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      }

      @Override
      public void close() {
         orgStatic.close();
         managerStatic.close();
         storageStatic.close();
      }

      final MVWorksheetStorage storage;
      private final MockedStatic<MVWorksheetStorage> storageStatic;
      private final MockedStatic<MVManager> managerStatic;
      private final MockedStatic<OrganizationManager> orgStatic;
   }
}
