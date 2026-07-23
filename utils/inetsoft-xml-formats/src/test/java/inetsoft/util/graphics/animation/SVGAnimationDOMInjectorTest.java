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
package inetsoft.util.graphics.animation;

import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.util.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SVGAnimationDOMInjector}.
 *
 * <p>Tests focus on:
 * <ul>
 *   <li>Hover CSS correctness — all annotation classes covered, dim rules present,
 *       uniform {@code .2s ease} transition.</li>
 *   <li>Treemap fade-in stagger — larger cells animate before smaller ones.</li>
 *   <li>Icicle cascade stagger — root depth animates before leaf depth; steps distributed across {@link AnimationConstants#STAGGER_WINDOW}.</li>
 *   <li>Mekko diagonal stagger — {@code delay = colIdx * COL_STEP + rowIdx * ROW_STEP} scaled to {@link AnimationConstants#STAGGER_WINDOW}.</li>
 *   <li>Label matching — text groups are tagged with hover CSS class and data-row/col.</li>
 * </ul>
 *
 * <p>Documents are constructed programmatically using JAXP {@link DocumentBuilder} so no
 * file-system fixtures are required.  Each helper produces minimal SVG structure sufficient
 * to exercise the production code path under test.
 */
class SVGAnimationDOMInjectorTest {

   // -------------------------------------------------------------------------
   // Document / element construction helpers
   // -------------------------------------------------------------------------

   /** Creates a minimal SVG document with a {@code viewBox} attribute. */
   private static Document newDocument() throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.newDocument();
      Element svg = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "svg");
      svg.setAttribute("viewBox", "0 0 800 600");
      doc.appendChild(svg);
      return doc;
   }

   /**
    * Adds an annotation group to the SVG root containing an inner {@code <g>} that wraps a
    * {@code <rect>} representing the given bounds.  This inner-wrapper structure matches the
    * VO rendering pattern that {@link SVGAnimationDOMInjector#applyAnimStyleToChildren} targets.
    *
    * @return the outer annotation group element
    */
   private static Element addAnnotGroup(Document doc, String cssClass, Map<String, String> dataAttrs,
                                         double x, double y, double w, double h)
   {
      Element svg = doc.getDocumentElement();
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("class", cssClass);

      for(Map.Entry<String, String> e : dataAttrs.entrySet()) {
         g.setAttribute("data-" + e.getKey(), e.getValue());
      }

      // Inner rendering <g> — this is the direct child that applyAnimStyleToChildren targets.
      Element inner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element rect = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "rect");
      rect.setAttribute("x", String.valueOf(x));
      rect.setAttribute("y", String.valueOf(y));
      rect.setAttribute("width", String.valueOf(w));
      rect.setAttribute("height", String.valueOf(h));
      inner.appendChild(rect);
      g.appendChild(inner);
      svg.appendChild(g);
      return g;
   }

   /**
    * Adds a Batik-style text label group ({@code text-rendering="geometricPrecision"}) at the
    * given SVG-coordinate position.  Batik encodes the full label position in the
    * {@code transform} attribute chain; {@code nearestCellByCtm} reads {@code m[4]/m[5]}
    * from the parsed transform to locate the label.
    *
    * @return the text group element
    */
   private static Element addTextGroup(Document doc, double tx, double ty) {
      Element svg = doc.getDocumentElement();
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("text-rendering", "geometricPrecision");
      g.setAttribute("transform",
                      String.format(Locale.US, "translate(%.1f,%.1f)", tx, ty));
      Element text = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "text");
      text.setTextContent("Label");
      g.appendChild(text);
      svg.appendChild(g);
      return g;
   }

   /**
    * Adds a mekko annotation group with a {@code <rect>} as a DIRECT child (no inner wrapper),
    * matching MekkoVO's painting structure — which is why mekko animation is applied to the
    * group element itself rather than via {@code applyAnimStyleToChildren}.
    *
    * @return the annotation group element
    */
   private static Element addMekkoCell(Document doc, String row, String col,
                                        double x, double y, double w, double h)
   {
      Element svg = doc.getDocumentElement();
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("class", SVGSupport.ANNOTATION_MEKKO);
      g.setAttribute("data-row", row);
      g.setAttribute("data-col", col);
      Element rect = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "rect");
      rect.setAttribute("x", String.valueOf(x));
      rect.setAttribute("y", String.valueOf(y));
      rect.setAttribute("width", String.valueOf(w));
      rect.setAttribute("height", String.valueOf(h));
      g.appendChild(rect);
      svg.appendChild(g);
      return g;
   }

   // -------------------------------------------------------------------------
   // Assertion helpers
   // -------------------------------------------------------------------------

   /** Concatenates the text content of all {@code <style>} elements under {@code svgRoot}. */
   private static String allStyleContent(Element svgRoot) {
      StringBuilder sb = new StringBuilder();
      NodeList nodes = svgRoot.getElementsByTagNameNS("*", "style");

      for(int i = 0; i < nodes.getLength(); i++) {
         sb.append(nodes.item(i).getTextContent());
      }

      return sb.toString();
   }

   /**
    * Returns the {@code style} attribute of the first {@link Element} child of
    * {@code annotGroup}, or {@code null} when no Element child exists.
    *
    * <p>For treemap / icicle / sunburst annotation groups, animation is applied to the
    * inner rendering {@code <g>} (first child), not the annotation group itself.
    */
   private static String firstChildStyle(Element annotGroup) {
      Node child = annotGroup.getFirstChild();

      while(child != null) {
         if(child instanceof Element e) {
            return e.getAttribute("style");
         }

         child = child.getNextSibling();
      }

      return null;
   }

   /**
    * Parses the animation delay (in seconds) from a CSS animation shorthand string such as
    * {@code "animation:inetsoft-treemap-fade 0.8s ease-out 0.25s both"}.
    *
    * <p>The pattern matches the second time value in the shorthand (the delay), skipping the
    * first time value (the duration).
    */
   private static double parseDelay(String style) {
      Matcher m = Pattern.compile("[\\s,](\\d+\\.?\\d*)s").matcher(style);
      double first = -1;
      double second = -1;

      while(m.find()) {
         double v = Double.parseDouble(m.group(1));

         if(first < 0) {
            first = v;
         }
         else {
            second = v;
            break;
         }
      }

      return second >= 0 ? second : 0;
   }

   // -------------------------------------------------------------------------
   // Hover CSS tests
   // -------------------------------------------------------------------------

   @Test
   void hoverCssCoversAllAnnotationClasses() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      String css = allStyleContent(doc.getDocumentElement());

      // All chart-type annotation classes must appear in the transition rule so hover
      // fades uniformly regardless of chart type.
      assertTrue(css.contains("inetsoft-bar"),           "hover CSS must include inetsoft-bar");
      assertTrue(css.contains("inetsoft-point"),         "hover CSS must include inetsoft-point");
      assertTrue(css.contains("inetsoft-candle"),        "hover CSS must include inetsoft-candle");
      assertTrue(css.contains("inetsoft-box"),           "hover CSS must include inetsoft-box");
      assertTrue(css.contains("inetsoft-radar"),         "hover CSS must include inetsoft-radar");
      assertTrue(css.contains("inetsoft-treemap"),       "hover CSS must include inetsoft-treemap");
      assertTrue(css.contains("inetsoft-mekko"),         "hover CSS must include inetsoft-mekko");
      assertTrue(css.contains("inetsoft-treemap-label"), "hover CSS must include inetsoft-treemap-label");
      assertTrue(css.contains("inetsoft-mekko-label"),   "hover CSS must include inetsoft-mekko-label");
   }

   @Test
   void hoverCssDimRulesPresent() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      String css = allStyleContent(doc.getDocumentElement());

      assertTrue(
         css.contains(".inetsoft-treemap.inetsoft-active") &&
         css.contains(".inetsoft-treemap:not(.inetsoft-active)"),
         "hover CSS must contain treemap :has() dim rule");
      assertTrue(
         css.contains(".inetsoft-mekko.inetsoft-active") &&
         css.contains(".inetsoft-mekko:not(.inetsoft-active)"),
         "hover CSS must contain mekko :has() dim rule");
      assertTrue(
         css.contains(".inetsoft-candle.inetsoft-active") &&
         css.contains(".inetsoft-candle:not(.inetsoft-active)"),
         "hover CSS must contain candle :has() dim rule");
      assertTrue(
         css.contains(".inetsoft-box.inetsoft-active") &&
         css.contains(".inetsoft-box:not(.inetsoft-active)"),
         "hover CSS must contain box :has() dim rule");
      // HOVER_DIM_OPACITY = 0.20 formatted as "%.2f" produces "0.20"
      String expectedOpacity = "opacity:" +
         String.format(java.util.Locale.US, "%.2f", AnimationConstants.HOVER_DIM_OPACITY) +
         "!important";
      assertTrue(css.contains(expectedOpacity),
                 "dim rules must use opacity:" + expectedOpacity);
   }

   @Test
   void hoverCssCrossTileDimRulesPresent() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      String css = allStyleContent(doc.getDocumentElement());

      // When a chart is split into multiple SVG tiles, the directive flags tiles that do not
      // contain the hovered element with inetsoft-dim-all so they dim too (the :has() rules are
      // scoped to a single SVG). Bar/pie tiles use the non-ready-gated rule; A1 types are gated.
      assertTrue(css.contains("svg.inetsoft-dim-all .inetsoft-bar"),
                 "hover CSS must contain cross-tile dim rule for bar/pie");
      assertTrue(css.contains("svg.ready.inetsoft-dim-all .inetsoft-treemap"),
                 "hover CSS must contain ready-gated cross-tile dim rule for A1 types");
   }

   @Test
   void hoverCssTransitionIsUniform() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      String css = allStyleContent(doc.getDocumentElement());

      assertTrue(css.contains("transition:opacity .2s ease"),
                 "all chart types must share the uniform transition:opacity .2s ease");
   }

   /** {@code data-animated} must be present on the SVG root after animation is injected. */
   @Test
   void dataAnimatedSetAfterAnimation() throws Exception {
      Document doc = newDocument();
      addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                    Map.of("row", "0", "col", "0", "level", "0"),
                    0, 0, 100, 100);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      assertTrue(doc.getDocumentElement().hasAttribute("data-animated"),
                 "data-animated must be present on the SVG root after animation injection");
   }

   /**
    * Pie charts have no {@code inetsoft-active} hover — no {@code .ready} gate is needed.
    * {@code data-animated} must NOT be set so the directive adds {@code .ready} immediately.
    */
   @Test
   void dataAnimatedAbsentForPie() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);
      assertFalse(doc.getDocumentElement().hasAttribute("data-animated"),
                  "data-animated must be absent for pie charts (no .ready gate needed)");
   }

   // -------------------------------------------------------------------------
   // noanim flag tests (design-time surfaces: hover CSS injected, animation suppressed)
   // -------------------------------------------------------------------------

   /**
    * A "grow:noanim" hint (bar chart in a design-time surface) must still inject the hover-dim
    * CSS, but must NOT inject any entrance animation, and must leave {@code data-animated} unset
    * so the client adds {@code .ready} immediately. This is the core mechanism of bug #75692:
    * hover highlighting must work in the binding pane even though animation is suppressed.
    */
   @Test
   void noAnimHintInjectsHoverCssButSuppressesBarAnimation() throws Exception {
      Document doc = newDocument();
      Element bar = addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                                  Map.of("row", "0", "col", "0"), 0, 0, 100, 100);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
         SVGSupport.ANIMATION_GROW + ":" + SVGSupport.ANIMATION_FLAG_NOANIM);
      String css = allStyleContent(doc.getDocumentElement());

      // Hover-dim CSS for bars must be present.
      assertTrue(
         css.contains(".inetsoft-bar.inetsoft-active") &&
         css.contains(".inetsoft-bar:not(.inetsoft-active)"),
         "noanim hint must still inject the bar :has() hover-dim rule");

      // No entrance animation may be injected: appendHoverCSS emits no @keyframes, while every
      // animation branch does, so the absence of @keyframes proves no animation was applied.
      assertFalse(css.contains("@keyframes"),
                  "noanim hint must not inject any animation @keyframes");
      String childStyle = firstChildStyle(bar);
      assertTrue(childStyle == null || childStyle.isEmpty(),
                 "noanim hint must not apply an animation style to the bar's inner children");

      // data-animated must be absent so the directive adds .ready immediately.
      assertFalse(doc.getDocumentElement().hasAttribute("data-animated"),
                  "noanim hint must leave data-animated unset");
   }

   /**
    * A "point:noanim" hint (A1 type in a design-time surface) must inject the ready-gated point
    * hover-dim CSS with no animation. The A1 types gate hover on {@code svg.ready}; with
    * {@code data-animated} absent the client applies {@code .ready} immediately, so hover works.
    */
   @Test
   void noAnimHintInjectsHoverCssForPoint() throws Exception {
      Document doc = newDocument();
      addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                    Map.of("row", "0", "col", "0"), 0, 0, 10, 10);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
         SVGSupport.ANIMATION_POINT + ":" + SVGSupport.ANIMATION_FLAG_NOANIM);
      String css = allStyleContent(doc.getDocumentElement());

      assertTrue(
         css.contains(".inetsoft-point.inetsoft-active") &&
         css.contains(".inetsoft-point:not(.inetsoft-active)"),
         "noanim hint must still inject the point :has() hover-dim rule");
      assertFalse(css.contains("@keyframes"),
                  "noanim hint must not inject any animation @keyframes");
      assertFalse(doc.getDocumentElement().hasAttribute("data-animated"),
                  "noanim hint must leave data-animated unset");
   }

   // -------------------------------------------------------------------------
   // Treemap animation tests
   // -------------------------------------------------------------------------

   @Test
   void treemap_cellsReceiveAnimation() throws Exception {
      Document doc = newDocument();
      Element cell = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "0", "col", "0", "level", "0"),
                                   0, 0, 200, 200);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);

      String childStyle = firstChildStyle(cell);
      assertNotNull(childStyle, "inner child should have an animation style");
      assertTrue(childStyle.contains("inetsoft-treemap-fade"),
                 "style must reference the treemap keyframe");
   }

   /**
    * Larger cells (by bounding-box area) should animate first (smaller delay) so the most
    * prominent regions of the treemap appear immediately.
    */
   @Test
   void treemap_largerCellGetsEarlierDelay() throws Exception {
      Document doc = newDocument();
      // Small cell (50×50 = 2500) added first in DOM order.
      Element small = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                    Map.of("row", "0", "col", "0", "level", "0"),
                                    0, 0, 50, 50);
      // Large cell (200×200 = 40000) added second in DOM order.
      Element large = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                    Map.of("row", "1", "col", "0", "level", "0"),
                                    100, 0, 200, 200);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);

      double smallDelay = parseDelay(firstChildStyle(small));
      double largeDelay = parseDelay(firstChildStyle(large));
      assertTrue(largeDelay < smallDelay,
                 "large cell (200×200) must animate before small cell (50×50): " +
                 "large delay=" + largeDelay + ", small delay=" + smallDelay);
   }

   /** A text group whose translate CTM falls inside a cell's bounds should be matched to it. */
   @Test
   void treemap_labelMatchedToCellByContainment() throws Exception {
      Document doc = newDocument();
      // Cell occupies (0,0)–(200,200).
      Element cell = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "3", "col", "1", "level", "0"),
                                   0, 0, 200, 200);
      // Label translate at (100,100) — inside the cell.
      Element label = addTextGroup(doc, 100, 100);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);

      assertEquals(SVGSupport.ANNOTATION_TREEMAP_LABEL, label.getAttribute("class"),
                   "contained label must be tagged with the treemap-label class");
      assertEquals("3", label.getAttribute("data-row"),
                   "label data-row must match the cell's data-row");
      assertEquals("1", label.getAttribute("data-col"),
                   "label data-col must match the cell's data-col");
      assertFalse(label.getAttribute("style").isEmpty(),
                  "matched label must receive an animation style");
   }

   /**
    * A text group without a {@code transform} attribute is skipped by {@code nearestCellByCtm}.
    * The injector must not throw and must leave the group unmodified.
    */
   @Test
   void treemap_labelWithoutTransformIsSkipped() throws Exception {
      Document doc = newDocument();
      addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                    Map.of("row", "0", "col", "0", "level", "0"),
                    0, 0, 200, 200);

      // Text group with no transform attribute — should be silently skipped.
      Element svg = doc.getDocumentElement();
      Element noTransformLabel = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      noTransformLabel.setAttribute("text-rendering", "geometricPrecision");
      svg.appendChild(noTransformLabel);

      assertDoesNotThrow(
         () -> SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
                                                       SVGSupport.ANIMATION_TREEMAP));
      assertTrue(noTransformLabel.getAttribute("class").isEmpty(),
                 "label without transform must not be tagged with any class");
   }

   // -------------------------------------------------------------------------
   // Icicle animation tests
   // -------------------------------------------------------------------------

   /** Root depth (highest level number) must animate before leaf depth (level=0). */
   @Test
   void icicle_rootLevelAnimatesBeforeLeaf() throws Exception {
      Document doc = newDocument();
      Element root = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "0", "col", "0", "level", "2"),
                                   0, 0, 200, 50);
      Element leaf = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "1", "col", "0", "level", "0"),
                                   0, 100, 100, 50);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_ICICLE);

      double rootDelay = parseDelay(firstChildStyle(root));
      double leafDelay = parseDelay(firstChildStyle(leaf));
      assertTrue(rootDelay < leafDelay,
                 "root (level=2) must animate before leaf (level=0): " +
                 "root=" + rootDelay + ", leaf=" + leafDelay);
   }

   /**
    * Stagger is distributed across {@link AnimationConstants#STAGGER_WINDOW} (2.0 s) using
    * {@link AnimationConstants#staggerDelay}.  With three cells (one per level) the step is
    * {@code 2.0 / (3-1) = 1.0 s}: delays should be 0.0, 1.0, and 2.0 respectively.
    */
   @Test
   void icicle_staggerDistributedAcrossWindow() throws Exception {
      Document doc = newDocument();
      // level=2 is the root (maxLevel); level=0 is the leaf.
      Element lvl2 = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "0", "col", "0", "level", "2"),
                                   0,   0, 200, 50);
      Element lvl1 = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "1", "col", "0", "level", "1"),
                                   0,  60, 200, 50);
      Element lvl0 = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "2", "col", "0", "level", "0"),
                                   0, 120, 200, 50);
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_ICICLE);

      assertEquals(0.00, parseDelay(firstChildStyle(lvl2)), 0.01, "root  (level=2) → 0.00 s");
      assertEquals(1.00, parseDelay(firstChildStyle(lvl1)), 0.01, "mid   (level=1) → 1.00 s");
      assertEquals(2.00, parseDelay(firstChildStyle(lvl0)), 0.01, "leaf  (level=0) → 2.00 s");
   }

   /** A label contained in an icicle cell should be tagged with the treemap-label class. */
   @Test
   void icicle_labelMatchedToCellByContainment() throws Exception {
      Document doc = newDocument();
      // Cell occupies (100,100)–(300,180).
      Element cell = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "5", "col", "2", "level", "1"),
                                   100, 100, 200, 80);
      // Label at (200,140) — inside the cell.
      Element label = addTextGroup(doc, 200, 140);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_ICICLE);

      assertEquals(SVGSupport.ANNOTATION_TREEMAP_LABEL, label.getAttribute("class"));
      assertEquals("5", label.getAttribute("data-row"));
      assertEquals("2", label.getAttribute("data-col"));
   }

   // -------------------------------------------------------------------------
   // Mekko animation tests
   // -------------------------------------------------------------------------

   /**
    * Verifies the diagonal stagger formula scaled to {@link AnimationConstants#STAGGER_WINDOW}.
    * Raw ratios are COL_RATIO=0.08 and ROW_RATIO=0.05; for this grid (1 col-step, 1 row-step)
    * rawMax = 0.08 + 0.05 = 0.13, scale = 2.0/0.13.
    * <ul>
    *   <li>Column 0, row 0: 0.00 s</li>
    *   <li>Column 0, row 1: ROW_STEP = 0.05 * (2.0/0.13) ≈ 0.77 s</li>
    *   <li>Column 1, row 0: COL_STEP = 0.08 * (2.0/0.13) ≈ 1.23 s</li>
    * </ul>
    */
   @Test
   void mekko_diagonalStagger_delaysFollowFormula() throws Exception {
      Document doc = newDocument();
      // Column 0 (x-center = 50): two cells stacked vertically.
      Element col0row0 = addMekkoCell(doc, "0", "0",   0,   0, 100, 90);
      Element col0row1 = addMekkoCell(doc, "1", "0",   0, 100, 100, 90);
      // Column 1 (x-center = 200): one cell.
      Element col1row0 = addMekkoCell(doc, "0", "1", 150,   0, 100, 90);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_MEKKO);

      // scale = 2.0 / (1*0.08 + 1*0.05) = 2.0/0.13
      double scale = 2.0 / 0.13;
      double colStep = 0.08 * scale;
      double rowStep = 0.05 * scale;

      // Mekko sets the animation style directly on the annotation group element.
      assertEquals(0.00,    parseDelay(col0row0.getAttribute("style")), 0.02, "col0 row0 → 0.00 s");
      assertEquals(rowStep, parseDelay(col0row1.getAttribute("style")), 0.02, "col0 row1 → ~0.77 s");
      assertEquals(colStep, parseDelay(col1row0.getAttribute("style")), 0.02, "col1 row0 → ~1.23 s");
   }

   /**
    * Two cells in the same column whose y-tops round to the same pixel value must not cause an
    * exception.  {@code putIfAbsent} keeps the first occurrence's row index; both cells share
    * the same animation delay but neither is skipped.
    */
   @Test
   void mekko_duplicateYtopDoesNotThrow() throws Exception {
      Document doc = newDocument();
      // y=10.0 and y=10.4 both round to 10 — same rowMap key after Math.round().
      Element cell1 = addMekkoCell(doc, "0", "0", 0, 10.0, 100, 90);
      Element cell2 = addMekkoCell(doc, "1", "0", 0, 10.4, 100, 90);

      assertDoesNotThrow(
         () -> SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
                                                       SVGSupport.ANIMATION_MEKKO));
      // Both cells must receive an animation style; the putIfAbsent collision means they
      // share the same row index (and therefore the same delay), but neither is skipped.
      assertFalse(cell1.getAttribute("style").isEmpty(),
                  "collision cell 1 must still receive animation style");
      assertFalse(cell2.getAttribute("style").isEmpty(),
                  "collision cell 2 must still receive animation style");
   }

   /** A text group whose translate CTM falls inside a mekko cell should be tagged for hover. */
   @Test
   void mekko_labelTaggedForHover() throws Exception {
      Document doc = newDocument();
      // Cell occupies (50,50)–(150,150).
      addMekkoCell(doc, "7", "3", 50, 50, 100, 100);
      // Label at (100,100) — inside the cell.
      Element label = addTextGroup(doc, 100, 100);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_MEKKO);

      assertEquals(SVGSupport.ANNOTATION_MEKKO_LABEL, label.getAttribute("class"),
                   "label inside mekko cell must be tagged with the mekko-label class");
      assertEquals("7", label.getAttribute("data-row"),
                   "label data-row must match the cell's data-row");
      assertEquals("3", label.getAttribute("data-col"),
                   "label data-col must match the cell's data-col");
   }

   // -------------------------------------------------------------------------
   // Radar animation tests
   // -------------------------------------------------------------------------

   /**
    * Verifies that {@code inetsoft-line} annotation groups are reclassified to
    * {@code inetsoft-radar} and receive the spring-scale animation style.
    * Also verifies that ghost-fill and hit-path elements are injected for fill="none" paths.
    */
   @Test
   void radar_lineGroupsReclassifiedAndAnimated() throws Exception {
      Document doc = newDocument();
      // Build an inetsoft-line annotation group containing a polygon path (fill="none").
      // computeRadarCenter reads polygon vertices from the path's "d" attribute to find
      // the radar center; the path must have M/L commands so vertex parsing succeeds.
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      g.setAttribute("data-row", "0");
      g.setAttribute("data-" + SVGSupport.ATTR_COLOR, "60,105,138");
      Element path = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      path.setAttribute("d", "M 100 50 L 150 150 L 50 150 Z");
      path.setAttribute("fill", "none");
      path.setAttribute("stroke", "blue");
      g.appendChild(path);
      doc.getDocumentElement().appendChild(g);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RADAR);

      assertEquals(SVGSupport.ANNOTATION_RADAR, g.getAttribute("class"),
                   "inetsoft-line group must be reclassified to inetsoft-radar");
      assertTrue(g.getAttribute("style").contains("inetsoft-radar-grow"),
                 "radar group must receive the spring-scale animation");

      // fill="none" path triggers ghost-fill (prepended) + hit-path (appended) injection.
      // Child count: ghost-fill path + original path + hit path = 3.
      int childCount = 0;
      Node child = g.getFirstChild();
      while(child != null) {
         if(child instanceof Element) childCount++;
         child = child.getNextSibling();
      }
      assertEquals(3, childCount,
                   "ghost-fill path and hit path must be injected for fill=none radar polygon");

      // Hit path is the last Element child.
      Node last = g.getLastChild();
      while(last != null && !(last instanceof Element)) last = last.getPreviousSibling();
      assertNotNull(last, "group must have at least one child element");
      assertEquals("rgba(0,0,0,0)", ((Element) last).getAttribute("fill"),
                   "last child must be the transparent hit path");
      assertEquals("all", ((Element) last).getAttribute("pointer-events"),
                   "hit path must capture pointer events");
   }

   /**
    * Two radar series groups should animate with the second series delayed by
    * {@code stagger = 0.25 s} relative to the first.
    */
   @Test
   void radar_secondSeriesIsDelayed() throws Exception {
      Document doc = newDocument();

      for(int i = 0; i < 2; i++) {
         Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
         g.setAttribute("class", SVGSupport.ANNOTATION_LINE);
         g.setAttribute("data-row", String.valueOf(i));
         g.setAttribute("data-" + SVGSupport.ATTR_COLOR, "60,105,138");
         Element path = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
         path.setAttribute("d", "M 100 50 L 150 150 L 50 150 Z");
         path.setAttribute("fill", "none");
         g.appendChild(path);
         doc.getDocumentElement().appendChild(g);
      }

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RADAR);

      List<Element> radar = new ArrayList<>();
      NodeList all = doc.getDocumentElement().getChildNodes();
      for(int i = 0; i < all.getLength(); i++) {
         if(all.item(i) instanceof Element e &&
            SVGSupport.ANNOTATION_RADAR.equals(e.getAttribute("class")))
         {
            radar.add(e);
         }
      }

      assertEquals(2, radar.size(), "both line groups must be reclassified");
      double delay0 = parseDelay(radar.get(0).getAttribute("style"));
      double delay1 = parseDelay(radar.get(1).getAttribute("style"));
      // staggerDelay(1, 2) - staggerDelay(0, 2) = 2.0/(2-1) = 2.0 s (full STAGGER_WINDOW)
      assertEquals(AnimationConstants.STAGGER_WINDOW, delay1 - delay0, 0.01,
                   "second series must be delayed by STAGGER_WINDOW relative to first");
   }

   // -------------------------------------------------------------------------
   // Sunburst animation tests
   // -------------------------------------------------------------------------

   /** Root level arcs (highest data-level) should animate before leaf arcs (level=0). */
   @Test
   void sunburst_rootLevelAnimatesBeforeLeaf() throws Exception {
      Document doc = newDocument();
      // Two arcs at different levels; root (level=1) should animate first (lower delay).
      Element root = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "0", "col", "0", "level", "1"),
                                   50, 50, 100, 100);
      Element leaf = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "1", "col", "0", "level", "0"),
                                   300, 50, 80, 80);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_SUNBURST);

      double rootDelay = parseDelay(firstChildStyle(root));
      double leafDelay = parseDelay(firstChildStyle(leaf));
      assertTrue(rootDelay < leafDelay,
                 "root arc (level=1) must animate before leaf arc (level=0): " +
                 "root=" + rootDelay + ", leaf=" + leafDelay);
   }

   /** Inner child of a sunburst arc group must reference the sunburst keyframe. */
   @Test
   void sunburst_arcsReceiveFadeAnimation() throws Exception {
      Document doc = newDocument();
      Element arc = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                  Map.of("row", "0", "col", "0", "level", "0"),
                                  100, 100, 100, 100);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_SUNBURST);

      String childStyle = firstChildStyle(arc);
      assertNotNull(childStyle, "inner child should have an animation style");
      assertTrue(childStyle.contains("inetsoft-sunburst-fade"),
                 "style must reference the sunburst keyframe");
   }

   /**
    * Two arcs at the same level (same ring) should receive staggered delays within the ring.
    * With two total arcs, {@code staggerDelay(1, 2) - staggerDelay(0, 2) = STAGGER_WINDOW = 2.0 s}.
    */
   @Test
   void sunburst_sameRingArcsAreStaggered() throws Exception {
      Document doc = newDocument();
      // Both arcs at level=0 (same ring), positioned on opposite sides of a virtual center.
      Element arc0 = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "0", "col", "0", "level", "0"),
                                   50, 0, 80, 80);   // centre (90, 40)
      Element arc1 = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                   Map.of("row", "1", "col", "0", "level", "0"),
                                   250, 0, 80, 80);  // centre (290, 40)

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_SUNBURST);

      double delay0 = parseDelay(firstChildStyle(arc0));
      double delay1 = parseDelay(firstChildStyle(arc1));
      // The two arcs are in the same ring, so their delays must differ (stagger within ring).
      assertNotEquals(delay0, delay1, 0.001,
                      "two arcs in the same ring must have different animation delays");
      // staggerDelay(1, 2) - staggerDelay(0, 2) = STAGGER_WINDOW / (2-1) = 2.0 s
      assertEquals(AnimationConstants.STAGGER_WINDOW, Math.abs(delay1 - delay0), 0.01,
                   "within-ring step for 2 arcs must equal STAGGER_WINDOW = 2.0 s");
   }

   // -------------------------------------------------------------------------
   // Candle / box animation tests (injectXPositionFadeAnimation)
   // -------------------------------------------------------------------------

   /**
    * Candlestick groups sorted by {@code data-x} (screen X center) must animate left-to-right:
    * the leftmost group gets delay 0, the rightmost gets the largest delay.
    */
   @Test
   void candle_leftToRightDelayOrdering() throws Exception {
      Document doc = newDocument();
      // Three candle groups with explicitly ordered data-x values.
      // data-x controls the sort; bounds are irrelevant for this test.
      Element left   = addAnnotGroup(doc, SVGSupport.ANNOTATION_CANDLE,
                                     Map.of("row", "0", "col", "0", "x", "100"),
                                     0, 0, 10, 50);
      Element middle = addAnnotGroup(doc, SVGSupport.ANNOTATION_CANDLE,
                                     Map.of("row", "1", "col", "0", "x", "200"),
                                     100, 0, 10, 50);
      Element right  = addAnnotGroup(doc, SVGSupport.ANNOTATION_CANDLE,
                                     Map.of("row", "2", "col", "0", "x", "300"),
                                     200, 0, 10, 50);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_CANDLE);

      // Candle/box animation is applied to the group itself (not inner children).
      double delayLeft   = parseDelay(left.getAttribute("style"));
      double delayMiddle = parseDelay(middle.getAttribute("style"));
      double delayRight  = parseDelay(right.getAttribute("style"));

      assertTrue(delayLeft < delayMiddle,
                 "left candle (x=100) must animate before middle (x=200)");
      assertTrue(delayMiddle < delayRight,
                 "middle candle (x=200) must animate before right (x=300)");
      assertEquals(0.0, delayLeft, 0.01,
                   "leftmost candle must start with delay 0");
   }

   /**
    * Boxes fade in left-to-right by screen X center (unchanged ordering), and each outlier point
    * fades in with the exact delay of its box via the shared {@code data-group} key.  The DOM
    * order here is deliberately the reverse of the x order to prove the ordering derives from
    * {@code data-x}, and that the group tag only maps a point to its box's delay — not the order.
    */
   @Test
   void box_pointsFadeInWithTheirBoxByGroup() throws Exception {
      Document doc = newDocument();
      // boxA is first in the DOM but sits to the RIGHT (x=150); boxB is second but to the LEFT (x=50).
      Element boxA = addAnnotGroup(doc, SVGSupport.ANNOTATION_BOX,
                                   Map.of("row", "0", "col", "0", "x", "150", "group", "A"),
                                   0, 0, 10, 50);
      Element boxB = addAnnotGroup(doc, SVGSupport.ANNOTATION_BOX,
                                   Map.of("row", "1", "col", "0", "x", "50", "group", "B"),
                                   100, 0, 10, 50);
      // One outlier point per group; the animation lands on the inner child (A2 pattern).
      Element pointA = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                                     Map.of("row", "0", "col", "0", "size", "3", "group", "A"),
                                     5, 5, 2, 2);
      Element pointB = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                                     Map.of("row", "1", "col", "0", "size", "3", "group", "B"),
                                     105, 5, 2, 2);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_BOX);

      double delayBoxA = parseDelay(boxA.getAttribute("style"));
      double delayBoxB = parseDelay(boxB.getAttribute("style"));
      double delayPointA = parseDelay(firstChildStyle(pointA));
      double delayPointB = parseDelay(firstChildStyle(pointB));

      // Left box (B, x=50) animates first at delay 0; right box (A, x=150) animates later —
      // ordering is by screen x, NOT by DOM/tag order.
      assertEquals(0.0, delayBoxB, 0.01, "leftmost box (x=50) must start with delay 0");
      assertTrue(delayBoxA > delayBoxB, "right box (x=150) must animate after the left box");

      // Each outlier point fades in with the exact delay of its own box's group.
      assertEquals(delayBoxA, delayPointA, 0.001, "point in group A must fade in with box A");
      assertEquals(delayBoxB, delayPointB, 0.001, "point in group B must fade in with box B");

      // Boxes animate on the group itself (A1); the point group's own opacity stays free so the
      // hover-dim CSS can override it (A2 pattern applies animation to the inner child instead).
      assertTrue(boxA.getAttribute("style").contains("inetsoft-box-fade"),
                 "box animation must be applied to the group element");
      assertTrue(pointA.getAttribute("style").isEmpty(),
                 "point group's own opacity must stay free for hover dim (A2 pattern)");
   }

   // -------------------------------------------------------------------------
   // Step-area animation tests
   // -------------------------------------------------------------------------

   /**
    * {@link SVGAnimationInjector#xRangesOverlap} must return {@code true} for overlapping paths
    * and {@code false} for disjoint paths.
    */
   @Test
   void xRangesOverlap_detectsOverlapAndDisjoint() {
      // Same x-range — stacked area series.
      assertTrue(SVGAnimationInjector.xRangesOverlap(
         "M0,10 L100,10 L100,20 L200,20",
         "M0,5  L100,5  L100,8  L200,8"),
         "Identical x-ranges must overlap");

      // Partial overlap.
      assertTrue(SVGAnimationInjector.xRangesOverlap(
         "M0,10 L150,10",
         "M100,5 L200,5"),
         "Partially overlapping x-ranges must overlap");

      // Disjoint — non-stacked step area series.
      assertFalse(SVGAnimationInjector.xRangesOverlap(
         "M0,10 L100,10",
         "M200,5 L300,5"),
         "Disjoint x-ranges must not overlap");

      // Single-point touch (minA == maxB) — treated as overlapping.
      assertTrue(SVGAnimationInjector.xRangesOverlap(
         "M0,10 L100,10",
         "M100,5 L200,5"),
         "Touching x-ranges at a single point must overlap");
   }

   /**
    * In a non-stacked step-area chart each colored series covers a different x-range.
    * {@code buildBandPolygon} must NOT be applied across disjoint x-ranges because it would
    * produce a skewed polygon that spans the full chart width incorrectly.
    *
    * <p>Verifies that each area fill path keeps its original {@code d} attribute unchanged
    * when adjacent series have disjoint x-ranges.
    */
   @Test
   void stepArea_nonStacked_fillPathUnchangedWhenXRangesDisjoint() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      // Series A: cyan fill, x-range 0–100.
      String fillA = "M0,50 L50,50 L50,30 L100,30 L100,0 L0,0 Z";
      String lineA = "M0,50 L50,50 L50,30 L100,30";
      Element areaA = addLineAreaPair(doc, svg, "34,211,238", fillA, lineA);

      // Series B: purple fill, x-range 200–300 (disjoint from A).
      String fillB = "M200,40 L250,40 L250,20 L300,20 L300,0 L200,0 Z";
      String lineB = "M200,40 L250,40 L250,20 L300,20";
      Element areaB = addLineAreaPair(doc, svg, "167,139,250", fillB, lineB);

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      // Locate the fill path inside each area annotation group.
      Element pathA = firstDescendantPathOf(areaA);
      Element pathB = firstDescendantPathOf(areaB);

      assertNotNull(pathA, "area A must have a descendant path");
      assertNotNull(pathB, "area B must have a descendant path");

      assertEquals(fillA, pathA.getAttribute("d"),
         "non-stacked area A fill must keep its original polygon (no band reshape)");
      assertEquals(fillB, pathB.getAttribute("d"),
         "non-stacked area B fill must keep its original polygon (no band reshape)");
   }

   /**
    * AreaVO emits cubic-curve fill geometry (with {@code data-smooth="true"}) for stacked area
    * charts when the smooth-lines option is on.  The polygon is already a non-overlapping band,
    * so {@code buildBandPolygon} must NOT rewrite it — that helper only emits {@code M}/{@code L}
    * commands and would re-flatten the curves.  The smooth-skip guard in
    * {@code injectLineAnimationFromAnnotations} guarantees the curved {@code d} attribute
    * survives even when adjacent stacked series have overlapping x-ranges (the condition that
    * would otherwise trigger the rewrite).
    */
   @Test
   void stackedArea_smooth_fillPathPreservedWhenOverlapping() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      // Both series share x-range 0–100 (the overlapping condition that would normally
      // trigger buildBandPolygon).  data-smooth="true" must short-circuit that rewrite.
      String fillTop = "M0,80 C25,90 75,90 100,80 L100,40 C75,30 25,30 0,40 Z";
      String lineTop = "M0,80 C25,90 75,90 100,80";
      Element areaTop = addLineAreaPair(doc, svg, "34,211,238", fillTop, lineTop);
      areaTop.setAttribute("data-" + SVGSupport.ATTR_SMOOTH, "true");

      String fillBot = "M0,40 C25,30 75,30 100,40 L100,0 L0,0 Z";
      String lineBot = "M0,40 C25,30 75,30 100,40";
      Element areaBot = addLineAreaPair(doc, svg, "167,139,250", fillBot, lineBot);
      areaBot.setAttribute("data-" + SVGSupport.ATTR_SMOOTH, "true");

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      Element pathTop = firstDescendantPathOf(areaTop);
      Element pathBot = firstDescendantPathOf(areaBot);

      assertNotNull(pathTop, "area top must have a descendant path");
      assertNotNull(pathBot, "area bot must have a descendant path");

      assertEquals(fillTop, pathTop.getAttribute("d"),
         "smooth area fill must keep its curved d attribute (rewrite must be skipped)");
      assertEquals(fillBot, pathBot.getAttribute("d"),
         "smooth area fill must keep its curved d attribute (rewrite must be skipped)");
   }

   /**
    * The inner Batik style {@code <g text-rendering="geometricPrecision">} that wraps the fill
    * path inside an {@code inetsoft-area} annotation group must NOT receive a fade animation.
    * Before the fix, {@code collectTextGroups} incorrectly identified it as a value-label group
    * and applied the dot-delay fade, hiding the area fill until all lines had finished drawing.
    */
   @Test
   void stepArea_innerBatikStyleGroupNotFadedAsLabel() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      String fill = "M0,50 L50,50 L50,30 L100,30 L100,0 L0,0 Z";
      String line = "M0,50 L50,50 L50,30 L100,30";
      addLineAreaPair(doc, svg, "34,211,238", fill, line);

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      // Collect all elements with text-rendering="geometricPrecision" anywhere in the SVG.
      List<Element> textRenderingGroups = new ArrayList<>();
      collectByAttr(svg, "text-rendering", "geometricPrecision", textRenderingGroups);

      for(Element g : textRenderingGroups) {
         String style = g.getAttribute("style");
         // Must not have a dot-delay fade (opacity:0 + inetsoft-line-fade animation).
         // Annotation inner style groups should be transparent only if a wipe was applied to
         // a child path — the parent g itself must not carry the fade.
         assertFalse(style.contains("inetsoft-line-fade"),
            "inner Batik style group inside inetsoft-area must not carry a label fade animation");
      }
   }

   /**
    * Adds a matched {@code inetsoft-area} / {@code inetsoft-line} pair to the SVG root, with
    * the area fill wrapped in a Batik-style inner {@code <g text-rendering="geometricPrecision">}
    * (matching the actual SVG structure produced by Batik for step-area charts).
    *
    * @return the outer {@code inetsoft-area} annotation group
    */
   private static Element addLineAreaPair(Document doc, Element svg,
                                           String color, String fillD, String lineD)
   {
      // Area annotation group: outer g → inner Batik style g → clip g → path
      Element areaAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      areaAnnot.setAttribute("class", SVGSupport.ANNOTATION_AREA);
      areaAnnot.setAttribute("data-color", color);
      areaAnnot.setAttribute("data-series", "0");

      Element batikStyleG = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      batikStyleG.setAttribute("text-rendering", "geometricPrecision");

      Element fillPath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      fillPath.setAttribute("d", fillD);
      fillPath.setAttribute("stroke", "none");

      batikStyleG.appendChild(fillPath);
      areaAnnot.appendChild(batikStyleG);
      svg.appendChild(areaAnnot);

      // Line annotation group: outer g → inner g → path
      Element lineAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      lineAnnot.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      lineAnnot.setAttribute("data-color", color);
      lineAnnot.setAttribute("data-series", "0");

      Element lineInner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element linePath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      linePath.setAttribute("d", lineD);
      linePath.setAttribute("fill", "none");

      lineInner.appendChild(linePath);
      lineAnnot.appendChild(lineInner);
      svg.appendChild(lineAnnot);

      return areaAnnot;
   }

   /** DFS traversal to find the first {@code <path>} descendant of the given element. */
   private static Element firstDescendantPathOf(Element el) {
      NodeList children = el.getChildNodes();
      for(int i = 0; i < children.getLength(); i++) {
         if(!(children.item(i) instanceof Element c)) continue;
         if("path".equals(c.getLocalName())) return c;
         Element found = firstDescendantPathOf(c);
         if(found != null) return found;
      }
      return null;
   }

   /** Collects all elements anywhere in the tree that have the given attribute set to the given value. */
   private static void collectByAttr(Element el, String attr, String value, List<Element> result) {
      NodeList children = el.getChildNodes();
      for(int i = 0; i < children.getLength(); i++) {
         if(!(children.item(i) instanceof Element c)) continue;
         if(value.equals(c.getAttribute(attr))) result.add(c);
         collectByAttr(c, attr, value, result);
      }
   }

   // -------------------------------------------------------------------------
   // Step / jump line — no ghost fill
   // -------------------------------------------------------------------------

   /**
    * Step and jump lines must not produce a ghost fill polygon.
    * The fill polygon generated by {@code buildFillPolygon} for a straight line is a simple
    * rectangle-like shape; for a stepped path it would be a visually incorrect "staircase shadow"
    * that does not follow the line.  When {@code data-step="true"} is present on the annotation
    * group the injector must skip ghost-fill insertion entirely.
    */
   @Test
   void stepLine_noGhostFillInjected() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element lineAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      lineAnnot.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_COLOR, "96,165,250");
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_SERIES, "0");
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_STEP, "true");

      Element inner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element linePath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      linePath.setAttribute("d", "M0,50 L50,50 L50,30 L100,30 L100,10 L150,10");
      linePath.setAttribute("fill", "none");
      inner.appendChild(linePath);
      lineAnnot.appendChild(inner);
      svg.appendChild(lineAnnot);

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      assertEquals(0, countGhostFills(svg),
         "no ghost-fill element must be inserted into the SVG root for a step/jump line");
   }

   /**
    * Jump lines (axis-aligned horizontal-then-vertical segments) set the same {@code data-step}
    * flag as step lines and must also be excluded from ghost-fill insertion.
    */
   @Test
   void jumpLine_noGhostFillInjected() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element lineAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      lineAnnot.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_COLOR, "96,165,250");
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_SERIES, "0");
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_STEP, "true");

      Element inner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element linePath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      // Jump line: horizontal segment first, then vertical drop (H-then-V pattern).
      linePath.setAttribute("d", "M0,50 L50,50 L100,30 L100,30 L150,10");
      linePath.setAttribute("fill", "none");
      inner.appendChild(linePath);
      lineAnnot.appendChild(inner);
      svg.appendChild(lineAnnot);

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      assertEquals(0, countGhostFills(svg),
         "no ghost-fill element must be inserted into the SVG root for a jump line");
   }

   /**
    * Regular (non-step) lines must still produce a ghost fill when {@code data-step} is absent.
    */
   @Test
   void regularLine_ghostFillInjected() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element lineAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      lineAnnot.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_COLOR, "96,165,250");
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_SERIES, "0");

      Element inner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element linePath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      linePath.setAttribute("d", "M0,50 L50,30 L100,10 L150,20");
      linePath.setAttribute("fill", "none");
      inner.appendChild(linePath);
      lineAnnot.appendChild(inner);
      svg.appendChild(lineAnnot);

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      assertTrue(countGhostFills(svg) > 0,
         "a ghost-fill element must be inserted into the SVG root for a regular line");
   }

   // -------------------------------------------------------------------------
   // Line series stagger — multiple lines must draw one at a time
   // -------------------------------------------------------------------------

   /**
    * Lines split from a single measure by a color dimension share the same {@code data-series}
    * column index but carry distinct {@code data-color}s.  They must rank as separate series so
    * each draws on its own staggered delay, matching area/stacked-area behavior — ranking by
    * {@code data-series} alone would collapse them to one rank and draw them all at once.
    */
   @Test
   void colorDimensionLines_drawStaggered() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element l0 = addLineSeries(doc, svg, "34,211,238",  "0", "M0,50 L50,40 L100,30 L150,20");
      Element l1 = addLineSeries(doc, svg, "167,139,250", "0", "M0,40 L50,30 L100,20 L150,10");
      Element l2 = addLineSeries(doc, svg, "96,165,250",  "0", "M0,30 L50,20 L100,10 L150,5");

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      double d0 = parseDelay(firstDescendantPathOf(l0).getAttribute("style"));
      double d1 = parseDelay(firstDescendantPathOf(l1).getAttribute("style"));
      double d2 = parseDelay(firstDescendantPathOf(l2).getAttribute("style"));

      assertTrue(d0 < d1 && d1 < d2,
         "color-dimension lines must draw on increasing staggered delays: " +
         "d0=" + d0 + ", d1=" + d1 + ", d2=" + d2);
      // Three series spread across STAGGER_WINDOW: last starts at the full window.
      assertEquals(AnimationConstants.STAGGER_WINDOW, d2 - d0, 0.01,
         "last series must start at STAGGER_WINDOW relative to the first");
   }

   /**
    * Lines from distinct measures (distinct {@code data-series}) must remain separately ranked
    * and staggered — confirms the composite-key ranking does not regress the multi-measure case.
    */
   @Test
   void multiMeasureLines_drawStaggered() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element l0 = addLineSeries(doc, svg, "34,211,238",  "0", "M0,50 L50,40 L100,30 L150,20");
      Element l1 = addLineSeries(doc, svg, "167,139,250", "1", "M0,40 L50,30 L100,20 L150,10");

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      double d0 = parseDelay(firstDescendantPathOf(l0).getAttribute("style"));
      double d1 = parseDelay(firstDescendantPathOf(l1).getAttribute("style"));

      assertEquals(AnimationConstants.STAGGER_WINDOW, d1 - d0, 0.01,
         "second measure must be delayed by STAGGER_WINDOW relative to the first");
   }

   /** A single line is the only series, so it draws immediately with no stagger delay. */
   @Test
   void singleLine_drawsWithoutDelay() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      Element l0 = addLineSeries(doc, svg, "34,211,238", "0", "M0,50 L50,40 L100,30 L150,20");

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      assertEquals(0, parseDelay(firstDescendantPathOf(l0).getAttribute("style")), 0.01,
         "a single line series must draw with no stagger delay");
   }

   /**
    * In a facet chart the same series (same data-series + data-color) repeats across panels.
    * Repeated keys must dedup to a stable rank so the series count is not inflated: same-key
    * lines in different panels must share a delay, and the rank spacing must stay at the
    * two-series spacing (STAGGER_WINDOW), not a four-series spacing.
    */
   @Test
   void facetPanelsWithRepeatedSeries_dedupToStableRank() throws Exception {
      Document doc = newDocument();
      Element svg = doc.getDocumentElement();

      // Panel 1: series A then series B.
      Element p1a = addLineSeries(doc, svg, "34,211,238",  "0", "M0,50 L50,40 L100,30 L150,20");
      Element p1b = addLineSeries(doc, svg, "167,139,250", "0", "M0,40 L50,30 L100,20 L150,10");
      // Panel 2: same two series repeated.
      Element p2a = addLineSeries(doc, svg, "34,211,238",  "0", "M200,50 L250,40 L300,30 L350,20");
      Element p2b = addLineSeries(doc, svg, "167,139,250", "0", "M200,40 L250,30 L300,20 L350,10");

      SVGAnimationDOMInjector.injectAnimation(svg, SVGSupport.ANIMATION_LINE);

      double d1a = parseDelay(firstDescendantPathOf(p1a).getAttribute("style"));
      double d1b = parseDelay(firstDescendantPathOf(p1b).getAttribute("style"));
      double d2a = parseDelay(firstDescendantPathOf(p2a).getAttribute("style"));
      double d2b = parseDelay(firstDescendantPathOf(p2b).getAttribute("style"));

      assertEquals(d1a, d2a, 0.01, "same series across panels must share a delay (stable rank)");
      assertEquals(d1b, d2b, 0.01, "same series across panels must share a delay (stable rank)");
      // Two distinct series → spacing is the full STAGGER_WINDOW.  A dedup failure would inflate
      // numSeries to 4 and shrink this spacing to STAGGER_WINDOW/3.
      assertEquals(AnimationConstants.STAGGER_WINDOW, d1b - d1a, 0.01,
         "series count must not be inflated by repeated facet keys");
   }

   /**
    * Adds a standalone {@code inetsoft-line} annotation group (outer g → inner g → path) to the
    * SVG root.  No paired area, modeling a pure line/stacked-line chart.
    *
    * @return the outer {@code inetsoft-line} annotation group
    */
   private static Element addLineSeries(Document doc, Element svg,
                                        String color, String series, String lineD)
   {
      Element lineAnnot = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      lineAnnot.setAttribute("class", SVGSupport.ANNOTATION_LINE);
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_COLOR, color);
      lineAnnot.setAttribute("data-" + SVGSupport.ATTR_SERIES, series);

      Element inner = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      Element linePath = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      linePath.setAttribute("d", lineD);
      linePath.setAttribute("fill", "none");

      inner.appendChild(linePath);
      lineAnnot.appendChild(inner);
      svg.appendChild(lineAnnot);

      return lineAnnot;
   }

   /**
    * Count direct {@code <path>} children of {@code parent} that have a translucent rgba fill
    * (the signature of injected ghost fill polygons).
    */
   private static int countGhostFills(Element parent) {
      int count = 0;
      NodeList children = parent.getChildNodes();
      for(int i = 0; i < children.getLength(); i++) {
         if(children.item(i) instanceof Element e &&
            "path".equals(e.getLocalName()) &&
            e.getAttribute("fill").startsWith("rgba("))
         {
            count++;
         }
      }
      return count;
   }

   // -------------------------------------------------------------------------
   // nearestCellByCtm fallback path test
   // -------------------------------------------------------------------------

   /**
    * When a label's translate-origin sits just outside all cell bounding boxes, the fallback
    * nearest-centre path should still match it to the closest cell.
    */
   @Test
   void nearestCell_fallbackMatchesClosestCellWhenOutsideAllBounds() throws Exception {
      Document doc = newDocument();
      // Cell A: (0,0)–(100,100), centre (50,50).
      Element cellA = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                    Map.of("row", "0", "col", "0", "level", "0"),
                                    0, 0, 100, 100);
      // Cell B: (200,0)–(300,100), centre (250,50).
      Element cellB = addAnnotGroup(doc, SVGSupport.ANNOTATION_TREEMAP,
                                    Map.of("row", "1", "col", "0", "level", "0"),
                                    200, 0, 100, 100);
      // Label at (110,50) — outside both cells, but closer to cell A (distance 60) than B (distance 140).
      Element label = addTextGroup(doc, 110, 50);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);

      assertEquals(SVGSupport.ANNOTATION_TREEMAP_LABEL, label.getAttribute("class"),
                   "fallback label must still be tagged with the label class");
      assertEquals("0", label.getAttribute("data-row"),
                   "fallback must match cell A (row=0), the nearest centre");
   }

   // -------------------------------------------------------------------------
   // Gantt milestone animation tests
   // -------------------------------------------------------------------------

   /**
    * Gantt charts carry the {@code grow:gantt} hint.  The milestone marker ({@code inetsoft-point})
    * must fade in after the last bar finishes — delay ≥ lastBarDelay + DURATION.
    */
   @Test
   void gantt_milestoneFadesAfterBars() throws Exception {
      Document doc = newDocument();
      // Horizontal gantt bars staggered top→bottom by Y center.
      Element bar0 = addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                                   Map.of("row", "0", "col", "0", "orient", "h"),
                                   0,   0, 200, 50);
      Element bar1 = addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                                   Map.of("row", "1", "col", "0", "orient", "h"),
                                   0, 100, 200, 50);
      // Milestone diamond marker.
      Element milestone = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                                        Map.of("row", "0", "col", "0", "size", "9"),
                                        150, 50, 10, 10);
      // Milestone value label (PointVO tags it with the dedicated point-label annotation).
      Element milestoneLabel = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT_LABEL,
                                             Map.of("row", "0", "col", "0"),
                                             150, 35, 30, 12);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
         SVGSupport.ANIMATION_GROW + ":" + SVGSupport.ANIMATION_FLAG_GANTT);

      String milestoneStyle = firstChildStyle(milestone);
      assertNotNull(milestoneStyle, "milestone inner child must receive an animation style");
      assertTrue(milestoneStyle.contains("inetsoft-bar-fade"),
                 "milestone must reuse the bar fade keyframe");

      double lastBarDelay = Math.max(parseDelay(firstChildStyle(bar0)),
                                     parseDelay(firstChildStyle(bar1)));
      double milestoneDelay = parseDelay(milestoneStyle);
      assertTrue(milestoneDelay >= lastBarDelay + AnimationConstants.DURATION - 0.01,
                 "milestone must start after the last bar finishes: milestone=" + milestoneDelay +
                 ", lastBar=" + lastBarDelay);

      // The milestone label must fade in sync with the milestone marker (same delay).
      String labelStyle = firstChildStyle(milestoneLabel);
      assertNotNull(labelStyle, "milestone label inner child must receive an animation style");
      assertTrue(labelStyle.contains("inetsoft-bar-fade"),
                 "milestone label must use the bar fade keyframe");
      assertEquals(milestoneDelay, parseDelay(labelStyle), 0.01,
                   "milestone label must fade in sync with the milestone marker");

      // Bar hover must NOT dim milestone labels; point hover MUST dim other milestone labels.
      // These rules are emitted globally by appendHoverCSS (not gated on the gantt flag); the
      // assertions guard that the point-label class is wired into the correct hover rule.
      String css = allStyleContent(doc.getDocumentElement());
      assertFalse(css.contains(".inetsoft-bar.inetsoft-active) .inetsoft-point-label"),
                  "bar hover must not dim milestone labels");
      assertTrue(css.contains(".inetsoft-point.inetsoft-active) .inetsoft-point-label:not(.inetsoft-active)"),
                 "point hover must dim other milestone labels");
   }

   /** Gantt with no milestone field has no {@code inetsoft-point} group; injection must no-op cleanly. */
   @Test
   void gantt_noMilestone_noError() throws Exception {
      Document doc = newDocument();
      addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                    Map.of("row", "0", "col", "0", "orient", "h"),
                    0, 0, 200, 50);

      assertDoesNotThrow(() -> SVGAnimationDOMInjector.injectAnimation(
         doc.getDocumentElement(),
         SVGSupport.ANIMATION_GROW + ":" + SVGSupport.ANIMATION_FLAG_GANTT));
      assertTrue(doc.getDocumentElement().hasAttribute("data-animated"),
                 "data-animated must be set even when there is no milestone point");
   }

   /**
    * A stacked gantt (multi-resource task bars) produces the {@code grow:stacked:gantt} hint.
    * The gantt flag must still be detected regardless of position, so the milestone fades after
    * the bars exactly as in the unstacked case.
    */
   @Test
   void gantt_stackedMilestoneFadesAfterBars() throws Exception {
      Document doc = newDocument();
      Element bar0 = addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                                   Map.of("row", "0", "col", "0", "orient", "h"),
                                   0,   0, 200, 50);
      Element bar1 = addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                                   Map.of("row", "1", "col", "0", "orient", "h"),
                                   0, 100, 200, 50);
      Element milestone = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                                        Map.of("row", "0", "col", "0", "size", "9"),
                                        150, 50, 10, 10);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
         SVGSupport.ANIMATION_GROW + ":" + SVGSupport.ANIMATION_FLAG_STACKED +
         ":" + SVGSupport.ANIMATION_FLAG_GANTT);

      String milestoneStyle = firstChildStyle(milestone);
      assertNotNull(milestoneStyle, "milestone inner child must receive an animation style");
      double lastBarDelay = Math.max(parseDelay(firstChildStyle(bar0)),
                                     parseDelay(firstChildStyle(bar1)));
      assertTrue(parseDelay(milestoneStyle) >= lastBarDelay + AnimationConstants.DURATION - 0.01,
                 "milestone must start after the last bar finishes for grow:stacked:gantt");
   }

   /**
    * A combo bar+point chart (plain {@code grow} hint, no gantt flag) must NOT animate its
    * point overlay — the milestone fade is strictly gated on the gantt flag.
    */
   @Test
   void combo_barPoint_noGanttFlag_pointsNotAnimated() throws Exception {
      Document doc = newDocument();
      addAnnotGroup(doc, SVGSupport.ANNOTATION_BAR,
                    Map.of("row", "0", "col", "0", "orient", "v"),
                    0, 0, 50, 200);
      Element point = addAnnotGroup(doc, SVGSupport.ANNOTATION_POINT,
                                    Map.of("row", "0", "col", "0", "size", "9"),
                                    20, 20, 10, 10);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_GROW);

      String pointStyle = firstChildStyle(point);
      assertTrue(pointStyle == null || pointStyle.isEmpty(),
                 "combo chart point overlay must not receive a fade without the gantt flag");
   }

   // -------------------------------------------------------------------------
   // Faceted pie animation tests
   // -------------------------------------------------------------------------

   /**
    * Adds a pie slice as a Batik-style {@code <path stroke="none">} wedge of the form
    * {@code M sx sy A rx ry 0 la sw ex ey L cx cy Z}, where (cx,cy) is the pie hub.
    *
    * @return the slice path element
    */
   private static Element addPieSlice(Document doc, double sx, double sy, double r,
                                      int la, int sw, double ex, double ey,
                                      double cx, double cy)
   {
      return addPieSliceTo(doc.getDocumentElement(), doc, sx, sy, r, la, sw, ex, ey, cx, cy);
   }

   /** As {@link #addPieSlice} but appends the slice to a given parent (e.g. a transformed group). */
   private static Element addPieSliceTo(Element parent, Document doc, double sx, double sy, double r,
                                        int la, int sw, double ex, double ey, double cx, double cy)
   {
      Element path = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      path.setAttribute("stroke", "none");
      path.setAttribute("d", String.format(Locale.US,
         "M%.4f %.4f A%.4f %.4f 0 %d %d %.4f %.4f L%.4f %.4f Z",
         sx, sy, r, r, la, sw, ex, ey, cx, cy));
      parent.appendChild(path);
      return path;
   }

   /**
    * Adds a realistic bezier donut ring slice ({@code M …C… L …C… Z}, no arc command, no center
    * hub) whose two radial edges intersect at (cx,cy) — the shape real donut slices take, since
    * they are a filled Area serialized by Batik as cubic beziers.  The slice spans the given
    * start angle to start+sweep (degrees) between innerR=10 and outerR=30, so different slices of
    * the same donut share the center while the bezier control points differ.
    */
   private static Element addRingSlice(Document doc, double cx, double cy,
                                       double startDeg, double sweepDeg)
   {
      double a1 = Math.toRadians(startDeg), a2 = Math.toRadians(startDeg + sweepDeg);
      double oR = 30, iR = 10;
      double oSx = cx + oR * Math.cos(a1), oSy = cy + oR * Math.sin(a1);   // outer start (a1)
      double oEx = cx + oR * Math.cos(a2), oEy = cy + oR * Math.sin(a2);   // outer end   (a2)
      double iSx = cx + iR * Math.cos(a2), iSy = cy + iR * Math.sin(a2);   // inner start (a2)
      double iEx = cx + iR * Math.cos(a1), iEy = cy + iR * Math.sin(a1);   // inner end   (a1)
      // Cubic control points along the tangents (k = 4/3·tan(sweep/4)), the standard bezier arc
      // approximation Batik emits — so the path is a real curved ring, not degenerate lines.
      double ko = 4.0 / 3.0 * Math.tan((a2 - a1) / 4), ki = 4.0 / 3.0 * Math.tan((a1 - a2) / 4);
      double oc1x = oSx - ko * oR * Math.sin(a1), oc1y = oSy + ko * oR * Math.cos(a1);
      double oc2x = oEx + ko * oR * Math.sin(a2), oc2y = oEy - ko * oR * Math.cos(a2);
      double ic1x = iSx - ki * iR * Math.sin(a2), ic1y = iSy + ki * iR * Math.cos(a2);
      double ic2x = iEx + ki * iR * Math.sin(a1), ic2y = iEy - ki * iR * Math.cos(a1);
      Element svg = doc.getDocumentElement();
      Element path = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      path.setAttribute("stroke", "none");
      path.setAttribute("d", String.format(Locale.US,
         "M%.4f %.4f C%.4f %.4f %.4f %.4f %.4f %.4f L%.4f %.4f C%.4f %.4f %.4f %.4f %.4f %.4f Z",
         oSx, oSy, oc1x, oc1y, oc2x, oc2y, oEx, oEy, iSx, iSy, ic1x, ic1y, ic2x, ic2y, iEx, iEy));
      svg.appendChild(path);
      return path;
   }

   /** Adds a donut center-hole overlay: a {@code <g class="inetsoft-bar">} wrapping a circle. */
   private static Element addHoleOverlay(Document doc, double cx, double cy, double r) {
      Element svg = doc.getDocumentElement();
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("class", SVGSupport.ANNOTATION_BAR);
      Element circle = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "circle");
      circle.setAttribute("cx", String.valueOf(cx));
      circle.setAttribute("cy", String.valueOf(cy));
      circle.setAttribute("r", String.valueOf(r));
      g.appendChild(circle);
      svg.appendChild(g);
      return g;
   }

   /** Parses the final {@code "L cx cy Z"} hub point from a slice path. */
   private static double[] parseHub(String d) {
      Matcher m = Pattern.compile(
         "L\\s*(-?\\d+\\.?\\d*)\\s+(-?\\d+\\.?\\d*)\\s*Z").matcher(d);
      double[] hub = null;

      while(m.find()) {
         hub = new double[]{ Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
      }

      return hub;
   }

   /** Extracts the {@code inetsoft-pie-sweep-N} keyframe name referenced by a slice's style. */
   private static String sweepKeyframeName(String style) {
      Matcher m = Pattern.compile("inetsoft-pie-sweep-\\d+").matcher(style);
      return m.find() ? m.group() : null;
   }

   /** Asserts every arc endpoint in the named sweep keyframe lies on the circle (cx,cy,r). */
   private static void assertSweepOnCircle(String css, String kfName,
                                           double cx, double cy, double r)
   {
      String block = keyframeBlock(css, kfName);
      Matcher pm = Pattern.compile("path\\('([^']*)'\\)").matcher(block);
      int checked = 0;

      while(pm.find()) {
         String path = pm.group(1);
         String arc = path.substring(path.indexOf('A'), path.indexOf('L'));
         Matcher nm = Pattern.compile("-?\\d+\\.?\\d*").matcher(arc);
         List<Double> nums = new ArrayList<>();
         while(nm.find()) nums.add(Double.parseDouble(nm.group()));
         double ex = nums.get(nums.size() - 2), ey = nums.get(nums.size() - 1);
         assertEquals(r, Math.hypot(ex - cx, ey - cy), 0.5,
                      kfName + " arc endpoint must stay on circle around (" + cx + "," + cy + ")");
         checked++;
      }

      assertTrue(checked >= 2, "expected multiple sweep keyframes for " + kfName);
   }

   /** Returns the body of the named {@code @keyframes} rule via brace matching. */
   private static String keyframeBlock(String css, String name) {
      int header = css.indexOf("@keyframes " + name + "{");
      assertTrue(header >= 0, "keyframes rule " + name + " must exist");
      int open = css.indexOf('{', header);
      int depth = 0;

      for(int i = open; i < css.length(); i++) {
         if(css.charAt(i) == '{') depth++;
         else if(css.charAt(i) == '}' && --depth == 0) return css.substring(open + 1, i);
      }

      return "";
   }

   /**
    * Two pie panels rendered into one SVG (a facet chart) must each sweep around their OWN hub.
    * Pre-fix, a single global center was used for every slice, so the second panel's wedges
    * collapsed/cut across the interior — this asserts each slice keeps its own hub center.
    */
   @Test
   void facetedPie_eachPanelSweepsAroundOwnCenter() throws Exception {
      Document doc = newDocument();
      // Panel 1: center (100,100), r=80. Panel 2: center (400,100), r=80. Two slices each.
      Element a = addPieSlice(doc, 180, 100, 80, 0, 1, 100, 180, 100, 100);
      Element b = addPieSlice(doc, 100, 180, 80, 0, 1,  20, 100, 100, 100);
      Element c = addPieSlice(doc, 480, 100, 80, 0, 1, 400, 180, 400, 100);
      Element e = addPieSlice(doc, 400, 180, 80, 0, 1, 320, 100, 400, 100);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      // Each slice's rewritten from-state must retain its OWN panel center as the hub.
      assertArrayEquals(new double[]{100, 100}, parseHub(a.getAttribute("d")), 0.01,
                        "panel-1 slice must keep hub (100,100)");
      assertArrayEquals(new double[]{400, 100}, parseHub(c.getAttribute("d")), 0.01,
                        "panel-2 slice must keep hub (400,100), not the global first-pie center");

      // Distinct keyframe names for all four arc slices (no cross-panel collision).
      Set<String> names = new HashSet<>();
      for(Element s : List.of(a, b, c, e)) {
         names.add(sweepKeyframeName(s.getAttribute("style")));
      }
      assertEquals(4, names.size(), "each slice must reference a unique sweep keyframe");

      // Panel-2 slice keyframe endpoints must lie on the circle around (400,100), radius 80.
      String css = allStyleContent(doc.getDocumentElement());
      String block = keyframeBlock(css, sweepKeyframeName(c.getAttribute("style")));
      Matcher pm = Pattern.compile("path\\('([^']*)'\\)").matcher(block);
      int checked = 0;

      while(pm.find()) {
         String path = pm.group(1);
         String arc = path.substring(path.indexOf('A'), path.indexOf('L'));
         Matcher nm = Pattern.compile("-?\\d+\\.?\\d*").matcher(arc);
         List<Double> nums = new ArrayList<>();
         while(nm.find()) nums.add(Double.parseDouble(nm.group()));
         double ex = nums.get(nums.size() - 2), ey = nums.get(nums.size() - 1);
         assertEquals(80.0, Math.hypot(ex - 400, ey - 100), 0.5,
                      "panel-2 arc endpoint must stay on its own outer circle");
         checked++;
      }

      assertTrue(checked >= 2, "expected multiple sweep keyframes for the panel-2 slice");
   }

   /** In a facet pie every panel begins sweeping at t=0 (panels animate in parallel). */
   @Test
   void facetedPie_panelsAnimateInParallel() throws Exception {
      Document doc = newDocument();
      Element a = addPieSlice(doc, 180, 100, 80, 0, 1, 100, 180, 100, 100);
      addPieSlice(doc, 100, 180, 80, 0, 1, 20, 100, 100, 100);
      Element c = addPieSlice(doc, 480, 100, 80, 0, 1, 400, 180, 400, 100);
      addPieSlice(doc, 400, 180, 80, 0, 1, 320, 100, 400, 100);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      // The first slice of each panel (its first arc group) begins at delay 0.
      assertEquals(0.0, parseDelay(a.getAttribute("style")), 0.001,
                   "panel-1 first slice must begin at t=0");
      assertEquals(0.0, parseDelay(c.getAttribute("style")), 0.001,
                   "panel-2 first slice must also begin at t=0 (parallel, not after panel 1)");
   }

   /** A faceted donut: each panel must be matched to its OWN center-hole and animate as a ring. */
   @Test
   void facetedDonut_eachPanelMatchedToOwnHole() throws Exception {
      Document doc = newDocument();
      // Full wedges (hub = center) plus a separate hole overlay per panel (the donut pattern).
      addPieSlice(doc, 180, 100, 80, 0, 1, 100, 180, 100, 100);
      Element c = addPieSlice(doc, 480, 100, 80, 0, 1, 400, 180, 400, 100);
      Element hole1 = addHoleOverlay(doc, 100, 100, 30);
      Element hole2 = addHoleOverlay(doc, 400, 100, 30);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      // Both overlays reclassified to the donut-hole class.
      assertEquals(SVGSupport.ANNOTATION_DONUT_HOLE, hole1.getAttribute("class"));
      assertEquals(SVGSupport.ANNOTATION_DONUT_HOLE, hole2.getAttribute("class"));

      // Panel-2 slice must animate as a ring (two arcs: outer + inner) — proving it was matched
      // to hole2 rather than left as a center-spoke wedge (a single arc).
      long arcCount = c.getAttribute("d").chars().filter(ch -> ch == 'A').count();
      assertEquals(2, arcCount, "panel-2 donut slice must be a ring (outer + inner arc)");
   }

   /**
    * A flat pie with symmetric slices (the no-measure case) has slices whose start points share
    * an x-coordinate (mirror angles about the horizontal axis).  Each such slice must still sweep
    * around the true pie center.  Pre-fix, the startX grouping merged them and reconstructed a
    * bogus center from another slice's Y, collapsing the wedge into a gap.
    */
   @Test
   void flatPie_symmetricSlicesSweepAroundOwnCenter() throws Exception {
      Document doc = newDocument();
      // Pie center (200,200), r=100. Slice B starts at +60deg, slice A at -60deg: both have
      // start x = 250, so the startX grouping would merge them. B is added first.
      Element b = addPieSlice(doc, 250, 286.60254, 100, 0, 1, 165.79799, 293.96926, 200, 200);
      Element a = addPieSlice(doc, 250, 113.39746, 100, 0, 1, 298.48078, 182.63518, 200, 200);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      // Both slices keep the true pie center as their hub (not a borrowed Y from the other slice).
      assertArrayEquals(new double[]{200, 200}, parseHub(a.getAttribute("d")), 0.01,
                        "upper mirror slice must sweep around the true center, not a bogus one");
      assertArrayEquals(new double[]{200, 200}, parseHub(b.getAttribute("d")), 0.01,
                        "lower mirror slice must sweep around the true center");

      // Both must actually sweep (neither misclassified as a 3D depth face), around (200,200).
      String css = allStyleContent(doc.getDocumentElement());
      String kfA = sweepKeyframeName(a.getAttribute("style"));
      String kfB = sweepKeyframeName(b.getAttribute("style"));
      assertNotNull(kfA, "upper mirror slice must receive a sweep animation");
      assertNotNull(kfB, "lower mirror slice must receive a sweep animation");
      assertSweepOnCircle(css, kfA, 200, 200, 100);
      assertSweepOnCircle(css, kfB, 200, 200, 100);
   }

   /**
    * Symmetric slices that share a start x-coordinate must still animate one-at-a-time: keying
    * the wheel sequence on the start angle (not the start x) keeps mirror slices on distinct
    * sequential delays.  Pre-fix the shared startX merged them onto one delay (simultaneous).
    */
   @Test
   void flatPie_symmetricSlicesAnimateSequentially() throws Exception {
      Document doc = newDocument();
      // Both start at x=250 (mirror angles +/-60deg about the horizontal). B added first.
      Element b = addPieSlice(doc, 250, 286.60254, 100, 0, 1, 165.79799, 293.96926, 200, 200);
      Element a = addPieSlice(doc, 250, 113.39746, 100, 0, 1, 298.48078, 182.63518, 200, 200);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      double delayB = parseDelay(b.getAttribute("style"));
      double delayA = parseDelay(a.getAttribute("style"));
      assertEquals(0.0, delayB, 0.001, "first slice in DOM order begins the wheel at t=0");
      assertTrue(delayA > delayB + 0.001,
                 "the mirror slice must follow sequentially, not animate at the same time: " +
                 "delayA=" + delayA + ", delayB=" + delayB);
   }

   /**
    * Real donut slices are bezier rings (a filled Area) with no arc command, no center hub, and
    * NO separate hole overlay — the hole is the empty ring center.  Facet position is baked into
    * the absolute coordinates.  A faceted donut must animate its panels in parallel: each facet,
    * identified by the intersection of its slices' radial edges, staggers from t=0.  Pre-fix, all
    * ring slices fell into one global sequence, so later facets started only after earlier facets
    * finished — the reported "each plot renders sequentially" symptom.
    */
   @Test
   void facetedDonut_bezierRingsAnimatePanelsInParallel() throws Exception {
      Document doc = newDocument();
      // Facet 1 centered at (100,100); facet 2 at (400,100). Two ring slices each, no overlays.
      Element f1s0 = addRingSlice(doc, 100, 100, 0, 90);
      Element f1s1 = addRingSlice(doc, 100, 100, 90, 90);
      Element f2s0 = addRingSlice(doc, 400, 100, 0, 90);
      Element f2s1 = addRingSlice(doc, 400, 100, 90, 90);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      double step = AnimationConstants.PIE_SLICE_DURATION;
      // Each facet restarts the stagger at 0 — both first slices begin together.
      assertEquals(0.0, parseDelay(f1s0.getAttribute("style")), 0.001,
                   "facet 1 first slice begins at t=0");
      assertEquals(0.0, parseDelay(f2s0.getAttribute("style")), 0.001,
                   "facet 2 first slice must also begin at t=0, not after facet 1");
      // Within each facet the slices still stagger one after another.
      assertEquals(step, parseDelay(f1s1.getAttribute("style")), 0.001,
                   "facet 1 second slice staggers within its own panel");
      assertEquals(step, parseDelay(f2s1.getAttribute("style")), 0.001,
                   "facet 2 second slice staggers within its own panel");
      // Bezier ring slices must take the opacity-fade path, never the arc sweep.
      assertFalse(f1s0.getAttribute("style").contains("inetsoft-pie-sweep"),
                  "bezier ring slice must not use the geometric sweep");
      assertFalse(f2s0.getAttribute("style").contains("inetsoft-pie-sweep"),
                  "bezier ring slice must not use the geometric sweep");
   }

   /**
    * A pie larger than the tile (split/clipped across SVG tiles) must fall back to the opacity
    * fade and NOT rewrite slice geometry: the arc sweep's rewritten {@code d} does not survive
    * per-tile clipping (the exploded/malformed-final-state bug), whereas a pure opacity fade
    * leaves the correct (statically clipped) wedge intact.
    */
   @Test
   void largePie_tiledFallsBackToFade() throws Exception {
      Document doc = newDocument();   // viewBox 0 0 800 600
      // Pie center (600,300), r=500 → bbox width 1000 > 800 (viewport): larger than the tile.
      Element a = addPieSlice(doc, 1100, 300, 500, 0, 1, 600, 800, 600, 300);
      Element b = addPieSlice(doc, 600, 800, 500, 0, 1, 100, 300, 600, 300);
      String origA = a.getAttribute("d");
      String origB = b.getAttribute("d");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      for(Element s : List.of(a, b)) {
         String style = s.getAttribute("style");
         assertTrue(style.contains("inetsoft-pie-fade"), "tiled pie slice must use the opacity fade");
         assertFalse(style.contains("inetsoft-pie-sweep"),
                     "tiled pie slice must NOT use the geometric sweep");
      }
      // Geometry must be untouched (still the original full wedge, not a rewritten from-state).
      assertEquals(origA, a.getAttribute("d"), "fade must not rewrite slice d");
      assertEquals(origB, b.getAttribute("d"), "fade must not rewrite slice d");
   }

   /** A normal-size pie (fits within the tile viewport) must still use the wheel-sweep. */
   @Test
   void normalPie_usesSweep() throws Exception {
      Document doc = newDocument();   // viewBox 0 0 800 600
      // Pie center (400,300), r=100 → bbox width 200 < 800: fits in the tile.
      Element a = addPieSlice(doc, 500, 300, 100, 0, 1, 400, 400, 400, 300);
      Element b = addPieSlice(doc, 400, 400, 100, 0, 1, 300, 300, 400, 300);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      assertTrue(a.getAttribute("style").contains("inetsoft-pie-sweep"),
                 "normal-size pie must keep the wheel-sweep");
   }

   /**
    * A pie small enough to fit a tile by size but positioned (via its parent transform) so it
    * straddles/exceeds the tile must also fall back to the fade: containment is tested in the
    * tile's coordinate space, not just by extent.  Covers the faceted-and-tiled seam case.
    */
   @Test
   void pieClippedByTileViaTransform_fallsBackToFade() throws Exception {
      Document doc = newDocument();   // viewBox 0 0 800 600
      // Small pie (center 100,100, r=80) inside a group translated so it spills past x=800.
      Element g = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "g");
      g.setAttribute("transform", "translate(760,0)");
      doc.getDocumentElement().appendChild(g);
      Element a = addPieSliceTo(g, doc, 180, 100, 80, 0, 1, 100, 180, 100, 100);
      Element b = addPieSliceTo(g, doc, 100, 180, 80, 0, 1, 20, 100, 100, 100);
      String origA = a.getAttribute("d");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      assertTrue(a.getAttribute("style").contains("inetsoft-pie-fade"),
                 "pie straddling the tile edge must use the fade");
      assertFalse(a.getAttribute("style").contains("inetsoft-pie-sweep"),
                  "pie straddling the tile edge must NOT use the sweep");
      assertEquals(origA, a.getAttribute("d"), "fade must not rewrite slice d");
   }

   /**
    * A faceted 3D pie (top arc face + non-arc depth quad per panel) must animate every slice,
    * with each top face sweeping around its own panel center.
    */
   @Test
   void faceted3DPie_assignsDepthFacesAndSweepsPerPanel() throws Exception {
      Document doc = newDocument();
      Element top1 = addPieSlice(doc, 180, 100, 80, 0, 1, 100, 160, 100, 100);
      Element top2 = addPieSlice(doc, 480, 100, 80, 0, 1, 400, 160, 400, 100);
      // Depth quads (no arc) under each panel's footprint.
      Element depth1 = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      depth1.setAttribute("stroke", "none");
      depth1.setAttribute("d", "M100 160 L180 100 L180 120 L100 180 Z");
      doc.getDocumentElement().appendChild(depth1);
      Element depth2 = doc.createElementNS(SVGAnimationDOMInjector.SVG_NS, "path");
      depth2.setAttribute("stroke", "none");
      depth2.setAttribute("d", "M400 160 L480 100 L480 120 L400 180 Z");
      doc.getDocumentElement().appendChild(depth2);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_PIE);

      assertArrayEquals(new double[]{400, 100}, parseHub(top2.getAttribute("d")), 0.01,
                        "panel-2 top face must sweep around its own center");
      // Every slice (top faces and depth quads) must receive an animation.
      for(Element s : List.of(top1, top2, depth1, depth2)) {
         assertTrue(s.getAttribute("style").contains("animation:"),
                    "every 3D pie face must receive an animation, including depth quads");
      }
   }

   // -------------------------------------------------------------------------
   // Relation / tree / network chart tests
   // -------------------------------------------------------------------------

   /** Adds a relation node annotation group with a stamped id and screen-Y. */
   private static Element relNode(Document doc, String id, double y) {
      Map<String, String> attrs = new LinkedHashMap<>();
      attrs.put(SVGSupport.ATTR_NODE_ID, id);
      attrs.put(SVGSupport.ATTR_Y, String.valueOf(y));
      return addAnnotGroup(doc, SVGSupport.ANNOTATION_RELATION, attrs, 0, y, 20, 16);
   }

   /** Adds a relation edge annotation group linking source→target node ids. */
   private static Element relEdge(Document doc, String source, String target) {
      Map<String, String> attrs = new LinkedHashMap<>();
      attrs.put(SVGSupport.ATTR_SOURCE, source);
      attrs.put(SVGSupport.ATTR_TARGET, target);
      return addAnnotGroup(doc, SVGSupport.ANNOTATION_RELATION_EDGE, attrs, 0, 0, 20, 2);
   }

   /**
    * A linear tree (root → child → grandchild, one node per depth) staggers strictly root-first:
    * delay grows by exactly {@link AnimationConstants#RELATION_LEVEL_STEP} per depth level.
    */
   @Test
   void relationTopologyStaggersRootBeforeLeaf() throws Exception {
      Document doc = newDocument();
      Element root = relNode(doc, "R", 100);
      Element child = relNode(doc, "A", 200);
      Element leaf = relNode(doc, "C", 300);
      relEdge(doc, "R", "A");
      relEdge(doc, "A", "C");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      double step = AnimationConstants.RELATION_LEVEL_STEP;
      assertEquals(0.0, parseDelay(firstChildStyle(root)), 0.001, "root → depth 0");
      assertEquals(step, parseDelay(firstChildStyle(child)), 0.001, "child → depth 1");
      assertEquals(2 * step, parseDelay(firstChildStyle(leaf)), 0.001, "grandchild → depth 2");
   }

   /**
    * Reveal order comes from the edge topology, not screen geometry: a root placed at the BOTTOM
    * of the chart (largest Y) still reveals first, where the legacy Y-clustering would reveal it
    * last.  Proves orientation-independence (bottom-up / left-right / radial trees).
    */
   @Test
   void relationDepthIsTopologyNotGeometry() throws Exception {
      Document doc = newDocument();
      Element root = relNode(doc, "R", 500);   // bottom of the chart
      Element child = relNode(doc, "A", 100);  // top of the chart
      relEdge(doc, "R", "A");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      assertEquals(0.0, parseDelay(firstChildStyle(root)), 0.001,
                   "topological root reveals first even though its Y is largest");
      assertTrue(parseDelay(firstChildStyle(child)) > parseDelay(firstChildStyle(root)),
                 "child reveals after its root regardless of geometry");
   }

   /**
    * Regression for the null-parent sentinel (Redmine #74993): a root whose parent is stamped with
    * the literal id {@code "null"} (RelationElement.getId's sentinel for a null "from" value) must
    * still be detected as a depth-0 root — the sentinel edge must not push it to depth 1.  A
    * depth-0 node's total delay is always below one {@link AnimationConstants#RELATION_LEVEL_STEP}
    * (base 0 + intra-level spread &lt; step), whereas a depth-1 node is at or above one step.
    */
   @Test
   void relationNullParentSentinelDetectsRealRoot() throws Exception {
      Document doc = newDocument();
      relNode(doc, "null", 50);                // sentinel node getId(...) == "null"
      Element root = relNode(doc, "R", 100);
      Element child = relNode(doc, "A", 200);
      relEdge(doc, "null", "R");               // root's parent is the null sentinel
      relEdge(doc, "R", "A");                  // a real edge keeps the topology path active

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      double step = AnimationConstants.RELATION_LEVEL_STEP;
      assertTrue(parseDelay(firstChildStyle(root)) < step,
                 "real root stays depth 0 (delay < one step) despite the null-parent sentinel edge");
      assertEquals(step, parseDelay(firstChildStyle(child)), 0.001,
                   "child of the real root is depth 1");
   }

   /**
    * Siblings at the same depth do not all fire at once: the intra-level spread gives each its own
    * moment (in DOM order), while keeping them all within one step of the depth base.
    */
   @Test
   void relationIntraLevelSpreadTricklesSiblings() throws Exception {
      Document doc = newDocument();
      relNode(doc, "R", 50);
      Element a = relNode(doc, "A", 100);
      Element b = relNode(doc, "B", 110);
      Element c = relNode(doc, "C", 120);
      relEdge(doc, "R", "A");
      relEdge(doc, "R", "B");
      relEdge(doc, "R", "C");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      double da = parseDelay(firstChildStyle(a));
      double db = parseDelay(firstChildStyle(b));
      double dc = parseDelay(firstChildStyle(c));
      double step = AnimationConstants.RELATION_LEVEL_STEP;

      assertTrue(da < db && db < dc,
                 "same-depth siblings stagger individually: " + da + " < " + db + " < " + dc);
      assertTrue(da >= step - 0.001 && dc < 2 * step,
                 "sibling delays lie within [depth-1 base, base + one step)");
   }

   /**
    * A fully cyclic graph has no root, so depth cannot be derived — the injector falls back to the
    * geometry (Y-centre) ordering.  It must not throw, every node must still animate, and the
    * higher (smaller-Y) node must reveal no later than the lower one.
    */
   @Test
   void relationCyclicGraphFallsBackToGeometry() throws Exception {
      Document doc = newDocument();
      Element top = relNode(doc, "A", 100);
      Element bottom = relNode(doc, "B", 300);
      relEdge(doc, "A", "B");
      relEdge(doc, "B", "A");   // cycle → no root → geometry fallback

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      assertTrue(firstChildStyle(top).contains("inetsoft-relation-fade"),
                 "cyclic-graph nodes still animate via the geometry fallback");
      assertTrue(firstChildStyle(bottom).contains("inetsoft-relation-fade"), "both nodes animate");
      assertTrue(parseDelay(firstChildStyle(top)) <= parseDelay(firstChildStyle(bottom)),
                 "top (smaller Y) reveals before bottom in the geometry fallback");
   }

   /**
    * Edges inherit their target (child) node's exact delay so an edge fades in together with the
    * node it points to, not merely at the same depth band.
    */
   @Test
   void relationEdgeInheritsTargetNodeDelay() throws Exception {
      Document doc = newDocument();
      relNode(doc, "R", 100);
      Element child = relNode(doc, "A", 200);
      Element edge = relEdge(doc, "R", "A");

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_RELATION);

      assertEquals(parseDelay(firstChildStyle(child)), parseDelay(firstChildStyle(edge)), 0.001,
                   "edge fades in with its target node (same delay)");
   }
}
