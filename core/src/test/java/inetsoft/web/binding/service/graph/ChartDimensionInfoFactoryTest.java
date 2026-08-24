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
package inetsoft.web.binding.service.graph;

import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.binding.drm.DataRefModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code pasteChartRef}'s Part 2 wiring: a resolved {@code NamedGroupInfoModel} (built by the
 * wiz layer's {@code FieldRefFactory.resolveNamedGroupInfo}) turns into a live
 * {@code XNamedGroupInfo} on the real ref -- the capability {@code VSDimensionRef.setNamedGroupInfo}
 * used to refuse outright (see the design doc's superseded "Feasibility update").
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartDimensionInfoFactoryTest {
   private final ChartDimensionInfoFactory.VSChartDimensionInfoFactory factory =
      new ChartDimensionInfoFactory.VSChartDimensionInfoFactory(
         new DataRefModelFactoryService(List.of()));

   private static ColumnRefModel regionColumnModel() {
      ColumnRefModel column = new ColumnRefModel();
      column.setAttribute("REGION");
      inetsoft.web.binding.drm.AttributeRefModel attr = new inetsoft.web.binding.drm.AttributeRefModel();
      attr.setAttribute("REGION");
      column.setDataRefModel(attr);
      return column;
   }

   private static NamedGroupInfoModel expertModel(String groupName, String value) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));

      DataRefModelFactoryService refModelService = mock(DataRefModelFactoryService.class);
      when(refModelService.createDataRefModel(any())).thenReturn(mock(DataRefModel.class));
      Object[] list = ConditionUtil.fromConditionListToModel(conds, refModelService);

      ConditionExpression expr = new ConditionExpression();
      expr.setName(groupName);
      expr.setList(list);

      NamedGroupInfoModel model = new NamedGroupInfoModel();
      model.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);
      model.addCondition(expr);
      return model;
   }

   @Test
   void wiresAnExpertNamedGroupModelIntoALiveRef() {
      ChartDimensionRefModel model = new ChartDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(regionColumnModel());
      model.setNamedGroupInfo(expertModel("West", "CA"));

      VSChartDimensionRef ref = new VSChartDimensionRef();
      factory.pasteChartRef(null, model, ref);

      assertInstanceOf(ExpertNamedGroupInfo.class, ref.getNamedGroupInfo());
      assertEquals(List.of("West"), List.of(ref.getNamedGroupInfo().getGroups()));
   }

   @Test
   void wiresAnAssetNamedGroupModelIntoALiveRef() {
      NamedGroupInfoModel model2 = new NamedGroupInfoModel();
      model2.setType(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
      model2.setName("Tiers");

      ChartDimensionRefModel model = new ChartDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(regionColumnModel());
      model.setNamedGroupInfo(model2);

      VSChartDimensionRef ref = new VSChartDimensionRef();

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         AssetRepository rep = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(rep);

         AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
         when(info.getName()).thenReturn("Tiers");
         summaryAttr.when(() -> SummaryAttr.getAssetNamedGroupInfos(any(), eq(rep), isNull()))
            .thenReturn(new AssetNamedGroupInfo[]{ info });

         factory.pasteChartRef(null, model, ref);
      }

      assertSame(ref.getNamedGroupInfo().getClass(), AssetNamedGroupInfo.class);
   }

   @Test
   void refusesANamedGroupOnACubeSourcedDimension() {
      ChartDimensionRefModel model = new ChartDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      // No DataRefModel on the model -- pasteChartRef must not overwrite the ref's own
      // already-cube-tagged DataRef, which is what carries the refType getRefType() reads.
      model.setNamedGroupInfo(expertModel("West", "CA"));

      VSChartDimensionRef ref = new VSChartDimensionRef();
      AttributeRef cubeAttr = new AttributeRef("REGION");
      cubeAttr.setRefType(DataRef.CUBE);
      ref.setDataRef(new ColumnRef(cubeAttr));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> factory.pasteChartRef(null, model, ref));
      assertTrue(thrown.getMessage().toLowerCase().contains("cube"));
      assertNull(ref.getNamedGroupInfo(), "a refused binding must not leave partial state");
   }

   /**
    * {@code order == SORT_NONE} clears an existing named group unconditionally -- the wiz layer
    * (Part 1) always forces {@code order = SORT_SPECIFIC} whenever it resolves a {@code
    * namedGroup}, so this path only fires for a caller explicitly clearing one, or a stale model
    * that still carries a {@code namedGroupInfo} despite an unspecific order.
    */
   @Test
   void sortNoneClearsAnExistingNamedGroupEvenIfTheModelStillCarriesOne() {
      ChartDimensionRefModel model = new ChartDimensionRefModel();
      model.setOrder(XConstants.SORT_NONE);
      model.setDataRefModel(regionColumnModel());
      model.setNamedGroupInfo(expertModel("West", "CA"));

      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setNamedGroupInfo(new ExpertNamedGroupInfo());
      factory.pasteChartRef(null, model, ref);

      assertNull(ref.getNamedGroupInfo());
   }
}
