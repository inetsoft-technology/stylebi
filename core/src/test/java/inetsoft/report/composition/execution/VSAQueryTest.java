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

import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code replaceGroupValues} splices a named group's own stored condition list in place of a
 * "filter by group name" condition. It widens from a hard {@code SNamedGroupInfo} cast to
 * {@code XNamedGroupInfo}, sources {@code dtype} from the dimension's own {@code DataRef} rather
 * than the group's (type-specific, occasionally-stale) embedded copy, and resolves an Asset
 * group's {@code "this"}-placeholder conditions before splicing (worksheet-local Expert groups
 * never carry the placeholder -- their conditions already reference the real column).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAQueryTest {
   private static VSDimensionRef regionRow() {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static CrosstabVSAssembly crosstabWithRow(VSDimensionRef row) {
      CrosstabVSAssembly crosstab =
         new CrosstabVSAssembly(new inetsoft.uql.viewsheet.Viewsheet(), "Crosstab1");
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setRuntimeRowHeaders(new VSDimensionRef[]{ row });
      cinfo.setRuntimeColHeaders(new VSDimensionRef[0]);
      crosstab.setVSCrosstabInfo(cinfo);
      return crosstab;
   }

   private static ConditionList filterByGroupName(String groupName) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(groupName);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));
      return conds;
   }

   @Test
   void splicesAnExpertNamedGroupsOwnConditionInPlaceOfTheGroupNameFilter() {
      VSDimensionRef row = regionRow();
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      Condition groupCond = new Condition(XSchema.STRING);
      groupCond.setOperation(XCondition.EQUAL_TO);
      groupCond.addValue("CA");
      ConditionList groupConds = new ConditionList();
      // Expert groups' conditions already reference the real column -- no "this" placeholder.
      groupConds.append(new ConditionItem(new AttributeRef("REGION"), groupCond, 0));
      info.setGroupCondition("West", groupConds);
      row.setNamedGroupInfo(info);

      ConditionList result = VSAQuery.replaceGroupValues(
         filterByGroupName("West"), crosstabWithRow(row), false);

      assertEquals(1, result.getConditionSize());
      ConditionItem item = result.getConditionItem(0);
      assertEquals("REGION", item.getAttribute().getAttribute());
      assertEquals("CA", item.getCondition().getValue(0));
   }

   @Test
   void resolvesTheThisPlaceholderInAnAssetNamedGroupBeforeSplicing() {
      VSDimensionRef row = regionRow();
      AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
      when(info.getType()).thenReturn(inetsoft.uql.util.XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
      when(info.isEmpty()).thenReturn(false);
      when(info.getGroups()).thenReturn(new String[]{ "West" });

      Condition placeholderCond = new Condition(XSchema.STRING);
      placeholderCond.setOperation(XCondition.EQUAL_TO);
      placeholderCond.addValue("CA");
      ConditionList placeholderConds = new ConditionList();
      // A repository-registered named group authored unattached to any column carries a "this"
      // placeholder in place of a real column reference.
      placeholderConds.append(new ConditionItem(new ColumnRef(new AttributeRef("this")),
                                                placeholderCond, 0));
      when(info.getGroupCondition("West")).thenReturn(placeholderConds);
      row.setNamedGroupInfo(info);

      ConditionList result = VSAQuery.replaceGroupValues(
         filterByGroupName("West"), crosstabWithRow(row), false);

      assertEquals(1, result.getConditionSize());
      ConditionItem item = result.getConditionItem(0);
      assertEquals("REGION", item.getAttribute().getAttribute(),
         "the \"this\" placeholder must be resolved to the real bound column before splicing");
      assertEquals("CA", item.getCondition().getValue(0));
   }

   @Test
   void leavesAnUngroupedFilterUnchanged() {
      VSDimensionRef row = regionRow();

      ConditionList result = VSAQuery.replaceGroupValues(
         filterByGroupName("Anything"), crosstabWithRow(row), false);

      assertEquals(1, result.getConditionSize());
      assertEquals("REGION", result.getConditionItem(0).getAttribute().getAttribute());
      assertEquals("Anything", result.getConditionItem(0).getCondition().getValue(0));
   }
}
