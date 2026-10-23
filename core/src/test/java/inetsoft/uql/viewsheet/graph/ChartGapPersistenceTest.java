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
package inetsoft.uql.viewsheet.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartGapPersistenceTest {
   @Test
   void anAuthorZeroSurvivesSaveAndLoad() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(0);
      TitleDescriptor title = new TitleDescriptor();
      title.setLabelGap(0);
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(0);

      assertTrue(parse(new AxisDescriptor(), xml(axis)).hasLabelGapUserValue());
      assertTrue(parse(new TitleDescriptor(), xml(title)).hasLabelGapUserValue());
      assertTrue(parse(new LegendsDescriptor(), xml(legends)).hasGapUserValue());
   }

   @Test
   void aStoredZeroWithoutTheMarkerLoadsAsNoValue() throws Exception {
      // the shape older builds wrote: a bare labelGap="0"
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(0);
      TitleDescriptor title = new TitleDescriptor();
      title.setLabelGap(0);
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(0);

      AxisDescriptor axis2 = parse(new AxisDescriptor(), withoutMarker(xml(axis), "labelGapZero"));
      TitleDescriptor title2 = parse(new TitleDescriptor(), withoutMarker(xml(title), "labelGapZero"));
      LegendsDescriptor legends2 = parse(new LegendsDescriptor(), withoutMarker(xml(legends), "gapZero"));

      assertFalse(axis2.hasLabelGapValue());
      assertFalse(title2.hasLabelGapValue());
      assertFalse(legends2.hasGapValue());
   }

   @Test
   void aChartDescriptorRestoreDropsLeftoverZeros() throws Exception {
      // bookmark restore parses a whole ChartDescriptor (ChartVSAssembly.parseStateContent)
      ChartDescriptor desc = new ChartDescriptor();
      desc.getTitlesDescriptor().getXTitleDescriptor().setLabelGap(0);
      desc.getLegendsDescriptor().setGap(0);
      String old = withoutMarker(withoutMarker(xml(desc), "labelGapZero"), "gapZero");

      ChartDescriptor restored = parse(new ChartDescriptor(), old);

      assertFalse(restored.getTitlesDescriptor().getXTitleDescriptor().hasLabelGapValue());
      assertFalse(restored.getLegendsDescriptor().hasGapValue());
   }

   @Test
   void aNonZeroAuthorValueNeedsNoMarker() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(5);
      String written = xml(axis);

      assertFalse(written.contains("labelGapZero"));
      assertEquals(5, parse(new AxisDescriptor(), written).getLabelGap());
   }

   @Test
   void anOpinionIsAnAuthorValueOrANonZeroStylesheetValue() {
      TitleDescriptor title = new TitleDescriptor();
      assertFalse(title.hasLabelGapValue());

      title.setLabelGap(0, CompositeValue.Type.CSS);
      assertFalse(title.hasLabelGapValue(), "a stylesheet zero cannot be told from unset");

      title.setLabelGap(20, CompositeValue.Type.CSS);
      assertTrue(title.hasLabelGapValue());
      assertFalse(title.hasLabelGapUserValue());

      title.setLabelGap(0);
      assertTrue(title.hasLabelGapValue());
      assertTrue(title.hasLabelGapUserValue());
   }

   @Test
   void resetLabelGapClearsOnlyTheGap() {
      TitleDescriptor title = new TitleDescriptor();
      title.setVisible(false);
      title.setLabelGap(20, CompositeValue.Type.CSS);
      title.setLabelGap(6);

      title.resetLabelGap(CompositeValue.Type.USER);

      assertEquals(20, title.getLabelGap(), "the stylesheet value shows through");
      assertFalse(title.isVisible(), "other author values survive");

      AxisDescriptor axis = new AxisDescriptor();
      axis.getAxisLabelTextFormat().getUserDefinedFormat().setRotation(45.0);
      axis.setLabelGap(6);
      axis.resetLabelGap(CompositeValue.Type.USER);

      assertFalse(axis.hasLabelGapUserValue());
      assertEquals(45.0, axis.getAxisLabelTextFormat().getUserDefinedFormat().getRotation());
   }

   @Test
   void aCloneDoesNotShareTheAxisLabelGap() {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(5);
      AxisDescriptor copy = axis.clone();

      copy.setLabelGap(9);

      assertEquals(5, axis.getLabelGap());
   }

   @Test
   void aLegendPaddingOpinionIsAnAuthorOrStylesheetValue() {
      LegendsDescriptor legends = new LegendsDescriptor();
      assertFalse(legends.hasPaddingValue());
      assertFalse(legends.hasPaddingUserValue());

      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
      assertTrue(legends.hasPaddingValue());
      assertFalse(legends.hasPaddingUserValue());

      legends.setPadding(new Insets(0, 4, 1, 4));
      assertTrue(legends.hasPaddingUserValue());
   }

   @Test
   void resetPaddingHandsBackTheStylesheetValue() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(20);
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
      legends.setPadding(new Insets(3, 3, 3, 3));

      legends.resetPadding(CompositeValue.Type.USER);

      assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
      assertFalse(legends.hasPaddingUserValue());
      assertEquals(20, legends.getGap(), "other author values survive");
   }

   @Test
   void anAuthorLegendPaddingSurvivesSaveLoadAndClone() throws Exception {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(1, 6, 2, 6));

      LegendsDescriptor loaded = parse(new LegendsDescriptor(), xml(legends));
      assertTrue(loaded.hasPaddingUserValue());
      assertEquals(new Insets(1, 6, 2, 6), loaded.getPadding());

      LegendsDescriptor copy = (LegendsDescriptor) legends.clone();
      copy.setPadding(new Insets(9, 9, 9, 9));
      assertEquals(new Insets(1, 6, 2, 6), legends.getPadding());
   }

   @Test
   void aLegendWithoutPaddingLoadsWithoutOne() throws Exception {
      assertFalse(parse(new LegendsDescriptor(), xml(new LegendsDescriptor())).hasPaddingValue());
   }

   private static String xml(XMLSerializable obj) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      obj.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static <T extends XMLSerializable> T parse(T target, String xml) throws Exception {
      target.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return target;
   }

   private static String withoutMarker(String xml, String marker) {
      String stripped = xml.replaceAll("\\s+" + marker + "=\"true\"", "");
      assertNotEquals(xml, stripped, "the author zero was saved with its marker");
      return stripped;
   }
}
