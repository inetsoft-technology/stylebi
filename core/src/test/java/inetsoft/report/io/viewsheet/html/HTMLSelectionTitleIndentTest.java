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
package inetsoft.report.io.viewsheet.html;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern HTML selection's title span starts over its rows; a legacy one is written as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLSelectionTitleIndentTest {
   private static final String INDENTED =
      "<span style='display:block;padding-left:22px;padding-right:0px;padding-top:0px;" +
      "padding-bottom:0px;'>";

   @Test
   void aMarkedListsTitleSpanStartsOverItsRows() {
      assertTrue(writeList(VizMark.MODERN_LIGHT).contains(INDENTED));
   }

   @Test
   void aMarkedTreesTitleSpanStartsOverItsRows() {
      assertTrue(writeTree(VizMark.MODERN_LIGHT).contains(INDENTED));
   }

   @Test
   void anUnmarkedTitleSpanIsUnchanged() {
      String html = writeList(null);

      assertTrue(html.contains("<span style='display:block;'>"));
      assertFalse(html.contains("padding-left:22px"));
   }

   private static String writeList(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      vs.addAssembly(list);
      configure((SelectionBaseVSAssemblyInfo) list.getVSAssemblyInfo(), mark);
      SelectionList values = new SelectionList();

      for(String label : new String[] { "Business", "Educational", "Games" }) {
         SelectionValue value = new SelectionValue(label, label);
         value.setFormat(new VSCompositeFormat());
         values.addSelectionValue(value);
      }

      list.getSelectionListInfo().setSelectionList(values);
      HTMLSelectionListHelper helper = new HTMLSelectionListHelper(coordinates(vs), vs, list);
      helper.setExporter(new HTMLVSExporter(new ByteArrayOutputStream()));
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, list);
      writer.flush();
      return out.toString();
   }

   private static String writeTree(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree1");
      vs.addAssembly(tree);
      configure((SelectionBaseVSAssemblyInfo) tree.getVSAssemblyInfo(), mark);
      SelectionList quarters = new SelectionList();

      for(String label : new String[] { "Q1-LY", "Q2-LY" }) {
         CompositeSelectionValue value = new CompositeSelectionValue(label, label);
         value.setFormat(new VSCompositeFormat());
         quarters.addSelectionValue(value);
      }

      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setSelectionList(quarters);
      tree.getSelectionTreeInfo().setCompositeSelectionValue(root);
      HTMLSelectionTreeHelper helper = new HTMLSelectionTreeHelper(coordinates(vs), vs, tree);
      helper.setExporter(new HTMLVSExporter(new ByteArrayOutputStream()));
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, tree);
      writer.flush();
      return out.toString();
   }

   // a 132 x 202 card at a 16px inset, titled
   private static void configure(SelectionBaseVSAssemblyInfo info, VizMark mark) {
      info.setVizMark(mark);
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(new Dimension(132, 202));
      info.setPadding(new Insets(16, 16, 16, 16));
      info.setTitleVisibleValue(true);
      info.setTitleValue("Region");
   }

   private static HTMLCoordinateHelper coordinates(Viewsheet vs) {
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(vs);
      return helper;
   }
}
