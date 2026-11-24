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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.painter.BulletGraphPresenter;
import inetsoft.report.painter.ButtonPresenter;
import inetsoft.report.painter.ShadowPresenter;
import inetsoft.report.painter.TrafficLightPresenter;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The presenters with colours of their own other than the bars (Bullet Graph, Traffic Light,
 * Button, Shadow) take modern defaults on a modern table; an author's colour and a classic table
 * are kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatTableLensPresenterColorsTest {
   private static final String BULLET = "inetsoft.report.painter.BulletGraphPresenter";
   private static final String LIGHT = "inetsoft.report.painter.TrafficLightPresenter";
   private static final String BUTTON = "inetsoft.report.painter.ButtonPresenter";
   private static final String SHADOW = "inetsoft.report.painter.ShadowPresenter";
   private static final Color AUTHOR = new Color(0x123456);

   @BeforeEach
   void setUp() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   @Test
   void aModernTrafficLightTakesTheModernStatusColours() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         TrafficLightPresenter light =
            (TrafficLightPresenter) lens(LIGHT, mark).getPresenter(1, 1);
         Color[] expected = VSObjectChromeDefaults.trafficLightColors(VizContext.of(mark));

         assertEquals(expected[0], light.getColor1());
         assertEquals(expected[1], light.getColor2());
         assertEquals(expected[2], light.getColor3());
      }
   }

   @Test
   void theModernStatusColoursAreNotThePresentersPureOnes() {
      Color[] colours = VSObjectChromeDefaults.trafficLightColors(VizContext.of(VizMark.MODERN_LIGHT));

      assertNotEquals(TrafficLightPresenter.DEFAULT_GREEN, colours[0]);
      assertNotEquals(TrafficLightPresenter.DEFAULT_YELLOW, colours[1]);
      assertNotEquals(TrafficLightPresenter.DEFAULT_RED, colours[2]);
   }

   @Test
   void anAuthorTrafficLightColourIsKeptAndTheOthersStillTakeTheDefault() throws Exception {
      PresenterRef ref = new PresenterRef(LIGHT);
      ref.setParameter("color3", AUTHOR);
      TrafficLightPresenter light =
         (TrafficLightPresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(AUTHOR, light.getColor3());
      assertEquals(VSObjectChromeDefaults.trafficLightColors(VizContext.of(VizMark.MODERN_LIGHT))[0],
                   light.getColor1());
   }

   @Test
   void aClassicTrafficLightKeepsItsOwnColours() throws Exception {
      TrafficLightPresenter light = (TrafficLightPresenter) lens(LIGHT, null).getPresenter(1, 1);

      assertEquals(TrafficLightPresenter.DEFAULT_GREEN, light.getColor1());
      assertEquals(TrafficLightPresenter.DEFAULT_YELLOW, light.getColor2());
      assertEquals(TrafficLightPresenter.DEFAULT_RED, light.getColor3());
   }

   @Test
   void aModernBulletGraphTakesTheNeutralBands() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         BulletGraphPresenter bullet = (BulletGraphPresenter) lens(BULLET, mark).getPresenter(1, 1);
         Color[] expected = VSObjectChromeDefaults.bulletBandColors(VizContext.of(mark));

         assertEquals(expected[0], bullet.getColor1());
         assertEquals(expected[1], bullet.getColor2());
         assertEquals(expected[2], bullet.getColor3());
         assertEquals(expected[3], bullet.getColor4());
      }
   }

   @Test
   void theNeutralBandsAreGreyAndGetLighterInLightMode() {
      Color[] bands = VSObjectChromeDefaults.bulletBandColors(VizContext.of(VizMark.MODERN_LIGHT));

      for(int i = 0; i < bands.length; i++) {
         // a neutral grey: the channels differ by a few steps at most
         int spread = Math.max(bands[i].getRed(), Math.max(bands[i].getGreen(), bands[i].getBlue())) -
            Math.min(bands[i].getRed(), Math.min(bands[i].getGreen(), bands[i].getBlue()));
         assertTrue(spread <= 12, "band " + i + " is not neutral: " + bands[i]);

         if(i > 0) {
            assertTrue(bands[i].getRed() > bands[i - 1].getRed());
         }
      }
   }

   @Test
   void anAuthorBulletBandIsKept() throws Exception {
      PresenterRef ref = new PresenterRef(BULLET);
      ref.setParameter("color2", AUTHOR);
      BulletGraphPresenter bullet =
         (BulletGraphPresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(AUTHOR, bullet.getColor2());
      assertNotNull(bullet.getColor1());
   }

   @Test
   void aClassicBulletGraphKeepsItsDerivedBands() throws Exception {
      BulletGraphPresenter bullet = (BulletGraphPresenter) lens(BULLET, null).getPresenter(1, 1);

      assertNull(bullet.getColor1());
      assertNull(bullet.getColor4());
   }

   @Test
   void aModernLightButtonTakesTheSoftFace() throws Exception {
      ButtonPresenter button = (ButtonPresenter) lens(BUTTON, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(VSObjectChromeDefaults.buttonFaceColor(VizContext.of(VizMark.MODERN_LIGHT)),
                   button.getColor());
      assertNotEquals(Color.lightGray, button.getColor());
   }

   @Test
   void aDarkModernButtonKeepsTheLightFaceItsBlackTextReadsOn() throws Exception {
      ButtonPresenter button = (ButtonPresenter) lens(BUTTON, VizMark.MODERN_DARK).getPresenter(1, 1);

      assertEquals(Color.lightGray, button.getColor());
   }

   @Test
   void anAuthorButtonColourIsKept() throws Exception {
      PresenterRef ref = new PresenterRef(BUTTON);
      ref.setParameter("color", AUTHOR);
      ButtonPresenter button = (ButtonPresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(AUTHOR, button.getColor());
   }

   @Test
   void aDarkModernShadowTakesTheCardSurfaceAndLightText() throws Exception {
      ShadowPresenter shadow = (ShadowPresenter) lens(SHADOW, VizMark.MODERN_DARK).getPresenter(1, 1);
      VizContext ctx = VizContext.of(VizMark.MODERN_DARK);

      assertEquals(VSObjectChromeDefaults.shadowShadingColor(ctx), shadow.getShading());
      assertEquals(VSObjectChromeDefaults.shadowTextColor(ctx), shadow.getTextColor());
   }

   @Test
   void aLightModernAndAClassicShadowKeepWhiteAndBlack() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, null }) {
         ShadowPresenter shadow = (ShadowPresenter) lens(SHADOW, mark).getPresenter(1, 1);

         assertEquals(Color.white, shadow.getShading());
         assertEquals(Color.black, shadow.getTextColor());
      }
   }

   @Test
   void anAuthorShadowColourIsKeptOnDark() throws Exception {
      PresenterRef ref = new PresenterRef(SHADOW);
      ref.setParameter("shading", AUTHOR);
      ShadowPresenter shadow = (ShadowPresenter) lens(ref, VizMark.MODERN_DARK).getPresenter(1, 1);

      assertEquals(AUTHOR, shadow.getShading());
   }

   private VSFormatTableLens lens(String presenter, VizMark mark) {
      return lens(new PresenterRef(presenter), mark);
   }

   private VSFormatTableLens lens(PresenterRef ref, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(vs, "Calc1");
      lastAssembly = assembly;
      assembly.getVSAssemblyInfo().setVizMark(mark);
      TableLayout layout = assembly.getTableLayout();
      layout.setColCount(2);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 2; c++) {
            layout.setCellBinding(r, c, new TableCellBinding(
               r == 0 ? CellBinding.BIND_TEXT : CellBinding.BIND_FORMULA, "x"));
         }
      }

      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getTableLayout()).thenReturn(layout);
      when(elem.getID()).thenReturn("Calc1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] {
         { "name", "value" }, { "a", 1 } }));

      CalcTableLens calc = new CalcTableLens(2, 2);
      calc.setElement(elem);
      calc.setObject(0, 0, "n");
      calc.setObject(0, 1, "v");
      calc.setObject(1, 0, new CalcTableLens.Formula("1"));
      calc.setObject(1, 1, new CalcTableLens.Formula("5"));
      RuntimeCalcTableLens runtime = calc.process();

      VSCompositeFormat vfmt = new VSCompositeFormat();
      vfmt.getUserDefinedFormat().setPresenterValue(ref);
      TableDataPath path =
         ((CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getCellDataPath(1, 1);
      assembly.getFormatInfo().setFormat(path, vfmt);
      vs.addAssembly(assembly);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "BarColor", null);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      return new VSFormatTableLens(box, "Calc1", runtime, false);
   }

   private GraalJavaScriptEnv env;
   private CalcTableVSAssembly lastAssembly;
}
