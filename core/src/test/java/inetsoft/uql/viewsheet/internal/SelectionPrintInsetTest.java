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

import inetsoft.report.*;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TextBoxElementDef;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A selection's card inset reaches the text box print layout converts it to. The converter's
 * private add methods are driven for real over an unattached viewsheet; only the sandbox and
 * the other collaborators are absent, and the selection paths do not touch them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionPrintInsetTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void thePrintedListTakesItsInset() throws Exception {
      SelectionListVSAssembly list = list("comfortable");

      assertEquals(new Insets(16, 16, 16, 16), convertedBoxFor(list).getPadding());
   }

   @Test
   void thePrintedListTakesTheTierInset() throws Exception {
      assertEquals(new Insets(12, 12, 12, 12), convertedBoxFor(list("compact")).getPadding());
      assertEquals(new Insets(8, 8, 8, 8), convertedBoxFor(list("dense")).getPadding());
   }

   @Test
   void thePrintedTreeTakesItsInset() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree1");
      place(tree);
      mark(tree.getVSAssemblyInfo());

      assertEquals(new Insets(16, 16, 16, 16), convertedBoxFor(tree).getPadding());
   }

   @Test
   void thePrintedContainerIsUnchanged() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      place(container);
      container.setAssemblies(new String[] { "List1" });
      vs.addAssembly(container);
      SelectionListVSAssembly child = new SelectionListVSAssembly(vs, "List1");
      vs.addAssembly(child);
      mark(container.getVSAssemblyInfo());

      // the container takes no inset of its own, so its box keeps its own default padding
      Insets baseline = new TextBoxElementDef(new TabularSheet(null, null),
                                              new DefaultTextLens("x")).getPadding();

      assertEquals(baseline, convertedBoxFor(container).getPadding());
   }

   @Test
   void aLegacySelectionPrintsUnchanged() throws Exception {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List1");
      place(list);

      // the box's own default padding, not a zero inset written over it
      Insets baseline = new TextBoxElementDef(new TabularSheet(null, null),
                                              new DefaultTextLens("x")).getPadding();

      assertEquals(baseline, convertedBoxFor(list).getPadding());
   }

   @Test
   void theBoxDoesNotShareTheAssemblyInset() throws Exception {
      SelectionListVSAssembly list = list("comfortable");
      TextBoxElement box = convertedBoxFor(list);
      Insets live = ((VSAssemblyInfo) list.getInfo()).getPadding();

      assertNotSame(live, box.getPadding());
      live.top = 99;
      assertEquals(16, box.getPadding().top, "a later edit to the assembly must not rewrite the page");
   }

   private SelectionListVSAssembly list(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List1");
      place(list);
      mark(list.getVSAssemblyInfo());
      return list;
   }

   private void place(VSAssembly assembly) {
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      info.setPixelOffset(new Point(20, 10));
      info.setPixelSize(new Dimension(200, 150));
      info.setLayoutPosition(new Point(20, 10));
      info.setLayoutSize(new Dimension(200, 150));
      ((TitledVSAssemblyInfo) info).setTitleVisible(false);
      vs.addAssembly(assembly);
   }

   private void mark(VSAssemblyInfo info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
   }

   /** Run the converter's own add method for the assembly, and return the one box it added. */
   private TextBoxElement convertedBoxFor(VSAssembly assembly) throws Exception {
      VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);
      Field field = VsToReportConverter.class.getDeclaredField("report");
      field.setAccessible(true);
      TabularSheet report = (TabularSheet) field.get(converter);
      SectionElementDef section = new SectionElementDef(report);
      report.addElement(0, 0, section);

      String method;
      Class<?> type;

      if(assembly instanceof SelectionListVSAssembly) {
         method = "addSelectionList";
         type = SelectionListVSAssembly.class;
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         method = "addSelectionTree";
         type = SelectionTreeVSAssembly.class;
      }
      else {
         method = "addCurrentSelection";
         type = CurrentSelectionVSAssembly.class;
      }

      Method m = VsToReportConverter.class.getDeclaredMethod(method, type, String.class);
      m.setAccessible(true);
      m.invoke(converter, assembly, section.getID());

      SectionBand band = section.getSection().getSectionContent()[0];
      assertEquals(1, band.getElementCount(), "exactly the selection's body box");
      return (TextBoxElement) band.getElement(0);
   }

   private final Viewsheet vs = new Viewsheet();
}
