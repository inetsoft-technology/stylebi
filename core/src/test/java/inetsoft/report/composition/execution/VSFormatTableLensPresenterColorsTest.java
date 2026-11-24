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
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.painter.Bar2Presenter;
import inetsoft.report.painter.BarPresenter;
import inetsoft.report.painter.BooleanPresenter;
import inetsoft.report.painter.BulletGraphPresenter;
import inetsoft.report.painter.IconCounterPresenter;
import inetsoft.report.painter.ImagePresenter;
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
import java.awt.image.BufferedImage;

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
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final String BAR2 = "inetsoft.report.painter.Bar2Presenter";
   private static final String ICON = "inetsoft.report.painter.IconCounterPresenter";
   private static final String CHECK = "inetsoft.report.painter.BooleanPresenter";
   private static final String IMAGE = "inetsoft.report.painter.ImagePresenter";
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
   void aModernTrafficLightGetsTheSoftRoundedHousing() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         TrafficLightPresenter light =
            (TrafficLightPresenter) lens(LIGHT, mark).getPresenter(1, 1);

         assertEquals(VSObjectChromeDefaults.objectBorderColor(VizContext.of(mark)),
                      light.modernHousing());
      }
   }

   @Test
   void aClassicTrafficLightKeepsItsSquareGrayHousing() throws Exception {
      TrafficLightPresenter light = (TrafficLightPresenter) lens(LIGHT, null).getPresenter(1, 1);

      assertNull(light.modernHousing());
   }

   // paints a presenter the way PresenterService does for a cell of this size: at its preferred
   // size, aligned right and centered, unless it fills the cell
   private static BufferedImage render(Presenter presenter, Object value, int width, int height,
                                       int visibleWidth)
   {
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, height);
      g.setClip(0, 0, visibleWidth, height);
      Bounds b = new Bounds(0, 0, width, height);
      Dimension psize = presenter.getPreferredSize(value);
      b = !presenter.isFill() && psize.width > 0 ?
         Common.alignPresenterCell(b, new Size(psize), StyleConstants.H_RIGHT | StyleConstants.V_CENTER) : b;
      presenter.paint(g, value, (int) b.x, (int) b.y, (int) b.width, (int) b.height);
      g.dispose();
      return image;
   }

   private static boolean ink(BufferedImage image, int x, int y) {
      Color c = new Color(image.getRGB(x, y));
      return 765 - (c.getRed() + c.getGreen() + c.getBlue()) > 60;
   }

   private static BufferedImage lightsImage(int visibleWidth) {
      TrafficLightPresenter light = new TrafficLightPresenter();
      light.useModernHousing(Color.BLACK);
      // the cell the client asks for: about 100 wide and 16 high once its padding is taken off
      return render(light, 50.0, 60, 16, visibleWidth);
   }

   @Test
   void theModernHousingKeepsItsRightEdgeWhenTheColumnIsNarrowerThanThePresenter() {
      BufferedImage image = lightsImage(40);

      assertTrue(ink(image, 39, 8), "right edge");
   }

   @Test
   void theModernHousingSitsTheSameDistanceFromTheLightsAboveAndBelow() {
      BufferedImage image = lightsImage(60);
      // down the middle light: the housing's top line, the light, the housing's bottom line
      int x = 30;
      int top = -1;
      int bottom = -1;

      for(int y = 0; y < image.getHeight(); y++) {
         if(ink(image, x, y)) {
            top = top < 0 ? y : top;
            bottom = y;
         }
      }

      // the first and last ink on this column are the housing's two lines
      assertTrue(top >= 0 && bottom > top, "no housing found");
      int firstLight = -1;
      int lastLight = -1;

      for(int y = top + 1; y < bottom; y++) {
         if(ink(image, x, y)) {
            firstLight = firstLight < 0 ? y : firstLight;
            lastLight = y;
         }
      }

      assertTrue(firstLight > 0, "no light found");
      assertEquals(firstLight - top, bottom - lastLight,
                   "space above the lights " + (firstLight - top) + " and below "
                   + (bottom - lastLight));
   }

   @Test
   void theModernHousingHasRoundedCorners() {
      BufferedImage image = lightsImage(60);
      int left = 0;

      while(left < image.getWidth() && !ink(image, left, 8)) {
         left++;
      }

      int top = 0;

      while(top < image.getHeight() && !ink(image, left + 12, top)) {
         top++;
      }

      // a square corner pixel would be solid; the rounded one is much lighter than the edge
      int corner = new Color(image.getRGB(left, top)).getRed();
      int edge = new Color(image.getRGB(left, 8)).getRed();
      assertTrue(corner > edge + 100, "corner " + corner + " is not rounder than the edge " + edge);
   }

   @Test
   void theClassicHousingIsStillSquare() {
      TrafficLightPresenter light = new TrafficLightPresenter();
      BufferedImage image = new BufferedImage(60, 15, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 60, 15);
      g.setClip(0, 0, 60, 15);
      light.paint(g, 50.0, 0, 0, 60, 15);
      g.dispose();

      assertEquals(Color.GRAY.getRGB(), image.getRGB(1, 1));
   }

   @Test
   void aModernPlainBarAndASignedBarAreTheSameThickness() throws Exception {
      VizMark mark = VizMark.MODERN_LIGHT;
      BarPresenter plain = (BarPresenter) lens(BAR, mark).getPresenter(1, 1);
      Bar2Presenter signed = (Bar2Presenter) lens(BAR2, mark).getPresenter(1, 1);

      // a full value fills both bars to the far right of an 87 by 16 cell
      int plainRows = barRows(render(plain, 100.0, 87, 16, 87), 80);
      int signedRows = barRows(render(signed, 100.0, 87, 16, 87), 80);

      assertEquals(8, signedRows);
      assertEquals(signedRows, plainRows);
   }

   @Test
   void aClassicPlainBarStillFillsTheCellHeightLessFour() throws Exception {
      BarPresenter plain = (BarPresenter) lens(BAR, null).getPresenter(1, 1);
      plain.setColor(Color.BLUE);

      assertEquals(12, barRows(render(plain, 100.0, 87, 16, 87), 80));
   }

   // the rows of a column that have some color in them
   private static int barRows(BufferedImage image, int x) {
      int rows = 0;

      for(int y = 0; y < image.getHeight(); y++) {
         if(ink(image, x, y)) {
            rows++;
         }
      }

      return rows;
   }

   @Test
   void aModernStarCounterDrawsSolidStarsInTheModernAmber() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         IconCounterPresenter stars = (IconCounterPresenter) lens(ICON, mark).getPresenter(1, 1);
         Color amber = VSObjectChromeDefaults.starColor(VizContext.of(mark));

         assertEquals(amber, stars.modernStyle());
         // the middle of the first of three stars is the star color, with no image around it
         BufferedImage image = render(stars, 3.0, 60, 16, 60);
         int startX = 60 - stars.getPreferredSize(3.0).width;
         assertEquals(amber.getRGB(), image.getRGB(startX + 2 + 6, 8));
      }
   }

   @Test
   void aStarCounterWithAnAuthorColourKeepsTheIcon() throws Exception {
      PresenterRef ref = new PresenterRef(ICON);
      ref.setParameter("iconColor", AUTHOR);
      IconCounterPresenter stars =
         (IconCounterPresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      // the icon image is 12 wide with 2 between, the same as the solid stars; what differs is
      // that the author's color gives a hue-shifted image, not the amber shape
      assertEquals(AUTHOR, stars.getIconColor());
      BufferedImage image = render(stars, 3.0, 60, 16, 60);
      int startX = 60 - stars.getPreferredSize(3.0).width;
      assertNotEquals(VSObjectChromeDefaults.starColor(VizContext.of(VizMark.MODERN_LIGHT)).getRGB(),
                      image.getRGB(startX + 2 + 6, 8));
   }

   @Test
   void aClassicStarCounterKeepsTheBuiltInImage() throws Exception {
      IconCounterPresenter stars = (IconCounterPresenter) lens(ICON, null).getPresenter(1, 1);

      assertNull(stars.modernStyle());
   }

   @Test
   void aModernCheckMarkIsARoundedBoxFilledWithTheAccentWhenTrue() throws Exception {
      BooleanPresenter check = (BooleanPresenter) lens(CHECK, VizMark.MODERN_LIGHT).getPresenter(1, 1);
      Color accent = VSObjectChromeDefaults.checkMarkColor(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(accent, check.modernAccent());
      BufferedImage on = render(check, 1997.19, 60, 16, 60);
      // the box is 14 wide, flush right: its top left area is the accent, its square corner is not
      int boxLeft = 60 - 14;
      assertEquals(accent.getRGB(), on.getRGB(boxLeft + 2, 4));
      assertNotEquals(accent.getRGB(), on.getRGB(boxLeft, 1), "the corner is rounded");
   }

   @Test
   void theCheckMarkBoxIsTheBarColourAndItsTickIsTheDeepInk() {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         VizContext ctx = VizContext.of(mark);
         Color fill = VSObjectChromeDefaults.checkMarkColor(ctx);
         Color tick = VSObjectChromeDefaults.checkMarkTickColor(ctx);

         // the box is as strong as the first Bullet Graph band, and the tick is the bar's color
         assertEquals(VSObjectChromeDefaults.bulletBandColors(ctx)[0], fill);
         assertEquals(VSObjectChromeDefaults.bulletBarColor(ctx), tick);
         // the tick has to read on the box: a clear difference in lightness
         assertTrue(Math.abs(lightness(fill) - lightness(tick)) > 90,
                    "tick " + tick + " does not read on " + fill);
      }
   }

   @Test
   void theStarColourIsThePalettesSixthColour() {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         VizContext ctx = VizContext.of(mark);

         assertEquals(VSChartPaletteDefaults.activePalette(ctx)[5], VSObjectChromeDefaults.starColor(ctx));
      }
   }

   @Test
   void theBulletBarFollowsTheTextColourSoItIsDarkInLightModeAndLightInDarkMode() {
      Color light = VSObjectChromeDefaults.bulletBarColor(VizContext.of(VizMark.MODERN_LIGHT));
      Color dark = VSObjectChromeDefaults.bulletBarColor(VizContext.of(VizMark.MODERN_DARK));

      assertTrue(lightness(light) < 110, "light mode bar is not dark: " + light);
      assertTrue(lightness(dark) > 150, "dark mode bar is not light: " + dark);
   }

   private static int lightness(Color c) {
      return (c.getRed() + c.getGreen() + c.getBlue()) / 3;
   }

   @Test
   void aModernCheckMarkLeavesTheGraphicsHintsAsItFoundThem() throws Exception {
      BooleanPresenter check = (BooleanPresenter) lens(CHECK, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      for(double value : new double[] { 1.0, 0.0 }) {
         BufferedImage image = new BufferedImage(60, 16, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = image.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

         check.paint(g, value, 0, 0, 60, 16);

         assertEquals(RenderingHints.VALUE_STROKE_NORMALIZE,
                      g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL));
         assertEquals(RenderingHints.VALUE_ANTIALIAS_OFF,
                      g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
         g.dispose();
      }
   }

   @Test
   void aModernCheckMarkIsAnEmptyBorderedBoxWhenFalse() throws Exception {
      BooleanPresenter check = (BooleanPresenter) lens(CHECK, VizMark.MODERN_LIGHT).getPresenter(1, 1);
      BufferedImage off = render(check, 0.0, 60, 16, 60);
      int boxLeft = 60 - 14;

      // the middle is empty and the edge is drawn
      assertEquals(Color.WHITE.getRGB(), off.getRGB(boxLeft + 7, 8));
      assertTrue(ink(off, boxLeft, 8) || ink(off, boxLeft + 1, 8), "no border");
   }

   @Test
   void aClassicCheckMarkKeepsTheBuiltInImage() throws Exception {
      BooleanPresenter check = (BooleanPresenter) lens(CHECK, null).getPresenter(1, 1);

      assertNull(check.modernAccent());
      assertEquals(new Dimension(10, 10), check.getPreferredSize(1.0));
   }

   @Test
   void aModernImagePresenterKeepsTheAspectRatioByDefault() throws Exception {
      ImagePresenter image = (ImagePresenter) lens(IMAGE, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertTrue(image.isMaintainAspectRatio());
   }

   @Test
   void aClassicImagePresenterStillStretchesByDefault() throws Exception {
      ImagePresenter image = (ImagePresenter) lens(IMAGE, null).getPresenter(1, 1);

      assertFalse(image.isMaintainAspectRatio());
   }

   @Test
   void anAuthorWhoUnticksTheAspectRatioKeepsThatOnAModernTable() throws Exception {
      PresenterRef ref = new PresenterRef(IMAGE);
      ref.setParameter("maintainAspectRatio", Boolean.FALSE);
      ImagePresenter image = (ImagePresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertFalse(image.isMaintainAspectRatio());
   }

   @Test
   void anAuthorWhoTicksTheAspectRatioKeepsThatOnAClassicTable() throws Exception {
      PresenterRef ref = new PresenterRef(IMAGE);
      ref.setParameter("maintainAspectRatio", Boolean.TRUE);
      ImagePresenter image = (ImagePresenter) lens(ref, null).getPresenter(1, 1);

      assertTrue(image.isMaintainAspectRatio());
   }

   @Test
   void aModernImageIsDrawnInsideTheCellWithItsOwnProportions() throws Exception {
      // a 32 by 16 red picture in a 60 by 20 cell: kept in proportion it is 40 wide
      java.io.File file = java.io.File.createTempFile("probe-image", ".png");
      file.deleteOnExit();
      BufferedImage picture = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
      Graphics2D pg = picture.createGraphics();
      pg.setColor(Color.RED);
      pg.fillRect(0, 0, 32, 16);
      pg.dispose();
      javax.imageio.ImageIO.write(picture, "png", file);
      String url = file.toURI().toURL().toString();

      ImagePresenter image = (ImagePresenter) lens(IMAGE, VizMark.MODERN_LIGHT).getPresenter(1, 1);
      BufferedImage kept = render(image, url, 60, 20, 60);

      assertTrue(ink(kept, 20, 10), "the picture is drawn");
      assertFalse(ink(kept, 55, 10), "the cell to the right of the picture stays empty");

      // the same picture stretched to the cell reaches the right side
      image.setMaintainAspectRatio(false);
      assertTrue(ink(render(image, url, 60, 20, 60), 55, 10), "stretched, it fills the cell");
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
   void theModernBandsAreTintsOfTheFirstPaletteColourAndGetLighterInLightMode() {
      Color[] bands = VSObjectChromeDefaults.bulletBandColors(VizContext.of(VizMark.MODERN_LIGHT));

      for(int i = 0; i < bands.length; i++) {
         // blue is the first palette color: the blue channel is at the top and red is lowest
         assertTrue(bands[i].getBlue() >= bands[i].getGreen() && bands[i].getGreen() > bands[i].getRed(),
                    "band " + i + " is not a blue tint: " + bands[i]);

         if(i > 0) {
            assertTrue(bands[i].getRed() > bands[i - 1].getRed(), "band " + i + " is not lighter");
         }
      }
   }

   @Test
   void theModernBandsGetDarkerInDarkMode() {
      Color[] bands = VSObjectChromeDefaults.bulletBandColors(VizContext.of(VizMark.MODERN_DARK));

      for(int i = 1; i < bands.length; i++) {
         assertTrue(bands[i].getRed() < bands[i - 1].getRed(), "band " + i + " is not darker");
      }
   }

   @Test
   void aModernBulletGraphTakesTheIndigoBarAndTarget() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         BulletGraphPresenter bullet = (BulletGraphPresenter) lens(BULLET, mark).getPresenter(1, 1);
         VizContext ctx = VizContext.of(mark);

         assertEquals(VSObjectChromeDefaults.bulletBarColor(ctx), bullet.getBarColor());
         assertEquals(VSObjectChromeDefaults.bulletTargetColor(ctx), bullet.modernTarget());
      }
   }

   @Test
   void anAuthorBulletBarColourIsKept() throws Exception {
      PresenterRef ref = new PresenterRef(BULLET);
      ref.setParameter("barColor", AUTHOR);
      BulletGraphPresenter bullet =
         (BulletGraphPresenter) lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(AUTHOR, bullet.getBarColor());
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
      assertNull(bullet.getBarColor());
      assertNull(bullet.modernTarget());
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
