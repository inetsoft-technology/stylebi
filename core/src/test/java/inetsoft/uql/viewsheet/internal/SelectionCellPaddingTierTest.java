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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that the selection base's cell padding follows the author opinion tier,
 * where DEFAULT is a seeded value and USER is an author opinion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionCellPaddingTierTest {
   @Test
   void theAuthorPredicateFollowsTheUserTier() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      assertFalse(info.isUserCellPadding());

      info.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.DEFAULT);
      assertFalse(info.isUserCellPadding(), "a seeded value is not an author opinion");

      info.setCellPadding(new Insets(2, 2, 2, 2), CompositeValue.Type.USER);
      assertTrue(info.isUserCellPadding());

      info.resetUserCellPadding();
      assertFalse(info.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding(),
                   "clearing the opinion falls back to the seed, not to null");
   }
}
