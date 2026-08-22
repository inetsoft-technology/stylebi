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
package inetsoft.web.binding.model;

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
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
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
 * {@code createDataRef()}'s Part 2 wiring (mirrors {@code ChartDimensionInfoFactoryTest}) plus
 * the {@code SORT_SPECIFIC}-stripping guard fix: the original guard checked {@code
 * getGroups().isEmpty()}, which is only populated by the manual-value ({@code
 * SIMPLE_NAMEDGROUP_INFO}) shape -- an Expert or Asset-ref model has its content in {@code
 * getConditions()}/{@code getName()} instead, so the old guard silently stripped
 * {@code SORT_SPECIFIC} right back off for those two shapes, undoing the wiring in the same call.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BDimensionRefModelTest {
   private static ColumnRefModel regionColumnModel() {
      ColumnRefModel column = new ColumnRefModel();
      column.setAttribute("REGION");
      AttributeRefModel attr = new AttributeRefModel();
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
      BDimensionRefModel model = new BDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(regionColumnModel());
      model.setNamedGroupInfo(expertModel("West", "CA"));

      DataRef dim = model.createDataRef();

      assertInstanceOf(VSDimensionRef.class, dim);
      XNamedGroupInfo info = ((VSDimensionRef) dim).getNamedGroupInfo();
      assertInstanceOf(ExpertNamedGroupInfo.class, info);
      assertEquals(List.of("West"), List.of(info.getGroups()));
      assertEquals(XConstants.SORT_SPECIFIC, ((VSDimensionRef) dim).getOrder(),
         "the guard fix must not strip SORT_SPECIFIC off an Expert-typed named group");
   }

   @Test
   void wiresAnAssetNamedGroupModelIntoALiveRef() {
      NamedGroupInfoModel ngModel = new NamedGroupInfoModel();
      ngModel.setType(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
      ngModel.setName("Tiers");

      BDimensionRefModel model = new BDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(regionColumnModel());
      model.setNamedGroupInfo(ngModel);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         AssetRepository rep = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(rep);

         AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
         when(info.getName()).thenReturn("Tiers");
         summaryAttr.when(() -> SummaryAttr.getAssetNamedGroupInfos(any(), eq(rep), isNull()))
            .thenReturn(new AssetNamedGroupInfo[]{ info });

         DataRef dim = model.createDataRef();

         assertSame(AssetNamedGroupInfo.class, ((VSDimensionRef) dim).getNamedGroupInfo().getClass());
         assertEquals(XConstants.SORT_SPECIFIC, ((VSDimensionRef) dim).getOrder(),
            "the guard fix must not strip SORT_SPECIFIC off an Asset-ref named group");
      }
   }

   @Test
   void refusesANamedGroupOnACubeSourcedDimension() {
      // No DataRefModel -- createDataRef() builds a fresh VSDimensionRef with no data ref set at
      // all by default, so pre-set one with the CUBE bit already on it is not applicable here;
      // instead confirm the guard fires against the ref's own resolved refType by giving it a
      // cube-tagged attribute directly through the model's DataRefModel.
      ColumnRefModel column = regionColumnModel();
      ((AttributeRefModel) column.getDataRefModel()).setRefType(DataRef.CUBE);

      BDimensionRefModel model = new BDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(column);
      model.setNamedGroupInfo(expertModel("West", "CA"));

      IllegalArgumentException thrown =
         assertThrows(IllegalArgumentException.class, model::createDataRef);
      assertTrue(thrown.getMessage().toLowerCase().contains("cube"));
   }

   /**
    * The pre-existing case the original guard protected: a {@code SORT_SPECIFIC} order with no
    * named-group content of any kind and no manual order must still have {@code SORT_SPECIFIC}
    * stripped -- confirming the fix widens the guard rather than removing its original purpose.
    */
   @Test
   void stillStripsSortSpecificWhenThereIsNoNamedGroupContentAtAll() {
      BDimensionRefModel model = new BDimensionRefModel();
      model.setOrder(XConstants.SORT_SPECIFIC);
      model.setDataRefModel(regionColumnModel());

      DataRef dim = model.createDataRef();

      assertEquals(0, ((VSDimensionRef) dim).getOrder() & XConstants.SORT_SPECIFIC,
         "an order with no named group and no manual order must still have SORT_SPECIFIC stripped");
   }
}
