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

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.GroupCondition;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.binding.model.FieldRef.NamedGroupValues;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80062 item 2: an aesthetic named group that differs from the group of a shelf dimension on
 * the same column is refused (the shelf's group would silently replace it).
 */
@WizAgentTestSupport
@Tag("core")
class AestheticNamedGroupShelfConflictTest {
   private static NamedGroupInfoModel group(String name, String... members) {
      NamedGroupInfoModel model = new NamedGroupInfoModel();
      model.setName("Custom");
      model.addGroup(new GroupCondition(name, new ArrayList<>(List.of((Object[]) members))));
      return model;
   }

   private static ChartDimensionRefModel dimension(NamedGroupInfoModel group) {
      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setFullName("Category");
      ref.setNamedGroupInfo(group);
      return ref;
   }

   private static ChartBindingModel chart(NamedGroupInfoModel shelf, NamedGroupInfoModel color) {
      ChartBindingModel model = new ChartBindingModel();
      List<ChartRefModel> x = new ArrayList<>();
      x.add(dimension(shelf));
      model.setXFields(x);
      AestheticInfo info = new AestheticInfo();
      info.setFullName("Category");
      info.setDataInfo(dimension(color));
      model.setColorField(info);
      return model;
   }

   private static FieldRef sent() {
      NamedGroupValues values = new NamedGroupValues(
         List.of(new NamedGroupValues.Clause("Creative", List.of("Games", "Graphics"))), null);
      return new FieldRef("Category", "dimension", null, null, null, null, null, values, null,
                          null, null, null, null, null);
   }

   @Test
   void refusesADifferentShelfGroupNamingBoth() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
            chart(group("Fun", "Games", "Educational"), group("Creative", "Games", "Graphics")),
            "color", sent()));
      assertTrue(e.getMessage().contains("Creative") && e.getMessage().contains("Fun") &&
                 e.getMessage().contains("set_chart_shelf"), e.getMessage());
   }

   @Test
   void acceptsTheIdenticalGroupRegardlessOfMemberOrder() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Creative", "Graphics", "Games"), group("Creative", "Games", "Graphics")),
         "color", sent()));
   }

   @Test
   void refusesTheSameMembersUnderADifferentGroupName() {
      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
            chart(group("Fun", "Games", "Graphics"), group("Creative", "Games", "Graphics")),
            "color", sent()));
   }

   @Test
   void leavesAPlainShelfDimensionToTheDroppedGroupCheck() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(null, group("Creative", "Games", "Graphics")), "color", sent()));
   }

   @Test
   void ignoresAFieldThatSentNoGroup() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Fun", "Games"), group("Fun", "Games")), "color",
         new FieldRef("Category", "dimension", null, null, null)));
   }
}
