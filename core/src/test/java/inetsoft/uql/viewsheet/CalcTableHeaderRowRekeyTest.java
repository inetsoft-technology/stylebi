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
package inetsoft.uql.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.report.TableLayout;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.composer.model.vs.CalcTablePropertyDialogModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.dialog.CalcTablePropertyDialogService;
import inetsoft.web.viewsheet.service.VSDialogService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79264: changing a calc table's header/trailer row count must re-key the per-cell format,
 * hyperlink and highlight (keyed by TableDataPath type) of every row whose type changed, in both
 * directions. The old type must come from the OLD header and trailer counts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalcTableHeaderRowRekeyTest {
   private static final int H = TableDataPath.HEADER;
   private static final int D = TableDataPath.DETAIL;
   private static final int T = TableDataPath.TRAILER;

   @Test
   void headerRaiseRekeysDetailToHeader() throws Exception {
      assertRekeyed(run(4, 1, 0, 2, 0, 1, D), 1, H);
   }

   @Test
   void headerLowerRekeysHeaderToDetail() throws Exception {
      assertRekeyed(run(4, 2, 0, 1, 0, 1, H), 1, D);
   }

   @Test
   void headerRaiseOverTwoRows() throws Exception {
      assertRekeyed(run(5, 1, 0, 3, 0, 2, D), 2, H);
   }

   @Test
   void trailerRaiseStillRekeysDetailToTrailer() throws Exception {
      assertRekeyed(run(4, 1, 0, 1, 1, 3, D), 3, T);
   }

   @Test
   void trailerLowerStillRekeysTrailerToDetail() throws Exception {
      assertRekeyed(run(4, 1, 1, 1, 0, 3, T), 3, D);
   }

   @Test
   void headerRaiseAndTrailerRaiseTogether() throws Exception {
      assertRekeyed(run(4, 1, 0, 2, 1, 3, D), 3, T);
   }

   @Test
   void unchangedCountsLeaveEntriesAlone() throws Exception {
      CalcTableVSAssemblyInfo out = run(4, 1, 0, 1, 0, 1, D);

      assertNotNull(out.getFormatInfo().getFormat(path(out, 1, D)));
      assertNull(out.getFormatInfo().getFormat(path(out, 1, H)));
   }

   private static void assertRekeyed(CalcTableVSAssemblyInfo out, int row, int newType) {
      TableDataPath path = path(out, row, newType);

      assertNotNull(out.getFormatInfo().getFormat(path), "format");
      assertNotNull(out.getHyperlinkAttr().getHyperlink(path), "hyperlink");
      assertNotNull(out.getHighlightAttr().getHighlight(path), "highlight");
   }

   private static TableDataPath path(CalcTableVSAssemblyInfo info, int row, int type) {
      return new TableDataPath(-1, type, XSchema.STRING, new String[] { "Cell [" + row + ",0]" });
   }

   /**
    * Build a calc table with the old counts and a format, hyperlink and highlight at
    * Cell [row,0] keyed by oldType, apply the new counts through the dialog service, and return
    * the info passed to editObjectProperty.
    */
   private static CalcTableVSAssemblyInfo run(int rows, int oldHeader, int oldTrailer,
                                              int newHeader, int newTrailer, int row,
                                              int oldType)
      throws Exception
   {
      CalcTableVSAssemblyInfo info = new CalcTableVSAssemblyInfo();
      info.setName("CalcTable1");
      TableLayout layout = VSLayoutTool.createLayout(new CalcTableLens(rows, 2));
      layout.setMode(TableLayout.CALC);
      info.setTableLayout(layout);
      info.setHeaderRowCount(oldHeader);
      info.setTrailerRowCount(oldTrailer);
      info.setHyperlinkAttr(new TableHyperlinkAttr());
      info.setHighlightAttr(new TableHighlightAttr());

      TableDataPath old = path(info, row, oldType);
      info.getFormatInfo().setFormat(old, new VSCompositeFormat());
      info.getHyperlinkAttr().setHyperlink(old, new Hyperlink("http://example.com"));

      Condition cond = new Condition();
      cond.addValue("x");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("c"), cond, 0));
      TextHighlight rule = new TextHighlight();
      rule.setName("h");
      rule.setConditionGroup(conds);
      rule.setForeground(Color.RED);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("h", rule);
      info.getHighlightAttr().setHighlight(old, group);

      CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("CalcTable1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService service = mock(ViewsheetService.class);
      when(service.getViewsheet(eq("rid"), any())).thenReturn(rvs);
      VSObjectPropertyService props = mock(VSObjectPropertyService.class);

      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      model.getTableViewGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("CalcTable1");
      model.setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel.builder()
                                            .scriptEnabled(false).expression("").build());
      model.getCalcTableAdvancedPaneModel().setHeaderRowCount(newHeader);
      model.getCalcTableAdvancedPaneModel().setTrailerRowCount(newTrailer);

      new CalcTablePropertyDialogService(props, mock(VSDialogService.class), service)
         .setCalcTablePropertyModel("rid", "CalcTable1", model, null, mock(Principal.class),
                                    null);

      ArgumentCaptor<CalcTableVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(CalcTableVSAssemblyInfo.class);
      verify(props).editObjectProperty(any(), captor.capture(), any(), any(), any(), any(),
                                       any(), anyBoolean(), any());
      return captor.getValue();
   }
}
