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
package inetsoft.util.graphics;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.*;
import java.net.URL;
import java.util.Map;

public interface SVGSupport {
   // -------------------------------------------------------------------------
   // Animation rendering hint
   // -------------------------------------------------------------------------

   /** Rendering hint key used to request CSS animation in the SVG output.
    *  Set the value to one of the {@code ANIMATION_*} constants below. */
   RenderingHints.Key ANIMATION_KEY = new AnimationKey();

   /** Bars grow from the baseline with a spring easing (default animation style). */
   String ANIMATION_GROW = "grow";

   /** Bars fade in from transparent (simpler alternative to grow). */
   String ANIMATION_FADE = "fade";

   /** Pie/donut slices sweep in from the start angle, staggered by slice order. */
   String ANIMATION_PIE = "pie";

   /** Line/area series draw on from left using stroke-dasharray, dots pop with spring easing. */
   String ANIMATION_LINE = "line";

   /** Point/scatter chart markers fade in, staggered largest-first. */
   String ANIMATION_POINT = "point";

   /** Candlestick chart candles fade in, staggered left-to-right (chronological order). */
   String ANIMATION_CANDLE = "candle";

   /** Box-plot boxes fade in, staggered left-to-right. */
   String ANIMATION_BOX = "box";

   /** Radar/spider chart polygons spring out from the chart center, staggered by series. */
   String ANIMATION_RADAR = "radar";

   // Sub-type flags appended to the base hint with ":" separators.
   // The injector parses these to handle each chart variant correctly.

   /** 3D chart variant (pie or bar). Example hints: "pie:3d", "grow:3d". */
   String ANIMATION_FLAG_3D = "3d";

   /** Stacked bar chart. Example hint: "grow:stacked". */
   String ANIMATION_FLAG_STACKED = "stacked";

   // -------------------------------------------------------------------------
   // Semantic annotation constants (stamped on <g> elements during rendering)
   // -------------------------------------------------------------------------

   /** CSS class for bar annotation groups ({@code <g class="inetsoft-bar" ...>}). */
   String ANNOTATION_BAR        = "inetsoft-bar";
   /** CSS class for pie/donut annotation groups. */
   String ANNOTATION_PIE        = "inetsoft-pie";
   /** CSS class for line series annotation groups. */
   String ANNOTATION_LINE       = "inetsoft-line";
   /** CSS class for area-fill annotation groups. */
   String ANNOTATION_AREA       = "inetsoft-area";
   /** CSS class for the donut center-hole overlay (white circle that punches out the center). */
   String ANNOTATION_DONUT_HOLE = "inetsoft-bar-hole";
   /** CSS class for value-label annotation groups, paired with their bar/slice by data-row/col. */
   String ANNOTATION_LABEL      = "inetsoft-bar-label";
   /** CSS class for point/scatter annotation groups ({@code <g class="inetsoft-point" ...>}). */
   String ANNOTATION_POINT      = "inetsoft-point";
   /** CSS class for candlestick annotation groups ({@code <g class="inetsoft-candle" ...>}). */
   String ANNOTATION_CANDLE     = "inetsoft-candle";
   /** CSS class for box-plot annotation groups ({@code <g class="inetsoft-box" ...>}). */
   String ANNOTATION_BOX        = "inetsoft-box";
   /** CSS class for radar/spider chart annotation groups ({@code <g class="inetsoft-radar" ...>}). */
   String ANNOTATION_RADAR      = "inetsoft-radar";
   /** CSS class for treemap/sunburst/icicle annotation groups ({@code <g class="inetsoft-treemap" ...>}). */
   String ANNOTATION_TREEMAP       = "inetsoft-treemap";
   /** CSS class for external text labels matched to treemap/sunburst/icicle cells. */
   String ANNOTATION_TREEMAP_LABEL = "inetsoft-treemap-label";
   /** CSS class for marimekko chart annotation groups ({@code <g class="inetsoft-mekko" ...>}). */
   String ANNOTATION_MEKKO         = "inetsoft-mekko";
   /** CSS class for external text labels matched to marimekko cells. */
   String ANNOTATION_MEKKO_LABEL   = "inetsoft-mekko-label";
   /** CSS class for relation/tree chart node annotation groups. */
   String ANNOTATION_RELATION = "inetsoft-relation";
   /** CSS class for relation/tree chart edge annotation groups. */
   String ANNOTATION_RELATION_EDGE = "inetsoft-relation-edge";
   /** CSS class for external text labels matched to relation/tree chart nodes. */
   String ANNOTATION_RELATION_LABEL = "inetsoft-relation-label";

   /** Animation hint for rectangular treemap charts. */
   String ANIMATION_TREEMAP  = "treemap";
   /** Animation hint for sunburst charts. */
   String ANIMATION_SUNBURST = "sunburst";
   /** Animation hint for icicle charts. */
   String ANIMATION_ICICLE   = "icicle";
   /** Animation hint for marimekko charts. */
   String ANIMATION_MEKKO    = "mekko";
   /** Animation hint for circle packing charts. */
   String ANIMATION_CIRCLE_PACKING = "circle_packing";
   /** Animation hint for relation/tree charts. */
   String ANIMATION_RELATION = "relation";

