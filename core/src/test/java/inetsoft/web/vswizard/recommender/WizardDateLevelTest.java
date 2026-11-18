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
package inetsoft.web.vswizard.recommender;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The wizard's date-level fallback starts at month on a marked chart, keeps an interval found
 * for the field, and stays year for the callers that pass no context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizardDateLevelTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedWizardFallsBackToMonth() {
      VSDimensionRef dim = dimension(XSchema.DATE);
      WizardRecommenderUtil.setDefaultDateLevel(dim, entry(null),
                                                VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(String.valueOf(DateRangeRef.MONTH_INTERVAL), dim.getDateLevelValue());
   }

   @Test
   void aMarkedWizardKeepsAFoundInterval() {
      VSDimensionRef dim = dimension(XSchema.DATE);
      WizardRecommenderUtil.setDefaultDateLevel(
         dim, entry(String.valueOf(DateRangeRef.YEAR_INTERVAL)),
         VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(String.valueOf(DateRangeRef.YEAR_INTERVAL), dim.getDateLevelValue());
   }

   @Test
   void theTwoArgumentFormKeepsYear() {
      VSDimensionRef dim = dimension(XSchema.DATE);
      WizardRecommenderUtil.setDefaultDateLevel(dim, entry(null));
      assertEquals(String.valueOf(DateRangeRef.YEAR_INTERVAL), dim.getDateLevelValue());
   }

   @Test
   void aTimeFieldFallsBackToHour() {
      VSDimensionRef dim = dimension(XSchema.TIME);
      WizardRecommenderUtil.setDefaultDateLevel(dim, entry(null),
                                                VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(String.valueOf(DateRangeRef.HOUR_INTERVAL), dim.getDateLevelValue());
   }

   private static VSDimensionRef dimension(String type) {
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataType(type);
      return dim;
   }

   private static AssetEntry entry(String interval) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN,
                                        "/col", null);

      if(interval != null) {
         entry.setProperty("interval", interval);
      }

      return entry;
   }
}
