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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.DefaultVSGauge;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.invocation.Invocation;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78009: the Gauge Properties "Gradient" blend color (range color slot 5) must reach the
 * rendered bands through the whole path the user takes: the property dialog GET/SET, save and
 * reload (writeXML/parseXML), sandbox evaluation of the design values, and the real renderer
 * for every gauge face. A blend color that is a variable ($(var)) is evaluated by the sandbox
 * and used the same way by the gauge, cylinder, thermometer and sliding scale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GaugeRangeGradientBlendPathTest {
   private static final Color G = new Color(0x00aa00);
   private static final Color Y = new Color(0xffcc00);
   private static final Color R = new Color(0xdd0000);
   private static final Color BLEND = new Color(0x0000ff);

   @Test
   void dialogBlendColorEndsLastBandOnEveryGaugeFace() throws Exception {
      int faces = 0;

      for(String id : VSGauge.getPrefixIDs()) {
         int face = Integer.parseInt(id);

         if(!(VSGauge.getGauge(face) instanceof DefaultVSGauge)) {
            continue; // the bullet graph has no gradient
         }

         faces++;
         // rows 1, 2 and 4 used, row 3 empty, blend set
         GaugeVSAssemblyInfo info = saveThroughDialog(face, true,
            new String[] { "5", "15", "", "20", "" },
            new String[] { hex(G), hex(Y), null, hex(R), null, hex(BLEND) });
         List<String> bands = render(newGauge(info), false);

         assertTrue(bands.contains(band(G, Y)), face + " " + bands);
         assertTrue(bands.contains(band(Y, R)), face + " " + bands);
         assertTrue(bands.contains(band(R, BLEND)), face + " " + bands);
      }

      assertTrue(faces > 5, "gauge faces found: " + faces);
   }

   @Test
   void dialogBlendColorIsIgnoredWhenGradientIsOff() throws Exception {
      GaugeVSAssemblyInfo info = saveThroughDialog(10010, false,
         new String[] { "5", "15", "20", "", "" },
         new String[] { hex(G), hex(Y), hex(R), null, null, hex(BLEND) });

      assertEquals(List.of(), render(newGauge(info), false));
   }

   @Test
   void variableBlendColorIsEvaluatedAndUsedByEveryRendererType() throws Exception {
      Color magenta = new Color(0xff00ff);
      String[] kinds = { "gauge", "cylinder", "thermometer", "slidingscale" };

      for(String kind : kinds) {
         Viewsheet vs = new Viewsheet();
         TextInputVSAssembly input = new TextInputVSAssembly(vs, "c");
         input.setSelectedObject("" + (magenta.getRGB() & 0xffffff));
         vs.addAssembly(input);

         VSAssembly design = create(kind, vs);
         RangeOutputVSAssemblyInfo info = (RangeOutputVSAssemblyInfo) design.getVSAssemblyInfo();
         info.setMinValue("0");
         info.setMaxValue("25");
         info.setRangeValues(new String[] { "5", "15", "20", "", "" });
         info.setRangeColorsValue(new Color[] { G, Y, R, null, null, BLEND });
         info.setRangeGradientValue(true);

         // the blend slot holds $(c) after save and reload
         RangeOutputVSAssemblyInfo reloaded = reload(info, BLEND.getRGB() + "", "$(c)");
         VSAssembly assembly = create(kind, vs);
         assembly.setVSAssemblyInfo(reloaded);
         vs.addAssembly(assembly);
         evaluate(vs, reloaded, assembly.getName());

         List<String> bands = render(renderer(kind, reloaded), !"slidingscale".equals(kind) &&
            !"gauge".equals(kind));

         assertTrue(bands.contains(band(R, magenta)), kind + " " + bands);
      }
   }

   // ---- the user's path ----

   private static GaugeVSAssemblyInfo saveThroughDialog(int face, boolean gradient,
                                                        String[] ranges, String[] colors)
      throws Exception
   {
      Viewsheet viewsheet = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(viewsheet, "Gauge1");
      viewsheet.addAssembly(gauge);
      GaugePropertyDialogService service = dialogService(viewsheet);

      GaugePropertyDialogModel model =
         service.getGaugePropertyDialogModel("Viewsheet1", "Gauge1", null);
      model.getGaugeGeneralPaneModel().getFacePaneModel().setFace(face);
      NumberRangePaneModel numberRange =
         model.getGaugeGeneralPaneModel().getNumberRangePaneModel();
      numberRange.setMin("0");
      numberRange.setMax("25");
      numberRange.setMajorIncrement("5");
      RangePaneModel range = model.getGaugeAdvancedPaneModel().getRangePaneModel();
      range.setRangeValues(ranges);
      range.setRangeColorValues(colors);
      range.setGradient(gradient);
      service.setGaugePropertyDialogModel("Viewsheet1", "Gauge1", model, "", null,
                                          mock(CommandDispatcher.class));

      // the blend color loads back into the dialog
      GaugePropertyDialogModel reopened =
         service.getGaugePropertyDialogModel("Viewsheet1", "Gauge1", null);
      assertEquals(colors[5],
         reopened.getGaugeAdvancedPaneModel().getRangePaneModel().getRangeColorValues()[5]);

      GaugeVSAssemblyInfo reloaded =
         (GaugeVSAssemblyInfo) reload((RangeOutputVSAssemblyInfo) gauge.getVSAssemblyInfo(),
                                      null, null);
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly assembly = new GaugeVSAssembly(vs, "Gauge1");
      assembly.setVSAssemblyInfo(reloaded);
      vs.addAssembly(assembly);
      evaluate(vs, reloaded, "Gauge1");
      return reloaded;
   }

   private static GaugePropertyDialogService dialogService(Viewsheet viewsheet) throws Exception {
      VSObjectPropertyService propertyService = new VSObjectPropertyService(
         mock(CoreLifecycleService.class), null, null, null, null, null, null, null,
         mock(QueryManagerService.class));
      ViewsheetService engine = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      VSDialogService dialogService = mock(VSDialogService.class);
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(300, 300));
      return new GaugePropertyDialogService(propertyService, mock(VSOutputService.class),
         dialogService, engine, mock(VSTrapService.class), mock(VSAssemblyInfoHandler.class),
         mock(QueryManagerService.class));
   }

   /** writeXML/parseXML, optionally replacing one design value in the saved XML. */
   private static RangeOutputVSAssemblyInfo reload(RangeOutputVSAssemblyInfo info,
                                                   String from, String to) throws Exception
   {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      info.writeXML(pw);
      pw.flush();
      String xml = sw.toString();

      if(from != null) {
         String saved = "[CDATA[" + from + "]]";
         assertTrue(xml.contains(saved), xml);
         xml = xml.replace(saved, "[CDATA[" + to + "]]");
      }

      Element elem = Tool.getFirstElement(Tool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8"));
      RangeOutputVSAssemblyInfo copy = info.getClass().getConstructor().newInstance();
      copy.parseXML(elem);
      return copy;
   }

   /** Evaluate every view design value the way the sandbox does at runtime. */
   private static void evaluate(Viewsheet vs, RangeOutputVSAssemblyInfo info, String name)
      throws Exception
   {
      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      Method execute = ViewsheetSandbox.class.getDeclaredMethod(
         "executeDynamicValue", DynamicValue.class, VSAScriptable.class, String.class,
         Supplier.class);
      execute.setAccessible(true);

      for(DynamicValue value : info.getViewDynamicValues(true)) {
         if(value != null) {
            execute.invoke(box, value, null, name, null);
         }
      }
   }

   // ---- rendering ----

   private static VSAssembly create(String kind, Viewsheet vs) {
      switch(kind) {
      case "gauge":
         return new GaugeVSAssembly(vs, "A1");
      case "cylinder":
         return new CylinderVSAssembly(vs, "A1");
      case "thermometer":
         return new ThermometerVSAssembly(vs, "A1");
      default:
         return new SlidingScaleVSAssembly(vs, "A1");
      }
   }

   private static VSImageable newGauge(GaugeVSAssemblyInfo info) {
      return renderer("gauge", info);
   }

   private static VSImageable renderer(String kind, RangeOutputVSAssemblyInfo info) {
      int face = info.getFace();
      VSImageable renderer;

      switch(kind) {
      case "gauge":
         renderer = VSGauge.getGauge(face);
         break;
      case "cylinder":
         renderer = VSCylinder.getCylinder(face);
         break;
      case "thermometer":
         renderer = VSThermometer.getThermometer(face);
         break;
      default:
         renderer = VSSlidingScale.getSlidingScale(face);
      }

      assertNotNull(renderer, kind + " " + face);
      renderer.setPixelSize(new Dimension(300, 300));
      renderer.setAssemblyInfo(info);
      return renderer;
   }

   /**
    * Fills the range bands on a recording Graphics2D and returns each gradient band as
    * "start->end".
    */
   private static List<String> render(VSImageable renderer, boolean endFirst) throws Exception {
      if(renderer instanceof DefaultVSGauge) {
         // the gauge's arc geometry (center/radius) comes from adjust()
         findMethod(renderer.getClass(), "adjust").invoke(renderer);
      }

      BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D real = img.createGraphics();
      Graphics2D g = mock(Graphics2D.class, AdditionalAnswers.delegatesTo(real));

      try {
         findMethod(renderer.getClass(), "fillRanges", Graphics2D.class).invoke(renderer, g);
      }
      finally {
         real.dispose();
      }

      List<String> bands = new ArrayList<>();

      for(Invocation invocation : mockingDetails(g).getInvocations()) {
         if(!"setPaint".equals(invocation.getMethod().getName())) {
            continue;
         }

         Object paint = invocation.getArgument(0);
         Color first;
         Color last;

         if(paint instanceof GradientPaint) {
            first = ((GradientPaint) paint).getColor1();
            last = ((GradientPaint) paint).getColor2();
         }
         else if(paint instanceof MultipleGradientPaint) {
            Color[] colors = ((MultipleGradientPaint) paint).getColors();
            first = colors[0];
            last = colors[colors.length - 1];
         }
         else {
            continue;
         }

         bands.add(endFirst ? band(last, first) : band(first, last));
      }

      return bands;
   }

   private static Method findMethod(Class<?> cls, String name, Class<?>... params)
      throws NoSuchMethodException
   {
      for(Class<?> c = cls; c != null; c = c.getSuperclass()) {
         try {
            Method method = c.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
         }
         catch(NoSuchMethodException ignore) {
            // look in the superclass
         }
      }

      throw new NoSuchMethodException(cls.getName() + "." + name);
   }

   private static String band(Color start, Color end) {
      return hex(start) + "->" + hex(end);
   }

   private static String hex(Color c) {
      return c == null ? null : String.format("#%06x", c.getRGB() & 0xffffff);
   }
}