   /** {@code data-col} — bar stagger column index (same value for all segments in one column). */
   String ATTR_COL    = "col";
   /** {@code data-slice} — pie/donut slice index (0-based, DOM order). */
   String ATTR_SLICE  = "slice";
   /** {@code data-series} — line/area series index (0-based column index). */
   String ATTR_SERIES = "series";
   /** {@code data-color} — series RGB color as {@code "r,g,b"} integers, e.g. {@code "60,105,138"}. */
   String ATTR_COLOR  = "color";
   /** {@code data-dashed} — {@code "true"} when the line uses a non-zero dash pattern. */
   String ATTR_DASHED = "dashed";
   /** {@code data-face} — {@code "top"} or {@code "depth"} for 3D pie faces. */
   String ATTR_FACE   = "face";
   /** {@code data-orient} — {@code "v"} (vertical) or {@code "h"} (horizontal) for bars. */
   String ATTR_ORIENT = "orient";
   /** {@code data-row} — dataset row index for a bar (matches {@code rowIdx} in ChartRegion). */
   String ATTR_ROW    = "row";
   /** {@code data-size} — visual radius in pixels, used by the animation injector to sort points. */
   String ATTR_SIZE   = "size";
   /** {@code data-x} — screen X center in pixels, used to sort schema VOs left-to-right. */
   String ATTR_X      = "x";
   /** {@code data-level} — nesting depth from {@code TreemapGeometry.getLevel()}; leaf=0, root=highest. */
   String ATTR_LEVEL  = "level";
   /** {@code data-id} — mxCell ID for relation/tree chart nodes, used to match edges to nodes. */
   String ATTR_ID     = "id";
   /** {@code data-source} — source node's mxCell ID for relation/tree chart edges. */
   String ATTR_SOURCE = "source";
   /** {@code data-target} — target node's mxCell ID for relation/tree chart edges. */
   String ATTR_TARGET = "target";

   /**
    * Redirect all subsequent SVG drawing into a new {@code <g class="cssClass" data-*="...">}
    * element.  Must be paired with {@link #endAnnotationGroup}.
    * No-op when {@code g} is not an SVG graphics context.
    */
   default void beginAnnotationGroup(Graphics2D g, String cssClass, Map<String, String> attrs) {}

   /**
    * Restore the drawing group that was active before the matching
    * {@link #beginAnnotationGroup} call.
    */
   default void endAnnotationGroup(Graphics2D g) {}

   // -------------------------------------------------------------------------
   // Cached singleton
   // -------------------------------------------------------------------------

   /** Cached singleton — engine property does not change at runtime. */
   SVGSupport[] _CACHE = new SVGSupport[1];

   /**
    * Returns {@code true} if {@code g} is a Batik {@code SVGGraphics2D} context.
    *
    * <p>This check is intentionally done via class simple-name so that it never triggers
    * loading of the Batik SVG classes.  Call this before {@link #getInstance()} in paint
    * methods that are also invoked for non-SVG output (raster images, gauges, PDF) to avoid
    * {@code ClassNotFoundException} for Batik's transitive W3C DOM CSS dependencies in those
    * rendering paths.
    */
   static boolean isSVGContext(Graphics2D g) {
      return "SVGGraphics2D".equals(g.getClass().getSimpleName());
   }

   final class AnimationKey extends RenderingHints.Key {
      AnimationKey() { super(77); }

      @Override
      public boolean isCompatibleValue(Object val) { return val instanceof String; }
   }

   Document createSVGDocument(URL url) throws IOException;

   Document createSVGDocument(InputStream input) throws IOException;

   Document createSVGDocument(Graphics2D graphics) throws IOException;

   Graphics2D createSVGGraphics();

   Graphics2D getSVGGraphics(String url, Dimension contentSize, boolean isShadow, Color bg,
                             double scale, int borderRadius);

   Element getSVGRootElement(Graphics2D graphics);

   Document getSVGDocument(Graphics2D graphics);

   boolean isSVGGraphics(Graphics graphics);

   void setCanvasSize(Graphics2D graphics, Dimension size);

   default Image getSVGImage(InputStream svgStream) throws Exception {
      return getSVGImage(svgStream, 0F, 0F);
   }

   default Image getSVGImage(InputStream svgStream, float width, float height) throws Exception {
      return getSVGImage(svgStream, width, height, 0F, 0F);
   }

   Image getSVGImage(InputStream svgStream, float width, float height,
                     float maxWidth, float maxHeight) throws Exception;

   byte[] transcodeSVGImage(Document document) throws Exception;

   void writeSVG(Graphics2D graphics, Writer writer) throws IOException;

   void writeSVG(Graphics2D graphics, Writer writer, boolean useCss) throws IOException;

   void writeSVG(Graphics2D graphics, OutputStream output) throws IOException;

   void printSVG(Graphics g, double x, double y, double width, double height, Document svg);

   void printSVG(Graphics2D g2, PageFormat pg, URL url);

   Dimension getSVGSize(URL url) throws IOException;

   Dimension getSVGSize(InputStream input) throws IOException;

   default Dimension getSVGSize(URL url, int contextWidth, int contextHeight) throws IOException {
      return getSVGSize(url);
   }

   default Dimension getSVGSize(InputStream input, int contextWidth, int contextHeight)
      throws IOException
   {
      return getSVGSize(input);
   }

   void mergeSVGDocument(Graphics2D g, String url, AffineTransform transform);

   SVGTransformer createSVGTransformer(URL url) throws IOException;

   SVGTransformer createSVGTransformer(InputStream input) throws IOException;

   void fixPNG(Document document);

   BufferedImage generateBufferedImage(InputStream input, int width, int height, String theme,
                                       String color);

   static SVGSupport getInstance() {
      if(_CACHE[0] != null) {
         return _CACHE[0];
      }

      try {
         Class<?> clazz = SVGSupport.class.getClassLoader()
            .loadClass("inetsoft.util.graphics.BatikSVGSupport");
         _CACHE[0] = (SVGSupport) clazz.getConstructor().newInstance();
         return _CACHE[0];
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create SVGSupport instance", e);
      }
   }
}
