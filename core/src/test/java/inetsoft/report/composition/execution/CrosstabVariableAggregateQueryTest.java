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

import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78087 through the viewsheet sandbox: a crosstab whose aggregate column is the
 * variable {@code $(ComboBox1)}, with ComboBox values {@code Sum(col)} / {@code Average(col)}
 * and the design formula left at Sum, must run its query (AbstractCrosstabVSAQuery) without
 * "Column not found", show the selected formula, and follow a change of the ComboBox.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class CrosstabVariableAggregateQueryTest {
   @Test
   void sumDesignFormulaFollowsComboBoxFormula() throws Exception {
      checkSwitching("Sum");
   }

   @Test
   void noneDesignFormulaStillFollowsComboBoxFormula() throws Exception {
      // the reporter's workaround
      checkSwitching("None");
   }

   private void checkSwitching(String designFormula) throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();

      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, COMBO);
      ListData data = new ListData();
      data.setValues(new Object[] { SUM, AVG });
      data.setLabels(new String[] { SUM, AVG });
      combo.setListData(data);
      combo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      vs.addAssembly(combo);

      crosstab(vs, "XVar", source, "$(" + COMBO + ")", designFormula);
      crosstab(vs, "XSum", source, "wind", "Sum");
      crosstab(vs, "XAvg", source, "wind", "Average");

      String sum = body(box.getData("XSum"));
      String avg = body(box.getData("XAvg"));
      assertNotEquals(sum, avg, "fixture must tell Sum from Average");

      for(String value : new String[] { SUM, AVG, SUM }) {
         select(box, combo, value);
         assertEquals(SUM.equals(value) ? sum : avg, body(box.getData("XVar")),
                      "crosstab with ComboBox value " + value);
      }
   }

   private static void select(ViewsheetSandbox box, ComboBoxVSAssembly combo, String value)
      throws Exception
   {
      combo.setSelectedObject(value);
      box.processChange(COMBO, VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED,
                        new ChangedAssemblyList());
      box.resetDataMap("XVar");
   }

   private static void crosstab(Viewsheet vs, String name, SourceInfo source, String column,
                                String formula)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
   }

   /**
    * The crosstab cells below the header row (the header names the aggregate differently).
    */
   private static String body(Object data) {
      TableLens lens = (TableLens) data;
      assertNotNull(lens, "no data");
      StringBuilder sb = new StringBuilder();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String COMBO = "ComboBox1";
   private static final String SUM = "Sum(wind)";
   private static final String AVG = "Average(wind)";
}
