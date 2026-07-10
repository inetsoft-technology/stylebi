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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.WindowExpressionRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression/contract test for {@link WorksheetTableService#applyWindowColumns}, the structured
 * counterpart of {@code applyExpressionColumns}: a {@code windowColumns} entry on the
 * {@code /ws/table} request must build a {@link ColumnRef} wrapping a {@link WindowExpressionRef}
 * whose synthesized {@code getExpression()} matches the pushdown-parity text produced by wiz's
 * {@code expandWindowColumns} helper.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceWindowColumnsTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // applyWindowColumns uses only its parameters, never instance state, so null
      // dependencies are safe here (mirrors WorksheetTableServiceConditionTest).
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   @Test
   void rowNumberWindowColumnBuildsWindowExpressionRef() throws Exception {
      WorksheetTable req = request("""
         {
           "windowColumns": [
             {
               "name": "rn",
               "fn": "ROW_NUMBER",
               "partitionBy": ["stage"],
               "orderBy": [ { "field": "amount", "direction": "DESC" } ]
             }
           ]
         }
         """);

      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(worksheet, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnSelection result = table.getColumnSelection(false);
      DataRef added = result.getAttribute("rn");

      assertNotNull(added, "expected a 'rn' column in the private column selection");
      assertInstanceOf(ColumnRef.class, added);
      ColumnRef colRef = (ColumnRef) added;
      assertTrue(colRef.isSQL(), "window column must be marked SQL so PreAssetQuery inlines it");
      assertInstanceOf(WindowExpressionRef.class, colRef.getDataRef());

      WindowExpressionRef winRef = (WindowExpressionRef) colRef.getDataRef();
      assertEquals(
         "ROW_NUMBER() OVER (PARTITION BY field['stage'] ORDER BY field['amount'] DESC)",
         winRef.getExpression());
      assertTrue(winRef.isSQL());
   }
}
