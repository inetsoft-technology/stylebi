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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PrintLayoutResolver}, which decides whether a viewsheet exports through its
 * own print layout, one inherited from an embedded viewsheet, or none at all.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutResolverTest {
   @Test
   void ownLayoutWins() {
      Viewsheet vs = new Viewsheet();
      PrintLayout own = printLayout("Text1");
      vs.getLayoutInfo().setPrintLayout(own);
      vs.addAssembly(embeddedViewsheet("Embedded1", printLayout("Chart1")));

      assertSame(own, PrintLayoutResolver.getPrintLayout(vs), "own layout is used");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs), "nothing is inherited");
      assertTrue(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void inheritsFromSingleEmbeddedViewsheet() {
      Viewsheet vs = new Viewsheet();
      PrintLayout childLayout = printLayout("Chart1");
      vs.addAssembly(embeddedViewsheet("Embedded1", childLayout));

      assertSame(childLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Embedded1", PrintLayoutResolver.getInheritedOwner(vs));
      assertTrue(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void emptyOwnLayoutStillInherits() {
      Viewsheet vs = new Viewsheet();
      vs.getLayoutInfo().setPrintLayout(new PrintLayout());
      PrintLayout childLayout = printLayout("Chart1");
      vs.addAssembly(embeddedViewsheet("Embedded1", childLayout));

      assertSame(childLayout, PrintLayoutResolver.getPrintLayout(vs),
                 "an empty layout is treated as absent, matching the exporter");
      assertEquals("Embedded1", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void twoCandidatesAreAmbiguous() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embeddedViewsheet("Embedded1", printLayout("Chart1")));
      vs.addAssembly(embeddedViewsheet("Embedded2", printLayout("Chart2")));

      assertNull(PrintLayoutResolver.getPrintLayout(vs), "no basis for choosing between them");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
      assertFalse(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void nonPrimaryEmbeddedViewsheetIsSkipped() {
      Viewsheet vs = new Viewsheet();
      Viewsheet embedded = embeddedViewsheet("Embedded1", printLayout("Chart1"));
      embedded.setPrimary(false);
      vs.addAssembly(embedded);

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void invisibleEmbeddedViewsheetIsSkipped() {
      Viewsheet vs = new Viewsheet();
      Viewsheet embedded = embeddedViewsheet("Embedded1", printLayout("Chart1"));
      embedded.getVSAssemblyInfo().setVisibleValue("hide");
      vs.addAssembly(embedded);

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void emptyChildLayoutIsTreatedAsAbsent() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embeddedViewsheet("Embedded1", new PrintLayout()));

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
      assertFalse(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void wrapperWithOtherVisibleContentDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embeddedViewsheet("Embedded1", printLayout("Chart1")));
      vs.addAssembly(new TextVSAssembly(vs, "Title"));

      assertNull(PrintLayoutResolver.getPrintLayout(vs),
                 "the inherited layout would not cover the title, so it is not used");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void wrapperWithOtherHiddenContentStillInherits() {
      Viewsheet vs = new Viewsheet();
      PrintLayout childLayout = printLayout("Chart1");
      vs.addAssembly(embeddedViewsheet("Embedded1", childLayout));
      TextVSAssembly hidden = new TextVSAssembly(vs, "HiddenParam");
      hidden.getVSAssemblyInfo().setVisibleValue("hide");
      vs.addAssembly(hidden);

      assertSame(childLayout, PrintLayoutResolver.getPrintLayout(vs),
                 "nothing visible is dropped, so inheritance still applies");
      assertEquals("Embedded1", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void secondVisibleEmbeddedViewsheetBlocksInheritance() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embeddedViewsheet("Embedded1", printLayout("Chart1")));
      vs.addAssembly(embeddedViewsheet("Embedded2", null));

      assertNull(PrintLayoutResolver.getPrintLayout(vs),
                 "the second embedded viewsheet would be dropped from the export");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void noEmbeddedViewsheetAtAll() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
      assertFalse(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void nullViewsheetIsTolerated() {
      assertNull(PrintLayoutResolver.getPrintLayout(null));
      assertNull(PrintLayoutResolver.getInheritedOwner(null));
      assertFalse(PrintLayoutResolver.hasPrintLayout(null));
   }

   /**
    * Build an embedded viewsheet assembly, optionally carrying its own print layout.
    */
   private Viewsheet embeddedViewsheet(String name, PrintLayout layout) {
      Viewsheet embedded = new Viewsheet();
      embedded.createVSAssembly(name);

      if(layout != null) {
         embedded.getLayoutInfo().setPrintLayout(layout);
      }

      return embedded;
   }

   /**
    * Build a non-empty print layout with a single entry.
    */
   private PrintLayout printLayout(String assemblyName) {
      PrintLayout layout = new PrintLayout();
      layout.setPrintInfo(new PrintInfo());
      layout.setVSAssemblyLayouts(List.of(
         new VSAssemblyLayout(assemblyName, new Point(0, 0), new Dimension(200, 100))));

      return layout;
   }
}
