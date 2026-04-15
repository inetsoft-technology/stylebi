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
 *   <li>Icicle cascade stagger — root depth animates before leaf depth at 0.25 s/level.</li>
 *   <li>Mekko diagonal stagger — {@code delay = colIdx * 0.08 + rowIdx * 0.05}.</li>
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
      assertTrue(css.contains("opacity:.2!important"),
                 "dim rules must use opacity:.2!important");
   }

   @Test
   void hoverCssTransitionIsUniform() throws Exception {
      Document doc = newDocument();
      SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(), SVGSupport.ANIMATION_TREEMAP);
      String css = allStyleContent(doc.getDocumentElement());

      assertTrue(css.contains("transition:opacity .2s ease"),
                 "all chart types must share the uniform transition:opacity .2s ease");
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
    * Each depth column adds {@code DEPTH_STEP = 0.25 s} of base delay.  With three levels
    * (2, 1, 0) and one cell per level, delays should be 0.0, 0.25, and 0.50 respectively.
    */
   @Test
   void icicle_depthStepIs025PerLevel() throws Exception {
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
      assertEquals(0.25, parseDelay(firstChildStyle(lvl1)), 0.01, "mid   (level=1) → 0.25 s");
      assertEquals(0.50, parseDelay(firstChildStyle(lvl0)), 0.01, "leaf  (level=0) → 0.50 s");
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
    * Verifies the diagonal stagger formula: {@code delay = colIdx * 0.08 + rowIdx * 0.05}.
    * <ul>
    *   <li>Column 0, row 0: 0.00 s</li>
    *   <li>Column 0, row 1: 0.05 s</li>
    *   <li>Column 1, row 0: 0.08 s</li>
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

      // Mekko sets the animation style directly on the annotation group element.
      assertEquals(0.00, parseDelay(col0row0.getAttribute("style")), 0.01, "col0 row0 → 0.00 s");
      assertEquals(0.05, parseDelay(col0row1.getAttribute("style")), 0.01, "col0 row1 → 0.05 s");
      assertEquals(0.08, parseDelay(col1row0.getAttribute("style")), 0.01, "col1 row0 → 0.08 s");
   }

   /**
    * Two cells in the same column whose y-tops round to the same pixel value must not cause an
    * exception.  {@code putIfAbsent} prevents a second cell from overwriting the first entry,
    * but both cells still receive a valid animation style.
    */
   @Test
   void mekko_duplicateYtopDoesNotThrow() throws Exception {
      Document doc = newDocument();
      // y=10.0 and y=10.4 both round to 10 — same rowMap key after Math.round().
      addMekkoCell(doc, "0", "0", 0, 10.0, 100, 90);
      addMekkoCell(doc, "1", "0", 0, 10.4, 100, 90);

      assertDoesNotThrow(
         () -> SVGAnimationDOMInjector.injectAnimation(doc.getDocumentElement(),
                                                       SVGSupport.ANIMATION_MEKKO));
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
}
