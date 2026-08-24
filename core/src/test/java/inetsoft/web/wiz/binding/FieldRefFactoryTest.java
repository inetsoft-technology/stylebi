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
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.XConstants;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.NamedGroupInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Guards the shared field-reference vocabulary. Specs 2b–2e inherit it and spec #4's
 * highlights embed it, so its shape and its fail-loud discriminator are the deliverable.
 */
@Tag("core")
class FieldRefFactoryTest {
   /**
    * The date level is an {@code XConstants} number whose mapping nobody can guess — year is 5,
    * quarter 4, month 3, week 2, day 1 — so a caller naturally writes {@code dateLevel: "year"}.
    * That was stored verbatim, and the binding then threw {@code For input string: "year"} on the
    * NEXT unrelated write to the same assembly, naming neither the field nor the level nor the
    * call that poisoned it. Found live on local-1199 while binding a crosstab for case 29.
    */
   @Test
   void normalizesNamedDateLevelsToTheirConstants() {
      assertEquals("5", DateLevels.normalize("year"));
      assertEquals("4", DateLevels.normalize("QUARTER"));
      assertEquals("3", DateLevels.normalize("Month"));
      assertEquals("2", DateLevels.normalize("week"));
      assertEquals("1", DateLevels.normalize("day"));
      assertEquals("0", DateLevels.normalize("none"));
   }

   @Test
   void passesANumericDateLevelThrough() {
      assertEquals("5", DateLevels.normalize("5"));
      assertEquals("0", DateLevels.normalize("0"));
   }

