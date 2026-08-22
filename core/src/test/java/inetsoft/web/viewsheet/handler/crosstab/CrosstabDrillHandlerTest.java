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
package inetsoft.web.viewsheet.handler.crosstab;

import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
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
import inetsoft.uql.util.XNamedGroupInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code getGroupCondition} has no live callers today (Decision 4) but was the codebase's own
 * cited example of a graceful defensive guard against non-{@code SNamedGroupInfo} types --
 * confirmed to be a stub that returned an empty condition list for Expert/Asset groups rather
 * than something that actually worked for them. Widened to splice the group's own condition list
 * (with "this"-placeholder resolution for Asset groups), matching Site 2's shared utility.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabDrillHandlerTest {
   private static ColumnRef regionColumn() {
      ColumnRef ref = new ColumnRef(new AttributeRef("REGION"));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   @Test
   void returnsAnEmptyConditionListForANullGroupInfo() {
      ConditionList result = CrosstabDrillHandler.getGroupCondition(null, regionColumn(), "West");
      assertTrue(result.isEmpty());
   }

   @Test
   void splicesAnExpertNamedGroupsOwnCondition() {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("CA");
      ConditionList groupConds = new ConditionList();
      groupConds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));
      info.setGroupCondition("West", groupConds);

      ConditionList result = CrosstabDrillHandler.getGroupCondition(info, regionColumn(), "West");

      assertEquals(1, result.getConditionSize());
      assertEquals("CA", result.getConditionItem(0).getCondition().getValue(0));
   }

   @Test
   void resolvesTheThisPlaceholderForAnAssetNamedGroup() {
      AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
      when(info.getType()).thenReturn(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);

      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("CA");
      ConditionList placeholderConds = new ConditionList();
      placeholderConds.append(
         new ConditionItem(new ColumnRef(new AttributeRef("this")), cond, 0));
      when(info.getGroupCondition("West")).thenReturn(placeholderConds);

      ConditionList result = CrosstabDrillHandler.getGroupCondition(info, regionColumn(), "West");

      assertEquals(1, result.getConditionSize());
      assertEquals("REGION", result.getConditionItem(0).getAttribute().getAttribute());
   }

   @Test
   void returnsAnEmptyConditionListWhenTheGroupNameHasNoCondition() {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      ConditionList result = CrosstabDrillHandler.getGroupCondition(info, regionColumn(), "NoSuch");
      assertTrue(result.isEmpty());
   }
}
