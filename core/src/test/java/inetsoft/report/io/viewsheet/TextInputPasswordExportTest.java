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

import inetsoft.report.LibManagerProvider;
import inetsoft.report.SectionBand;
import inetsoft.report.TabularSheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TextBoxElementDef;
import inetsoft.report.io.viewsheet.html.HTMLCoordinateHelper;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.io.viewsheet.pdf.PDFVSExporter;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VsToReportConverter;
import inetsoft.util.DataSpace;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79696: the value of a text input whose Input Editor is a password was drawn in clear text
 * by every server side exporter (the browser hides it client side, exporters have no such step).
 * The decision lives on {@link TextInputVSAssemblyInfo}; these tests check it and that each
 * exporter that renders a text input value goes through it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextInputPasswordExportTest {
   private static final String SECRET = "Zq7secretVXP014";
   private static final String VISIBLE = "Zq7visibleVXP014";
   private static final String MASK = "******";

   private static TextInputVSAssembly textInput(boolean password, String value) {
      TextInputVSAssembly assembly = new TextInputVSAssembly(new Viewsheet(), "TextInput1");
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setColumnOption(password ? new PasswordColumnOption() : new TextColumnOption());
      assembly.setSelectedObject(value);
      info.setPixelSize(new Dimension(100, 20));
      return assembly;
   }

   private static Object invoke(Object target, Class<?> declaring, String name, Class<?>[] types,
                                Object... args) throws Exception
   {
      Method method = declaring.getDeclaredMethod(name, types);
      method.setAccessible(true);

      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException e) {
         if(e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
         }

         throw e;
      }
   }

   private static void set(Object target, Class<?> declaring, String name, Object value)
      throws Exception
   {
      Field field = declaring.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   @Test
   void passwordOptionMasksNonEmptyText() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) textInput(true, SECRET)
         .getVSAssemblyInfo();

      assertTrue(info.isPassword());
      assertEquals(MASK, info.getExportText(SECRET));
      assertEquals(MASK, TextInputVSAssemblyInfo.PASSWORD_MASK);
   }

   @Test
   void passwordOptionKeepsEmptyAndNullEmpty() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) textInput(true, "")
         .getVSAssemblyInfo();

      assertEquals("", info.getExportText(""));
      assertNull(info.getExportText(null));
   }

   @Test
   void plainTextOptionKeepsText() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) textInput(false, VISIBLE)
         .getVSAssemblyInfo();

      assertFalse(info.isPassword());
      assertEquals(VISIBLE, info.getExportText(VISIBLE));
   }

   @Test
   void nullColumnOptionIsNotAPassword() {
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) textInput(false, VISIBLE)
         .getVSAssemblyInfo();
      info.setColumnOption(null);

      assertFalse(info.isPassword());
      assertEquals(VISIBLE, info.getExportText(VISIBLE));
   }

   private String htmlFor(TextInputVSAssembly assembly) throws Exception {
      StringWriter out = new StringWriter();
      HTMLCoordinateHelper helper = spy(new HTMLCoordinateHelper());
      doReturn(new Rectangle2D.Double(0, 0, 100, 20)).when(helper)
         .getBounds(any(VSAssemblyInfo.class));
      HTMLVSExporter exporter = mock(HTMLVSExporter.class, CALLS_REAL_METHODS);
      set(exporter, HTMLVSExporter.class, "writer", new PrintWriter(out));
      set(exporter, HTMLVSExporter.class, "helper", helper);

      invoke(exporter, HTMLVSExporter.class, "writeTextInput",
         new Class<?>[] { TextInputVSAssembly.class }, assembly);
      return out.toString();
   }

   @Test
   void htmlExportMasksPassword() throws Exception {
      String html = htmlFor(textInput(true, SECRET));

      assertFalse(html.contains(SECRET), html);
      assertTrue(html.contains(MASK), html);
   }

   @Test
   void htmlExportKeepsPlainValue() throws Exception {
      assertTrue(htmlFor(textInput(false, VISIBLE)).contains(VISIBLE));
   }

   private List<String> drawnText(Class<? extends AbstractVSExporter> type, TextInputVSAssembly a)
      throws Exception
   {
      List<String> texts = new ArrayList<>();
      Answer<Object> answer = inv -> {
         String name = inv.getMethod().getName();

         if("writeText".equals(name) && inv.getArguments().length == 2) {
            texts.add((String) inv.getArgument(1));
            return null;
         }

         if("writeTextInputWithLabel".equals(name)) {
            texts.add((String) inv.getArgument(1));
            return true;
         }

         return inv.callRealMethod();
      };

      AbstractVSExporter exporter = mock(type, withSettings().defaultAnswer(answer));
      invoke(exporter, type, "writeTextInput", new Class<?>[] { TextInputVSAssembly.class }, a);
      return texts;
   }

   @Test
   void svgAndPngExportMaskPassword() throws Exception {
      List<String> texts = drawnText(SVGVSExporter.class, textInput(true, SECRET));
      assertFalse(texts.isEmpty());
      assertFalse(texts.contains(SECRET), texts.toString());
      assertTrue(texts.contains(MASK), texts.toString());
   }

   @Test
   void svgExportKeepsPlainValue() throws Exception {
      assertTrue(drawnText(SVGVSExporter.class, textInput(false, VISIBLE)).contains(VISIBLE));
   }

   @Test
   void pdfExportMasksPassword() throws Exception {
      List<String> texts = drawnText(PDFVSExporter.class, textInput(true, SECRET));
      assertFalse(texts.isEmpty());
      assertFalse(texts.contains(SECRET), texts.toString());
      assertTrue(texts.contains(MASK), texts.toString());
   }

   @Test
   void pdfExportKeepsPlainValue() throws Exception {
      assertTrue(drawnText(PDFVSExporter.class, textInput(false, VISIBLE)).contains(VISIBLE));
   }

   private List<String> printLayoutTexts(TextInputVSAssembly assembly) throws Exception {
      VsToReportConverter converter = new VsToReportConverter(
         mock(ViewsheetSandbox.class), mock(LibManagerProvider.class), mock(Cluster.class),
         mock(FileSystemService.class), mock(DataSpace.class));
      SectionElementDef section = (SectionElementDef) invoke(
         converter, VsToReportConverter.class, "createReportSection", new Class<?>[0]);
      section.setID("S1");
      Field reportField = VsToReportConverter.class.getDeclaredField("report");
      reportField.setAccessible(true);
      ((TabularSheet) reportField.get(converter)).addElement(0, 0, section);
      assembly.getVSAssemblyInfo().setLayoutPosition(new Point(0, 0));
      assembly.getVSAssemblyInfo().setLayoutSize(new Dimension(100, 20));

      invoke(converter, VsToReportConverter.class, "convertVSAssembly",
         new Class<?>[] { VSAssembly.class, String.class, boolean.class }, assembly, "S1", false);

      List<String> texts = new ArrayList<>();
      SectionBand band = section.getSection().getSectionContent()[0];

      for(int i = 0; i < band.getElementCount(); i++) {
         if(band.getElement(i) instanceof TextBoxElementDef) {
            texts.add(((TextBoxElementDef) band.getElement(i)).getText());
         }
      }

      return texts;
   }

   @Test
   void printLayoutConversionMasksPassword() throws Exception {
      List<String> texts = printLayoutTexts(textInput(true, SECRET));
      assertFalse(texts.contains(SECRET), texts.toString());
      assertTrue(texts.contains(MASK), texts.toString());
   }

   @Test
   void printLayoutConversionKeepsPlainValue() throws Exception {
      assertTrue(printLayoutTexts(textInput(false, VISIBLE)).contains(VISIBLE));
   }
}
