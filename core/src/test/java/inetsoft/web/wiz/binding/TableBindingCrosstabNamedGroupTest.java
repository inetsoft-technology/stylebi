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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.internal.binding.SimpleNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.asset.NamedGroupInfo;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSCrosstabBindingFactory;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78362: an add/remove/move (and a non-shelf write) on a crosstab must leave an untouched
 * dimension's expert named group exactly as stored, and must not hand it to another dimension.
 *
 * <p>Drives the real {@link TableBindingService} and {@link TableBindingMutator}, then the real
 * {@link VSCrosstabBindingFactory#updateAssembly} onto a live crosstab whose design headers were
 * built the way the factory builds them, so what is asserted is the STORED group (operator, sort
 * order, owning column), not a hand-built model. Every write re-reads the live crosstab through the
 * real {@code createModel}, so a chain of writes behaves like the production session.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingCrosstabNamedGroupTest {
   private static final int GROUPED_ORDER = XConstants.SORT_SPECIFIC | XConstants.SORT_ASC;

   private MockedStatic<AssetUtil> assetUtil;

   @BeforeEach
   void noRepository() {
      // a real repository has no group called "Custom" either; everything else stays real
      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(null);
   }

   @AfterEach
   void release() {
      assetUtil.close();
   }

   // ── add / remove / move ───────────────────────────────────────────────────

   @Test
   void appendKeepsTheOperatorAndSortOfAnUntouchedExpertGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), null, null);

      VSDimensionRef[] rows = f.rows();
      assertEquals(2, rows.length);
      assertStoredWest(rows[0], "REGION");
      assertNull(rows[1].getNamedGroupInfo());
   }

   @Test
   void insertAtZeroDoesNotHandTheGroupToTheNewDimension() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), 0, null);

      VSDimensionRef[] rows = f.rows();
      assertEquals("YEAR", rows[0].getGroupColumnValue());
      assertNull(rows[0].getNamedGroupInfo(), "the fresh dimension must not inherit a neighbour's group");
      assertOwnDataRefOrNone(rows[0], "YEAR");
      assertStoredWest(rows[1], "REGION");
   }

   @Test
   void removingAnEarlierDimensionKeepsTheGroupOnItsOwnColumn() throws Exception {
      Fixture f = new Fixture(List.of(plain("YEAR"), expertRegion()), List.of(), null);

      f.service.removeField("tok", principal(), "Crosstab1", "rows", "YEAR");

      VSDimensionRef[] rows = f.rows();
      assertEquals(1, rows.length);
      assertStoredWest(rows[0], "REGION");
   }

   @Test
   void removingTheGroupedDimensionLeavesNoGroupBehind() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.removeField("tok", principal(), "Crosstab1", "rows", "REGION");

      VSDimensionRef[] rows = f.rows();
      assertEquals(1, rows.length);
      assertNull(rows[0].getNamedGroupInfo());
   }

   @Test
   void movingASiblingBetweenShelvesKeepsTheGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.moveField("tok", principal(), "Crosstab1", "rows", "cols", "YEAR", null);

      assertStoredWest(f.rows()[0], "REGION");
      assertEquals(1, f.cols().length);
      assertNull(f.cols()[0].getNamedGroupInfo());
   }

   @Test
   void movingTheGroupedDimensionCarriesItsOwnGroupToTheTargetShelf() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(plain("STATE")), null);

      f.service.moveField("tok", principal(), "Crosstab1", "rows", "cols", "REGION", 0);

      assertEquals(1, f.rows().length);
      assertNull(f.rows()[0].getNamedGroupInfo(), "YEAR must not inherit REGION's group");
      VSDimensionRef[] cols = f.cols();
      assertEquals(2, cols.length);
      assertStoredWest(cols[0], "REGION");
      assertEquals("STATE", cols[1].getGroupColumnValue());
      assertNull(cols[1].getNamedGroupInfo());
   }

   @Test
   void movingTheGroupedDimensionBackAndForthKeepsTheStoredGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.moveField("tok", principal(), "Crosstab1", "rows", "cols", "REGION", null);
      f.service.moveField("tok", principal(), "Crosstab1", "cols", "rows", "REGION", null);

      assertStoredWest(f.rows()[0], "REGION");
      assertEquals(0, f.cols().length);
   }

   @Test
   void movingTheGroupedDimensionOntoAggregatesLegitimatelyDropsTheGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.moveField("tok", principal(), "Crosstab1", "rows", "aggregates", "REGION", null);

      assertEquals(1, f.rows().length);
      assertNull(f.rows()[0].getNamedGroupInfo());
   }

   // ── writes that never touch the shelf ─────────────────────────────────────

   @Test
   void aNonShelfWriteDoesNotFlattenTheOperator() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.setOptions("tok", principal(), "Crosstab1", Map.of("rowTotals", false));

      assertStoredWest(f.rows()[0], "REGION");
   }

   @Test
   void aSortOnAnotherDimensionDoesNotFlattenTheGroupedOne() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.setSort("tok", principal(), "Crosstab1", "rows", "YEAR", null,
                        new DimensionSortRanking.Sort("desc", null, null));

      assertStoredWest(f.rows()[0], "REGION");
      assertEquals(XConstants.SORT_DESC, f.rows()[1].getOrder());
   }

   @Test
   void aNonStringConditionRoundTripsUnchanged() throws Exception {
      VSDimensionRef amount = plain("AMOUNT");
      ConditionList list = new ConditionList();
      Condition condition = new Condition(XSchema.INTEGER);
      condition.setOperation(XCondition.GREATER_THAN);
      condition.addValue(100);
      list.append(new ConditionItem(new AttributeRef(null, "AMOUNT"), condition, 0));
      ExpertNamedGroupInfo expert = new ExpertNamedGroupInfo();
      expert.setGroupCondition("Big", list);
      amount.setNamedGroupInfo(expert);
      amount.setOrder(GROUPED_ORDER);
      Fixture f = new Fixture(List.of(amount), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), 0, null);

      ExpertNamedGroupInfo stored = (ExpertNamedGroupInfo) f.rows()[1].getNamedGroupInfo();
      Condition stored0 = (Condition) stored.getGroupCondition("Big").getCondition(0);
      assertEquals(XCondition.GREATER_THAN, stored0.getOperation());
      assertEquals(100, ((Number) stored0.getValues().get(0)).intValue());
      assertEquals(GROUPED_ORDER, f.rows()[1].getOrder());
   }

   // ── same column bound twice ───────────────────────────────────────────────

   @Test
   void aGroupOnlyOnTheQuarterOccurrenceStaysThereWhenAFieldIsInsertedAtZero() throws Exception {
      VSDimensionRef year = plain("ORDER_DATE");
      year.setDateLevelValue("1");
      VSDimensionRef quarter = plain("ORDER_DATE");
      quarter.setDateLevelValue("2");
      quarter.setNamedGroupInfo(westGroup("ORDER_DATE"));
      quarter.setOrder(GROUPED_ORDER);
      Fixture f = new Fixture(List.of(year, quarter), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("REGION"), 0, null);

      VSDimensionRef[] rows = f.rows();
      assertEquals(3, rows.length);
      assertNull(rows[0].getNamedGroupInfo());
      assertNull(rows[1].getNamedGroupInfo(), "the Year occurrence must not gain the group");
      assertEquals("2", rows[2].getDateLevelValue());
      assertEquals(XCondition.STARTING_WITH, westOp(rows[2]));
   }

   // ── set_table_fields (explicit replace, echo inference) ───────────────────

   @Test
   void echoingTheReadBackPlaceholderKeepsTheStoredGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(dim("YEAR"), named("REGION", "Custom")), null);

      VSDimensionRef[] rows = f.rows();
      assertNull(rows[0].getNamedGroupInfo());
      assertStoredWest(rows[1], "REGION");
   }

   @Test
   void aRealGroupNamedCustomIsAppliedNotMistakenForAnEcho() throws Exception {
      Worksheet ws = worksheetWith(namedGroup("Custom", "REGION", XCondition.EQUAL_TO, "CA"));
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), ws);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(named("REGION", "Custom")), null);

      ExpertNamedGroupInfo stored = (ExpertNamedGroupInfo) f.rows()[0].getNamedGroupInfo();
      assertNotNull(stored.getGroupCondition("West"), "the group keeps its own member name");
      Condition condition = (Condition) stored.getGroupCondition("West").getCondition(0);
      assertEquals(XCondition.EQUAL_TO, condition.getOperation(),
                   "the real worksheet group named Custom must win over the old stored one");
      assertEquals("CA", condition.getValues().get(0));
   }

   @Test
   void aDuplicateSameColumnEntryAheadOfTheEchoDoesNotConsumeThePreviousGroup()
      throws Exception
   {
      Worksheet ws = worksheetWith(namedGroup("Coastal", "REGION", XCondition.EQUAL_TO, "CA"));
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), ws);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(named("REGION", "Coastal"), named("REGION", "Custom")), null);

      VSDimensionRef[] rows = f.rows();
      assertEquals(2, rows.length);
      assertEquals(XCondition.EQUAL_TO,
                   ((ExpertNamedGroupInfo) rows[0].getNamedGroupInfo())
                      .getGroupCondition("West").getCondition(0).getOperation());
      assertStoredWest(rows[1], "REGION");
   }

   @Test
   void anUnknownNamedGroupStillFailsLoudly() {
      Fixture f = new Fixture(List.of(plain("REGION")), List.of(), null);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                                  List.of(named("REGION", "NoSuchGroup")), null));

      assertTrue(thrown.getMessage().contains("NoSuchGroup"));
   }

   // ── worksheet-local group written by the plugin, then edited ──────────────

   @Test
   void aWorksheetLocalGroupWrittenThroughTheToolSurvivesLaterAdds() throws Exception {
      Worksheet ws = worksheetWith(namedGroup("Coastal", "REGION", XCondition.STARTING_WITH, "W"));
      Fixture f = new Fixture(List.of(plain("REGION")), List.of(), ws);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(named("REGION", "Coastal")), null);
      assertEquals(XCondition.STARTING_WITH, westOp(f.rows()[0]), "sanity: stored as written");

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), 0, null);

      VSDimensionRef[] rows = f.rows();
      assertNull(rows[0].getNamedGroupInfo());
      assertEquals(XCondition.STARTING_WITH, westOp(rows[1]));
      assertTrue((rows[1].getOrder() & XConstants.SORT_SPECIFIC) != 0);
      assertEquals("REGION", rows[1].getDataRef().getName());
   }

   // ── SIMPLE groups share the positional defect ─────────────────────────────

   @Test
   void insertAtZeroBeforeASimpleGroupDoesNotHandItToTheNewDimension() throws Exception {
      VSDimensionRef region = plain("REGION");
      SimpleNamedGroupInfo simple = new SimpleNamedGroupInfo();
      simple.setGroupValue("bu", List.of("Business", "Hardware"));
      region.setNamedGroupInfo(simple);
      region.setOrder(GROUPED_ORDER);
      Fixture f = new Fixture(List.of(region), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), 0, null);

      VSDimensionRef[] rows = f.rows();
      assertNull(rows[0].getNamedGroupInfo());
      assertOwnDataRefOrNone(rows[0], "YEAR");
      assertInstanceOf(SNamedGroupInfo.class, rows[1].getNamedGroupInfo());
      assertEquals(List.of("Business", "Hardware"),
                   ((SNamedGroupInfo) rows[1].getNamedGroupInfo()).getGroupValue("bu"));
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   private static void assertStoredWest(VSDimensionRef ref, String column) {
      assertEquals(column, ref.getGroupColumnValue());
      assertEquals(column, ref.getDataRef().getName());
      assertNotNull(ref.getNamedGroupInfo(), column + " must keep its group");
      assertEquals(XCondition.STARTING_WITH, westOp(ref),
                   "the operator must stay as stored, not flatten to EQUAL_TO");
      assertEquals(List.of("W"), ((Condition) ((ExpertNamedGroupInfo) ref.getNamedGroupInfo())
         .getGroupCondition("West").getCondition(0)).getValues());
      assertEquals(GROUPED_ORDER, ref.getOrder(), "the SORT_SPECIFIC bit must be kept");
   }

   /** A fresh dimension has no data ref yet; what it must never have is a neighbour's. */
   private static void assertOwnDataRefOrNone(VSDimensionRef ref, String column) {
      assertTrue(ref.getDataRef() == null || column.equals(ref.getDataRef().getName()),
                 "the fresh dimension must not inherit a neighbour's data ref");
   }

   private static int westOp(VSDimensionRef ref) {
      return ((ExpertNamedGroupInfo) ref.getNamedGroupInfo())
         .getGroupCondition("West").getCondition(0).getOperation();
   }

   // ── explicit ungroup (bug #79017) ─────────────────────────────────────────

   @Test
   void anExplicitNamedGroupValuesNullRemovesTheGroupAndKeepsAnAscendingSort() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(clearing("REGION"), dim("YEAR")), null);

      VSDimensionRef[] rows = f.rows();
      assertNull(rows[0].getNamedGroupInfo(), "the group must be removed, not restored");
      assertEquals(XConstants.SORT_ASC, rows[0].getOrder());
      assertNotNull(rows[0].getDataRef(), "the column itself must stay bound");
      assertNull(rows[1].getNamedGroupInfo());
   }

   @Test
   void clearingOneColumnLeavesAnotherGroupedColumnAlone() throws Exception {
      VSDimensionRef state = plain("STATE");
      state.setNamedGroupInfo(westGroup("STATE"));
      state.setOrder(GROUPED_ORDER);
      Fixture f = new Fixture(List.of(expertRegion(), state), List.of(), null);

      f.service.setShelf("tok", principal(), "Crosstab1", "rows",
                         List.of(clearing("REGION"), named("STATE", "Custom")), null);

      assertNull(f.rows()[0].getNamedGroupInfo());
      assertStoredWest(f.rows()[1], "STATE");
   }

   /**
    * add_table_field adds a new occurrence and leaves the existing one alone, but the factory
    * restore would hand the old group to whichever fresh REGION comes first -- the new one when
    * it is inserted ahead of the grouped one -- unless the clear is honoured here too.
    */
   @Test
   void addFieldWithAnExplicitNamedGroupValuesNullAddsAnUngroupedOccurrence() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion(), plain("YEAR")), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", clearing("REGION"), 0, null);

      VSDimensionRef[] rows = f.rows();
      assertEquals(3, rows.length);
      assertEquals("REGION", rows[0].getGroupColumnValue());
      assertNull(rows[0].getNamedGroupInfo(), "the new occurrence must not inherit the group");
      assertStoredWest(rows[1], "REGION");
   }

   @Test
   void addFieldWithoutTheNullKeepsAnExistingGroup() throws Exception {
      Fixture f = new Fixture(List.of(expertRegion()), List.of(), null);

      f.service.addField("tok", principal(), "Crosstab1", "rows", dim("YEAR"), null, null);

      assertStoredWest(f.rows()[0], "REGION");
   }

   private static FieldRef clearing(String column) {
      try {
         return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
            "{\"column\":\"" + column + "\",\"type\":\"dimension\",\"namedGroupValues\":null}",
            FieldRef.class);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef named(String column, String namedGroup) {
      return new FieldRef(column, "dimension", null, null, namedGroup);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static VSDimensionRef plain(String column) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setGroupColumnValue(column);
      ref.setDataRef(new AttributeRef(null, column));
      ref.setDataType(XSchema.STRING);
      ref.setOrder(XConstants.SORT_ASC);
      return ref;
   }

   private static ExpertNamedGroupInfo westGroup(String column) {
      ConditionList list = new ConditionList();
      Condition condition = new Condition(XSchema.STRING);
      condition.setOperation(XCondition.STARTING_WITH);
      condition.addValue("W");
      list.append(new ConditionItem(new AttributeRef(null, column), condition, 0));
      ExpertNamedGroupInfo expert = new ExpertNamedGroupInfo();
      expert.setGroupCondition("West", list);
      return expert;
   }

   private static VSDimensionRef expertRegion() {
      VSDimensionRef ref = plain("REGION");
      ref.setNamedGroupInfo(westGroup("REGION"));
      ref.setOrder(GROUPED_ORDER);
      return ref;
   }

   private static DefaultNamedGroupAssembly namedGroup(String name, String column, int operation,
                                                      String value)
   {
      ConditionList list = new ConditionList();
      Condition condition = new Condition(XSchema.STRING);
      condition.setOperation(operation);
      condition.addValue(value);
      list.append(new ConditionItem(new AttributeRef(null, column), condition, 0));
      NamedGroupInfo info = new NamedGroupInfo();
      info.setGroupCondition("West", list);

      DefaultNamedGroupAssembly assembly = mock(DefaultNamedGroupAssembly.class);
      when(assembly.getName()).thenReturn(name);
      when(assembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(assembly.getAttachedSource()).thenReturn(SOURCE);
      when(assembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, column));
      when(assembly.getNamedGroupInfo()).thenReturn(info);
      return assembly;
   }

   private static Worksheet worksheetWith(DefaultNamedGroupAssembly... groups) {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(groups);
      return ws;
   }

   private static final inetsoft.uql.asset.SourceInfo SOURCE =
      new inetsoft.uql.asset.SourceInfo(inetsoft.uql.asset.SourceInfo.ASSET, null, "Query1");

   /** A real service over a real factory and a live crosstab seeded with design headers. */
   private static final class Fixture {
      final CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      final TableBindingService service;

      Fixture(List<VSDimensionRef> rows, List<VSDimensionRef> cols, Worksheet ws) {
         DataRefModelFactoryService refService = refService();
         VSCrosstabBindingFactory factory = new VSCrosstabBindingFactory(refService);
         VSCrosstabInfo info = assembly.getVSCrosstabInfo();
         info.setDesignRowHeaders(rows.toArray(new DataRef[0]));
         info.setDesignColHeaders(cols.toArray(new DataRef[0]));
         assembly.setSourceInfo(SOURCE);

         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(assembly);
         when(vs.getBaseWorksheet()).thenReturn(ws);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

         try {
            doAnswer(invocation -> {
               ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
               mutation.run(rvs, "rt1", null);
               return null;
            }).when(sessions).mutate(anyString(), any(Principal.class), any());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         VSBindingModelService bindings = mock(VSBindingModelService.class);

         try {
            doAnswer(invocation -> {
               ApplyVSAssemblyInfoEvent event = invocation.getArgument(1);
               VSBindingModelService.ungroupDimensions(assembly, event.getUngroupDimensions());
               factory.updateAssembly((CrosstabBindingModel) event.getBinding(), assembly);
               return null;
            }).when(bindings).setBinding(anyString(), any(), any(Principal.class), any());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         VSBindingService binding = mock(VSBindingService.class);
         when(binding.createModel(any())).thenAnswer(invocation -> {
            CrosstabBindingModel model = factory.createModel(assembly);
            model.setSource(new inetsoft.web.binding.model.SourceInfo());
            model.getSource().setSource("Query1");
            return model;
         });

         service = new TableBindingService(sessions, binding, bindings, refService,
                                           mock(HideColumnsDialogService.class),
                                           mock(VSAssemblyInfoHandler.class));
      }

      VSDimensionRef[] rows() {
         return cast(assembly.getVSCrosstabInfo().getDesignRowHeaders());
      }

      VSDimensionRef[] cols() {
         return cast(assembly.getVSCrosstabInfo().getDesignColHeaders());
      }

      private static VSDimensionRef[] cast(DataRef[] refs) {
         List<VSDimensionRef> out = new ArrayList<>();

         for(DataRef ref : refs) {
            out.add((VSDimensionRef) ref);
         }

         return out.toArray(new VSDimensionRef[0]);
      }

      private static DataRefModelFactoryService refService() {
         BDimensionRefModel.VSDimensionRefModelFactory dimensionFactory =
            new BDimensionRefModel.VSDimensionRefModelFactory();
         DataRefModelFactoryService service = new DataRefModelFactoryService(
            List.of(dimensionFactory, new AttributeRefModel.AttributeRefModelFactory()));
         dimensionFactory.setDataRefModelFactoryService(service);
         return service;
      }
   }
}
