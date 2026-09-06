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
package inetsoft.report.composition.execution;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VBS-009: settles, against a real join-execution pipeline (not a hand-set {@code ColumnRef}
 * flag), whether a duplicate non-key column surviving a join actually ends up with
 * {@code aalias == false} -- the mechanism the original diagnosis proposed and the refuter found
 * no supporting code path for. This mirrors production exactly: {@code RelationalJoinTableAssembly}
 * built the same way {@link inetsoft.web.wiz.worksheet.WorksheetEditService.Editor#addJoin} builds
 * it, then {@link AssetQuerySandbox#refreshColumnSelection} run the same way
 * {@code WorksheetEventUtil.refreshColumnSelection} runs it from
 * {@code WorksheetEditService#refreshAssemblies} after every agent write (with the sandbox set
 * active, matching {@code RuntimeWorksheet}'s own {@code box.setActive(true)}, so this exercises
 * the real query-execution branch of {@code refreshColumnSelection}, not its inactive/metadata
 * shortcut).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class JoinDuplicateColumnAliasMechanismTest {

   @Test
   void innerJoinOfTwoTablesSharingANonKeyColumnProducesAnAliasWithApplyingAliasTrue() throws Exception {
      Worksheet ws = new Worksheet();

      EmbeddedTableAssembly left = new EmbeddedTableAssembly(ws, "REGIONS_LEFT");
      ColumnSelection leftCs = new ColumnSelection();
      leftCs.addAttribute(new ColumnRef(new AttributeRef("ID")));
      leftCs.addAttribute(new ColumnRef(new AttributeRef("REGION")));
      left.setColumnSelection(leftCs, false);
      left.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "REGION" },
            { "1", "West" },
            { "2", "East" },
         }));
      ws.addAssembly(left);

      EmbeddedTableAssembly right = new EmbeddedTableAssembly(ws, "REGIONS_RIGHT");
      ColumnSelection rightCs = new ColumnSelection();
      rightCs.addAttribute(new ColumnRef(new AttributeRef("ID")));
      rightCs.addAttribute(new ColumnRef(new AttributeRef("REGION")));
      right.setColumnSelection(rightCs, false);
      right.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "REGION" },
            { "1", "Western" },
            { "2", "Eastern" },
         }));
      ws.addAssembly(right);

      // Mirrors WorksheetEditService.Editor#addJoin's own construction of the operator/assembly,
      // including using a FULL join (the original bug report's own join type).
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("REGIONS_LEFT");
      op.setRightTable("REGIONS_RIGHT");
      op.setLeftAttribute(new AttributeRef(null, "ID"));
      op.setRightAttribute(new AttributeRef(null, "ID"));
      op.setOperation(TableAssemblyOperator.FULL_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      RelationalJoinTableAssembly joined = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ left, right }, new TableAssemblyOperator[]{ top });
      ws.addAssembly(joined);

      // Mirrors RuntimeWorksheet's own AssetQuerySandbox construction (active=true), and
      // WorksheetEventUtil.refreshColumnSelection -> AssetQuerySandbox#refreshColumnSelection,
      // which is what WorksheetEditService#refreshAssemblies runs after every agent write op.
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      box.refreshColumnSelection("JOINED", false);

      ColumnSelection result = joined.getColumnSelection(true);
      ColumnRef duplicate = null;

      for(int i = 0; i < result.getAttributeCount(); i++) {
         DataRef ref = result.getAttribute(i);

         if(ref instanceof ColumnRef cr && cr.getAlias() != null && cr.getAlias().startsWith("REGION_")) {
            duplicate = cr;
         }
      }

      assertNotNull(duplicate,
         "the real join-execution pipeline must alias one side's duplicate REGION column, " +
         "matching the bug report's observed \"REGION_1\"-style name");
      assertTrue(duplicate.isApplyingAlias(),
         "AssetUtil.fixAlias (the code path that actually produces this alias) never touches " +
         "the aalias flag, so it must still be true here -- this disproves the original " +
         "diagnosis's specific \"FULL join duplicate columns end up with aalias == false\" claim");
      assertNotNull(result.getAttribute(duplicate.getAlias()),
         "since aalias is true, ColumnRef.getName() returns the alias, so a bare " +
         "ColumnSelection.getAttribute(alias) call -- the exact lookup removeColumn/renameColumn/" +
         "setColumnVisibility perform -- must already resolve this column. remove_column's real " +
         "server-side failure on VBS-009's actual repro is therefore NOT explained by the " +
         "aalias mechanism; it must come from something else (e.g. the caller/model posting a " +
         "different or stale identifier than what fixAlias actually assigned, or a bug elsewhere " +
         "in this pipeline this test does not cover).");
   }
}
