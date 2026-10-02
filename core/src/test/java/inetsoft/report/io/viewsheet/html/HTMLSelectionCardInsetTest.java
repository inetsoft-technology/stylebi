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
package inetsoft.report.io.viewsheet.html;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
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
 * An HTML selection draws its rows inside the card inset: the scrolling row box is moved in by the
 * inset and shrunk to the content, and at a zero inset the box is written exactly as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLSelectionCardInsetTest {
   private static final Insets INSET = new Insets(16, 16, 16, 16);
   private static final Insets NONE = new Insets(0, 0, 0, 0);

   // a 132 x 202 card with no title: 132 - 32 wide, 202 - 32 tall
   @Test
   void aListsRowBoxSitsInsideTheInset() {
      assertTrue(writeList(INSET).contains(
         "<div style='position:relative;left:16px;top:16px;overflow:auto;width:100px;height:170px'>"));
   }

   // five 20px rows fit the 170 content, so no scrollbar: 100 - 2 of border
   @Test
   void aListsRowsTakeTheContentWidth() {
      assertTrue(writeList(INSET).contains("<div style='width:98.0px;"));
   }

   @Test
   void aListWithoutAnInsetWritesTheRowBoxAsBefore() {
      String html = writeList(NONE);

      assertTrue(html.contains("<div style='overflow:auto;width:100%;height:202'>"));
      assertTrue(html.contains("<div style='width:130.0px;"));
   }

   @Test
   void aTreesRowBoxSitsInsideTheInset() {
      assertTrue(writeTree(INSET).contains(
         "<div style='position:relative;left:16px;top:16px;overflow:auto;width:100px;height:170px'>"));
   }

   // no measure, so a label is the value width less 26 of checkbox and indent: 132 - 32 - 26
   @Test
   void aTreesLabelsTakeTheContentWidth() {
      assertTrue(writeTree(INSET).contains("<div style='width:74.0px;overflow:hidden"));
   }

   @Test
   void aTreeWithoutAnInsetWritesTheRowBoxAsBefore() {
      String html = writeTree(NONE);

      assertTrue(html.contains("<div style='overflow:auto;width:100%;height:202'>"));
      assertTrue(html.contains("<div style='width:106.0px;overflow:hidden"));
   }

   private static String writeList(Insets inset) {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      vs.addAssembly(list);
      configure((SelectionBaseVSAssemblyInfo) list.getVSAssemblyInfo(), inset);
      SelectionList values = new SelectionList();

      for(String label : new String[] { "Business", "Educational", "Games", "Graphics", "Hardware" }) {
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

   private static String writeTree(Insets inset) {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree1");
      vs.addAssembly(tree);
      configure((SelectionBaseVSAssemblyInfo) tree.getVSAssemblyInfo(), inset);
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

   private static void configure(SelectionBaseVSAssemblyInfo info, Insets inset) {
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(new Dimension(132, 202));
      info.setPadding(inset);
      info.setTitleVisibleValue(false);
   }

   private static HTMLCoordinateHelper coordinates(Viewsheet vs) {
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(vs);
      return helper;
   }
}
