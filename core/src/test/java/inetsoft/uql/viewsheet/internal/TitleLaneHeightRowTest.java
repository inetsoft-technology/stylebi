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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TitleLaneHeightRowTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // real construction writes the calendar's 36px lane in initDefaultFormat, not in its constructor
   private <T extends VSAssemblyInfo & TitledVSAssemblyInfo> T built(T info) {
      info.initDefaultFormat();
      return info;
   }

   private <T extends VSAssemblyInfo & TitledVSAssemblyInfo> T marked(T info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      return built(info);
   }

   @Test
   void includedTypesTakeTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals(26, marked(new ChartVSAssemblyInfo()).getTitleHeight(), "chart");
      assertEquals(26, marked(new TableVSAssemblyInfo()).getTitleHeight(), "table");
      assertEquals(26, marked(new CrosstabVSAssemblyInfo()).getTitleHeight(), "crosstab");
      assertEquals(26, marked(new CalcTableVSAssemblyInfo()).getTitleHeight(), "calc table");
      assertEquals(26, marked(new EmbeddedTableVSAssemblyInfo()).getTitleHeight(), "embedded table");
      assertEquals(26, marked(new SelectionListVSAssemblyInfo()).getTitleHeight(), "selection list");
      assertEquals(26, marked(new SelectionTreeVSAssemblyInfo()).getTitleHeight(), "selection tree");
      assertEquals(26, marked(new CurrentSelectionVSAssemblyInfo()).getTitleHeight(), "selection container");
      assertEquals(26, marked(new CalendarVSAssemblyInfo()).getTitleHeight(), "calendar");
   }

   @Test
   void excludedTypesNeverTakeTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(AssetUtil.defh, marked(new CheckBoxVSAssemblyInfo()).getTitleHeight(), "check box");
      assertEquals(AssetUtil.defh, marked(new RadioButtonVSAssemblyInfo()).getTitleHeight(), "radio button");
      assertEquals(AssetUtil.defh, marked(new TimeSliderVSAssemblyInfo()).getTitleHeight(), "range slider");
   }

   @Test
   void unmarkedTypesAreUntouchedAtEveryDensity() {
      for(String mode : new String[]{ "dense", "compact", "comfortable" }) {
         SreeEnv.setProperty("viewsheet.density", mode);
         assertEquals(AssetUtil.defh, built(new ChartVSAssemblyInfo()).getTitleHeight(), mode + " chart");
         assertEquals(AssetUtil.defh, built(new TableVSAssemblyInfo()).getTitleHeight(), mode + " table");
         assertEquals(36, built(new CalendarVSAssemblyInfo()).getTitleHeight(), mode + " calendar");
      }
   }

   @Test
   void anAuthorHeightSurvivesOnAMarkedAssembly() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = marked(new ChartVSAssemblyInfo());
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      assertEquals(25, info.getTitleHeight());
   }

   @Test
   void theStoredHeightIsUnchangedWhileTheRowResolvesIt() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = marked(new ChartVSAssemblyInfo());
      assertEquals(30, info.getTitleHeight(), "the lane resolves live");
      assertEquals(AssetUtil.defh, info.getTitleHeightValue(), "the stored height does not move");
   }
}