   @Test
   void refusesADateLevelItCannotResolveRatherThanStoringIt() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> DateLevels.normalize("fortnight"));

      assertTrue(thrown.getMessage().contains("fortnight"));
      assertTrue(thrown.getMessage().contains("year"), "the message must list what is accepted");
   }

   /** A number outside the known set is as poisonous as a word, and just as silent. */
   @Test
   void refusesAnUnknownNumericDateLevel() {
      assertThrows(IllegalArgumentException.class, () -> DateLevels.normalize("99"));
   }

   /**
    * -1 is StyleBI's own sentinel for "no date level" — {@code VSDimensionRef.setDateLevel} maps
    * it to null — so refs read back from a live binding carry it. The first version of this guard
    * refused it and broke every round trip that reads a dimension and writes it elsewhere; five
    * existing tests caught that immediately.
    */
   @Test
   void acceptsTheUnsetSentinel() {
      assertEquals("-1", DateLevels.normalize("-1"));
   }

   @Test
   void leavesAnAbsentDateLevelAlone() {
      assertNull(DateLevels.normalize(null));
   }

   @Test
   void readsADimensionAsItsColumnAndDateLevel() {
      BDimensionRefModel model = new BDimensionRefModel();
      model.setColumnValue("Order Date");
      model.setDateLevel("5");

      FieldRef ref = FieldRefFactory.from(model);

      assertEquals("Order Date", ref.column());
      assertEquals("dimension", ref.type());
      assertEquals("5", ref.dateLevel());
      assertNull(ref.aggregate(), "a dimension has no aggregate");
   }

   @Test
   void readsAMeasureAsItsColumnAndFormula() {
      BAggregateRefModel model = new BAggregateRefModel();
      model.setColumnValue("Sales");
      model.setFormula("Sum");

      FieldRef ref = FieldRefFactory.from(model);

      assertEquals("Sales", ref.column());
      assertEquals("measure", ref.type());
      assertEquals("Sum", ref.aggregate());
      assertNull(ref.dateLevel(), "a measure has no date level");
   }

   @Test
   void requireTypeRejectsAMissingDiscriminatorNamingTheField() {
      FieldRef ref = new FieldRef("Sales", null, null, null, null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> FieldRefFactory.requireType(ref));
      assertTrue(thrown.getMessage().contains("Sales"));
      assertTrue(thrown.getMessage().contains("type"));
   }

   @Test
   void requireTypeRejectsAnUnrecognizedDiscriminator() {
      FieldRef ref = new FieldRef("Sales", "metric", null, null, null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> FieldRefFactory.requireType(ref));
      assertTrue(thrown.getMessage().contains("metric"),
                 "the error must name what was supplied, got: " + thrown.getMessage());
   }

   @Test
   void requireTypeAcceptsBothValidDiscriminatorsCaseInsensitively() {
      FieldRefFactory.requireType(new FieldRef("A", "DIMENSION", null, null, null));
      FieldRefFactory.requireType(new FieldRef("B", "measure", null, null, null));
   }

   // ── namedGroup resolution ─────────────────────────────────────────────────

   private static final SourceInfo QUERY1_SOURCE =
      new SourceInfo(SourceInfo.ASSET, null, "Query1");

   private static RuntimeViewsheet rvsWithWorksheet(Worksheet ws) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static DataRefModelFactoryService refModelService() {
      DataRefModelFactoryService service = mock(DataRefModelFactoryService.class);
      when(service.createDataRefModel(any())).thenReturn(mock(DataRefModel.class));
      return service;
   }

   /**
    * {@code createNamedGroupInfo}'s {@code ASSET_NAMEDGROUP_INFO_REF} branch -- constant {@code
    * 4}, confirmed by {@code AssetNamedGroupInfo.getType()} itself -- is what this must produce
    * for a registered-name match. {@code ASSET_NAMEDGROUP_INFO} (constant {@code 3}, #4707's own
    * calc-table-specific sentinel) has no branch in that method and would silently resolve to
    * {@code null} at apply time.
    */
   @Test
   void resolveNamedGroupInfoBuildsAnExpertNamedGroupFromAWorksheetLocalAssembly() throws Exception {
      Condition condition = mock(Condition.class);
      when(condition.getOperation()).thenReturn(Condition.EQUAL_TO);
      when(condition.getValues()).thenReturn(java.util.List.of("CA"));
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(new AttributeRef(null, "REGION"), condition, 0));
      NamedGroupInfo namedGroupInfo = new NamedGroupInfo();
      namedGroupInfo.setGroupCondition("West", conditionList);

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Coastal");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource()).thenReturn(QUERY1_SOURCE);
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));
      when(ngAssembly.getNamedGroupInfo()).thenReturn(namedGroupInfo);

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[]{ ngAssembly });

      NamedGroupInfoModel model = FieldRefFactory.resolveNamedGroupInfo(
         "Coastal", rvsWithWorksheet(ws), QUERY1_SOURCE, "REGION", refModelService());

      assertEquals(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO, model.getType());
      assertEquals(1, model.getConditions().size());
      assertEquals("West", model.getConditions().get(0).getName());
      assertEquals(1, model.getConditions().get(0).getList().length);
   }

   @Test
   void resolveNamedGroupInfoBuildsAnAssetReferenceForARegisteredNameUsingTheRefConstant()
      throws Exception
   {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         AssetRepository rep = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(rep);

         AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
         when(info.getName()).thenReturn("Tiers");
         summaryAttr.when(() -> SummaryAttr.getAssetNamedGroupInfos(any(), eq(rep), isNull()))
            .thenReturn(new AssetNamedGroupInfo[]{ info });

         NamedGroupInfoModel model = FieldRefFactory.resolveNamedGroupInfo(
            "Tiers", rvsWithWorksheet(ws), QUERY1_SOURCE, "REGION", refModelService());

         // The crux of stylebi's own designer-found bug: type 3 (ASSET_NAMEDGROUP_INFO,
         // #4707's calc-table sentinel) has no branch in createNamedGroupInfo and silently
         // resolves to null. Type 4 (ASSET_NAMEDGROUP_INFO_REF) is the one it actually handles.
         assertEquals(4, XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
         assertEquals(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF, model.getType());
         assertNotEquals(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO, model.getType());
         assertEquals("Tiers", model.getName());
      }
   }

   @Test
   void resolveNamedGroupInfoRefusesAnUnrecognizedNameNamingTheFieldAndColumn() {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(null);

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> FieldRefFactory.resolveNamedGroupInfo(
               "NoSuchGroup", rvsWithWorksheet(ws), QUERY1_SOURCE, "REGION", refModelService()));

         assertTrue(thrown.getMessage().contains("NoSuchGroup"));
         assertTrue(thrown.getMessage().contains("REGION"));
      }
   }

   /**
    * {@code toChartRef} must force {@code order = SORT_SPECIFIC} when a {@code namedGroup}
    * resolves -- {@code OrderInfo.isSpecific()} gates whether the named group's conditions are
    * ever folded into the actual grouping, so a resolved-but-unspecific order silently renders
    * without any grouping at all.
    */
   @Test
   void toChartRefForcesSortSpecificWhenANamedGroupResolves() throws Exception {
      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Coastal");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource()).thenReturn(QUERY1_SOURCE);
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));
      when(ngAssembly.getNamedGroupInfo()).thenReturn(new NamedGroupInfo());

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[]{ ngAssembly });

      FieldRef field = new FieldRef("REGION", "dimension", null, null, "Coastal");
      ChartRefModel ref = FieldRefFactory.toChartRef(
         field, rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      assertInstanceOf(ChartDimensionRefModel.class, ref);
      assertEquals(XConstants.SORT_SPECIFIC, ((ChartDimensionRefModel) ref).getOrder());
      assertNotNull(((ChartDimensionRefModel) ref).getNamedGroupInfo());
   }

   @Test
   void toChartRefRefusesAnUnrecognizedNamedGroup() {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(null);
         FieldRef field = new FieldRef("REGION", "dimension", null, null, "NoSuchGroup");

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> FieldRefFactory.toChartRef(
               field, rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService()));

         assertTrue(thrown.getMessage().contains("NoSuchGroup"));
      }
   }

   /**
    * The chart read reports a per-measure chart type, which makes handing one of its refs straight
    * back to a write the obvious next move — and no write takes one. Refused here rather than only
    * in the plugin: that is the outermost tier and the most bypassable one, and anything reaching
    * these endpoints directly would otherwise get the silent drop this surface is written against.
    */
   @Test
   void refusesAnInboundChartTypeRatherThanDroppingIt() {
      FieldRef field = new FieldRef("PAID", "measure", "Sum", null, null, 5);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> FieldRefFactory.requireType(field));

      assertTrue(thrown.getMessage().contains("PAID"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("set_chart_type"), thrown.getMessage());
   }

   @Test
   void refusesAnInboundRuntimeChartTypeToo() {
      FieldRef field = new FieldRef("PAID", "measure", "Sum", null, null, null, 1);

      assertThrows(IllegalArgumentException.class, () -> FieldRefFactory.requireType(field));
   }

   /** The guard sits on requireType, so it covers the chart path through toChartRef as well. */
   @Test
   void refusesAnInboundChartTypeOnTheChartWritePathToo() {
      FieldRef field = new FieldRef("PAID", "measure", "Sum", null, null, 5);

      assertThrows(IllegalArgumentException.class, () -> FieldRefFactory.toChartRef(field));
   }

   @Test
   void stillAcceptsARefThatCarriesNoChartType() {
      FieldRefFactory.requireType(new FieldRef("PAID", "measure", "Sum", null, null));
   }
}
