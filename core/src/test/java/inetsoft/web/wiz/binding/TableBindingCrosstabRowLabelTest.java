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
package inetsoft.web.wiz.binding;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.ClearTableHeaderAliasHandler;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78530 (item 3): a crosstab row label is a positional {@code MESSAGE_FORMAT} override on
 * {@code Cell [0,i]}, not binding state. A wiz write that took a row dimension off rows left its
 * label on the vacated path: the corner kept showing it, and the next field there inherited it.
 *
 * <p>{@code setBinding} is stood in for by {@link Crosstab#setBinding}, which does what
 * {@code VSBindingModelService.setBinding} + {@code VSAssemblyInfoHandler.apply} do to the format
 * map -- clone the live assembly (after the wiz pre-clear), apply the new headers, and run the
 * real {@code VSUtil.syncCrosstabPath}, with {@code processClearAliasFormat} exactly when the event
 * asks for it -- so these assert the rendered corner state end to end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingCrosstabRowLabelTest {
   /** The report's repro: label Region, then set_table_fields(rows, []). */
   @Test
   void emptyingRowsClearsTheCornerLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region"), cols("Category"));
      ct.label(0, "Region W");
      assertEquals("Region W", ct.label(0), "fixture: the label is written where it is read");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows", List.of(), null);

      assertNull(ct.label(0));
      assertTrue(ct.event.isClearVacatedAliases());
   }

   /** Case A: remove the first row; the survivor keeps its label and the shift leaves nothing. */
   @Test
   void removingARowKeepsTheSurvivorsLabelAndLeavesNoShiftResidue() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().removeField("tok", principal(), "Crosstab1", "rows", "Region");

      assertEquals("SL", ct.label(0));
      assertNull(ct.label(1));

      ct.service().addField("tok", principal(), "Crosstab1", "rows", dim("City"), 1, null);

      assertEquals("SL", ct.label(0));
      assertNull(ct.label(1), "City must not inherit the vacated State slot");
   }

   /** One-call replace: buildMap's runtime-ID fallback used to hand Region's label to State. */
   @Test
   void replacingTheOnlyRowInOneCallDropsItsLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region"), cols("Category"));
      ct.label(0, "Region W");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("State")), null);

      assertNull(ct.label(0), "State must not inherit Region's label");
   }

   @Test
   void replacingOneOfTwoRowsKeepsTheOtherLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows",
                            List.of(dim("Region"), dim("City")), null);

      assertEquals("RL", ct.label(0));
      assertNull(ct.label(1), "City must not inherit State's label");
   }

   /** Case B: rows -> cols is syncCornerPath's no-branch case, which the processor never sees. */
   @Test
   void movingTheOnlyRowToColsClearsItsLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region"), cols("Category"));
      ct.label(0, "RW");

      ct.service().moveField("tok", principal(), "Crosstab1", "rows", "cols", "Region", null);

      assertNull(ct.label(0));

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("State")), null);

      assertNull(ct.label(0), "the next rows[0] must not inherit RW");
   }

   @Test
   void movingOneOfTwoRowsToColsKeepsTheOtherLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().moveField("tok", principal(), "Crosstab1", "rows", "cols", "Region", null);

      assertEquals("SL", ct.label(0));
      assertNull(ct.label(1));
   }

   @Test
   void movingARowToAggregatesClearsItsLabel() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().moveField("tok", principal(), "Crosstab1", "rows", "aggregates", "Region",
                             null);

      assertEquals("SL", ct.label(0));
      assertNull(ct.label(1));
   }

   /** [A2]: a date level changed in place is the same field; it keeps its label, as before. */
   @Test
   void changingADateLevelInPlaceKeepsTheLabel() throws Exception {
      Crosstab ct = new Crosstab(new DataRef[] { dateDim("Order Date", "5") }, cols("Category"));
      ct.label(0, "Order Year");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows",
                            List.of(new FieldRef("Order Date", "dimension", null, "Quarter", null)),
                            null);

      assertEquals("Order Year", ct.label(0));
   }

   /**
    * [R4]: "leaving" is decided on design values. A $(var) date level has a runtime full name
    * that differs from its design value; an untouched row must not be pre-cleared because of it.
    */
   @Test
   void aRowWithAVariableDateLevelThatStaysKeepsItsLabel() throws Exception {
      Crosstab ct = new Crosstab(new DataRef[] { dateDim("Order Date", "$(lvl)"), dim0("Region") },
                                 cols("Category"));
      ct.label(0, "Period");
      ct.label(1, "RL");

      ct.service().removeField("tok", principal(), "Crosstab1", "cols", "Category");

      assertEquals("Period", ct.label(0));
      assertEquals("RL", ct.label(1));
   }

   /** Reordering rows keeps each label with its own field (syncCornerPath re-keys by name). */
   @Test
   void reorderingRowsMovesEachLabelWithItsField() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows",
                            List.of(dim("State"), dim("Region")), null);

      assertEquals("SL", ct.label(0));
      assertEquals("RL", ct.label(1));
   }

   /** Control: a write that leaves rows alone leaves every row label alone. */
   @Test
   void aColsOnlyWriteLeavesRowLabelsIntact() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().setShelf("tok", principal(), "Crosstab1", "cols",
                            List.of(dim("Category"), dim("Segment")), null);

      assertEquals("RL", ct.label(0));
      assertEquals("SL", ct.label(1));
   }

   /**
    * [R1]: with a variable-expanded dimension the design and runtime rows differ in length, so the
    * design index is not the path index; nothing is pre-cleared and only the processor applies.
    */
   @Test
   void skipsThePreClearWhenDesignAndRuntimeRowsDiffer() throws Exception {
      Crosstab ct = new Crosstab(rows("Region"), cols("Category"));
      ct.info().setRuntimeRowHeaders(new DataRef[] { dim0("Region"), dim0("State") });
      ct.label(0, "RL");
      ct.label(1, "SL");
      ct.syncOnBinding = false;

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("City")), null);

      assertEquals("RL", ct.labelAtBinding.get(0), "no pre-clear before setBinding");
      assertEquals("SL", ct.labelAtBinding.get(1));
      assertTrue(ct.event.isClearVacatedAliases());
   }

   /**
    * [R1]: a period calendar on the crosstab's table does not shift the pre-clear index. Native
    * addDataRef's replace adds 1 there, but the wiz label writer, syncCornerPath and buildMap all
    * key Cell [0,i] by the unshifted runtime row index; a +1 here would clear the survivor's label
    * and leave the replaced row's label for the newcomer.
    */
   @Test
   void aPeriodCalendarDoesNotShiftThePreClearIndex() throws Exception {
      Crosstab ct = new Crosstab(rows("Region", "State"), cols("Category"));
      ct.live.setSourceInfo(new inetsoft.uql.asset.SourceInfo(
         inetsoft.uql.asset.SourceInfo.ASSET, null, "Orders"));
      Viewsheet vs = ct.live.getViewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.setTableName("Orders");
      calendar.setDataRef(new ColumnRef(new AttributeRef(null, "Order Date")));
      calendar.setPeriod(true);
      calendar.setDates(new String[] { "y2024" });
      vs.addAssembly(calendar);
      assertNotNull(VSUtil.getPeriodCalendar(vs, ct.live.getTableName()),
                    "fixture: a period calendar on the crosstab's table");
      ct.label(0, "RL");
      ct.label(1, "SL");

      ct.service().setShelf("tok", principal(), "Crosstab1", "rows",
                            List.of(dim("City"), dim("State")), null);

      assertNull(ct.label(0), "City must not inherit Region's label");
      assertEquals("SL", ct.label(1));
   }

   // ── fixture ──────────────────────────────────────────────────────────────

   /** A live crosstab, its session plumbing, and a setBinding that syncs the format map. */
   private static final class Crosstab {
      Crosstab(DataRef[] rows, DataRef[] cols) {
         Viewsheet vs = new Viewsheet();
         live = new CrosstabVSAssembly(vs, "Crosstab1");
         VSCrosstabInfo info = new VSCrosstabInfo();
         setHeaders(info, rows, cols);
         live.setVSCrosstabInfo(info);
         live.setFormatInfo(new FormatInfo());
      }

      VSCrosstabInfo info() {
         return live.getVSCrosstabInfo();
      }

      /** Writes a row label the way applyCrosstabLabels does, HEADER/GROUP_HEADER duality too. */
      void label(int index, String label) {
         SetTableHeaderAliasHandler.setAliasWithHeaderDuality(cornerPath(index),
                                                              live.getFormatInfo(), label);
      }

      /** The rendered (HEADER) label at {@code Cell [0,index]}, or null. */
      String label(int index) {
         return labelOf(live.getFormatInfo(), index);
      }

      TableBindingService service() throws Exception {
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         Viewsheet vs = mock(Viewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(vs.getAssembly(anyString())).thenAnswer(invocation -> live);

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         VSBindingService binding = mock(VSBindingService.class);
         when(binding.createModel(any())).thenAnswer(invocation -> modelOf(live));

         VSBindingModelService bindings = mock(VSBindingModelService.class);
         doAnswer(invocation -> {
            setBinding(invocation.getArgument(1));
            return null;
         }).when(bindings).setBinding(anyString(), any(), any(), any());

         return new TableBindingService(sessions, binding, bindings,
                                        mock(DataRefModelFactoryService.class),
                                        mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class),
                                        mock(inetsoft.web.binding.handler.VSAssemblyInfoHandler.class));
      }

      /** What VSBindingModelService.setBinding + VSAssemblyInfoHandler.apply do to the formats. */
      private void setBinding(ApplyVSAssemblyInfoEvent event) {
         this.event = event;
         labelAtBinding = new HashMap<>();

         for(int i = 0; i < 4; i++) {
            labelAtBinding.put(i, label(i));
         }

         if(!syncOnBinding) {
            return;
         }

         CrosstabBindingModel model = (CrosstabBindingModel) event.getBinding();
         CrosstabVSAssemblyInfo oinfo = (CrosstabVSAssemblyInfo) live.getInfo().clone();
         CrosstabVSAssembly next = (CrosstabVSAssembly) live.clone();
         VSCrosstabInfo cinfo = new VSCrosstabInfo();
         setHeaders(cinfo, refsOf(model.getRows()), refsOf(model.getCols()));
         next.setVSCrosstabInfo(cinfo);
         VSUtil.syncCrosstabPath(next, oinfo, false, next.getFormatInfo().getFormatMap(),
                                 event.isClearVacatedAliases()
                                    ? ClearTableHeaderAliasHandler::processClearAliasFormat : null);
         live = next;
      }

      CrosstabVSAssembly live;
      ApplyVSAssemblyInfoEvent event;
      Map<Integer, String> labelAtBinding;
      boolean syncOnBinding = true;
   }

   private static void setHeaders(VSCrosstabInfo info, DataRef[] rows, DataRef[] cols) {
      info.setDesignRowHeaders(rows);
      info.setDesignColHeaders(cols);
      info.setRuntimeRowHeaders(cloneAll(rows));
      info.setRuntimeColHeaders(cloneAll(cols));
   }

   private static DataRef[] cloneAll(DataRef[] refs) {
      DataRef[] out = new DataRef[refs.length];

      for(int i = 0; i < refs.length; i++) {
         out[i] = (DataRef) refs[i].clone();
      }

      return out;
   }

   /** The model createModel reads off the live crosstab, as VSDimensionRefModelFactory builds it. */
   private static CrosstabBindingModel modelOf(CrosstabVSAssembly crosstab) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setSource(BindingSources.assetSource("Orders"));
      model.setRows(modelsOf(crosstab.getVSCrosstabInfo().getRowHeaders()));
      model.setCols(modelsOf(crosstab.getVSCrosstabInfo().getColHeaders()));
      return model;
   }

   private static List<BDimensionRefModel> modelsOf(DataRef[] refs) {
      List<BDimensionRefModel> out = new ArrayList<>();

      for(DataRef ref : refs) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         BDimensionRefModel model = new BDimensionRefModel(dim);
         model.setColumnValue(dim.getGroupColumnValue());
         model.setName(dim.getGroupColumnValue());
         out.add(model);
      }

      return out;
   }

   private static DataRef[] refsOf(List<BDimensionRefModel> models) {
      List<DataRef> out = new ArrayList<>();

      for(BDimensionRefModel model : models == null ? List.<BDimensionRefModel>of() : models) {
         String level = model.getDateLevel();
         boolean none = level == null || level.isBlank() || "-1".equals(level);
         out.add(none ? dim0(model.getColumnValue())
                    : dateDim(model.getColumnValue(), level));
      }

      return out.toArray(new DataRef[0]);
   }

   private static DataRef[] rows(String... columns) {
      return Arrays.stream(columns).map(TableBindingCrosstabRowLabelTest::dim0)
         .toArray(DataRef[]::new);
   }

   private static DataRef[] cols(String... columns) {
      return rows(columns);
   }

   private static VSDimensionRef dim0(String column) {
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, column)));
      dim.setGroupColumnValue(column);
      return dim;
   }

   private static VSDimensionRef dateDim(String column, String level) {
      AttributeRef attr = new AttributeRef(null, column);
      attr.setDataType(XSchema.DATE);
      ColumnRef col = new ColumnRef(attr);
      col.setDataType(XSchema.DATE);
      VSDimensionRef dim = new VSDimensionRef(col);
      dim.setGroupColumnValue(column);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level);
      return dim;
   }

   private static TableDataPath cornerPath(int index) {
      return new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
                               new String[] { "Cell [0," + index + "]" });
   }

   private static String labelOf(FormatInfo formatInfo, int index) {
      VSCompositeFormat format = formatInfo.getFormatMap().get(cornerPath(index));
      VSFormat ufmt = format == null ? null : format.getUserDefinedFormat();

      return ufmt != null && VSFormat.MESSAGE_FORMAT.equals(ufmt.getFormatValue())
         ? ufmt.getFormatExtentValue() : null;
   }

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
