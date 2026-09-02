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
package inetsoft.web.composer.model.vs;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class IntervalPaneModelTest {
   /**
    * A fresh IntervalPaneModel's "level" default (DateComparisonInfo.YEAR = 16) is not a
    * member of the level domain (only ALL/YEAR_TO_DATE/.../SAME_MONTH etc. are valid), so it
    * was always silently coerced to ALL by DynamicValue.findValue()'s restriction-list
    * fallback. Pin that outcome explicitly so the default can't drift to another
    * invalid-for-level value without this test catching it.
    */
   @Test
   void defaultLevelResolvesToAll() {
      IntervalPaneModel model = new IntervalPaneModel();

      Assertions.assertEquals(
         DateComparisonInfo.ALL, model.toDateComparisonInterval().getLevel());
   }
}
