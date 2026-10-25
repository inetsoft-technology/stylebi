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

import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When a selection's title text starts over its rows in export and print: marked, standalone,
 * left-aligned, and without a format.css title padding of its own.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionTitleOverRowsTest {
   @Test
   void aMarkedStandaloneListTakesIt() {
      assertTrue(markedList().isTitleOverRows());
   }

   @Test
   void aMarkedTreeTakesIt() {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree1");
      vs.addAssembly(tree);
      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) tree.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertTrue(info.isTitleOverRows());
   }

   @Test
   void anUnmarkedListKeepsItsTitle() {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List1");
      vs.addAssembly(list);
      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) list.getVSAssemblyInfo();
      // a new assembly inherits its sheet's mark, which follows the gate
      info.setVizMark(null);
      info.setPadding(new Insets(16, 16, 16, 16));

      assertFalse(info.isTitleOverRows(), "an inset alone does not make a list modern");
   }

   @Test
   void aCssTitlePaddingKeepsItsOwn() {
      SelectionBaseVSAssemblyInfo info = markedList();
      info.setTitlePadding(new Insets(0, 9, 0, 0), CompositeValue.Type.CSS);

      assertFalse(info.isTitleOverRows());
   }

   @Test
   void aCenteredOrRightTitleKeepsItsPlace() {
      SelectionBaseVSAssemblyInfo info = markedList();

      titleFormat(info).setAlignment(StyleConstants.H_CENTER);
      assertFalse(info.isTitleOverRows(), "centered");

      titleFormat(info).setAlignment(StyleConstants.H_RIGHT);
      assertFalse(info.isTitleOverRows(), "right");
   }

   @Test
   void aContainerChildKeepsItsTitle() {
      SelectionBaseVSAssemblyInfo info = markedList();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.setAssemblies(new String[] { "List1" });
      vs.addAssembly(container);

      assertFalse(info.isTitleOverRows());
   }

   private SelectionBaseVSAssemblyInfo markedList() {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List1");
      vs.addAssembly(list);
      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) list.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPadding(new Insets(16, 16, 16, 16));
      return info;
   }

   /** The list's own title format, created if it has none yet. */
   private static VSFormat titleFormat(SelectionBaseVSAssemblyInfo info) {
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = finfo.getFormat(VSAssemblyInfo.TITLEPATH);

      if(format == null) {
         format = new VSCompositeFormat();
         finfo.setFormat(VSAssemblyInfo.TITLEPATH, format);
      }

      return format.getUserDefinedFormat();
   }

   private final Viewsheet vs = new Viewsheet();
}
