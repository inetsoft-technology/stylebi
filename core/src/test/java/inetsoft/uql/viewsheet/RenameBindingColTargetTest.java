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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79342: renaming a calc field must carry chart target lines, which are bound by the
 * aggregate-wrapped measure name, over to the new name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RenameBindingColTargetTest {
   private static GraphTarget target(ChartVSAssembly chart, String measure) {
      GraphTarget target = new GraphTarget();
      target.setField(measure);
      target.setFieldLabel(measure);
      chart.getChartDescriptor().addTarget(target);
      return target;
   }

   @Test
   void targetFollowsRename() {
      ChartVSAssembly chart = new ChartVSAssembly();
      GraphTarget target = target(chart, "Sum(Net Revenue)");

      chart.renameBindingCol("Net Revenue", "Net Sales");

      assertEquals("Sum(Net Sales)", target.getField());
      assertEquals("Sum(Net Sales)", target.getFieldLabel());
   }

   @Test
   void parenthesizedNameRenames() {
      ChartVSAssembly chart = new ChartVSAssembly();
      GraphTarget target = target(chart, "Sum(Rev (USD))");

      chart.renameBindingCol("Rev (USD)", "Rev2");

      assertEquals("Sum(Rev2)", target.getField());
   }

   @Test
   void oneLetterNameKeepsFunctionPrefix() {
      ChartVSAssembly chart = new ChartVSAssembly();
      GraphTarget target = target(chart, "Sum(m)");

      chart.renameBindingCol("m", "X");

      assertEquals("Sum(X)", target.getField());
   }

   @Test
   void unrelatedTargetUntouched() {
      ChartVSAssembly chart = new ChartVSAssembly();
      GraphTarget target = target(chart, "Sum(Cost)");

      chart.renameBindingCol("Net Revenue", "Net Sales");

      assertEquals("Sum(Cost)", target.getField());
      assertEquals("Sum(Cost)", target.getFieldLabel());
   }
}
