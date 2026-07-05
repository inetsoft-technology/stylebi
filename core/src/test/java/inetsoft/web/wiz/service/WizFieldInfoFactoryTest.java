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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the crosstab dimension echo: a date-grouped dimension must carry the
 * level-qualified {@code fullName} (parity with the chart variants) so the downstream facts pack
 * can key dimensions distinctly instead of collapsing to a false "single value" caveat.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizFieldInfoFactoryTest {
   @Test
   void crosstabDimEchoCarriesLevelQualifiedFullName() {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("date_start");
      dim.setDataType(XSchema.TIME_INSTANT);
      dim.setDateLevelValue(String.valueOf(XConstants.DAY_OF_WEEK_DATE_GROUP));

      DimensionFieldInfo info = WizFieldInfoFactory.createCrosstabDimensionFieldInfo(dim);

      String expectedFullName =
         DateRangeRef.getName("date_start", XConstants.DAY_OF_WEEK_DATE_GROUP);

      assertEquals("date_start", info.getField());
      assertFalse(info.getFullName() == null || info.getFullName().isEmpty()); // NOT empty
      assertEquals(expectedFullName, info.getFullName());   // level-qualified, e.g. "DayOfWeek(date_start)"
      assertNotNull(info.getDateGroupLevel());              // level echoed
   }
}
