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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import inetsoft.report.internal.Common;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #78672: a Text component's padding must reach the PowerPoint text box as insets (in
 * points, converted from pixels like the bounds), not be dropped in favor of the 1pt default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PPTTextPaddingTest {
   private static final double EMU = 12700;

   private XMLSlideShow show;
   private XSLFSlide slide;
   private PPTVSExporter exporter;
   private Viewsheet vs;

   @BeforeEach
   void setUp() throws Exception {
      show = new XMLSlideShow();
      slide = show.createSlide();
      PPTContext context = Mockito.mock(PPTContext.class);
      Mockito.when(context.getSlideShow()).thenReturn(show);
      exporter = new PPTVSExporter(context, new ByteArrayOutputStream());
      vs = new Viewsheet();
      PPTCoordinateHelper coordinator = new PPTCoordinateHelper();
      coordinator.setViewsheet(vs);
      set("slide", slide);
      set("coordinator", coordinator);
   }

   @Test
   void topPaddingBecomesTopInsetInPoints() throws Exception {
      assertInsets(new Insets(40, 0, 0, 0), 30, 0, 0, 0);
   }

   @Test
   void uniformPaddingBecomesInsetsInPoints() throws Exception {
      assertInsets(new Insets(10, 10, 10, 10), 7.5, 7.5, 7.5, 7.5);
   }

   @Test
   void noPaddingKeepsOnePointInsets() throws Exception {
      assertInsets(new Insets(0, 0, 0, 0), 1, 1, 1, 1);
   }

   private static final String SENTENCE =
      "The quick brown fox jumps over the lazy dog and keeps running across the wide open " +
      "field until the night falls over the quiet hills far away from the town";

   /** Padding reduces the width the lines are wrapped at, as well as the insets. */
   @Test
   void wrappedLinesFitPaddedWidth() throws Exception {
      Insets padding = new Insets(0, 40, 0, 40);
      XSLFTextBox box = write(SENTENCE, 300, 200, padding, true);
      double areaW = box.getAnchor().getWidth() - 60;
      double[] widths = lineWidths(box);
      assertTrue(widths.length > 1);

      for(double w : widths) {
         assertTrue(w <= areaW - 2 + 0.01, w + " exceeds the text area " + (areaW - 2));
      }

      // more padding wraps into more lines
      XSLFTextBox none = write(SENTENCE, 300, 200, new Insets(0, 0, 0, 1), true);
      assertTrue(lines(box).length > lines(none).length);
   }

   /** The lines written are cut off at the padded height, not the component height. */
   @Test
   void lineCountFitsPaddedHeight() throws Exception {
      String text = "one\ntwo\nthree\nfour\nfive\nsix";
      int unpadded = lines(write(text, 300, 200, new Insets(0, 0, 0, 1), true)).length;
      XSLFTextBox box = write(text, 300, 200, new Insets(60, 0, 60, 0), true);
      int padded = lines(box).length;
      double areaH = box.getAnchor().getHeight() - 90;
      double fontH = Common.getHeight(new Font("Dialog", Font.PLAIN, 10));

      assertTrue(unpadded > padded, "padded " + padded + " unpadded " + unpadded);
      assertTrue(padded >= 1);
      assertTrue((padded + 1) * fontH >= areaH && padded * fontH <= areaH + fontH,
                 padded + " lines in " + areaH);
   }

   /** Without wrapping, the text is truncated at the padded width. */
   @Test
   void noWrapTruncatesAtPaddedWidth() throws Exception {
      XSLFTextBox box = write(SENTENCE, 300, 200, new Insets(0, 40, 0, 40), false);
      double[] widths = lineWidths(box);
      assertEquals(1, widths.length);
      assertTrue(widths[0] <= box.getAnchor().getWidth() - 60 - 2 + 0.01, "" + widths[0]);
      XSLFTextBox none = write(SENTENCE, 300, 200, new Insets(0, 0, 0, 1), false);
      assertTrue(lineWidths(none)[0] > widths[0]);
   }

   /** Padding larger than the box still writes at least the first line. */
   @Test
   void paddingLargerThanBoxWritesFirstLine() throws Exception {
      XSLFTextBox box = write(SENTENCE, 100, 60, new Insets(80, 80, 80, 80), true);
      String[] lines = lines(box);
      assertTrue(lines.length >= 1);
      assertTrue(lines[0].length() > 0);
   }

   /** All-zero padding keeps the unpadded wrapping. */
   @Test
   void zeroPaddingWrapsAtFullWidth() throws Exception {
      XSLFTextBox box = write(SENTENCE, 300, 200, new Insets(0, 0, 0, 0), true);
      double[] widths = lineWidths(box);
      double areaW = box.getAnchor().getWidth() - 2;

      for(double w : widths) {
         assertTrue(w <= areaW + 0.01);
      }

      // the first line fills most of the full width, which padded text could not
      assertTrue(widths[0] > areaW - 40, "" + widths[0]);
   }

   /**
    * Table/selection cells write through PPTValueHelper directly with the padding in points
    * (scale 1); the cell text must wrap and be cut off inside the padded area as well.
    */
   @Test
   void tableCellWrapsInsidePaddedArea() throws Exception {
      String text = "Regional revenue summary for the quarter";
      XSLFTextBox none = writeCell(text, new Insets(0, 0, 0, 0));
      XSLFTextBox box = writeCell(text, new Insets(20, 30, 20, 30));
      double areaW = box.getAnchor().getWidth() - box.getLeftInset() - box.getRightInset();
      double areaH = box.getAnchor().getHeight() - box.getTopInset() - box.getBottomInset();
      double fontH = Common.getHeight(new Font("Dialog", Font.PLAIN, 10));

      for(double w : lineWidths(box)) {
         assertTrue(w <= areaW + 0.01, w + " exceeds the cell area " + areaW);
      }

      assertTrue(lines(box).length >= 1);
      assertTrue(lines(box).length < lines(none).length);
      assertTrue((lines(box).length - 1) * fontH < areaH, lines(box).length + " in " + areaH);
   }

   /** A word longer than the padded area is broken instead of overflowing the insets. */
   @Test
   void unbreakableWordFitsPaddedWidth() throws Exception {
      XSLFTextBox box = write("Supercalifragilisticexpialidocious_and_more_unbreakable next",
                              200, 100, new Insets(20, 20, 20, 20), true);
      double areaW = box.getAnchor().getWidth() - box.getLeftInset() - box.getRightInset();

      for(double w : lineWidths(box)) {
         assertTrue(w <= areaW + 0.01, w + " exceeds " + areaW);
      }
   }

   private XSLFTextBox writeCell(String text, Insets padding) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setFont(new Font("Dialog", Font.PLAIN, 12));
      fmt.getUserDefinedFormat().setWrapping(true);
      PPTValueHelper helper = new PPTValueHelper(slide);
      helper.setBounds(new java.awt.geom.Rectangle2D.Double(10, 10, 120, 60));
      helper.setFormat(fmt);
      helper.setPadding(padding);
      helper.setValue(text);
      helper.writeTextBox(true);
      java.util.List<XSLFShape> shapes = slide.getShapes();
      return (XSLFTextBox) shapes.get(shapes.size() - 1);
   }

   private XSLFTextBox write(String text, int w, int h, Insets padding, boolean wrap)
      throws Exception
   {
      TextVSAssembly assembly = new TextVSAssembly(vs, "Text" + vs.getAssemblies().length);
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPadding(padding);
      info.setPixelSize(new Dimension(w, h));
      info.getFormat().getUserDefinedFormat().setFont(new Font("Dialog", Font.PLAIN, 12));
      info.getFormat().getUserDefinedFormat().setWrapping(wrap);
      vs.addAssembly(assembly);
      int before = slide.getShapes().size();
      exporter.writeText(assembly, text);
      java.util.List<XSLFShape> shapes = slide.getShapes();
      assertEquals(before + 1, shapes.size());
      return (XSLFTextBox) shapes.get(shapes.size() - 1);
   }

   private String[] lines(XSLFTextBox box) {
      String text = box.getText();
      return text.split("[\\r\\n\\u000b]");
   }

   private double[] lineWidths(XSLFTextBox box) {
      XSLFTextRun run = box.getTextParagraphs().get(0).getTextRuns().get(0);
      Font font = new Font("Dialog", Font.PLAIN, (int) Math.round(run.getFontSize()));
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      String[] lines = lines(box);
      double[] widths = new double[lines.length];

      for(int i = 0; i < lines.length; i++) {
         widths[i] = fm.stringWidth(lines[i]);
      }

      return widths;
   }

   private void assertInsets(Insets padding, double top, double left, double bottom,
                             double right) throws Exception
   {
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      info.setPadding(padding);
      info.setPixelSize(new Dimension(300, 80));
      vs.addAssembly(text);

      exporter.writeText(text, "hello");

      String xml = slide.getXmlObject().toString();
      assertAttr(xml, "tIns", top);
      assertAttr(xml, "lIns", left);
      assertAttr(xml, "bIns", bottom);
      assertAttr(xml, "rIns", right);
   }

   private static void assertAttr(String xml, String name, double points) {
      assertEquals(true, xml.contains(name + "=\"" + Math.round(points * EMU) + "\""),
                   name + " should be " + points + "pt in " + xml);
   }

   private void set(String name, Object value) throws Exception {
      Field f = PPTVSExporter.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(exporter, value);
   }
}
