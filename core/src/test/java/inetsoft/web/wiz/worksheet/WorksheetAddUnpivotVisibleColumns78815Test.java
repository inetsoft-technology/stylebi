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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78815: {@code addUnpivot} bounded {@code headerColumns} by the source's private
 * (hidden-inclusive) selection, while the melt consumes the public one. A source with a hidden
 * column, or an aggregate source whose public selection drops unused columns, therefore accepted
 * a header count that leaves nothing to melt, and quoted the wrong column count in the refusal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  WorksheetAddUnpivotVisibleColumns78815Test.CacheConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetAddUnpivotVisibleColumns78815Test {
   @Configuration
   static class CacheConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;

   @Test
   void hiddenColumnSourceRefusesHeaderCountEqualToVisibleCount() throws Exception {
      hiddenSource();

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.addUnpivot("UP", "S", 4)));
      assertTrue(ex.getMessage().contains("must be between 0 and 3 (table has 4 columns)"),
                 ex.getMessage());
      assertNull(ws.getAssembly("UP"));
   }

   @Test
   void hiddenColumnSourceAcceptsHeaderCountBelowVisibleCount() throws Exception {
      hiddenSource();

      apply(ed -> ed.addUnpivot("UP", "S", 3));

      assertEquals(3, ((UnpivotTableAssembly) ws.getAssembly("UP")).getHeaderColumns());
   }

   @Test
   void aggregateSourceBoundsHeaderCountByItsPublicColumns() throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly s = TestWorksheets.tableWithColumns(ws, "S", "v", "g", "w");
      s.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "integer", "string", "integer" },
         new Object[][]{ { "v", "g", "w" }, { 1, "a", 10 }, { 2, "b", 20 } }));
      ColumnSelection cs = s.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("v")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("g")).setDataType(XSchema.STRING);
      ((ColumnRef) cs.getAttribute("w")).setDataType(XSchema.INTEGER);
      ws.addAssembly(s);
      svc = WorksheetUnpivotDownstream78790Test.service(ws);
      apply(ed -> ed.addMirror("A", "S"));
      apply(ed -> ed.setGroupAggregate(
         "A", List.of(new GroupSpec("g")), List.of(new AggregateSpec("v", "SUM", null)),
         false, false));

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.addUnpivot("UP", "A", 2)));
      assertTrue(ex.getMessage().contains("must be between 0 and 1 (table has 2 columns)"),
                 ex.getMessage());

      apply(ed -> ed.addUnpivot("UP", "A", 1));
      assertEquals(1, ((UnpivotTableAssembly) ws.getAssembly("UP")).getHeaderColumns());
   }

   /** S(name hidden, score, q1, q2, q3): private 5 columns, public 4. */
   private void hiddenSource() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(WorksheetUnpivotDownstream78790Test.source(ws, "S"));
      svc = WorksheetUnpivotDownstream78790Test.service(ws);
      apply(ed -> ed.setColumnVisibility("S", "name", false));
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      svc.apply("TOK", agent, m);
   }
}
