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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A format that cannot show the card tint swaps it, once, before the assembly is written. Only the
 * DEFAULT-tier tint is swapped: an author's colour is kept, and a format that paints the page keeps
 * the tint.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CardTintExportTest {
   @Test
   void aSubstitutingExporterReplacesTheDefaultTint() {
      TableVSAssembly table = modernTable();
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();

      prepare(new Substituting(Color.RED), table);

      assertEquals("#ff0000", format.getDefaultFormat().getBackgroundValue());
      assertEquals(100, format.getDefaultFormat().getAlphaValue());
      assertEquals(new Color(255, 0, 0, 255), format.getBackground(), "an opaque fill");
      assertFalse(format.isDefaultCardTint());
   }

   @Test
   void aNullSubstituteLeavesNoFill() {
      TableVSAssembly table = modernTable();
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();

      prepare(new Substituting(null), table);

      assertNull(format.getDefaultFormat().getBackgroundValue());
      assertEquals(100, format.getDefaultFormat().getAlphaValue());
      assertNull(format.getBackground());
   }

   @Test
   void anAuthorColourIsNeverSubstituted() {
      TableVSAssembly table = modernTable();
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();
      format.getUserDefinedFormat().setBackgroundValue("#00ff00");

      prepare(new Substituting(Color.RED), table);

      assertEquals(VSObjectChromeDefaults.cardTintValue(),
                   format.getDefaultFormat().getBackgroundValue(), "the tier under it is untouched");
      assertEquals(VSObjectChromeDefaults.CARD_TINT_ALPHA,
                   format.getDefaultFormat().getAlphaValue());
      assertEquals(new Color(0, 255, 0, 255), format.getBackground());
   }

   @Test
   void aPaintingExporterLeavesTheTint() {
      TableVSAssembly table = modernTable();
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();

      prepare(new HTMLVSExporter(new ByteArrayOutputStream()), table);

      assertTrue(format.isDefaultCardTint());
      assertEquals(Math.round(VSObjectChromeDefaults.CARD_TINT_ALPHA * 255f / 100),
                   format.getBackground().getAlpha());
   }

   // a table created on a modern sheet, so it carries the seeded tint
   private static TableVSAssembly modernTable() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);
      assertTrue(table.getVSAssemblyInfo().getFormat().isDefaultCardTint(), "seeded tint");
      return table;
   }

   // typed as the base, where prepareAssembly is reachable from this package
   private static void prepare(AbstractVSExporter exporter, VSAssembly assembly) {
      exporter.prepareAssembly(assembly);
   }

   private static final class Substituting extends HTMLVSExporter {
      Substituting(Color substitute) {
         super(new ByteArrayOutputStream());
         this.substitute = substitute;
      }

      @Override
      protected boolean substitutesCardTint() {
         return true;
      }

      @Override
      protected Color cardTintSubstitute(VSAssemblyInfo info) {
         return substitute;
      }

      private final Color substitute;
   }
}
