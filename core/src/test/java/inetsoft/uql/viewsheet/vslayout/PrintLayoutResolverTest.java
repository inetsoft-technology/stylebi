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

   // Bug #78132: a thin wrapper of a thin wrapper inherits the grandchild's layout
   @Test
   void inheritsThroughThinIntermediateWrapper() {
      Viewsheet vs = new Viewsheet();
      PrintLayout leafLayout = printLayout("Chart1");
      vs.addAssembly(wrapper("Mid", embeddedViewsheet("Leaf", leafLayout)));

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Mid.Leaf", PrintLayoutResolver.getInheritedOwner(vs),
                   "the owner is named absolute to the exported sheet");
      assertTrue(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void inheritsThroughTwoThinIntermediateWrappers() {
      Viewsheet vs = new Viewsheet();
      PrintLayout leafLayout = printLayout("Chart1");
      vs.addAssembly(wrapper("Outer", wrapper("Mid", embeddedViewsheet("Leaf", leafLayout))));

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Outer.Mid.Leaf", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void inheritsWithCollidingDefaultNames() {
      Viewsheet vs = new Viewsheet();
      PrintLayout leafLayout = printLayout("Chart1");
      vs.addAssembly(wrapper("Viewsheet1", embeddedViewsheet("Viewsheet1", leafLayout)));

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Viewsheet1.Viewsheet1", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void intermediateWithVisibleContentDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart1")));
      mid.addAssembly(new TextVSAssembly(mid, "Title"));
      vs.addAssembly(mid);

      assertNull(PrintLayoutResolver.getPrintLayout(vs),
                 "the inherited layout would not cover the intermediate's title");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
      assertFalse(PrintLayoutResolver.hasPrintLayout(vs));
   }

   @Test
   void intermediateWithHiddenContentStillInherits() {
      Viewsheet vs = new Viewsheet();
      PrintLayout leafLayout = printLayout("Chart1");
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", leafLayout));
      TextVSAssembly hidden = new TextVSAssembly(mid, "HiddenParam");
      hidden.getVSAssemblyInfo().setVisibleValue("hide");
      mid.addAssembly(hidden);
      vs.addAssembly(mid);

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Mid.Leaf", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void intermediateWithSecondVisibleEmbeddedViewsheetDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart1")),
                             embeddedViewsheet("Other", null)));

      assertNull(PrintLayoutResolver.getPrintLayout(vs),
                 "the second embedded viewsheet would be dropped from the export");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void intermediateWithTwoResolvingChildrenIsAmbiguous() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(wrapper("Mid", embeddedViewsheet("Leaf1", printLayout("Chart1")),
                             wrapper("Mid2", embeddedViewsheet("Leaf2", printLayout("Chart2")))));

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void ownLayoutChildAndThinChainChildAreAmbiguous() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embeddedViewsheet("Embedded1", printLayout("Chart1")));
      vs.addAssembly(wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart2"))));

      assertNull(PrintLayoutResolver.getPrintLayout(vs),
                 "a child that inherits a layout is a candidate as much as one that has its own");
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void hiddenIntermediateDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart1")));
      mid.getVSAssemblyInfo().setVisibleValue("hide");
      vs.addAssembly(mid);

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void nonPrimaryIntermediateDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart1")));
      mid.setPrimary(false);
      vs.addAssembly(mid);

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void hiddenGrandchildDoesNotInherit() {
      Viewsheet vs = new Viewsheet();
      Viewsheet leaf = embeddedViewsheet("Leaf", printLayout("Chart1"));
      leaf.getVSAssemblyInfo().setVisibleValue("hide");
      vs.addAssembly(wrapper("Mid", leaf));

      assertNull(PrintLayoutResolver.getPrintLayout(vs));
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void intermediateOwnLayoutWins() {
      Viewsheet vs = new Viewsheet();
      PrintLayout midLayout = printLayout("Leaf");
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", printLayout("Chart1")));
      mid.getLayoutInfo().setPrintLayout(midLayout);
      vs.addAssembly(mid);

      assertSame(midLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Mid", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void emptyIntermediateLayoutIsTreatedAsAbsent() {
      Viewsheet vs = new Viewsheet();
      PrintLayout leafLayout = printLayout("Chart1");
      Viewsheet mid = wrapper("Mid", embeddedViewsheet("Leaf", leafLayout));
      mid.getLayoutInfo().setPrintLayout(new PrintLayout());
      vs.addAssembly(mid);

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(vs));
      assertEquals("Mid.Leaf", PrintLayoutResolver.getInheritedOwner(vs));
   }

   @Test
   void inheritanceStopsAtDepthLimit() {
      PrintLayout leafLayout = printLayout("Chart1");
      Viewsheet atLimit = chain(8, leafLayout);
      Viewsheet beyondLimit = chain(9, printLayout("Chart1"));

      assertSame(leafLayout, PrintLayoutResolver.getPrintLayout(atLimit),
                 "a layout eight embedding levels down is inherited");
      assertEquals("W1.W2.W3.W4.W5.W6.W7.Leaf", PrintLayoutResolver.getInheritedOwner(atLimit));
      assertNull(PrintLayoutResolver.getPrintLayout(beyondLimit),
                 "a deeper layout is not, and the sheet exports its canvas");
      assertNull(PrintLayoutResolver.getInheritedOwner(beyondLimit));
   }

   @Test
   void cyclicEmbeddingTerminates() {
      Viewsheet vs = new Viewsheet();
      Viewsheet a = embeddedViewsheet("A", null);
      Viewsheet b = embeddedViewsheet("B", null);
      vs.addAssembly(a);
      a.addAssembly(b);
      // A contains B and B contains A, while the parent chain B -> A -> root stays acyclic so
      // names can still be resolved
      b.setViewsheet(null);
      b.addAssembly(a);
      a.setViewsheet(vs);
      b.setViewsheet(a);
      // the regular visibility check searches the nested sheets for tip views, which never
      // ends on a cycle, so pin the visibility the way a device layout does
      a.getVSAssemblyInfo().setLayoutVisible(VSAssembly.ALWAYS_SHOW);
      b.getVSAssemblyInfo().setLayoutVisible(VSAssembly.ALWAYS_SHOW);

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
    * Build an embedded viewsheet assembly without a print layout that embeds the given ones.
    */
   private Viewsheet wrapper(String name, Viewsheet... children) {
      Viewsheet wrapper = embeddedViewsheet(name, null);

      for(Viewsheet child : children) {
         wrapper.addAssembly(child);
      }

      return wrapper;
   }

   /**
    * Build a root viewsheet over a chain of thin wrappers W1..W(depth - 1) ending in an embedded
    * viewsheet Leaf with the given print layout, <tt>depth</tt> embedding levels down.
    */
   private Viewsheet chain(int depth, PrintLayout leafLayout) {
      Viewsheet child = embeddedViewsheet("Leaf", leafLayout);

      for(int i = depth - 1; i > 0; i--) {
         child = wrapper("W" + i, child);
      }

      Viewsheet vs = new Viewsheet();
      vs.addAssembly(child);
      return vs;
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
