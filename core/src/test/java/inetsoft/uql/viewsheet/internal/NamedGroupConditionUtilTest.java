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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extracted from the near-identical {@code TableConditionUtil.syncConditionList} /
 * {@code BaseTableShowDetailsService.syncConditionList} so chart/crosstab/table dimensions can
 * share the same "this"-placeholder resolution those two calc-table paths already rely on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NamedGroupConditionUtilTest {
   @Test
   void needsColumnResolutionIsTrueOnlyForTheTwoAssetBackedShapes() {
      assertTrue(NamedGroupConditionUtil.needsColumnResolution(new AssetNamedGroupInfo()));
      assertFalse(NamedGroupConditionUtil.needsColumnResolution(new ExpertNamedGroupInfo()));
      assertFalse(NamedGroupConditionUtil.needsColumnResolution(new SNamedGroupInfo()));
      assertFalse(NamedGroupConditionUtil.needsColumnResolution(null));
   }

   @Test
   void replacesTheThisPlaceholderWithTheRealColumn() {
      Condition cond = new Condition();
      cond.setOperation(Condition.EQUAL_TO);
      cond.addValue("CA");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef("this")), cond, 0));

      NamedGroupConditionUtil.resolveConditionColumn(list, "REGION");

      assertEquals("REGION", list.getConditionItem(0).getAttribute().getAttribute());
   }

   @Test
   void leavesARealColumnAttributeAlone() {
      Condition cond = new Condition();
      cond.setOperation(Condition.EQUAL_TO);
      cond.addValue("CA");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef("STATE")), cond, 0));

      NamedGroupConditionUtil.resolveConditionColumn(list, "REGION");

      assertEquals("STATE", list.getConditionItem(0).getAttribute().getAttribute());
   }

   @Test
   void toleratesAConditionItemWithNoAttribute() {
      Condition cond = new Condition();
      cond.setOperation(Condition.EQUAL_TO);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(null, cond, 0));

      assertDoesNotThrow(() -> NamedGroupConditionUtil.resolveConditionColumn(list, "REGION"));
   }
}
