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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * Bug #78197: Oval/Line/Rectangle dialog read-model -> unchanged write-model must round trip
 * whatever color string is stored, and a garbage Static color must be refused by field name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ShapePropertyDialogRoundTripTest {
   @BeforeEach
   void setup() throws Exception {
      propertyService = mock(VSObjectPropertyService.class);
      dialogService = mock(VSDialogService.class);
      viewsheetService = mock(ViewsheetService.class);
      rvs = mock(RuntimeViewsheet.class);
      vs = mock(Viewsheet.class);
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      when(vs.getAssemblies(anyBoolean())).thenReturn(new Assembly[0]);
      when(dialogService.getAssemblyPosition(any(VSAssemblyInfo.class), any(Viewsheet.class)))
         .thenReturn(new Point(0, 0));
      when(dialogService.getAssemblySize(any(VSAssemblyInfo.class), any(Viewsheet.class)))
         .thenReturn(new Dimension(10, 10));
   }

   private VSFormat userFormat(VSAssembly assembly) {
      return assembly.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
   }

   private void oval(String fg, String bg) {
      OvalVSAssembly a = new OvalVSAssembly(vs, "Oval1");
      userFormat(a).setForegroundValue(fg);
      userFormat(a).setBackgroundValue(bg);
      when(vs.getAssembly("Oval1")).thenReturn(a);
   }

   private OvalPropertyDialogService ovalService() {
      return new OvalPropertyDialogService(propertyService, dialogService, viewsheetService);
   }

   private VSFormat capturedFormat() throws Exception {
      ArgumentCaptor<VSAssemblyInfo> c = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(propertyService).editObjectProperty(
         any(), c.capture(), anyString(), nullable(String.class), nullable(String.class),
         nullable(Principal.class), nullable(CommandDispatcher.class), anyBoolean(),
         nullable(Integer.class));
      return c.getValue().getFormat().getUserDefinedFormat();
   }

   @Test
   void ovalRoundTrip() throws Exception {
      oval("notacolor", "red");
      OvalPropertyDialogService s = ovalService();
      OvalPropertyDialogModel m = s.getOvalPropertyDialogModel("rt", "Oval1", null);
      s.setOvalPropertyDialogModel("rt", "Oval1", m, null, null, null);
      VSFormat f = capturedFormat();
      assertEquals("", f.getForegroundValue());
      assertEquals("16711680", f.getBackgroundValue());
   }

   @Test
   void lineRoundTrip() throws Exception {
      String[][] cases = { { "red", "16711680" }, { null, "" }, { "", "" },
                           { "#00ff00", "65280" } };

      for(String[] c : cases) {
         reset(propertyService);
         LineVSAssembly a = new LineVSAssembly(vs, "Line1");
         userFormat(a).setForegroundValue(c[0]);
         when(vs.getAssembly("Line1")).thenReturn(a);
         LinePropertyDialogService s =
            new LinePropertyDialogService(propertyService, dialogService, viewsheetService);
         LinePropertyDialogModel m = s.getLinePropertyDialogModel("rt", "Line1", null);
         s.setLinePropertyDialogModel("rt", "Line1", m, null, null, null);
         assertEquals(c[1], capturedFormat().getForegroundValue(), "stored " + c[0]);
      }
   }

   @Test
   void rectangleRoundTrip() throws Exception {
      RectangleVSAssembly a = new RectangleVSAssembly(vs, "Rect1");
      userFormat(a).setForegroundValue("notacolor");
      userFormat(a).setBackgroundValue("#FF0000");
      when(vs.getAssembly("Rect1")).thenReturn(a);
      RectanglePropertyDialogService s =
         new RectanglePropertyDialogService(propertyService, dialogService, viewsheetService);
      RectanglePropertyDialogModel m = s.getRectanglePropertyDialogModel("rt", "Rect1", null);
      s.setRectanglePropertyDialogModel("rt", "Rect1", m, null, null, null);
      VSFormat f = capturedFormat();
      assertEquals("", f.getForegroundValue());
      assertEquals("16711680", f.getBackgroundValue());
   }

   @Test
   void garbageStaticFillRefused() throws Exception {
      oval("#ff0000", "#00ff00");
      OvalPropertyDialogService s = ovalService();
      OvalPropertyDialogModel m = s.getOvalPropertyDialogModel("rt", "Oval1", null);
      FillPropPaneModel fill = m.getOvalPropertyPaneModel().getFillPropPaneModel();
      fill.setColor("Static");
      fill.setColorValue("notacolor");
      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> s.setOvalPropertyDialogModel("rt", "Oval1", m, null, null, null));
      assertTrue(ex.getMessage().contains("fillPropPaneModel.colorValue"));
      verify(propertyService, never()).editObjectProperty(
         any(), any(), anyString(), nullable(String.class), nullable(String.class),
         nullable(Principal.class), nullable(CommandDispatcher.class), anyBoolean(),
         nullable(Integer.class));
   }

   @Test
   void dynamicPassthrough() throws Exception {
      oval("=expr", "$(x)");
      OvalPropertyDialogService s = ovalService();
      OvalPropertyDialogModel m = s.getOvalPropertyDialogModel("rt", "Oval1", null);
      LinePropPaneModel line = m.getOvalPropertyPaneModel().getLinePropPaneModel();
      assertEquals("=expr", line.getColor());
      assertNull(line.getColorValue());
      s.setOvalPropertyDialogModel("rt", "Oval1", m, null, null, null);
      VSFormat f = capturedFormat();
      assertEquals("=expr", f.getForegroundValue());
      assertEquals("$(x)", f.getBackgroundValue());
   }

   // Bug #78471: the shape shadow color has no dynamic form and only #rrggbb draws the same in
   // every renderer, so the dialog write is strict and the dialog read maps any stored value.
   private void shadowShape(boolean oval, String storedShadowColor) {
      ShapeVSAssembly a = oval ? new OvalVSAssembly(vs, "Shape1")
         : new RectangleVSAssembly(vs, "Shape1");
      ((ShapeVSAssemblyInfo) a.getVSAssemblyInfo()).getShadowInfo().setColor(storedShadowColor);
      when(vs.getAssembly("Shape1")).thenReturn(a);
   }

   private ShadowPropPaneModel shadowModel(boolean oval, Object dialogModel) {
      return oval
         ? ((OvalPropertyDialogModel) dialogModel).getOvalPropertyPaneModel().getShadowPropPaneModel()
         : ((RectanglePropertyDialogModel) dialogModel).getRectanglePropertyPaneModel()
            .getShadowPropPaneModel();
   }

   private RectanglePropertyDialogService rectangleService() {
      return new RectanglePropertyDialogService(propertyService, dialogService, viewsheetService);
   }

   private Object getShapeModel(boolean oval) throws Exception {
      return oval
         ? ovalService().getOvalPropertyDialogModel("rt", "Shape1", null)
         : rectangleService().getRectanglePropertyDialogModel("rt", "Shape1", null);
   }

   private void setShapeModel(boolean oval, Object dialogModel) throws Exception {
      if(oval) {
         ovalService().setOvalPropertyDialogModel(
            "rt", "Shape1", (OvalPropertyDialogModel) dialogModel, null, null, null);
      }
      else {
         rectangleService().setRectanglePropertyDialogModel(
            "rt", "Shape1", (RectanglePropertyDialogModel) dialogModel, null, null, null);
      }
   }

   private String capturedShadowColor() throws Exception {
      ArgumentCaptor<VSAssemblyInfo> c = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(propertyService).editObjectProperty(
         any(), c.capture(), anyString(), nullable(String.class), nullable(String.class),
         nullable(Principal.class), nullable(CommandDispatcher.class), anyBoolean(),
         nullable(Integer.class));
      return ((ShapeVSAssemblyInfo) c.getValue()).getShadowInfo().getColor();
   }

   @Test
   void shadowColorStrictWriteNormalizesToHex() throws Exception {
      String[][] cases = { { "#FF0000", "#ff0000" }, { "#F00", "#ff0000" },
                           { "0xff0000", "#ff0000" }, { "16711680", "#ff0000" },
                           { " #00ff00 ", "#00ff00" }, { "0", "#000000" },
                           { "", "#000000" }, { null, "#000000" } };

      for(boolean oval : new boolean[] { true, false }) {
         for(String[] c : cases) {
            reset(propertyService);
            shadowShape(oval, "#123456");
            Object m = getShapeModel(oval);
            shadowModel(oval, m).setColor(c[0]);
            setShapeModel(oval, m);
            assertEquals(c[1], capturedShadowColor(), (oval ? "oval " : "rectangle ") + c[0]);
         }
      }
   }

   @Test
   void shadowColorStrictWriteRefusesNonHex() throws Exception {
      String[] bad = { "red", "notacolor", "rgba(1,2,3,0.5)", "#ff000080", "$(X)", "=1",
                       "16777216", "-1", "  " };

      for(boolean oval : new boolean[] { true, false }) {
         for(String value : bad) {
            reset(propertyService);
            shadowShape(oval, "#123456");
            Object m = getShapeModel(oval);
            shadowModel(oval, m).setColor(value);
            IllegalArgumentException ex = assertThrows(
               IllegalArgumentException.class, () -> setShapeModel(oval, m), value);
            assertTrue(ex.getMessage().contains("shadowPropPaneModel.color"), ex.getMessage());
            verify(propertyService, never()).editObjectProperty(
               any(), any(), anyString(), nullable(String.class), nullable(String.class),
               nullable(Principal.class), nullable(CommandDispatcher.class), anyBoolean(),
               nullable(Integer.class));
         }
      }
   }

   @Test
   void shadowColorLenientReadRoundTrips() throws Exception {
      // stored the way script, import or an older wiz write stores it (ShapeShadow.setColor)
      String[][] cases = { { "red", "#ff0000" }, { "#ff000080", "#ff0000" },
                           { "#F00", "#ff0000" }, { "16711680", "#ff0000" },
                           { "rgba(255,0,0,0.5)", "#ff0000" }, { "RGB( 0 , 0 , 255 )", "#0000ff" },
                           { "0xFF0000", "#ff0000" }, { " #00FF00 ", "#00ff00" },
                           { "notacolor", "#000000" }, { "$(X)", "#000000" },
                           { "rgb(300,0,0)", "#000000" } };

      for(boolean oval : new boolean[] { true, false }) {
         for(String[] c : cases) {
            reset(propertyService);
            shadowShape(oval, c[0]);
            Object m = getShapeModel(oval);
            String label = (oval ? "oval " : "rectangle ") + c[0];
            assertEquals(c[1], shadowModel(oval, m).getColor(), label);

            // an otherwise unchanged model (e.g. a shadowApply-only wiz write) writes the mapped value
            shadowModel(oval, m).setApply(true);
            setShapeModel(oval, m);
            assertEquals(c[1], capturedShadowColor(), label);
         }
      }
   }

   // Bug #78471: the wiz write path (alias -> PropertyPath -> dialog service) with the value types
   // JSON delivers: a number is coerced to its decimal string and then normalized; a boolean
   // is refused naming the field.
   @Test
   void shadowColorAliasWriteCoercesJsonValues() throws Exception {
      Object[][] cases = { { 16711680, "#ff0000" }, { "#00FF00", "#00ff00" }, { "", "#000000" } };

      for(boolean oval : new boolean[] { true, false }) {
         String type = oval ? "oval" : "rectangle";
         String path = inetsoft.web.wiz.viewsheet.PropertyAliases.resolveForWrite(type, "shadowColor");

         for(Object[] c : cases) {
            reset(propertyService);
            shadowShape(oval, "#123456");
            Object m = inetsoft.web.wiz.viewsheet.PropertyPath.set(getShapeModel(oval), path, c[0]);
            setShapeModel(oval, m);
            assertEquals(c[1], capturedShadowColor(), type + " " + c[0]);
         }

         for(Object bad : new Object[] { true, 16777216 }) {
            reset(propertyService);
            shadowShape(oval, "#123456");
            Object m = inetsoft.web.wiz.viewsheet.PropertyPath.set(getShapeModel(oval), path, bad);
            IllegalArgumentException ex = assertThrows(
               IllegalArgumentException.class, () -> setShapeModel(oval, m), type + " " + bad);
            assertTrue(ex.getMessage().contains("shadowPropPaneModel.color"), ex.getMessage());
         }
      }
   }

   /**
    * Bug #78587 (d): the dialog read-model must hold a copy of the stored GradientColor, so a
    * patch that mutates the gradient and then fails on a later key (never reaching the write)
    * leaves the assembly's gradient unchanged.
    */
   @Test
   void failedPatchLeavesStoredGradientUnchanged() throws Exception {
      for(boolean oval : new boolean[] { true, false }) {
         String type = oval ? "oval" : "rectangle";
         String pane = oval ? "ovalPropertyPaneModel" : "rectanglePropertyPaneModel";
         ShapeVSAssembly a = oval ? new OvalVSAssembly(vs, "Shape1")
            : new RectangleVSAssembly(vs, "Shape1");
         GradientColor stored = new GradientColor();
         stored.setApply(true);
         stored.setDirection("linear");
         stored.setAngle(30);
         userFormat(a).setGradientColorValue(stored);
         when(vs.getAssembly("Shape1")).thenReturn(a);

         Object model = getShapeModel(oval);
         String base = pane + ".fillPropPaneModel.";
         Object patched = inetsoft.web.wiz.viewsheet.PropertyPath.set(
            model, base + "gradientColor.direction", "radial");
         assertThrows(RuntimeException.class, () -> inetsoft.web.wiz.viewsheet.PropertyPath.set(
            patched, base + "alpha", "not-a-number"), type);

         GradientColor live = userFormat(a).getGradientColorValue();
         assertEquals("linear", live.getDirection(), type);
         assertEquals(30, live.getAngle(), type);
      }
   }

   private VSObjectPropertyService propertyService;
   private VSDialogService dialogService;
   private ViewsheetService viewsheetService;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
}
