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
    * Box-plot groups follow the same left-to-right delay ordering as candle groups —
    * both use {@link SVGAnimationDOMInjector#injectXPositionFadeAnimation}.
    */
   @Test
   void box_leftToRightDelayOrdering() throws Exception {
      Document doc = newDocument();
      Element left  = addAnnotGroup(doc, SVGSupport.ANNOTATION_BOX,
                                    Map.of("row", "0", "col", "0", "x", "50"),
                                    0, 0, 10, 50);
      Element right = addAnnotGroup(doc, SVGSupport.ANNOTATION_BOX,
                                    Map.of("row", "1", "col", "0", "x", "150"),
                                    100, 0, 10, 50);

      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_BOX);

      double delayLeft  = parseDelay(left.getAttribute("style"));
      double delayRight = parseDelay(right.getAttribute("style"));

      assertEquals(0.0, delayLeft,  0.01, "leftmost box must start with delay 0");
      assertTrue(delayRight > delayLeft, "right box (x=150) must animate after left (x=50)");
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
}
