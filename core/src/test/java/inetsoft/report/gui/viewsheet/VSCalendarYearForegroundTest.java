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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * applyYearCellForeground() must resolve across all three format tiers, not just USER - a seeded
 * body colour lives on DEFAULT, and a USER-tier grey written there would outrank it and paint
 * dark-on-dark in the year view.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarYearForegroundTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void aSeededDefaultTierColourIsNotOverwritten() {
      // A freshly-constructed VSFormat's foreground resolves to black by default (fgval starts as
      // the placeholder "0"), not null - so a genuinely unset tier, as both a loaded asset and
      // CalendarVSAssemblyInfo.applyCalendarSeed leave it, clears both the value and the cached
      // color. Without this, the USER tier here would already resolve non-null on its own and the
      // old USER-only guard would never fire, masking the regression this test exists to catch.
      VSCompositeFormat format = clearedFormat();
      Color seeded = new Color(0xCAC4D0);
      format.getDefaultFormat().setForeground(seeded);
      assertEquals(seeded, format.getForeground(), "precondition: the seed resolves through DEFAULT");

      VSCalendar.applyYearCellForeground(format);

      assertEquals(seeded, format.getForeground());
      assertFalse(format.getUserDefinedFormat().isForegroundDefined(),
                  "the grey fallback must not write to the USER tier when DEFAULT already resolves");
   }

   @Test
   void aFormatWithNoForegroundOnAnyTierGetsTheLegacyGrey() {
      VSCompositeFormat format = clearedFormat();
      assertNull(format.getForeground(), "precondition: nothing resolves a foreground yet");

      VSCalendar.applyYearCellForeground(format);

      assertEquals(new Color(90, 90, 90), format.getForeground());
   }

   /**
    * A format whose USER and DEFAULT tiers both resolve to no foreground - the state a loaded
    * asset carries when nobody has set a colour. VSFormat's raw fallback field defaults to
    * Color.BLACK (not null), so clearing only the dynamic value leaves a stale black behind;
    * setForeground(null, false) clears that field too while keeping the tier "not customized"
    * (defined=false), the way CalendarVSAssemblyInfo's own applyTo() clears DEFAULT and an
    * unauthored USER tier reads after being parsed from an asset with no foreground element.
    */
   private static VSCompositeFormat clearedFormat() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getUserDefinedFormat().setForegroundValue(null, false);
      format.getUserDefinedFormat().setForeground(null, false);
      format.getDefaultFormat().setForegroundValue(null, false);
      format.getDefaultFormat().setForeground(null, false);
      return format;
   }

   @Test
   void aNullFormatIsTolerated() {
      assertDoesNotThrow(() -> VSCalendar.applyYearCellForeground(null));
   }

   /**
    * End to end for a gate-off calendar: the object format's own black reaches the year cells
    * through getFormat(path, false), so the grey fallback must not fire. A calendar created before
    * the seed existed resolves black, and a newly created one has to match it.
    */
   @Test
   void aGateOffCalendarYearCellTakesNoGrey() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().initDefaultFormat();
      VSCompositeFormat format = calendar.getVSAssemblyInfo().getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.YEAR_CALENDAR), false);

      VSCalendar.applyYearCellForeground(format);

      assertEquals(Color.BLACK, format.getForeground(),
                   "the year view greyed a gate-off calendar's month labels");
   }
}
