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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVDefinitionKey;
import inetsoft.mv.MVManager;
import inetsoft.mv.MVMetaData;
import inetsoft.mv.data.MVStorage;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.test.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #81366, the valid flag of the MV status is false once the table the MV was built from has
 * been edited in its worksheet in any way that changes its data (a filter, not only the
 * expression of a column), and stays true for an edit that doesn't.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVServiceStaleTest {
   private static final String WS_ID = "1^2^__NULL__^ws1^host-org";

   @BeforeEach
   void setUp() {
      service = new MVService(mock(ContentRepositoryTreeService.class),
                              mock(MVSupportService.class), mock(Cluster.class),
                              mock(MVManager.class), mock(DataCycleManager.class),
                              mock(SecurityEngine.class), mock(MVStorage.class));
      saved = worksheet(null);
      def = def(saved);
      worksheets = new HashMap<>();
   }

   @Test
   void unchangedWorksheetIsNotStale() throws Exception {
      worksheets.put(WS_ID, worksheet(null));

      assertFalse(stale());
   }

   @Test
   void addedFilterIsStale() throws Exception {
      worksheets.put(WS_ID, worksheet("12100"));

      assertTrue(stale());
   }

   @Test
   void mvAnalyzedBeforeTheFingerprintWasRecordedIsNotStale() throws Exception {
      ReflectionTestUtils.setField(def, "data", new MVMetaData(WS_ID, "ORDERS", "ORDERS"));
      worksheets.put(WS_ID, worksheet("12100"));

      assertFalse(stale());
   }

   @Test
   void missingWorksheetIsNotStale() throws Exception {
      worksheets.put(WS_ID, null);

      assertFalse(stale());
   }

   private boolean stale() throws Exception {
      Method method = MVService.class.getDeclaredMethod(
         "isStale", MVDef.class, Map.class, Principal.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(service, def, worksheets, mock(Principal.class));
   }

   private static MVDef def(Worksheet built) {
      MVMetaData meta = new MVMetaData(WS_ID, "ORDERS", "ORDERS");
      meta.setDefinitionKey("ORDERS",
                            MVDefinitionKey.create((TableAssembly) built.getAssembly("ORDERS")));
      MVDef def = spy(new MVDef());
      // the expression check reads the snapshot stored with the MV, which is not under test
      doReturn(false).when(def).isExpressionChanged(any(Worksheet.class));
      ReflectionTestUtils.setField(def, "data", meta);
      ReflectionTestUtils.setField(def, "otname", "ORDERS");
      ReflectionTestUtils.setField(def, "tname", "ORDERS");
      return def;
   }

   private static Worksheet worksheet(String filter) {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "ORDERS");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef("ORDERS", "ORDER_ID")));
      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols, true);

      if(filter != null) {
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(filter);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(
            new ColumnRef(new AttributeRef("ORDERS", "ORDER_ID")), cond, 0));
         table.setPreConditionList(list);
      }

      ws.addAssembly(table);
      return ws;
   }

   private MVService service;
   private Worksheet saved;
   private MVDef def;
   private Map<String, Worksheet> worksheets;
}
