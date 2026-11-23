/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dark modern table's text is light when its table style is cleared. With a style the lens drops
 * the object foreground and the style supplies the text color, so this default only reaches an
 * unstyled table, which otherwise fell back to black on the dark card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableDarkTextSeedTest {
   // the object default every table starts with, which a table with no style reads
   private static final String BLACK = "0";

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void aDarkModernTableTakesTheLightText() {
      gate(true, true);
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(), objectForeground(newTable()));
   }

   @Test
   void aLightModernTableKeepsTheBlackText() {
      gate(true, false);
      assertEquals(BLACK, objectForeground(newTable()));
   }

   @Test
   void aClassicTableKeepsTheBlackText() {
      gate(false, false);
      assertEquals(BLACK, objectForeground(newTable()));
   }

   @Test
   void revertingADarkTableRestoresTheClassicText() {
      gate(false, false);
      Viewsheet vs = new Viewsheet();
      TableVSAssemblyInfo table = add(vs);
      VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK);
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(), objectForeground(table),
                   "premise: dark mark seeds the light text");

      VizModernizeUtil.revert(vs);
      assertEquals(BLACK, objectForeground(table));
   }

   @Test
   void anAuthorTextColorIsKeptWhenTheMarkChanges() {
      gate(false, false);
      Viewsheet vs = new Viewsheet();
      TableVSAssemblyInfo table = add(vs);
      table.getFormat().getUserDefinedFormat().setForegroundValue("0xff0000");

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_DARK);
      assertEquals("0xff0000", table.getFormat().getUserDefinedFormat().getForegroundValue());
   }

   private static void gate(boolean modern, boolean dark) {
      SreeEnv.setProperty("viewsheet.modernVisualization", Boolean.toString(modern));
      SreeEnv.setProperty("viewsheet.darkMode", dark ? "true" : null);
   }

   private static String objectForeground(TableVSAssemblyInfo table) {
      return table.getFormat().getDefaultFormat().getForegroundValue();
   }

   private static TableVSAssemblyInfo newTable() {
      return add(new Viewsheet());
   }

   private static TableVSAssemblyInfo add(Viewsheet vs) {
      TableVSAssembly assembly = new TableVSAssembly(vs, "Table1");
      assembly.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(assembly);
      return (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
   }
}
