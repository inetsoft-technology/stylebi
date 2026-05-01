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
import org.w3c.dom.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DOM-based SVG animation injector.
 *
 * <p>Unlike {@link SVGAnimationInjector} which post-processes the serialized SVG string,
 * this class operates directly on the live W3C DOM document before serialization.
 * This avoids all regex fragility and allows clean element creation and attribute manipulation.
 */
public class SVGAnimationDOMInjector {

   static final String SVG_NS = "http://www.w3.org/2000/svg";

   /** Entry point: inject animations into the live DOM document in-place. */
   public static void injectAnimation(Document doc, String animHint) {
      injectAnimation(doc.getDocumentElement(), animHint);
   }

   /**
    * Inject animations into a pre-assembled SVG root element.
    * Call {@code g2d.getRoot()} to obtain the assembled element before invoking this, then
    * stream via {@code g2d.stream(root, writer)}.
    *
    * <p>The {@code animHint} format is {@code "base[:flag]*"} where base is one of
    * {@code "grow"}, {@code "fade"}, {@code "pie"}, {@code "line"}, and optional flags
    * are {@code "3d"}, {@code "stacked"}, {@code "area"} (defined as {@code ANIMATION_FLAG_*}
    * constants in {@link SVGSupport}).  Example: {@code "grow:stacked"}, {@code "pie:3d"}.
    *
    * <p>Sets the {@code data-animated} attribute (empty string, presence-only flag) on the SVG
    * root element when any animation was injected.  The Angular directive detects this flag to
    * schedule the {@code .ready} class that gates hover CSS rules so dimming never fires during
    * the entrance animation.
    */
   public static void injectAnimation(Element svgRoot, String animHint) {
      Document doc = svgRoot.getOwnerDocument();

      String base = animHint.split(":")[0];

      appendHoverCSS(svgRoot, doc);

      // animated = true for every branch that injects animation (even single-element charts
      // where staggerDelay(0,1)=0, so lastDelay would stay 0 and cannot be used as a signal).
      // Pie is excluded: it has no inetsoft-active hover so no .ready gate is needed.
      boolean animated = false;

      if(SVGSupport.ANIMATION_PIE.equals(base)) {
         injectPieAnimation(svgRoot, doc);
      }
      else if(SVGSupport.ANIMATION_LINE.equals(base)) {
         injectLineAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_POINT.equals(base)) {
         injectPointAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_CANDLE.equals(base)) {
         injectCandleAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_BOX.equals(base)) {
         injectBoxAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_RADAR.equals(base)) {
         injectRadarAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_TREEMAP.equals(base)) {
         injectTreemapAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_SUNBURST.equals(base)) {
         injectSunburstAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_ICICLE.equals(base)) {
         injectIcicleAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_MEKKO.equals(base)) {
         injectMekkoAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_CIRCLE_PACKING.equals(base)) {
         injectCirclePackingAnimation(svgRoot, doc);
         animated = true;
      }
      else if(SVGSupport.ANIMATION_RELATION.equals(base)) {
         injectRelationAnimation(svgRoot, doc);
         animated = true;
      }
      else {
         boolean fadeOnly = SVGSupport.ANIMATION_FADE.equals(base);
         List<Element> annotBars = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_BAR);
         injectBarAnimationFromAnnotations(annotBars, svgRoot, doc, fadeOnly);
         animated = true;
      }

      // Signal to the Angular directive that animation was injected so it can schedule .ready.
      // Only presence matters — the directive uses a fixed READY_MS gate, not the delay value.
      if(animated) {
         svgRoot.setAttribute("data-animated", "");
      }
   }

   // -------------------------------------------------------------------------
   // Annotation group collection
   // -------------------------------------------------------------------------

   /** Recursively collect all {@code <g class="cssClass">} elements in DOM order. */
   private static List<Element> collectAnnotationGroups(Element root, String cssClass) {
      List<Element> result = new ArrayList<>();
      collectByClass(root, cssClass, result);
      return result;
   }

   private static void collectByClass(Element el, String cls, List<Element> out) {
      if(cls.equals(el.getAttribute("class"))) {
         out.add(el);
         return; // don't recurse — annotation groups are not nested
      }

      NodeList ch = el.getChildNodes();

      for(int i = 0; i < ch.getLength(); i++) {
         if(ch.item(i) instanceof Element c) {
            collectByClass(c, cls, out);
         }
      }
   }

   // -------------------------------------------------------------------------
   // Annotation-based bar animation
   // -------------------------------------------------------------------------

   /**
    * Animate bars using semantic annotation groups ({@code <g class="inetsoft-bar" data-col="N">}).
    * No color or shape heuristics — every annotation group gets the animation.
    *
    * <p>When {@link AnimationConstants#BAR_GROW_ENABLED} is {@code false} (default), bars fade
    * in opacity-only per the design spec.  Set it to {@code true} to restore the legacy
    * scaleY/scaleX spring-from-baseline grow animation.
    */
   private static void injectBarAnimationFromAnnotations(List<Element> annotBars,
                                                           Element svgRoot, Document doc,
                                                           boolean fadeOnly)
   {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-bar-grow-y{from{transform:scaleY(0)}to{transform:scaleY(1)}}" +
         "@keyframes inetsoft-bar-grow-x{from{transform:scaleX(0)}to{transform:scaleX(1)}}" +
         "@keyframes inetsoft-bar-fade{from{opacity:0}to{opacity:1}}");

      // Derive orientation from first bar's data-orient attribute (set by BarVO).
      // Fall back to geometry if missing.
      boolean horizontal = false;

      if(!annotBars.isEmpty()) {
         String orient = annotBars.getFirst().getAttribute("data-" + SVGSupport.ATTR_ORIENT);
         horizontal = "h".equals(orient);
      }

      // Compute bounding boxes for all annotation groups.
      List<double[]> allBounds = new ArrayList<>();

      for(Element g : annotBars) {
         allBounds.add(annotGroupBounds(g));
      }

      // Stagger by bar position (X center for vertical bars, Y center for horizontal).
      // Bars at the same position (e.g. stacked segments) share the same delay bucket.
      // Sort unique center positions to establish stagger order.
      List<Double> positions = new ArrayList<>();

      for(double[] b : allBounds) {
         // Vertical bars: stagger left→right by X center.
         // Horizontal bars: stagger top→bottom by Y center.
         double center = horizontal ? (b[1] + b[3]) / 2.0 : (b[0] + b[2]) / 2.0;
         positions.add(center);
      }

      List<Double> sortedUnique = positions.stream()
         .distinct()
         .sorted()
         .toList();

      int numCols = sortedUnique.size();

      // O(1) lookup: center position → stagger column index.
      Map<Double, Integer> colIndexMap = new HashMap<>();
      for(int k = 0; k < sortedUnique.size(); k++) {
         colIndexMap.put(sortedUnique.get(k), k);
      }

      boolean useGrow = !fadeOnly && AnimationConstants.BAR_GROW_ENABLED;

      double baseline = useGrow
         ? SVGAnimationInjector.findBarBaseline(allBounds, horizontal)
         : 0.0;
      String growAnim = horizontal ? "inetsoft-bar-grow-x" : "inetsoft-bar-grow-y";

      for(int i = 0; i < annotBars.size(); i++) {
         Element g    = annotBars.get(i);
         double[] b   = allBounds.get(i);
         double pos   = horizontal ? (b[1] + b[3]) / 2.0 : (b[0] + b[2]) / 2.0;
         int colIdx   = colIndexMap.getOrDefault(pos, 0);
         double delay = AnimationConstants.staggerDelay(colIdx, numCols);

         if(useGrow) {
            // Per-bar transform-origin anchored at the baseline.
            double dimMin  = horizontal ? b[0] : b[1];
            double dimSize = horizontal ? (b[2] - b[0]) : (b[3] - b[1]);
            double p = dimSize > 0 ? (baseline - dimMin) / dimSize * 100.0 : 0.0;
            String barOrigin = horizontal
               ? String.format(java.util.Locale.US, "%.2f%% 50%%", p)
               : String.format(java.util.Locale.US, "50%% %.2f%%", p);
            mergeStyle(g, SVGAnimationInjector.buildAnimStyle(barOrigin, growAnim, delay));
         }
         else {
            // A2 pattern: apply fade to inner child elements so the annotation group's own
            // opacity is never animated. This prevents a fill-mode conflict with hover dim
            // (which sets opacity on the group via :has()) without needing a .ready gate.
            applyAnimStyleToChildren(g, SVGAnimationInjector.buildFadeStyle(delay));
         }
      }

      // Fade text label groups after the last bar has finished animating.
      double lastBarDelay = AnimationConstants.staggerDelay(numCols - 1, numCols);
      double dotDelay     = lastBarDelay + AnimationConstants.DURATION + AnimationConstants.READY_BUFFER;
      String labelAnimStyle = String.format(java.util.Locale.US,
         "opacity:0;animation:inetsoft-bar-fade %.2fs %s %.2fs both",
         AnimationConstants.DURATION, AnimationConstants.EASING, dotDelay);

      // A2 pattern: collect inetsoft-bar-label annotation groups directly and animate their
      // inner children (text primitives), not the annotation group itself. Hover-dim CSS targets
      // the annotation group, so animating that group's opacity directly would create a
      // fill-mode:both conflict that !important cannot resolve.
      List<Element> annotLabelGroups = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_LABEL);

      for(Element labelG : annotLabelGroups) {
         applyAnimStyleToChildren(labelG, labelAnimStyle);
      }

   }

   /**
    * Compute the SVG-space bounding box of all shape children inside an annotation group.
    *
    * <p>Batik wraps each bar in a rendering {@code <g transform="translate(...) matrix(...)">}
    * that applies a Y-flip and viewport offset.  A full recursive descent with CTM accumulation
    * is used so both path and rect shapes inside nested transformed groups are correctly mapped
    * to SVG coordinates.
    */
   private static double[] annotGroupBounds(Element g) {
      double[] acc = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
      // Start with identity CTM; the annotation group itself carries no transform.
      NodeList ch = g.getChildNodes();

      for(int i = 0; i < ch.getLength(); i++) {
         if(ch.item(i) instanceof Element c) {
            collectShapeBounds(c, IDENTITY_MATRIX.clone(), acc);
         }
      }

      return acc[0] == Double.MAX_VALUE ? new double[]{0, 0, 0, 0}
                                        : new double[]{acc[0], acc[1], acc[2], acc[3]};
   }

   private static final double[] IDENTITY_MATRIX = {1, 0, 0, 1, 0, 0};

   /**
    * Recursively walk {@code el} accumulating shape bounds into {@code acc[4]} = {minX,minY,maxX,maxY}.
    * {@code ctm} is the current transform matrix [a,b,c,d,e,f] in SVG notation.
    */
   private static void collectShapeBounds(Element el, double[] ctm, double[] acc) {
      String tag = el.getLocalName();
      if(tag == null) tag = el.getTagName();

      // If this element has a transform, compose it into the ctm.
      String tfStr = el.getAttribute("transform");

      if(!tfStr.isEmpty()) {
         double[] m = parseSVGTransform(tfStr);
         ctm = composeMatrix(ctm, m);
      }

      if("rect".equals(tag)) {
         double x = parseAttr(el, "x");
         double y = parseAttr(el, "y");
         double w = parseAttr(el, "width");
         double h = parseAttr(el, "height");
         expandBounds(acc, ctm, x, y);
         expandBounds(acc, ctm, x + w, y);
         expandBounds(acc, ctm, x, y + h);
         expandBounds(acc, ctm, x + w, y + h);
      }
      else if("path".equals(tag)) {
         double[] b = SVGAnimationInjector.parseBarBounds(el.getAttribute("d"));

         if(b[2] > b[0] || b[3] > b[1]) {
            expandBounds(acc, ctm, b[0], b[1]);
            expandBounds(acc, ctm, b[2], b[1]);
            expandBounds(acc, ctm, b[0], b[3]);
            expandBounds(acc, ctm, b[2], b[3]);
         }
      }
      else {
         // Recurse into children (e.g. rendering <g> wrapping a <rect> or <path>).
         NodeList ch = el.getChildNodes();

         for(int i = 0; i < ch.getLength(); i++) {
            if(ch.item(i) instanceof Element c) {
               collectShapeBounds(c, ctm, acc);
            }
         }
      }
   }

   /** Apply ctm to point (px,py) and expand the {minX,minY,maxX,maxY} accumulator. */
   private static void expandBounds(double[] acc, double[] m, double px, double py) {
      double x = m[0] * px + m[2] * py + m[4];
      double y = m[1] * px + m[3] * py + m[5];
      if(x < acc[0]) acc[0] = x;
      if(y < acc[1]) acc[1] = y;
      if(x > acc[2]) acc[2] = x;
      if(y > acc[3]) acc[3] = y;
   }

   /**
    * Compose two SVG affine matrices m1 and m2 (each [a,b,c,d,e,f]) such that
    * the result transforms a point as m1(m2(p)).  This corresponds to the SVG rule where
    * "T1 T2" applied to a point p means T1(T2(p)).
    */
   private static double[] composeMatrix(double[] m1, double[] m2) {
      return new double[]{
         m1[0]*m2[0] + m1[2]*m2[1],
         m1[1]*m2[0] + m1[3]*m2[1],
         m1[0]*m2[2] + m1[2]*m2[3],
         m1[1]*m2[2] + m1[3]*m2[3],
         m1[0]*m2[4] + m1[2]*m2[5] + m1[4],
         m1[1]*m2[4] + m1[3]*m2[5] + m1[5]
      };
   }

   /**
    * Parse an SVG transform attribute string into a 6-element affine matrix [a,b,c,d,e,f].
    * Handles {@code matrix(...)}, {@code translate(...)}, and space-separated compositions.
    * Returns identity if the string is unrecognised.
    */
   private static double[] parseSVGTransform(String s) {
      double[] result = IDENTITY_MATRIX.clone();
      // Only matrix() and translate() are parsed; scale(), rotate(), skewX(), skewY() are treated
      // as identity. Batik emits matrix() and translate() for all bar-group transforms in practice,
      // so this is safe. If a future renderer uses other transform functions, extend this parser.
      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("(matrix|translate)\\s*\\(([^)]+)\\)")
            .matcher(s);

      while(m.find()) {
         String fn     = m.group(1);
         String[] nums = m.group(2).trim().split("[,\\s]+");
         double[] t    = IDENTITY_MATRIX.clone();

         if("matrix".equals(fn) && nums.length >= 6) {
            for(int i = 0; i < 6; i++) t[i] = Double.parseDouble(nums[i]);
         }
         else if("translate".equals(fn) && nums.length >= 1) {
            t[4] = Double.parseDouble(nums[0]);
            t[5] = nums.length >= 2 ? Double.parseDouble(nums[1]) : 0;
         }

         result = composeMatrix(result, t);
      }

      return result;
   }


   // -------------------------------------------------------------------------
   // Pie animation
   // -------------------------------------------------------------------------

   private static void injectPieAnimation(Element svgRoot, Document doc) {
      List<Element> slices     = new ArrayList<>();
      List<Element> textGroups = new ArrayList<>();
      collectSlicesAndText(svgRoot, slices, textGroups);

      if(slices.isEmpty()) {
         return;
      }

      // Detect and reclassify the donut center-hole overlay; returns {cx,cy,innerR} or null.
      double[] holeParams = preprocessDonutHole(svgRoot);

      // Extract pie center from the first arc path's "L cx cy Z" hub point.
      double[] pieCenter = null;

      for(Element slice : slices) {
         String d = slice.getAttribute("d");
         if(d.contains("A")) {
            pieCenter = extractPieCenter(d);
            break;
         }
      }

      // Each arc slice sweeps in by animating the CSS "d" property via per-slice @keyframes.
      // This creates the wheel/clock effect: slices grow one at a time in angular (DOM) order.
      //
      // The from-state is the same path structure (M A L Z) but with the arc endpoint equal
      // to the start point — a zero-length arc that encloses no area.  CSS path interpolation
      // requires both from/to to share the same command sequence, so this matches exactly.
      //
      // One keyframe per ~5° keeps the leading arc edge on the outer circle at every frame.
      // The large-arc flag is recomputed per keyframe so la=1 slices animate without artifacts.
      //
      // 3D pie faces: multiple arc-containing paths share the same startX (one per 3D face of
      // each logical slice).  We deduplicate by startX so all faces of the same slice receive
      // the same animation delay, keeping the 3D faces in sync during the sweep.
      final double SLICE_DUR = AnimationConstants.PIE_SLICE_DURATION;

      appendStyle(svgRoot, doc, "@keyframes inetsoft-pie-fade{from{opacity:0}to{opacity:1}}");

      // groupKeys: unique arc startX values in DOM encounter order.
      // Used for (a) arcIdx deduplication and (b) non-arc path delay matching.
      List<Double> groupKeys  = new ArrayList<>();
      // groupRefSY: M-point Y of the topmost (first-encountered) path in each group.
      // For 3D pies the top-face is rendered first and has the smallest M-point Y.
      // Used in Pass 2 to compute the per-face cy offset so each face's arc keypoints
      // trace its own offset ellipse rather than the shared top-face ellipse.
      List<Double> groupRefSY = new ArrayList<>();

      // Pass 1: compute deduplicated arcIdx for each slice.
      // Multiple paths sharing the same startX (3D faces) all get the same arcIdx.
      int[] sliceArcIdx = new int[slices.size()];
      Arrays.fill(sliceArcIdx, -1);

      for(int si = 0; si < slices.size(); si++) {
         String d = slices.get(si).getAttribute("d");
         if(!d.contains("A")) continue;

         double startX = extractMStartX(d);
         double[] arcRef = extractArcParams(d);
         double startY = arcRef != null ? arcRef[1] : 0.0;

         int existingIdx = -1;
         for(int j = 0; j < groupKeys.size(); j++) {
            if(Math.abs(groupKeys.get(j) - startX) <= 0.01) {
               existingIdx = j;
               break;
            }
         }

         if(existingIdx < 0) {
            existingIdx = groupKeys.size();
            groupKeys.add(startX);
            groupRefSY.add(startY);
         }

         sliceArcIdx[si] = existingIdx;
      }

      int numArcGroups = groupKeys.size();

      // Fallback for donut/pie charts rendered with bezier curves (C/Q commands) instead of
      // SVG arc commands (A).  Some renderers approximate arcs as cubic bezier splines, so
      // d.contains("A") is false for every slice and groupKeys stays empty.  In that case
      // we cannot do the SMIL arc-sweep animation, so apply staggered opacity fade instead.
      if(numArcGroups == 0) {
         for(int si = 0; si < slices.size(); si++) {
            mergeStyle(slices.get(si), String.format(java.util.Locale.US,
               "opacity:0;animation:inetsoft-pie-fade %.2fs %s %.2fs both",
               AnimationConstants.PIE_FADE_DURATION, AnimationConstants.PIE_FADE_EASING, si * SLICE_DUR));
         }

         double centerTextDelay = slices.size() * SLICE_DUR + 0.1;

         for(Element textGroup : textGroups) {
            mergeStyle(textGroup, String.format(java.util.Locale.US,
               "opacity:0;animation:inetsoft-pie-fade %.2fs %s %.2fs both",
               AnimationConstants.PIE_TEXT_DURATION, AnimationConstants.PIE_FADE_EASING, centerTextDelay));
         }

         return;
      }

      // Pass 1.5: compute sweep angle (degrees) for each arc group so durations can be
      // made proportional.  All slices animating with the same fixed duration look wrong
      // because a 165° slice fills 33x faster (visually) than a 5° slice.
      double[] groupSweepDeg = new double[numArcGroups];

      if(pieCenter != null) {
         for(int si = 0; si < slices.size(); si++) {
            int arcIdx = sliceArcIdx[si];
            if(arcIdx < 0 || groupSweepDeg[arcIdx] > 0) continue; // skip non-arc or already set
            String d = slices.get(si).getAttribute("d");
            if(!d.contains("A")) continue;
            double[] arc = extractArcParams(d);
            if(arc == null) continue;
            double sx = arc[0], sy = arc[1], exFull = arc[7], eyFull = arc[8];
            double sw = arc[6];
            double cx = pieCenter[0], cy = pieCenter[1];
            double startAngle = Math.atan2(sy - cy, sx - cx);
            double endAngle   = Math.atan2(eyFull - cy, exFull - cx);
            double sweep = sw == 1.0 ? endAngle - startAngle : startAngle - endAngle;
            if(sweep <= 0) sweep += 2 * Math.PI;
            groupSweepDeg[arcIdx] = sweep * 180.0 / Math.PI;
         }
      }

      // Fill any group whose sweep wasn't resolved (non-arc groups, pieCenter null, etc.)
      // with an equal share of the remaining degrees so total stays near 360°.
      double totalSweepDeg = 0;
      for(double s : groupSweepDeg) totalSweepDeg += s;
      if(totalSweepDeg <= 0) totalSweepDeg = 360.0;

      int zeroCount = 0;
      for(double s : groupSweepDeg) if(s <= 0) zeroCount++;
      double fillSweep = zeroCount > 0 ? Math.max(0, 360.0 - totalSweepDeg) / zeroCount : 0;

      for(int i = 0; i < numArcGroups; i++) {
         if(groupSweepDeg[i] <= 0) groupSweepDeg[i] = fillSweep > 0 ? fillSweep : 360.0 / numArcGroups;
      }

      // Recompute total after filling.
      totalSweepDeg = 0;
      for(double s : groupSweepDeg) totalSweepDeg += s;

      // Proportional durations: total animation time = numArcGroups * SLICE_DUR (same feel
      // as before), but each slice gets time proportional to its angular sweep so the arc
      // tip moves at a constant angular velocity across all slices.
      double totalAnimTime   = numArcGroups * SLICE_DUR;
      double[] groupDuration = new double[numArcGroups];
      double[] groupBegin    = new double[numArcGroups];
      double cumulativeBegin = 0;

      for(int i = 0; i < numArcGroups; i++) {
         groupDuration[i] = (groupSweepDeg[i] / totalSweepDeg) * totalAnimTime;
         groupBegin[i]    = cumulativeBegin;
         cumulativeBegin += groupDuration[i];
      }

      double totalAnimEndTime = cumulativeBegin; // = sum of all groupDurations

      // Pass 2: apply animations.
      // Per-slice @keyframes rules are collected here and flushed in one <style> block after
      // the loop to avoid one appendStyle call per slice.
      int sliceKeyframeIdx = 0;
      StringBuilder cssKeyframes = new StringBuilder();

      for(int si = 0; si < slices.size(); si++) {
         Element slice = slices.get(si);
         String d = slice.getAttribute("d");
         int myArcIdx = sliceArcIdx[si];

         if(!d.contains("A") || myArcIdx < 0) {
            // Non-arc depth quad: snap to full opacity at the moment the matching slice sweep begins.
            double snapDelay = myArcIdx >= 0
               ? groupBegin[myArcIdx]
               : findDepthFaceDelay(d, groupKeys, groupBegin);
            if(snapDelay >= 0) {
               mergeStyle(slice, String.format(java.util.Locale.US,
                  "opacity:0;animation:inetsoft-pie-fade 0.001s steps(1,start) %.3fs both",
                  snapDelay));
            }
            continue;
         }

         double delay    = groupBegin[myArcIdx];
         double sliceDur = groupDuration[myArcIdx];

         if(pieCenter != null) {
            double[] arc = extractArcParams(d);

            if(arc != null) {
               // For 3D pies there are multiple faces per logical slice: the top elliptical face
               // (M-point Y ≈ groupRefSY) and one or more depth/side faces (M-point Y > refSY).
               //
               // Depth/side faces are left fully opaque with no animation — they remain visible
               // throughout so the slice is never see-through.  Only the top face sweeps.
               double sy_ref = groupRefSY.get(myArcIdx);
               boolean isDepthFace = arc[1] > sy_ref + 1.0;

               if(isDepthFace) {
                  // Arc depth face: snap to full opacity when this slice's sweep begins.
                  mergeStyle(slice, String.format(java.util.Locale.US,
                     "opacity:0;animation:inetsoft-pie-fade 0.001s steps(1,start) %.3fs both",
                     delay));
                  continue;
               }

               // SMIL d-attribute animation with multi-keyframe sweep (top face only).
               //
               // A simple from/to SMIL animation interpolates the arc endpoint LINEARLY between
               // the zero-arc start point and the full-wedge end point.  For large slices the
               // straight-line path through the coordinates cuts across the pie interior instead
               // of following the outer circle edge, producing the visual artifact where the
               // leading outer corner appears to start somewhere towards the center.
               //
               // Fix: generate one keyframe per ~5° so the arc endpoint at each keyframe is
               // computed as (cx + r*cos(θ), cy + r*sin(θ)) for the correct intermediate angle.
               // This keeps the leading edge on the outer circle at every animation frame.
               // The large-arc flag is also recomputed per keyframe so la=1 slices animate
               // correctly without needing a separate opacity-fade fallback.
               double sx = arc[0], sy = arc[1];
               double rx = arc[2], ry = arc[3];
               double xrot = arc[4], sw = arc[6];
               double exFull = arc[7], eyFull = arc[8];
               double cx = pieCenter[0];
               // Each 3D face is offset downward from the top face by the 3D depth.
               // Using the top-face cy for all faces makes depth-face keypoints trace the
               // top ellipse instead of their own offset ellipse, causing outer-edge misalignment.
               // Per-face cy = topCenter_y + (this_face_M_y - topFace_M_y) places the arc
               // keypoints on each face's own correctly offset ellipse.
               double cy = pieCenter[1] + (sy - groupRefSY.get(myArcIdx));

               double startAngle = Math.atan2(sy - cy, sx - cx);
               double endAngle   = Math.atan2(eyFull - cy, exFull - cx);

               // Determine total angular sweep respecting the sweep-flag direction.
               // sw=1 → clockwise (positive angle direction in SVG screen coords).
               double totalSweep;

               if(sw == 1.0) {
                  totalSweep = endAngle - startAngle;
                  if(totalSweep <= 0) totalSweep += 2 * Math.PI;
               }
               else {
                  totalSweep = endAngle - startAngle;
                  if(totalSweep >= 0) totalSweep -= 2 * Math.PI;
               }

               // One keyframe per 5°; minimum 2 (start + end).
               int N = Math.max(2, (int) Math.ceil(Math.abs(totalSweep) * 180.0 / Math.PI / 5.0));

               StringBuilder values = new StringBuilder();

               for(int k = 0; k <= N; k++) {
                  double t     = (double) k / N;
                  double angle = startAngle + totalSweep * t;
                  double ex    = cx + rx * Math.cos(angle);
                  double ey    = cy + ry * Math.sin(angle);
                  // Recompute large-arc flag based on how much has been swept so far.
                  int currentLa = Math.abs(totalSweep * t) > Math.PI ? 1 : 0;

                  if(k > 0) values.append(';');

                  if(holeParams != null) {
                     values.append(buildRingKeyframe(sx, sy, rx, ry, xrot, currentLa, (int) sw,
                                                     ex, ey, cx, cy, holeParams[2]));
                  }
                  else {
                     values.append(String.format(java.util.Locale.US,
                        "M%.4f %.4f A%.4f %.4f %.0f %d %.0f %.4f %.4f L%.4f %.4f Z",
                        sx, sy, rx, ry, xrot, currentLa, sw, ex, ey, cx, cy));
                  }
               }

               // Set d to the from-state (k=0: zero-length arc) for first-paint.
               String fromD = holeParams != null
                  ? buildRingFromD(sx, sy, rx, ry, xrot, (int) sw, cx, cy, holeParams[2])
                  : String.format(java.util.Locale.US,
                     "M%.4f %.4f A%.4f %.4f %.0f 0 %.0f %.4f %.4f L%.4f %.4f Z",
                     sx, sy, rx, ry, xrot, sw, sx, sy, cx, cy);
               slice.setAttribute("d", fromD);

               // Build a unique @keyframes rule for this slice's sweep path sequence.
               // CSS d-property animation requires path() wrapper around each SVG path string.
               // Keyframe percentages are equally spaced in time (one per ~5° angular step),
               // and linear timing between keyframes traces the arc edge correctly.
               String kfName = "inetsoft-pie-sweep-" + sliceKeyframeIdx++;
               String[] paths = values.toString().split(";");
               cssKeyframes.append("@keyframes ").append(kfName).append("{");

               for(int k = 0; k < paths.length; k++) {
                  double pct = paths.length == 1 ? 100.0 : 100.0 * k / (paths.length - 1);
                  cssKeyframes.append(String.format(java.util.Locale.US,
                     "%.2f%%{d:path('%s')}", pct, paths[k]));
               }

               cssKeyframes.append("}");
               mergeStyle(slice, String.format(java.util.Locale.US,
                  "animation:%s %.3fs linear %.3fs forwards", kfName, sliceDur, delay));
               continue;
            }
         }

         // No center available: opacity fade.
         mergeStyle(slice, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-pie-fade %.2fs %s %.2fs both",
            AnimationConstants.PIE_FADE_DURATION, AnimationConstants.PIE_FADE_EASING, delay));
      }

      if(!cssKeyframes.isEmpty()) {
         appendStyle(svgRoot, doc, cssKeyframes.toString());
      }

      // Center text (donut label/value): fade in after ALL slices have finished sweeping.
      double centerTextDelay = totalAnimEndTime + 0.1;

      for(Element textGroup : textGroups) {
         mergeStyle(textGroup, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-pie-fade %.2fs %s %.2fs both",
            AnimationConstants.PIE_TEXT_DURATION, AnimationConstants.PIE_FADE_EASING, centerTextDelay));
      }
   }

   // -------------------------------------------------------------------------
   // Line animation
   // -------------------------------------------------------------------------

   private static void injectLineAnimation(Element svgRoot, Document doc) {
      List<Element> annotLines = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_LINE);
      List<Element> annotAreas = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_AREA);
      injectLineAnimationFromAnnotations(annotLines, annotAreas, svgRoot, doc);
   }

   /**
    * Annotation-driven line/area animation.
    *
    * <p>Uses {@code inetsoft-line} and {@code inetsoft-area} annotation groups stamped by
    * {@link inetsoft.graph.visual.LineVO} and {@link inetsoft.graph.visual.AreaVO} during
    * rendering.  Each group carries {@code data-series} (0-based index), {@code data-color}
    * ("r,g,b"), and (for lines) {@code data-dashed} ("true" when dash is active).
    *
    * <p>No color parsing, chromatic heuristics, or fill classification is needed — all identity
    * information comes directly from the annotations.
    */
   private static void injectLineAnimationFromAnnotations(List<Element> annotLines,
                                                            List<Element> annotAreas,
                                                            Element svgRoot, Document doc)
   {
      // Line rank: data-series column index → 0-based rank.
      // getColIndex() starts at 1 in some charts; TreeSet + sequential rank normalizes to 0-based.
      // In a facet chart the same column indices repeat across panels; TreeSet deduplicates them.
      TreeSet<Integer> lineColSet = new TreeSet<>();

      for(Element g : annotLines) {
         lineColSet.add(parseIntAttr(g, "data-" + SVGSupport.ATTR_SERIES, 0));
      }

      Map<Integer, Integer> lineSeriesRank = new LinkedHashMap<>();
      int rank = 0;

      for(int col : lineColSet) {
         lineSeriesRank.put(col, rank++);
      }

      // Area rank: data-color in DOM first-seen order.
      // AreaVO.getColIndex() is unreliable for ordering — in stacked area charts all AreaVO
      // instances share the same measure column index.  data-color is unique per series and
      // preserves DOM (visual) order, so it gives the correct stagger without extra metadata.
      LinkedHashMap<String, Integer> areaColorRank = new LinkedHashMap<>();

      for(Element g : annotAreas) {
         String color = g.getAttribute("data-" + SVGSupport.ATTR_COLOR);
         areaColorRank.putIfAbsent(color, areaColorRank.size());
      }

      int numLineSeries = lineSeriesRank.size();
      int numAreaSeries = areaColorRank.size();
      int numSeries     = Math.max(numLineSeries, numAreaSeries);
      boolean isAreaChart = !annotAreas.isEmpty();
      // Dots and value labels appear after all series have finished their line animation.
      double dotDelay = AnimationConstants.staggerDelay(numSeries - 1, numSeries)
                        + AnimationConstants.DURATION + AnimationConstants.READY_BUFFER;

      String css =
         "@keyframes inetsoft-line-draw{from{stroke-dashoffset:var(--len,2000)}to{stroke-dashoffset:0}}" +
         "@keyframes inetsoft-line-wipe{from{clip-path:inset(0 100% 0 0)}to{clip-path:inset(0 0% 0 0)}}" +
         "@keyframes inetsoft-line-fade{from{opacity:0}to{opacity:1}}";
      appendStyle(svgRoot, doc, css);

      List<GhostFillInfo> ghostFills = new ArrayList<>();

      // Single pass over all annotated line groups.
      // data-dashed tells us which animation to apply — no pre-scan of stroke-dasharray needed.
      // firstDescendantPath recurses through Batik's intermediate style <g> to reach the <path>.
      for(Element g : annotLines) {
         Element path = firstDescendantPath(g);

         if(path == null) {
            continue;
         }

         // In an area chart the border lines are emitted by AreaVO — their data-series column
         // index is unreliable (same issue as area fills).  Match them to areas by color instead.
         int seriesIdx;

         if(isAreaChart) {
            String color = g.getAttribute("data-" + SVGSupport.ATTR_COLOR);
            seriesIdx = areaColorRank.getOrDefault(color, 0);
         }
         else {
            int col = parseIntAttr(g, "data-" + SVGSupport.ATTR_SERIES, 0);
            seriesIdx = lineSeriesRank.getOrDefault(col, 0);
         }

         double delay     = AnimationConstants.staggerDelay(seriesIdx, numSeries);
         boolean isDashed = "true".equals(g.getAttribute("data-" + SVGSupport.ATTR_DASHED));

         if(isDashed) {
            // Clip-path wipe — leaves stroke-dasharray pattern intact.
            // clip-path:inset(0 100% 0 0) mirrors the from-keyframe to prevent a flash on
            // the first paint frame before fill-mode takes effect.
            // Keeps cubic-bezier easing (ease-in-out) which is correct for a left-to-right reveal.
            mergeStyle(path, String.format(
               "clip-path:inset(0 100%% 0 0);animation:inetsoft-line-wipe %.2fs cubic-bezier(0.4,0,0.2,1) %.2fs both",
               AnimationConstants.DURATION, delay));
         }
         else {
            // Stroke-dashoffset draw-on.
            // Add 2 to the ceiling so the dasharray is always strictly longer than the actual path.
            // Keeps cubic-bezier easing (ease-in-out) which is correct for a progressive draw-on.
            long len = (long) Math.ceil(SVGAnimationInjector.computePathLength(path.getAttribute("d"))) + 2;
            path.setAttribute("stroke-dasharray", len + " " + len);
            path.setAttribute("stroke-dashoffset", String.valueOf(len));
            mergeStyle(path, String.format(
               "stroke-dashoffset:%d;--len:%d;animation:inetsoft-line-draw %.2fs cubic-bezier(0.4,0,0.2,1) %.2fs both",
               len, len, AnimationConstants.DURATION, delay));
         }

         // Ghost fill — color comes from data-color annotation, not from SVG stroke parsing.
         // The transform lives on Batik's inner style group (path's parent), not on the annotation
         // group itself.  Each series fills independently from its line to the baseline (y=0 in
         // local coords), so crossing lines never produce self-intersecting polygons.
         // Step/jump lines use axis-aligned segments; a fill polygon would create a visually
         // incorrect "ghost" area that doesn't match the stepped shape.
         boolean isStep = "true".equals(g.getAttribute("data-" + SVGSupport.ATTR_STEP));
         if(!isAreaChart && !isStep) {
            String pathFill = path.getAttribute("fill");
            boolean hasFill = !pathFill.isEmpty() && !"none".equals(pathFill) && !pathFill.startsWith("url(");

            if(!hasFill) {
               int[] rgb     = parseColorData(g.getAttribute("data-" + SVGSupport.ATTR_COLOR));
               String lineD  = path.getAttribute("d");
               String polygon = SVGAnimationInjector.buildFillPolygon(lineD);

               if(rgb != null && polygon != null && !polygon.isEmpty()) {
                  String transform = ((Element) path.getParentNode()).getAttribute("transform");
                  String clipPath  = path.getAttribute("clip-path");
                  ghostFills.add(new GhostFillInfo(polygon, rgb, delay,
                                                   (Element) g.getParentNode(), g, seriesIdx,
                                                   transform, clipPath));
               }
            }
         }

         // Endpoint marker circles inside the annotation group (rendered by Batik for dashed lines
         // as visual endpoint anchors). These are artifacts in animated SVG — hide them permanently.
         for(Element circle : descendantCircles(g)) {
            mergeStyle(circle, "opacity:0");
         }
      }

      // Area fill groups — wipe left-to-right; reshape into non-overlapping bands.
      //
      // annotAreas and annotLines are both collected in DOM traversal order.  Within each
      // chart panel the SVG emits [area_seriesN, line_seriesN] pairs, so annotAreas[i] and
      // annotLines[i] are always the same series in the same panel.  Using parallel indexing
      // avoids the cross-panel coordinate mismatch that occurs when matching by color alone
      // (facet charts repeat the same color across multiple panels).
      List<AreaBandEntry> areaBandEntries = new ArrayList<>();

      for(int aIdx = 0; aIdx < annotAreas.size(); aIdx++) {
         Element g    = annotAreas.get(aIdx);
         Element path = firstDescendantPath(g);

         if(path == null) {
            areaBandEntries.add(null);
            continue;
         }

         String color     = g.getAttribute("data-" + SVGSupport.ATTR_COLOR);
         int    seriesIdx = areaColorRank.getOrDefault(color, 0);
         double delay     = AnimationConstants.staggerDelay(seriesIdx, numSeries);

         // Collect the panel-local line path for band polygon computation below.
         // The matching line group is annotLines[aIdx] — same series, same panel.
         String localLinePath = "";

         if(aIdx < annotLines.size()) {
            Element lineGroup  = annotLines.get(aIdx);
            String  lineColor  = lineGroup.getAttribute("data-" + SVGSupport.ATTR_COLOR);

            if(color.equals(lineColor)) {
               Element linePathEl = firstDescendantPath(lineGroup);
               if(linePathEl != null) {
                  localLinePath = linePathEl.getAttribute("d");
               }
            }
         }

         areaBandEntries.add(new AreaBandEntry(path, localLinePath));

         // The path may carry a chart-boundary clip-path attribute. CSS clip-path set on the
         // same element (by the wipe animation's fill-mode) would evict it. Preserve the
         // boundary clip on a wrapper <g> so both clips apply independently.
         String existingClip = path.getAttribute("clip-path");

         if(existingClip != null && !existingClip.isEmpty()) {
            path.removeAttribute("clip-path");
            Element wrapper = doc.createElementNS(SVG_NS, "g");
            wrapper.setAttribute("clip-path", existingClip);
            Node pathParent = path.getParentNode();
            pathParent.insertBefore(wrapper, path);
            wrapper.appendChild(path);
         }

         mergeStyle(path, String.format(
            "clip-path:inset(0 100%% 0 0);animation:inetsoft-line-wipe %.2fs cubic-bezier(0.4,0,0.2,1) %.2fs both",
            AnimationConstants.DURATION, delay));
      }

      // Reshape area fill polygons into non-overlapping bands, one chart panel at a time.
      // Each panel contributes numAreaSeries consecutive entries to areaBandEntries.
      // Within each panel: sort by average line y, replace non-bottom fills with band polygons.
      // Bottom series keeps Batik's polygon (already goes from its line to the baseline).
      if(numAreaSeries > 0) {
         for(int panelStart = 0; panelStart < areaBandEntries.size(); panelStart += numAreaSeries) {
            int panelEnd = Math.min(panelStart + numAreaSeries, areaBandEntries.size());
            List<AreaBandEntry> panel = new ArrayList<>();

            for(int i = panelStart; i < panelEnd; i++) {
               if(areaBandEntries.get(i) != null) {
                  panel.add(areaBandEntries.get(i));
               }
            }

            panel.sort((a, b) -> Double.compare(
               SVGAnimationInjector.averageLineY(b.linePath),
               SVGAnimationInjector.averageLineY(a.linePath)));

            for(int i = 0; i < panel.size() - 1; i++) {
               AreaBandEntry top  = panel.get(i);
               AreaBandEntry bot  = panel.get(i + 1);

               if(!top.linePath.isEmpty() && !bot.linePath.isEmpty()
                  && SVGAnimationInjector.xRangesOverlap(top.linePath, bot.linePath))
               {
                  String band = SVGAnimationInjector.buildBandPolygon(top.linePath, bot.linePath);
                  if(band != null && !band.isEmpty()) {
                     top.fillPath.setAttribute("d", band);
                  }
               }
            }
         }
      }

      // Inject ghost fills in reverse so earlier (lower-indexed) series render behind later ones.
      for(int gi = ghostFills.size() - 1; gi >= 0; gi--) {
         GhostFillInfo gf = ghostFills.get(gi);
         injectGhostFill(doc, gf.panel, gf.insertBeforeGroup, gf.polygon,
                         gf.rgb, gf.delay, ghostFills.size(), gi, gf.transform,
                         gf.clipPath);
      }

      // Dot groups: siblings of annotated line groups that contain point markers.
      // Only look within panels that already have annotated lines — avoids full-SVG scan.
      Set<Element> processedParents = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Element g : annotLines) {
         Element parent = (Element) g.getParentNode();

         if(parent == null || !processedParents.add(parent)) {
            continue;
         }

         NodeList siblings = parent.getChildNodes();

         for(int i = 0; i < siblings.getLength(); i++) {
            if(!(siblings.item(i) instanceof Element sibling)) {
               continue;
            }

            String sibClass = sibling.getAttribute("class");

            // inetsoft-point annotation groups on a line chart (PointVO paints markers that
            // sit on top of the line). Fade them in after all series have started drawing so
            // the dot appears after, not before, its line.
            if(SVGSupport.ANNOTATION_POINT.equals(sibClass)) {
               mergeStyle(sibling, String.format(
                  "opacity:0;animation:inetsoft-line-fade %.2fs %s %.2fs both",
                  AnimationConstants.DURATION, AnimationConstants.EASING, dotDelay));
               continue;
            }

            // Skip all other annotated groups (they have an inetsoft-* class) and non-<g> elements.
            if(!"g".equals(sibling.getLocalName()) || !sibClass.isEmpty()) {
               continue;
            }

            if(classifyLineGroup(sibling) == LineGroupType.DOTS) {
               for(Element circle : childCircles(sibling)) {
                  mergeStyle(circle, String.format(
                     "opacity:0;animation:inetsoft-line-fade %.2fs %s %.2fs both",
                     AnimationConstants.DURATION, AnimationConstants.EASING, dotDelay));
               }
            }
         }
      }

      // Pass 5: data value label groups — delay until after all lines have drawn.
      List<Element> valueLabelGroups = new ArrayList<>();
      collectTextGroups(svgRoot, valueLabelGroups);

      for(Element labelG : valueLabelGroups) {
         mergeStyle(labelG, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-line-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, dotDelay));
      }

   }

   // -------------------------------------------------------------------------
   // DOM helpers
   // -------------------------------------------------------------------------

   /**
    * Recursively collect pie slice paths and center text groups, keeping them separate.
    *
    * <ul>
    *   <li>{@code slices}    — {@code <path stroke="none">} elements that are NOT inside a
    *       text group (arc slices and rhombus side faces).</li>
    *   <li>{@code textGroups} — {@code <g text-rendering="geometricPrecision">} elements
    *       (donut center label and value).  Recursion stops here so that glyph paths inside
    *       these groups are never mistaken for slice paths.</li>
    * </ul>
    * Skips {@code <defs>}.
    */
   private static void collectSlicesAndText(Element el,
                                            List<Element> slices,
                                            List<Element> textGroups)
   {
      if("defs".equals(el.getLocalName())) {
         return;
      }

      NodeList children = el.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(!(child instanceof Element c)) {
            continue;
         }

         if("g".equals(c.getLocalName())
            && "geometricPrecision".equals(c.getAttribute("text-rendering")))
         {
            // Center text group — collect as-is, do NOT recurse so glyph paths inside
            // are never treated as pie slices.
            textGroups.add(c);
         }
         // Pie slices from Batik are rendered with stroke="none" by default.
         // If a chart is configured with a visible slice border/stroke, getAttribute("stroke")
         // won't return "none" and those slices will be silently skipped (no animation).
         else if("path".equals(c.getLocalName()) && "none".equals(c.getAttribute("stroke"))) {
            slices.add(c);
         }
         else {
            collectSlicesAndText(c, slices, textGroups);
         }
      }
   }

   private enum LineGroupType { LINE, DOTS, AREA_FILL, OTHER }

   /** Classify a {@code <g>} element as a line series, dot group, area fill, or other. */
   private static LineGroupType classifyLineGroup(Element g) {
      // Area fill: group-level url(#gradient...) fill (Batik style)
      String groupFill = g.getAttribute("fill");

      if(groupFill.startsWith("url(")) {
         return LineGroupType.AREA_FILL;
      }

      // LINE requires a child <path fill="none"> (distinguishes from dots groups,
      // which share the same chromatic stroke/fill attributes on the <g>).
      Element p = firstChildPath(g);

      if(p != null) {
         String pathFill = p.getAttribute("fill");

         if("none".equals(pathFill)) {
            // Candidate line path: any non-none stroke is a valid line series element.
            // Note: empty pathFill (unset) is NOT equivalent to fill="none" — glyph paths in
            // data-label groups inherit fill from the <g>, so they would be misclassified.
            String stroke = g.getAttribute("stroke");

            if(stroke.isEmpty()) {
               stroke = p.getAttribute("stroke");
            }

            if(!stroke.isEmpty() && !"none".equals(stroke)) {
               return LineGroupType.LINE;
            }
         }
         else if(pathFill.startsWith("url(")) {
            // Path-level gradient fill (Batik style area fills)
            return LineGroupType.AREA_FILL;
         }
      }

      // Dots: group contains circle children (no path, or path with non-none fill)
      if(!childCircles(g).isEmpty()) {
         return LineGroupType.DOTS;
      }

      return LineGroupType.OTHER;
   }

   /** First direct child {@code <path>} element, or null. */
   private static Element firstChildPath(Element g) {
      NodeList children = g.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(child instanceof Element && "path".equals(child.getLocalName())) {
            return (Element) child;
         }
      }

      return null;
   }

   /**
    * Find the first {@code <path>} descendant by recursing through {@code <g>} children.
    *
    * <p>Needed for annotation groups: Batik's SVGGraphics2D inserts an intermediate {@code <g>}
    * for stroke/fill state changes, so the actual {@code <path>} is a grandchild (or deeper) of
    * the annotation group, not a direct child.
    */
   private static Element firstDescendantPath(Element g) {
      NodeList children = g.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(!(child instanceof Element c)) {
            continue;
         }

         String tag = c.getLocalName();

         if("path".equals(tag)) {
            return c;
         }

         if("g".equals(tag)) {
            Element found = firstDescendantPath(c);

            if(found != null) {
               return found;
            }
         }
      }

      return null;
   }

   /** All {@code <circle>} elements anywhere in the subtree of {@code root}. */
   private static List<Element> descendantCircles(Element root) {
      List<Element> result = new ArrayList<>();
      collectDescendantCircles(root, result);
      return result;
   }

   private static void collectDescendantCircles(Element el, List<Element> out) {
      NodeList children = el.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         if(!(children.item(i) instanceof Element c)) {
            continue;
         }

         if("circle".equals(c.getLocalName())) {
            out.add(c);
         }
         else {
            collectDescendantCircles(c, out);
         }
      }
   }

   /** All direct child {@code <circle>} elements. */
   private static List<Element> childCircles(Element g) {
      List<Element> result = new ArrayList<>();
      NodeList children = g.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(child instanceof Element && "circle".equals(child.getLocalName())) {
            result.add((Element) child);
         }
      }

      return result;
   }

   /**
    * Extract the pie/donut center point from an arc slice path.
    * Every pie slice ends with {@code "L cx cy Z"} where (cx, cy) is the hub.
    * Returns null if the pattern is not found (e.g., full-circle or unusual path).
    */
   private static double[] extractPieCenter(String d) {
      if(d == null || d.isEmpty()) {
         return null;
      }

      // Match the final "L x y Z" — all slices converge to this hub point
      java.util.regex.Matcher m = java.util.regex.Pattern
         .compile("L\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s+(-?[0-9]+(?:\\.[0-9]+)?)\\s*Z")
         .matcher(d);

      double[] center = null;

      while(m.find()) {
         // Take the last match in case the path has multiple L commands
         center = new double[]{ Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
      }

      return center;
   }

   /**
    * Extract arc parameters from a pie slice path of the form
    * {@code M startX startY A rx ry xrot largeArc sweep endX endY ...}.
    *
    * @return [startX, startY, rx, ry, xrot, largeArc, sweep, endX, endY], or null on failure.
    */
   private static double[] extractArcParams(String d) {
      if(d == null || d.isEmpty()) {
         return null;
      }

      int mIdx = d.indexOf('M');
      // Searches for uppercase 'A' (absolute arc command) only. Batik always emits uppercase
      // path commands, so lowercase 'a' (relative arc) is not expected here.
      int aIdx = d.indexOf('A', mIdx < 0 ? 0 : mIdx + 1);
      if(mIdx < 0 || aIdx < 0) return null;

      // Find end of A command: next path command letter (case-insensitive, skip digits/signs/dots)
      int aEnd = d.length();
      for(int i = aIdx + 1; i < d.length(); i++) {
         char ch = d.charAt(i);
         if(Character.isLetter(ch)) { aEnd = i; break; }
      }

      double[] mNums = parsePathNumbers(d.substring(mIdx + 1, aIdx));
      double[] aNums = parsePathNumbers(d.substring(aIdx + 1, aEnd));

      if(mNums.length < 2 || aNums.length < 7) return null;

      return new double[]{
         mNums[0], mNums[1],  // 0,1: startX, startY
         aNums[0], aNums[1],  // 2,3: rx, ry
         aNums[2],            // 4:   x-rotation
         aNums[3],            // 5:   large-arc flag
         aNums[4],            // 6:   sweep flag
         aNums[5], aNums[6]   // 7,8: endX, endY
      };
   }

   /** Extract all numbers (including negatives and decimals) from a path data fragment. */
   private static double[] parsePathNumbers(String s) {
      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(s);
      List<Double> nums = new ArrayList<>();
      while(m.find()) nums.add(Double.parseDouble(m.group()));
      double[] arr = new double[nums.size()];
      for(int i = 0; i < arr.length; i++) arr[i] = nums.get(i);
      return arr;
   }


   /**
    * Scan all M/L x-coordinates in a non-arc depth path and return the maximum begin-time
    * among those that match a known arc-group key.  Returns -1 if nothing matches.
    */
   private static double findDepthFaceDelay(String d, List<Double> groupKeys,
                                            double[] groupBegin)
   {
      double maxDelay = -1;
      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("(?<=[ML])\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
            .matcher(d);

      while(m.find()) {
         double x = Double.parseDouble(m.group(1));

         for(int j = 0; j < groupKeys.size(); j++) {
            if(Math.abs(groupKeys.get(j) - x) <= 0.01) {
               if(groupBegin[j] > maxDelay) maxDelay = groupBegin[j];
               break;
            }
         }
      }

      return maxDelay;
   }

   /** Extract the x-coordinate of the first M command in a path {@code d} attribute. */
   private static double extractMStartX(String d) {
      if(d == null || d.isEmpty()) return 0;

      int m = d.indexOf('M');
      if(m < 0) return 0;

      java.util.regex.Matcher mat =
         java.util.regex.Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(d.substring(m + 1));

      return mat.find() ? Double.parseDouble(mat.group()) : 0;
   }

   private static double parseAttr(Element el, String name) {
      String v = el.getAttribute(name);
      return v.isEmpty() ? 0 : Double.parseDouble(v);
   }

   /**
    * Parse a {@code data-color} attribute value ("r,g,b" comma-separated integers) to int[3].
    * Returns null if the attribute is absent, empty, or malformed.
    */
   private static int[] parseColorData(String colorAttr) {
      if(colorAttr == null || colorAttr.isEmpty()) {
         return null;
      }

      String[] parts = colorAttr.split(",", 3);

      if(parts.length != 3) {
         return null;
      }

      try {
         return new int[]{
            Integer.parseInt(parts[0].trim()),
            Integer.parseInt(parts[1].trim()),
            Integer.parseInt(parts[2].trim())
         };
      }
      catch(NumberFormatException e) {
         return null;
      }
   }

   /** Read an integer attribute from an element, returning {@code defaultVal} if absent/invalid. */
   private static int parseIntAttr(Element el, String attr, int defaultVal) {
      String v = el.getAttribute(attr);

      if(v.isEmpty()) {
         return defaultVal;
      }

      try {
         return Integer.parseInt(v);
      }
      catch(NumberFormatException e) {
         return defaultVal;
      }
   }

   /**
    * Inject a ghost (translucent) fill polygon for a solid line series.
    * The fill polygon goes from the line path down to the baseline (y=0 in local coords).
    * Wipes left-to-right in sync with the line draw (same delay and easing).
    */
   private static void injectGhostFill(Document doc, Element panel, Element insertBefore,
                                       String polygon, int[] rgb, double lineDelay,
                                       int numSeries, int seriesIdx, String transform,
                                       String clipPath)
   {
      double opacity = 0.05 + (numSeries - 1 - seriesIdx) * 0.015;
      opacity = Math.min(opacity, 0.15);
      String fill = String.format("rgba(%d,%d,%d,%.3f)", rgb[0], rgb[1], rgb[2], opacity);

      String wipeStyle = String.format(
         "clip-path:inset(0 100%% 0 0);animation:inetsoft-line-wipe %.2fs cubic-bezier(0.4,0,0.2,1) %.2fs both",
         AnimationConstants.DURATION, lineDelay);

      Element ghostPath = doc.createElementNS(SVG_NS, "path");
      ghostPath.setAttribute("d", polygon);
      ghostPath.setAttribute("fill", fill);
      ghostPath.setAttribute("stroke", "none");
      ghostPath.setAttribute("style", wipeStyle);

      if(clipPath != null && !clipPath.isEmpty()) {
         // Boundary clip on a wrapper so the wipe clip-path does not evict it.
         Element wrapper = doc.createElementNS(SVG_NS, "g");
         if(transform != null && !transform.isEmpty()) {
            wrapper.setAttribute("transform", transform);
         }
         wrapper.setAttribute("clip-path", clipPath);
         wrapper.appendChild(ghostPath);
         panel.insertBefore(wrapper, insertBefore);
      }
      else {
         if(transform != null && !transform.isEmpty()) {
            ghostPath.setAttribute("transform", transform);
         }
         panel.insertBefore(ghostPath, insertBefore);
      }
   }

   // -------------------------------------------------------------------------
   // Point / scatter chart animation
   // -------------------------------------------------------------------------

   /**
    * Fade in point markers staggered largest-first (primary sort), left-to-right (tiebreaker).
    * Each {@code inetsoft-point} annotation group receives an {@code animation} inline style;
    * a single {@code @keyframes inetsoft-point-fade} block is appended to a {@code <style>}.
    */
   private static void injectPointAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-point-fade{from{opacity:0}to{opacity:1}}");

      List<Element> points = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_POINT);

      if(points.isEmpty()) {
         return;
      }

      // Primary: largest radius first (most prominent data points appear first).
      // Tiebreaker: left-to-right by the cx of the first circle descendant so that
      // same-size points in a standard scatter plot animate in reading order.
      points.sort(Comparator
         .comparingDouble((Element g) -> {
            String s = g.getAttribute("data-size");

            try {
               return s.isEmpty() ? 0.0 : -Double.parseDouble(s); // negative = descending
            }
            catch(NumberFormatException e) {
               return 0.0;
            }
         })
         .thenComparingDouble(SVGAnimationDOMInjector::firstChildCx));

      int n = points.size();

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(i, n);
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-point-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         // Apply the animation to the inner content group(s), not the outer annotation group.
         // The outer .inetsoft-point group must have no animation on its own opacity so that
         // hover CSS (opacity:.2!important) can take effect via the .ready gate without a
         // cascade conflict.
         Node child = points.get(i).getFirstChild();
         while(child != null) {
            if(child instanceof Element e) {
               e.setAttribute("style", animStyle);
            }
            child = child.getNextSibling();
         }
      }

   }

   /**
    * Returns the {@code cx} attribute of the first circle descendant of the annotation group's
    * inner content {@code <g>}, or {@code 0} if none is found.  Used as a tiebreaker sort key
    * for same-size points so that equal-radius markers animate left-to-right.
    *
    * <p>The SVG structure is: annotation {@code <g>} → content {@code <g>} → {@code <circle>}.
    * This method looks one level deeper than the annotation group's direct children.
    *
    * <p>Non-circle point shapes (polygons, stars, images, etc.) have no {@code cx} attribute
    * and always return {@code 0.0}, so same-size markers with non-default shapes animate in
    * their original DOM order rather than strictly left-to-right.  This is a cosmetic
    * limitation of the tiebreaker only — sort order and animation are otherwise unaffected.
    */
   private static double firstChildCx(Element g) {
      // Annotation group's first child is the content wrapper <g> drawn by GShape.
      Node child = g.getFirstChild();

      while(child != null) {
         if(child instanceof Element inner) {
            // Look for cx on the grandchildren (e.g., <circle cx="...">) of the annotation group.
            Node grandchild = inner.getFirstChild();

            while(grandchild != null) {
               if(grandchild instanceof Element e) {
                  String cx = e.getAttribute("cx");

                  if(!cx.isEmpty()) {
                     try {
                        return Double.parseDouble(cx);
                     }
                     catch(NumberFormatException ignored) {
                     }
                  }
               }

               grandchild = grandchild.getNextSibling();
            }
         }

         child = child.getNextSibling();
      }

      return 0.0;
   }

   /**
    * Apply an animation style string to all direct {@link Element} children of an annotation group.
    * Following the {@link #injectPointAnimation} pattern, the animation is placed on the inner
    * content elements so the outer annotation {@code <g>} remains free of opacity animation.
    * This allows hover CSS ({@code opacity:.2!important}) to override without cascade conflicts.
    */
   private static void applyAnimStyleToChildren(Element annotGroup, String animStyle) {
      Node child = annotGroup.getFirstChild();

      while(child != null) {
         if(child instanceof Element e) {
            e.setAttribute("style", animStyle);
         }

         child = child.getNextSibling();
      }
   }

   // -------------------------------------------------------------------------
   // Treemap animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in for rectangular treemap charts.
    *
    * <p>Cells are sorted by bounding-box area (largest first) so the most prominent cells
    * appear first, with smaller cells trailing behind.  Stagger is spread across the
    * {@link AnimationConstants#STAGGER_WINDOW} regardless of cell count.
    */
   private static void injectTreemapAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-treemap-fade{from{opacity:0}to{opacity:1}}");

      List<Element> cells = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_TREEMAP);

      if(cells.isEmpty()) {
         return;
      }

      cells.sort(Comparator.comparingDouble((Element g) -> {
         double[] b = annotGroupBounds(g);
         return -((b[2] - b[0]) * (b[3] - b[1])); // negative = descending (largest first)
      }));

      int n = cells.size();

      // Compute bounds after sorting (order has changed but elements are the same).
      List<double[]> cellBounds = cells.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      // cell index → animation style, reused when matching labels.
      List<String> cellStyles = new ArrayList<>(n);

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(i, n);
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-treemap-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         applyAnimStyleToChildren(cells.get(i), animStyle);
         cellStyles.add(animStyle);
      }

      // Match external text labels to cells: apply matching animation style so labels fade in
      // with their section, and tag with the label class + data-row/col for hover dimming.
      List<Element> textGroups = new ArrayList<>();
      collectTextGroups(svgRoot, textGroups);

      for(Element textG : textGroups) {
         int idx = nearestCellByCtm(textG, cellBounds);

         if(idx >= 0) {
            mergeStyle(textG, cellStyles.get(idx));
            tagLabelForHover(textG, cells.get(idx), SVGSupport.ANNOTATION_TREEMAP_LABEL);
         }
      }

   }

   // -------------------------------------------------------------------------
   // Circle packing animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in for circle packing charts.
    *
    * <p>Circles are sorted by bounding-box area (largest first) so the outermost (most prominent)
    * circles appear first, with smaller nested circles trailing behind.  Stagger is spread across
    * {@link AnimationConstants#STAGGER_WINDOW} regardless of circle count.
    *
    * <p>Text labels are matched to their nearest circle via CTM containment and receive the same
    * animation delay so they fade in together with their circle.  Labels are tagged with
    * {@link SVGSupport#ANNOTATION_TREEMAP_LABEL} so hover dim rules apply to both circles and
    * their labels simultaneously.
    */
   private static void injectCirclePackingAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-circle-packing-fade{from{opacity:0}to{opacity:1}}");

      List<Element> circles = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_TREEMAP);

      if(circles.isEmpty()) {
         return;
      }

      circles.sort(Comparator.comparingDouble((Element g) -> {
         double[] b = annotGroupBounds(g);
         return -((b[2] - b[0]) * (b[3] - b[1])); // negative = descending (largest first)
      }));

      int n = circles.size();
      List<double[]> circleBounds = circles.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      List<String> circleStyles = new ArrayList<>(n);

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(i, n);
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-circle-packing-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         applyAnimStyleToChildren(circles.get(i), animStyle);
         circleStyles.add(animStyle);
      }

      List<Element> textGroups = new ArrayList<>();
      collectTextGroups(svgRoot, textGroups);

      // nearestCellByCtm picks the containing cell whose centre is closest to the text reference
      // point. For nested circles (inner inside outer), the inner circle's centre is always
      // closer to its own label, so nested containment resolves correctly without special casing.
      for(Element textG : textGroups) {
         int idx = nearestCellByCtm(textG, circleBounds);

         if(idx >= 0) {
            mergeStyle(textG, circleStyles.get(idx));
            tagLabelForHover(textG, circles.get(idx), SVGSupport.ANNOTATION_TREEMAP_LABEL);
         }
      }
   }

   // -------------------------------------------------------------------------
   // Relation / tree chart animation
   // -------------------------------------------------------------------------

   /**
    * Inject fade-in animation for relation/tree charts, ordered root-first (top-to-bottom).
    *
    * <p>Each {@code inetsoft-relation} annotation group (one per tree node) fades in using
    * the A2 pattern — animation is applied to inner child elements so the group's own opacity
    * is never animated and hover dimming works without a {@code .ready} gate.
    *
    * <p>Nodes are sorted by their SVG Y-centre (ascending = topmost = root level first) and
    * clustered into discrete depth bands: consecutive nodes whose Y-centres differ by more than
    * half the average node height begin a new level.  All nodes in the same band receive the
    * same stagger delay, so siblings at the same depth appear together.
    */
   private static void injectRelationAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-relation-fade{from{opacity:0}to{opacity:1}}");

      List<Element> nodes = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_RELATION);

      if(nodes.isEmpty()) {
         return;
      }

      List<double[]> bounds = nodes.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      // Sort node indices by Y-centre ascending (root = topmost = smallest Y in SVG coords).
      // NOTE: this assumes a vertical (top-to-bottom) tree layout. For horizontal COMPACT_TREE
      // layouts the level ordering will be approximate (nodes sorted by the width axis instead of
      // the depth axis), but the animation will still complete correctly — it just won't follow
      // root-first order on horizontal trees. For force-directed network graph layouts there is
      // no inherent ordering at all; nodes will stagger in approximate top-to-bottom order.
      List<Integer> order = new ArrayList<>();

      for(int i = 0; i < nodes.size(); i++) {
         order.add(i);
      }

      order.sort(Comparator.comparingDouble(i -> (bounds.get(i)[1] + bounds.get(i)[3]) / 2.0));

      // Cluster into level bands: a gap larger than 50% of the average node height signals
      // a new level.  Clamp threshold to at least 5px for degenerate single-pixel nodes.
      double avgHeight = bounds.stream().mapToDouble(b -> b[3] - b[1]).average().orElse(20.0);
      double threshold = Math.max(avgHeight * 0.5, 5.0);
      int[] levelOf = new int[nodes.size()];
      int numLevels = 0;
      double prevLevelY = Double.NEGATIVE_INFINITY;

      for(int idx : order) {
         double cy = (bounds.get(idx)[1] + bounds.get(idx)[3]) / 2.0;

         if(numLevels == 0 || cy - prevLevelY > threshold) {
            prevLevelY = cy;
            numLevels++;
         }

         levelOf[idx] = numLevels - 1;
      }

      // A2 fade staggered by level — root (level 0) appears first, leaves last.
      for(int i = 0; i < nodes.size(); i++) {
         double delay = AnimationConstants.staggerDelay(levelOf[i], numLevels);
         applyAnimStyleToChildren(nodes.get(i), String.format(java.util.Locale.US,
            "animation:inetsoft-relation-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay));
      }

      // Compute average center-Y for each level band so edges can be matched to levels.
      double[] levelAvgY = new double[numLevels];
      int[] levelCount = new int[numLevels];

      for(int i = 0; i < nodes.size(); i++) {
         double cy = (bounds.get(i)[1] + bounds.get(i)[3]) / 2.0;
         levelAvgY[levelOf[i]] += cy;
         levelCount[levelOf[i]]++;
      }

      for(int l = 0; l < numLevels; l++) {
         if(levelCount[l] > 0) {
            levelAvgY[l] /= levelCount[l];
         }
      }

      // Edge animation: each edge fades in with its child node (the end of the edge furthest
      // from the root, i.e. the maximum Y in SVG coordinates).
      // NOTE: eb[3] (maxY) is the child end only for vertical layouts. For horizontal trees the
      // child end would be eb[2] (maxX). This is acceptable for the current vertical-only support.
      List<Element> edgeGroups = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_RELATION_EDGE);

      for(Element edgeG : edgeGroups) {
         double[] eb = annotGroupBounds(edgeG);
         double childY = eb[3]; // maxY = child end of the edge in SVG coords (vertical layout)
         int bestLevel = 0;
         double bestDist = Math.abs(childY - levelAvgY[0]);

         for(int l = 1; l < numLevels; l++) {
            double dist = Math.abs(childY - levelAvgY[l]);

            if(dist < bestDist) {
               bestDist = dist;
               bestLevel = l;
            }
         }

         double delay = AnimationConstants.staggerDelay(bestLevel, numLevels);
         applyAnimStyleToChildren(edgeG, String.format(java.util.Locale.US,
            "animation:inetsoft-relation-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay));
      }

      // Label animation: labels are stamped with class="inetsoft-relation-label" and
      // data-row/data-col directly by RelationVO during rendering, so no geometric matching
      // is needed — just look them up by data-row and apply the same stagger delay as the
      // matched node.
      Map<String, Integer> nodeIndexByRow = new HashMap<>();
      for(int i = 0; i < nodes.size(); i++) {
         String row = nodes.get(i).getAttribute("data-" + SVGSupport.ATTR_ROW);
         if(!row.isEmpty()) {
            nodeIndexByRow.put(row, i);
         }
      }

      List<Element> labelGroups = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_RELATION_LABEL);
      for(Element labelG : labelGroups) {
         String row = labelG.getAttribute("data-" + SVGSupport.ATTR_ROW);
         Integer idx = nodeIndexByRow.get(row);
         if(idx != null) {
            double delay = AnimationConstants.staggerDelay(levelOf[idx], numLevels);
            // Use A2 pattern (apply to children) for consistency with nodes/edges, so the
            // group's own opacity is never set and hover dim CSS can override without conflict.
            applyAnimStyleToChildren(labelG, String.format(java.util.Locale.US,
               "animation:inetsoft-relation-fade %.2fs %s %.2fs both",
               AnimationConstants.DURATION, AnimationConstants.EASING, delay));
         }
      }
   }

   // -------------------------------------------------------------------------
   // Sunburst animation
   // -------------------------------------------------------------------------

   /**
    * Inject a ring-depth-first spiral fade-in for sunburst charts.
    *
    * <p>Arcs are ordered ring-by-ring (root inward first), and within each ring clockwise from
    * 12 o'clock.  Stagger is distributed across {@link AnimationConstants#STAGGER_WINDOW} using
    * a flat formula so the total animation window is always bounded regardless of arc count.
    *
    * <p>Text label groups are matched to their nearest arc by Euclidean distance and receive
    * the same {@code animation-delay} so they fade in together with their arc.
    */
   private static void injectSunburstAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-sunburst-fade{from{opacity:0}to{opacity:1}}");

      List<Element> arcs = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_TREEMAP);

      if(arcs.isEmpty()) {
         return;
      }

      int n = arcs.size();
      List<double[]> bounds = arcs.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      // Centroid of all arc bounding-box centres approximates the sunburst origin.
      double sumX = 0, sumY = 0;
      for(double[] b : bounds) {
         sumX += (b[0] + b[2]) / 2.0;
         sumY += (b[1] + b[3]) / 2.0;
      }
      double originX = sumX / n;
      double originY = sumY / n;

      // Order arcs ring-by-ring (root first), clockwise within each ring, then assign flat
      // stagger delays across STAGGER_WINDOW so the total is always bounded regardless of count.
      Map<Integer, List<Integer>> byLevel = new TreeMap<>(Comparator.reverseOrder());
      for(int i = 0; i < n; i++) {
         int level = parseIntAttr(arcs.get(i), SVGSupport.ATTR_LEVEL);
         byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(i);
      }

      List<Integer> staggerOrder = new ArrayList<>(n);
      for(List<Integer> ringIndices : byLevel.values()) {
         List<Integer> sorted = new ArrayList<>(ringIndices);
         sorted.sort(Comparator.comparingDouble(i -> {
            double[] b = bounds.get(i);
            double dx = (b[0] + b[2]) / 2.0 - originX;
            double dy = (b[1] + b[3]) / 2.0 - originY;
            double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
            if(angle < 0) angle += 2 * Math.PI;
            if(angle >= 2 * Math.PI) angle -= 2 * Math.PI;
            return angle;
         }));
         staggerOrder.addAll(sorted);
      }

      int[] arcStaggerPos = new int[n];
      for(int k = 0; k < staggerOrder.size(); k++) {
         arcStaggerPos[staggerOrder.get(k)] = k;
      }

      // arc index → animation style string, used later to match labels.
      List<String> arcStyles = new ArrayList<>(n);

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(arcStaggerPos[i], n);
         String style = String.format(java.util.Locale.US,
            "animation:inetsoft-sunburst-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         arcStyles.add(style);
         applyAnimStyleToChildren(arcs.get(i), style);
      }

      // Match each label text group to its arc.
      //
      // Batik renders rotated text as one <g text-rendering="geometricPrecision"> per glyph.
      // Most glyphs have their translate point inside the arc's rectangular bbox (containment
      // match). The LAST glyph's translate sits at the trailing edge of the label and can fall
      // just outside the bbox, causing nearestCellByCtm to assign it to a different arc via
      // the nearest-center fallback.
      //
      // Strategy:
      //   1. Match every glyph group individually with nearestCellByCtm — keeps animation delays correct.
      //   2. For hover tagging only: a glyph that was matched by nearest-center (outside every
      //      arc bbox) looks for a spatially close glyph in DOM order that WAS matched by
      //      containment and adopts that arc instead.  Glyphs of one label are within
      //      GLYPH_DIST SVG units of each other; glyphs from different labels are further apart.
      List<Element> textGroups = new ArrayList<>();
      collectTextGroups(svgRoot, textGroups);

      int numText = textGroups.size();
      double[] labelTx = new double[numText];
      double[] labelTy = new double[numText];
      int[] labelArc = new int[numText];
      boolean[] labelContained = new boolean[numText];
      Arrays.fill(labelArc, -1);

      for(int i = 0; i < numText; i++) {
         String tfStr = textGroups.get(i).getAttribute("transform");
         if(tfStr.isEmpty()) continue;
         double[] ctm = parseSVGTransform(tfStr);
         labelTx[i] = ctm[4];
         labelTy[i] = ctm[5];
         int idx = nearestCellByCtm(textGroups.get(i), bounds);
         labelArc[i] = idx;
         if(idx >= 0) {
            double[] b = bounds.get(idx);
            labelContained[i] = labelTx[i] >= b[0] && labelTx[i] <= b[2]
                                 && labelTy[i] >= b[1] && labelTy[i] <= b[3];
         }
      }

      // Apply animation style per-element (unmodified — animation delays are always correct).
      for(int i = 0; i < numText; i++) {
         if(labelArc[i] >= 0) {
            mergeStyle(textGroups.get(i), arcStyles.get(labelArc[i]));
         }
      }

      // Hover tagging: correct nearest-center mismatches by proximity to contained neighbors.
      int[] hoverArc = labelArc.clone();

      for(int i = 0; i < numText; i++) {
         if(labelContained[i] || labelArc[i] < 0) continue; // already correct or unmatched
         double bestDist = AnimationConstants.SUNBURST_GLYPH_MAX_DIST;
         int override = -1;
         for(int j = Math.max(0, i - AnimationConstants.SUNBURST_GLYPH_WINDOW); j <= Math.min(numText - 1, i + AnimationConstants.SUNBURST_GLYPH_WINDOW); j++) {
            if(j == i || !labelContained[j] || labelArc[j] < 0) continue;
            double d = Math.hypot(labelTx[j] - labelTx[i], labelTy[j] - labelTy[i]);
            if(d < bestDist) {
               bestDist = d;
               override = labelArc[j];
            }
         }
         if(override >= 0) {
            hoverArc[i] = override;
         }
      }

      for(int i = 0; i < numText; i++) {
         if(hoverArc[i] >= 0) {
            tagLabelForHover(textGroups.get(i), arcs.get(hoverArc[i]), SVGSupport.ANNOTATION_TREEMAP_LABEL);
         }
      }

   }

   // -------------------------------------------------------------------------
   // Icicle animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in for icicle charts.
    *
    * <p>Cells are ordered by {@code data-level} (root — highest value — first, leaves — level 0
    * — last) and within each level top-to-bottom.  Stagger is distributed across
    * {@link AnimationConstants#STAGGER_WINDOW} using a flat formula so the total window is
    * always bounded.
    */
   private static void injectIcicleAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-icicle-fade{from{opacity:0}to{opacity:1}}");

      List<Element> cells = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_TREEMAP);

      if(cells.isEmpty()) {
         return;
      }

      int n = cells.size();
      List<double[]> bounds = cells.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      // Group cell indices by level; sort within each level top-to-bottom.
      Map<Integer, List<Integer>> byLevel = new HashMap<>();
      for(int i = 0; i < n; i++) {
         int level = parseIntAttr(cells.get(i), SVGSupport.ATTR_LEVEL);
         byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(i);
      }
      for(List<Integer> indices : byLevel.values()) {
         indices.sort(Comparator.comparingDouble(i -> bounds.get(i)[1]));
      }

      // Build stagger order: root (highest data-level) first with delay=0, leaves (level=0) last.
      List<Integer> levelOrder = new ArrayList<>(byLevel.keySet());
      levelOrder.sort(Comparator.reverseOrder()); // highest data-level = root = first; level=0 = leaf = last

      List<Integer> staggerOrder = new ArrayList<>(n);
      for(int level : levelOrder) {
         staggerOrder.addAll(byLevel.get(level));
      }

      int[] cellStaggerPos = new int[n];
      for(int k = 0; k < staggerOrder.size(); k++) {
         cellStaggerPos[staggerOrder.get(k)] = k;
      }

      // cell index → animation style, used to match labels after cell pass.
      List<String> cellStyles = new ArrayList<>(n);

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(cellStaggerPos[i], n);
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-icicle-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         applyAnimStyleToChildren(cells.get(i), animStyle);
         cellStyles.add(animStyle);
      }

      // Match label text groups to their cell and apply the same animation style so labels
      // fade in together with their section, and tag for hover dimming.
      List<Element> textGroups = new ArrayList<>();
      collectTextGroups(svgRoot, textGroups);

      for(Element textG : textGroups) {
         int bestIdx = nearestCellByCtm(textG, bounds);

         if(bestIdx >= 0) {
            mergeStyle(textG, cellStyles.get(bestIdx));
            tagLabelForHover(textG, cells.get(bestIdx), SVGSupport.ANNOTATION_TREEMAP_LABEL);
         }
      }

   }

   // -------------------------------------------------------------------------
   // Marimekko animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in for marimekko charts.
    *
    * <p>Cells use a diagonal wave: {@code delay = colIdx * COL_STEP + rowIdx * ROW_STEP} scaled
    * so the last cell always starts at {@link AnimationConstants#STAGGER_WINDOW} seconds.
    */
   private static void injectMekkoAnimation(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-mekko-fade{from{opacity:0}to{opacity:1}}");

      List<Element> cells = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_MEKKO);

      if(cells.isEmpty()) {
         return;
      }

      // Diagonal stagger: delay = colIdx * COL_STEP + rowIdx * ROW_STEP.
      // colIdx = left-to-right column order by x-center.
      // rowIdx = top-to-bottom position within that column by y-top (rounded to nearest pixel).
      // COL_STEP and ROW_STEP ratios preserved from original (8:5); scaled so the last cell
      // always starts at STAGGER_WINDOW regardless of grid dimensions.
      final double COL_RATIO = 0.08;
      final double ROW_RATIO = 0.05;

      List<double[]> bounds = cells.stream()
         .map(SVGAnimationDOMInjector::annotGroupBounds)
         .collect(Collectors.toList());

      // Distinct sorted x-centers → column indices.
      List<Double> xCenters = bounds.stream()
         .map(b -> (b[0] + b[2]) / 2.0)
         .collect(Collectors.toList());
      List<Double> sortedCols = xCenters.stream()
         .distinct().sorted()
         .collect(Collectors.toList());

      // Build O(1) lookup map: x-center → column index.
      Map<Double, Integer> colIndexMap = new HashMap<>();
      for(int k = 0; k < sortedCols.size(); k++) {
         colIndexMap.put(sortedCols.get(k), k);
      }

      // Within each column, rank cells top-to-bottom by y-top (rounded to nearest pixel).
      // Build a map from colX → sorted list of y-top values so rowIdx is stable.
      Map<Double, List<Long>> colYMap = new HashMap<>();
      for(int i = 0; i < cells.size(); i++) {
         double cx = xCenters.get(i);
         long yTop = Math.round(bounds.get(i)[1]);
         colYMap.computeIfAbsent(cx, k -> new ArrayList<>()).add(yTop);
      }

      // Build O(1) lookup map: (x-center, y-top) → row index within that column.
      Map<Double, Map<Long, Integer>> rowIndexMap = new HashMap<>();
      for(Map.Entry<Double, List<Long>> e : colYMap.entrySet()) {
         List<Long> ys = new ArrayList<>(e.getValue());
         java.util.Collections.sort(ys);
         Map<Long, Integer> rowMap = new HashMap<>();
         for(int k = 0; k < ys.size(); k++) {
            // putIfAbsent: when two cells in the same column share a sub-pixel-rounded y-top,
            // keep the first (lowest) row index rather than overwriting with a higher one.
            rowMap.putIfAbsent(ys.get(k), k);
         }
         rowIndexMap.put(e.getKey(), rowMap);
      }

      // Compute raw max delay (using ratio constants) to derive scale factor.
      int maxColIdx = sortedCols.size() - 1;
      int maxRowIdx = colYMap.values().stream()
         .mapToInt(List::size)
         .max().orElse(1) - 1;
      double rawMax = maxColIdx * COL_RATIO + maxRowIdx * ROW_RATIO;
      double scale = rawMax > 0 ? AnimationConstants.STAGGER_WINDOW / rawMax : 1.0;
      double COL_STEP = COL_RATIO * scale;
      double ROW_STEP = ROW_RATIO * scale;

      // cell index → animation style, reused when matching labels.
      List<String> cellStyles = new ArrayList<>(cells.size());

      for(int i = 0; i < cells.size(); i++) {
         double cx = xCenters.get(i);
         int colIdx = colIndexMap.get(cx);
         long yTop = Math.round(bounds.get(i)[1]);
         int rowIdx = rowIndexMap.get(cx).get(yTop);
         double delay = colIdx * COL_STEP + rowIdx * ROW_STEP;
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-mekko-fade %.2fs %s %.2fs both",
            AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         cells.get(i).setAttribute("style", animStyle);
         cellStyles.add(animStyle);
      }

      // Match external text labels to cells: apply matching animation style so labels fade in
      // with their section, and tag with the label class + data-row/col for hover dimming.
      List<Element> textGroups = new ArrayList<>();
      collectTextGroups(svgRoot, textGroups);

      for(Element textG : textGroups) {
         int idx = nearestCellByCtm(textG, bounds);

         if(idx >= 0) {
            mergeStyle(textG, cellStyles.get(idx));
            tagLabelForHover(textG, cells.get(idx), SVGSupport.ANNOTATION_MEKKO_LABEL);
         }
      }

   }

   /**
    * Return the index into {@code cellBounds} of the cell that best matches the given text
    * group's SVG position, determined via its transform CTM.
    *
    * <p>Containment is preferred over bare nearest-centre so that labels sitting inside a cell
    * area are matched to that cell even when another cell's centre is slightly closer.
    *
    * <p>Text groups without a {@code transform} attribute return {@code -1} and are skipped.
    * In practice Batik always emits a {@code transform} on label groups (it encodes the full
    * SVG-coordinate position there, not in child element attributes), so a missing transform
    * indicates a non-label group that should be ignored.
    *
    * @return the matched index, or {@code -1} if the text group has no {@code transform} attribute
    */
   private static int nearestCellByCtm(Element textG, List<double[]> cellBounds) {
      String tfStr = textG.getAttribute("transform");

      if(tfStr.isEmpty()) {
         return -1;
      }

      double[] ctm = parseSVGTransform(tfStr);
      double tx = ctm[4];
      double ty = ctm[5];

      int bestIdx = -1;
      double bestDist = Double.MAX_VALUE;

      // Prefer cells whose bounding box contains the text reference point.
      for(int i = 0; i < cellBounds.size(); i++) {
         double[] b = cellBounds.get(i);
         boolean contained = tx >= b[0] && tx <= b[2] && ty >= b[1] && ty <= b[3];

         if(contained) {
            double cx = (b[0] + b[2]) / 2.0;
            double cy = (b[1] + b[3]) / 2.0;
            double dist = (tx - cx) * (tx - cx) + (ty - cy) * (ty - cy);

            if(dist < bestDist) {
               bestDist = dist;
               bestIdx = i;
            }
         }
      }

      // Fallback: nearest centre when the label sits outside all cell bounding boxes.
      if(bestIdx < 0) {
         bestDist = Double.MAX_VALUE;

         for(int i = 0; i < cellBounds.size(); i++) {
            double[] b = cellBounds.get(i);
            double cx = (b[0] + b[2]) / 2.0;
            double cy = (b[1] + b[3]) / 2.0;
            double dist = (tx - cx) * (tx - cx) + (ty - cy) * (ty - cy);

            if(dist < bestDist) {
               bestDist = dist;
               bestIdx = i;
            }
         }
      }

      return bestIdx;
   }

   /**
    * Stamp a text label group with a hover-dimming CSS class and copy the {@code data-row} /
    * {@code data-col} attributes from the matched annotation group so the Angular directive can
    * pair the label with the cell and toggle {@code inetsoft-active} on both simultaneously.
    */
   private static void tagLabelForHover(Element textG, Element matchedCell, String labelClass) {
      textG.setAttribute("class", labelClass);
      String row = matchedCell.getAttribute("data-row");
      String col = matchedCell.getAttribute("data-col");

      if(!row.isEmpty()) {
         textG.setAttribute("data-row", row);
      }

      if(!col.isEmpty()) {
         textG.setAttribute("data-col", col);
      }
   }

   /**
    * Parse the {@code data-<attr>} integer attribute of an element, returning {@code 0} on
    * missing or malformed values.
    *
    * <p><b>Note:</b> this overload automatically prepends {@code "data-"} to {@code attr}.
    * Pass a short name like {@code SVGSupport.ATTR_LEVEL} (not {@code "data-level"}) or the
    * attribute read will be {@code "data-data-level"}.  The 3-arg overload takes the full
    * attribute name as-is and should be used when the caller already holds the full name.
    */
   private static int parseIntAttr(Element el, String attr) {
      String v = el.getAttribute("data-" + attr);

      if(v.isEmpty()) {
         return 0;
      }

      try {
         return Integer.parseInt(v);
      }
      catch(NumberFormatException e) {
         return 0;
      }
   }

   // -------------------------------------------------------------------------
   // Candlestick animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in animation for candlestick charts.
    *
    * <p>Each {@code inetsoft-candle} annotation group (one per candle) fades in over
    * {@link AnimationConstants#DURATION} seconds.  Items are sorted by screen X position
    * ({@code data-x}) so they animate left-to-right in visual order.  Delays are spread
    * evenly across {@link AnimationConstants#STAGGER_WINDOW} seconds regardless of item count.
    */
   private static void injectCandleAnimation(Element svgRoot, Document doc) {
      injectXPositionFadeAnimation(svgRoot, doc,
         SVGSupport.ANNOTATION_CANDLE, "inetsoft-candle-fade");
   }

   // -------------------------------------------------------------------------
   // Box-plot animation
   // -------------------------------------------------------------------------

   /**
    * Inject staggered fade-in animation for box-plot charts.
    *
    * <p>Each {@code inetsoft-box} annotation group (one per box) fades in left-to-right.
    * Delays are distributed across {@link AnimationConstants#STAGGER_WINDOW}.
    */
   private static void injectBoxAnimation(Element svgRoot, Document doc) {
      injectXPositionFadeAnimation(svgRoot, doc,
         SVGSupport.ANNOTATION_BOX, "inetsoft-box-fade");
   }

   /**
    * Shared fade-in implementation for chart types whose items are staggered left-to-right
    * by screen X position.  Used by both candlestick and box-plot animations.
    *
    * <p>The animation style is applied directly to the annotation group element because
    * {@code CandlePainter} and {@code BoxPainter} emit paths as direct children with no
    * wrapping inner {@code <g>}.  Hover dimming is gated via the {@code svg.ready} class so
    * hover CSS does not conflict with the animation's fill-mode.
    */
   private static void injectXPositionFadeAnimation(Element svgRoot, Document doc,
                                                      String annotClass, String keyframeName)
   {
      appendStyle(svgRoot, doc,
         "@keyframes " + keyframeName + "{from{opacity:0}to{opacity:1}}");

      List<Element> items = collectAnnotationGroups(svgRoot, annotClass);

      if(items.isEmpty()) {
         return;
      }

      // Sort left-to-right by screen X center so items animate in visual order regardless
      // of how the underlying data rows are ordered in the dataset.
      items.sort(Comparator.comparingDouble(g -> {
         String s = ((Element) g).getAttribute("data-" + SVGSupport.ATTR_X);

         try {
            return s.isEmpty() ? 0.0 : Double.parseDouble(s);
         }
         catch(NumberFormatException e) {
            return 0.0;
         }
      }));

      int n = items.size();

      for(int i = 0; i < n; i++) {
         double delay = AnimationConstants.staggerDelay(i, n);
         String animStyle = String.format(java.util.Locale.US,
            "animation:%s %.2fs %s %.2fs both",
            keyframeName, AnimationConstants.DURATION, AnimationConstants.EASING, delay);
         items.get(i).setAttribute("style", animStyle);
      }

   }

   // -------------------------------------------------------------------------
   // Radar/spider chart animation
   // -------------------------------------------------------------------------

   /**
    * Inject spring-scale animation for radar (spider) charts.
    *
    * <p>Each series polygon springs out from the radar center with a gentle overshoot,
    * staggered by series index (back series first).  LineVO emits {@code inetsoft-line}
    * groups and AreaVO emits {@code inetsoft-area} groups — both are collected and
    * re-classified to {@code inetsoft-radar} so hover CSS targets the correct class.
    *
    * <p>For line-style radar ({@code CHART_RADAR}) the path has {@code fill="none"}, so:
    * <ul>
    *   <li>A semi-transparent ghost fill path is injected inside the group (inherits animation).
    *   <li>A transparent hit path is appended so the enclosed area captures pointer events.
    * </ul>
    * Fill-style radar ({@code CHART_FILL_RADAR}) already has a filled path; neither extra
    * element is needed.
    */
   private static void injectRadarAnimation(Element svgRoot, Document doc) {
      // Collect both line and area annotation groups — radar uses one or the other.
      List<Element> groups = new ArrayList<>();
      groups.addAll(collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_LINE));
      groups.addAll(collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_AREA));

      if(groups.isEmpty()) {
         return;
      }

      // Re-classify all groups to inetsoft-radar so hover CSS and the directive are correct.
      for(Element g : groups) {
         g.setAttribute("class", SVGSupport.ANNOTATION_RADAR);
      }

      // Preserve DOM order (data-series is unreliable for radar — AreaVO.getColIndex() returns
      // the axis count, not the series index, so all groups share the same data-series value).
      // DOM order matches the back-to-front paint order, which is the correct stagger sequence.

      // Assign sequential data-row to each group so the directive can map canvas hover events
      // (row = series index, col = axis index) to the correct polygon.  PointVO emits
      // data-row = series index in the same back-to-front order, so group[i] → data-row=i.
      //
      // Known limitation: for facet radar charts (multiple panels in one SVG), groups from all
      // panels are collected and numbered globally (0, 1, …, n-1 across panels).  The per-series
      // CSS hover rules keyed on these data-row values therefore span panels, so hovering a series
      // in one panel also dims the same-numbered series in sibling panels.  Single-panel radar
      // charts (the common case) are unaffected.
      for(int i = 0; i < groups.size(); i++) {
         groups.get(i).setAttribute("data-row", String.valueOf(i));
      }

      // Compute the radar center (centroid of all vertices from the first valid polygon).
      // For a regular n-gon centred at (cx, cy), the vertex centroid == (cx, cy) exactly.
      double[] center = computeRadarCenter(groups);
      double cx = center[0];
      double cy = center[1];

      // Smooth ease-out grow from center — no overshoot/spring-back.
      // opacity:1 must be explicit in the 100% stop; omitting it causes fill-mode:both to
      // revert opacity to the element's inline value (opacity:0) once the animation ends.
      // Simple fade keyframe re-used for points/labels that appear after the polygons settle.
      appendStyle(svgRoot, doc,
         "@keyframes inetsoft-radar-grow{" +
         "0%{transform:scale(0);opacity:0}" +
         "100%{transform:scale(1);opacity:1}}" +
         "@keyframes inetsoft-radar-fade{from{opacity:0}to{opacity:1}}");

      int n = groups.size();

      // Per-series hover rules for each series i.  Gated on svg.ready so dimming never fires
      // during the entrance animation.
      //
      // Two activation sources exist:
      //   A) Polygon interior — mouse over the hit path inside .inetsoft-radar[data-row=i].
      //      The hit path is a descendant of .inetsoft-radar, so :hover propagates up to it.
      //   B) Vertex circle   — .inetsoft-point[data-row=i] circles are painted AFTER radar
      //      groups (later DOM order → higher z-order), so they sit on top of the hit paths.
      //      Without an explicit CSS :hover rule keyed on .inetsoft-point, the full vertex
      //      circle area would not trigger the dim effect (only the thin crescent of hit-path
      //      area visible around the circle edge would activate .inetsoft-radar:hover).
      //
      // Both sources dim the same set of targets:
      //   • sibling radar polygons  (not[data-row=i])
      //   • other-series points     (not[data-row=i])
      // The hovered series' own polygon and vertex points stay at full opacity.
      StringBuilder perSeriesCss = new StringBuilder();
      String dim = String.format(java.util.Locale.US, "%.2f", AnimationConstants.HOVER_DIM_OPACITY);

      for(int i = 0; i < n; i++) {
         perSeriesCss.append(String.format(java.util.Locale.US,
            // When series i's polygon is CSS-hovered (mouse inside the interior), dim the
            // vertex points of all other series. The hovered series' own points are excluded
            // by :not([data-row=i]) so they stay at full opacity.
            "svg.ready:has(.inetsoft-radar[data-row=\"%d\"]:hover) .inetsoft-point:not([data-row=\"%d\"])" +
            "{opacity:" + dim + "!important}", i, i));
      }

      appendStyle(svgRoot, doc, perSeriesCss.toString());

      for(int i = 0; i < n; i++) {
         Element g = groups.get(i);
         double delay = AnimationConstants.staggerDelay(i, n);

         // transform-origin in SVG user-space so the scale radiates from the radar center,
         // not from the polygon's own bounding-box centre (which would be off-centre for
         // asymmetric score distributions).
         mergeStyle(g, String.format(java.util.Locale.US,
            "transform-origin:%.2fpx %.2fpx;" +
            "opacity:0;animation:inetsoft-radar-grow %.2fs %s %.2fs both",
            cx, cy, AnimationConstants.DURATION, AnimationConstants.EASING, delay));

         // For line radar the path has fill="none": inject a ghost fill and a hit area.
         Element path = firstDescendantPath(g);

         if(path != null && "none".equals(path.getAttribute("fill"))) {
            String pathD = path.getAttribute("d");

            if(!pathD.isEmpty()) {
               // Ghost fill — semi-transparent copy of the polygon, inside the group so it
               // inherits the spring animation.  pointer-events:none keeps interaction on the
               // explicit hit path added below.
               int[] rgb = parseColorData(g.getAttribute("data-" + SVGSupport.ATTR_COLOR));

               if(rgb != null) {
                  Element ghostPath = doc.createElementNS(SVG_NS, "path");
                  ghostPath.setAttribute("d", pathD);
                  ghostPath.setAttribute("fill",
                     String.format("rgba(%d,%d,%d,0.12)", rgb[0], rgb[1], rgb[2]));
                  ghostPath.setAttribute("stroke", "none");
                  ghostPath.setAttribute("pointer-events", "none");
                  // path may be nested inside a Batik intermediate style <g>; insertBefore
                  // requires the reference node to be a direct child of the receiver.
                  Element pathParent = (Element) path.getParentNode();
                  pathParent.insertBefore(ghostPath, path);
               }

               // Hit path — rgba(0,0,0,0) is transparent but still captures pointer events
               // (unlike fill="none" which opts the filled area out of pointer events entirely).
               // Append to path's parent for the same reason as the ghost path above.
               Element hitPath = doc.createElementNS(SVG_NS, "path");
               hitPath.setAttribute("d", pathD);
               hitPath.setAttribute("fill", "rgba(0,0,0,0)");
               hitPath.setAttribute("stroke", "none");
               hitPath.setAttribute("pointer-events", "all");
               path.getParentNode().appendChild(hitPath);
            }
         }
      }

      // Points and value labels appear after all polygons have settled.
      double dotDelay = AnimationConstants.staggerDelay(n - 1, n) +
         AnimationConstants.DURATION + AnimationConstants.READY_BUFFER;

      Set<Element> processedParents = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Element g : groups) {
         Element parent = (Element) g.getParentNode();

         if(parent == null || !processedParents.add(parent)) {
            continue;
         }

         NodeList siblings = parent.getChildNodes();

         for(int i = 0; i < siblings.getLength(); i++) {
            if(!(siblings.item(i) instanceof Element sibling)) {
               continue;
            }

            String sibClass = sibling.getAttribute("class");

            // Fade in inetsoft-point and inetsoft-bar-label annotation groups after polygons.
            // Also fade in unannotated value-label <g> elements: Batik renders text as path
            // glyphs inside plain <g> elements. Both axis name labels ("Revenue", "Innovation")
            // and per-datapoint value labels ("72", "65") have font-family set, so font-family
            // alone is not a reliable discriminator.  Axis labels are bold (font-weight="bold");
            // value labels carry no font-weight attribute.  Exclude bold groups to leave axis
            // labels always visible.
            if(SVGSupport.ANNOTATION_POINT.equals(sibClass) ||
               SVGSupport.ANNOTATION_LABEL.equals(sibClass) ||
               (!sibling.getAttribute("font-family").isEmpty() &&
                sibling.getAttribute("font-weight").isEmpty() &&
                sibClass.isEmpty()))
            {
               mergeStyle(sibling, String.format(java.util.Locale.US,
                  "opacity:0;animation:inetsoft-radar-fade %.2fs %s %.2fs both",
                  AnimationConstants.DURATION, AnimationConstants.EASING, dotDelay));
            }
         }
      }

   }

   /**
    * Compute the radar chart centre as the centroid of all polygon vertices in the first
    * valid annotated group.  Batik emits closed polygon paths as
    * {@code M x0 y0 L x1 y1 … L xN yN Z}; stripping SVG command letters and parsing the
    * remaining numbers as (x, y) pairs gives all vertex coordinates.
    */
   private static double[] computeRadarCenter(List<Element> groups) {
      for(Element g : groups) {
         Element path = firstDescendantPath(g);

         if(path == null) {
            continue;
         }

         String d = path.getAttribute("d");

         if(d.isEmpty()) {
            continue;
         }

         // Strip all SVG command letters (M, L, Z, C, Q, …) then split on whitespace/commas.
         String[] tokens = d.replaceAll("[A-Za-z]", " ").trim().split("[\\s,]+");

         double sumX = 0, sumY = 0;
         int count = 0;

         for(int i = 0; i + 1 < tokens.length; i += 2) {
            try {
               sumX += Double.parseDouble(tokens[i]);
               sumY += Double.parseDouble(tokens[i + 1]);
               count++;
            }
            catch(NumberFormatException ignored) {
               // Skip malformed tokens.
            }
         }

         if(count > 0) {
            return new double[]{ sumX / count, sumY / count };
         }
      }

      // Fallback: use the SVG viewBox midpoint so the animation scales from a reasonable
      // center rather than the top-left corner (0, 0), which would look obviously wrong.
      if(!groups.isEmpty()) {
         Element svgRoot = (Element) groups.get(0).getOwnerDocument().getDocumentElement();
         String viewBox = svgRoot.getAttribute("viewBox");

         if(!viewBox.isEmpty()) {
            String[] parts = viewBox.trim().split("[\\s,]+");

            if(parts.length >= 4) {
               try {
                  double vbX = Double.parseDouble(parts[0]);
                  double vbY = Double.parseDouble(parts[1]);
                  double vbW = Double.parseDouble(parts[2]);
                  double vbH = Double.parseDouble(parts[3]);
                  return new double[]{ vbX + vbW / 2.0, vbY + vbH / 2.0 };
               }
               catch(NumberFormatException ignored) {
               }
            }
         }
      }

      return new double[]{ 0, 0 };
   }

   // -------------------------------------------------------------------------
   // Hover CSS (server-injected; JS toggles inetsoft-active)
   // -------------------------------------------------------------------------

   /**
    * Inject hover dim CSS for all chart types.
    *
    * <p>Bars (A2 pattern) are not gated — the annotation group is never animated directly, so
    * hover dim cannot conflict with fill-mode. A1 chart types (point, candle, box, treemap,
    * mekko, radar) are gated on {@code svg.ready}, which the Angular directive adds ~900ms after
    * SVG load — just long enough for the first animated element to complete.
    *
    * <p>The {@code :has()} selector requires Chrome 105+, Firefox 121+, Safari 15.4+.
    * On older browsers the dim rules are silently ignored.
    */
   private static void appendHoverCSS(Element svgRoot, Document doc) {
      String dim = String.format(java.util.Locale.US, "%.2f", AnimationConstants.HOVER_DIM_OPACITY);
      String tr  = AnimationConstants.HOVER_TRANSITION;
      appendStyle(svgRoot, doc,
         // Bar (A2 pattern): animation is applied to inner child <path> elements, not to the
         // annotation group itself. The group's own opacity is never animated, so hover dim
         // does not conflict with fill-mode on the inner paths.
         // No .ready gate is needed — bar hover is active immediately on load.
         ".inetsoft-bar,.inetsoft-bar-label,.inetsoft-relation,.inetsoft-relation-edge,.inetsoft-relation-label{transition:" + tr + "}" +
         "svg:has(.inetsoft-bar.inetsoft-active) .inetsoft-bar:not(.inetsoft-active)," +
         "svg:has(.inetsoft-bar.inetsoft-active) .inetsoft-bar-label:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         "svg:has(.inetsoft-relation.inetsoft-active) .inetsoft-relation:not(.inetsoft-active)," +
         "svg:has(.inetsoft-relation.inetsoft-active) .inetsoft-relation-edge:not(.inetsoft-active)," +
         "svg:has(.inetsoft-relation.inetsoft-active) .inetsoft-relation-label:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // A1 chart types: animation is applied directly to the annotation group that is also
         // the hover target. The .ready gate prevents hover dim from conflicting with the
         // group's own animation fill-mode during the entrance animation (~0.9s gate).
         "svg.ready .inetsoft-point,svg.ready .inetsoft-candle,svg.ready .inetsoft-box," +
         "svg.ready .inetsoft-treemap,svg.ready .inetsoft-mekko," +
         "svg.ready .inetsoft-treemap-label,svg.ready .inetsoft-mekko-label," +
         "svg.ready .inetsoft-radar" +
         "{transition:" + tr + "}" +
         // Point dimming.
         "svg.ready:has(.inetsoft-point.inetsoft-active) .inetsoft-point:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // Candlestick dimming.
         "svg.ready:has(.inetsoft-candle.inetsoft-active) .inetsoft-candle:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // Box-plot dimming.
         "svg.ready:has(.inetsoft-box.inetsoft-active) .inetsoft-box:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // Treemap + label dimming.
         "svg.ready:has(.inetsoft-treemap.inetsoft-active) .inetsoft-treemap:not(.inetsoft-active)," +
         "svg.ready:has(.inetsoft-treemap.inetsoft-active) .inetsoft-treemap-label:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // Mekko + label dimming.
         "svg.ready:has(.inetsoft-mekko.inetsoft-active) .inetsoft-mekko:not(.inetsoft-active)," +
         "svg.ready:has(.inetsoft-mekko.inetsoft-active) .inetsoft-mekko-label:not(.inetsoft-active)" +
         "{opacity:" + dim + "!important}" +
         // Area/line hover uses JS-only inline style.opacity (no inetsoft-active class is set).
         // CSS :has(.inetsoft-area.inetsoft-active) rules are not used because CSS :has()
         // cannot scope to a single facet panel within one SVG — all panels would be affected.
         // Radar polygon dimming via CSS :hover only — no inetsoft-active toggling for radar.
         // Hovering inside a polygon's hit area dims sibling polygons; vertex points produce
         // no dim effect. pointer-events:all on the group makes the filled interior reactive.
         // Per-series point dimming is injected in injectRadarAnimation() once N is known.
         ".inetsoft-radar{pointer-events:all}" +
         "svg.ready:has(.inetsoft-radar:hover) .inetsoft-radar:not(:hover)" +
         "{opacity:" + dim + "!important}");
   }

   // -------------------------------------------------------------------------
   // Donut hole processing
   // -------------------------------------------------------------------------

   /**
    * Scan {@code inetsoft-bar} annotation groups for the donut center-hole overlay
    * (an annotation whose only shape descendant is a {@code <circle>}).
    *
    * <p>When found, reclassifies the group to {@code inetsoft-bar-hole} and pins its opacity to 1
    * via an inline {@code !important} style so it is immune to any class-based dim rule.
    *
    * @return {@code {cx, cy, innerR}} in the group's local coordinate system, or {@code null}
    *         when the SVG is not a donut chart.
    */
   private static double[] preprocessDonutHole(Element svgRoot) {
      List<Element> annotBars = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_BAR);

      for(Element g : annotBars) {
         Element circle = findDescendantCircle(g);

         if(circle == null || hasDescendantPath(g)) {
            continue;
         }

         // This is the donut center-hole overlay.
         g.setAttribute("class", SVGSupport.ANNOTATION_DONUT_HOLE);
         String existing = g.getAttribute("style");
         String holeStyle = "opacity:1!important";
         g.setAttribute("style",
                        existing.isEmpty() ? holeStyle : existing + ";" + holeStyle);

         double cx = parseAttr(circle, "cx");
         double cy = parseAttr(circle, "cy");
         double r  = parseAttr(circle, "r");

         if(r > 0) {
            return new double[]{ cx, cy, r };
         }
      }

      return null;
   }

   /**
    * Returns the first {@code <circle>} element anywhere in the subtree of {@code el},
    * or {@code null} if none exists.
    */
   private static Element findDescendantCircle(Element el) {
      NodeList children = el.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(!(child instanceof Element c)) {
            continue;
         }

         if("circle".equals(c.getLocalName())) {
            return c;
         }

         Element found = findDescendantCircle(c);

         if(found != null) {
            return found;
         }
      }

      return null;
   }

   /**
    * Returns {@code true} if {@code el} has any {@code <path>} or {@code <rect>}
    * descendant anywhere in its subtree.
    */
   private static boolean hasDescendantPath(Element el) {
      NodeList children = el.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(!(child instanceof Element c)) {
            continue;
         }

         String tag = c.getLocalName();

         if("path".equals(tag) || "rect".equals(tag)) {
            return true;
         }

         if(hasDescendantPath(c)) {
            return true;
         }
      }

      return false;
   }

   // -------------------------------------------------------------------------
   // Donut ring keyframe helpers
   // -------------------------------------------------------------------------

   /**
    * Build one ring-segment keyframe path for the donut sweep animation.
    *
    * <p>Input geometry: outer arc from {@code (sx,sy)} to {@code (ex,ey)} at radius {@code rx}/{@code ry}.
    * Inner arc runs in the reverse direction at {@code innerR}, connecting the projected inner
    * endpoints so the path is a closed ring segment (no spoke to the center).
    */
   private static String buildRingKeyframe(double sx, double sy, double rx, double ry, double xrot,
                                            int la, int sw, double ex, double ey,
                                            double cx, double cy, double innerR)
   {
      double ratio = innerR / rx;
      double ix2   = cx + ratio * (ex - cx);
      double iy2   = cy + ratio * (ey - cy);
      double ix1   = cx + ratio * (sx - cx);
      double iy1   = cy + ratio * (sy - cy);
      return String.format(java.util.Locale.US,
         "M%.4f %.4f A%.4f %.4f %.0f %d %d %.4f %.4f L%.4f %.4f A%.4f %.4f 0 %d %d %.4f %.4f Z",
         sx, sy, rx, ry, xrot, la, sw, ex, ey, ix2, iy2, innerR, innerR, la, 1 - sw, ix1, iy1);
   }

   /**
    * Build the from-state (zero-length arc) ring path for the donut sweep animation.
    *
    * <p>The from-state is a degenerate ring where both the outer and inner arcs collapse to
    * a single point at {@code (sx,sy)} / its inner projection, enclosing zero area.
    */
   private static String buildRingFromD(double sx, double sy, double rx, double ry, double xrot,
                                         int sw, double cx, double cy, double innerR)
   {
      double ratio = innerR / rx;
      double ix1   = cx + ratio * (sx - cx);
      double iy1   = cy + ratio * (sy - cy);
      return String.format(java.util.Locale.US,
         "M%.4f %.4f A%.4f %.4f %.0f 0 %d %.4f %.4f L%.4f %.4f A%.4f %.4f 0 0 %d %.4f %.4f Z",
         sx, sy, rx, ry, xrot, sw, sx, sy, ix1, iy1, innerR, innerR, 1 - sw, ix1, iy1);
   }

   /** Append a {@code <style>} element to the first {@code <defs>} (creating one if needed). */
   private static void appendStyle(Element svgRoot, Document doc, String css) {
      Element defs = getOrCreateDefs(svgRoot, doc);
      Element style = doc.createElementNS(SVG_NS, "style");
      style.setAttribute("type", "text/css");
      style.setTextContent(css);
      defs.appendChild(style);
   }

   private static Element getOrCreateDefs(Element svgRoot, Document doc) {
      NodeList existing = svgRoot.getElementsByTagName("defs");

      if(existing.getLength() > 0) {
         return (Element) existing.item(0);
      }

      Element defs = doc.createElementNS(SVG_NS, "defs");
      svgRoot.insertBefore(defs, svgRoot.getFirstChild());
      return defs;
   }

   /**
    * Merge an animation style string into the element's existing {@code style} attribute.
    * Appends to any existing inline styles rather than replacing them.
    */
   private static void mergeStyle(Element el, String newStyle) {
      String existing = el.getAttribute("style");

      if(existing.isEmpty()) {
         el.setAttribute("style", newStyle);
      }
      else {
         String sep = existing.endsWith(";") ? "" : ";";
         el.setAttribute("style", existing + sep + newStyle);
      }
   }

   /**
    * Recursively collect {@code <g>} elements with {@code text-rendering="geometricPrecision"},
    * which are the label groups produced by the chart renderer.  Skips {@code <defs>}.
    *
    * <p><b>Batik-specific heuristic:</b> {@code text-rendering="geometricPrecision"} is a
    * Batik-emitted attribute on every label group; no other SVG element in the output carries
    * this attribute. If a future SVG renderer change stops emitting this attribute, or starts
    * emitting it on non-label elements, label animation and hover matching will silently break.
    */
   private static void collectTextGroups(Element el, List<Element> result) {
      if("defs".equals(el.getLocalName())) {
         return;
      }

      NodeList children = el.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if(!(child instanceof Element c)) {
            continue;
         }

         if("g".equals(c.getLocalName())
            && "geometricPrecision".equals(c.getAttribute("text-rendering")))
         {
            // Skip inner Batik style groups that are direct children of annotation groups
            // (inetsoft-area, inetsoft-line, etc.). These share the text-rendering attribute
            // but are chart element wrappers, not text label groups.
            String parentClass = el.getAttribute("class");

            if(!parentClass.startsWith("inetsoft-")) {
               result.add(c);
            }
            else {
               // Still recurse in case a label is nested deeper inside the Batik wrapper.
               collectTextGroups(c, result);
            }
         }
         else {
            collectTextGroups(c, result);
         }
      }
   }

   private static final class AreaBandEntry {
      final Element fillPath;
      final String linePath;

      AreaBandEntry(Element fillPath, String linePath) {
         this.fillPath = fillPath;
         this.linePath = linePath;
      }
   }

   private static final class GhostFillInfo {
      final String polygon;
      final int[] rgb;
      final double delay;
      final Element panel;
      final Element insertBeforeGroup;
      final int seriesIdx;
      final String transform;
      final String clipPath;

      GhostFillInfo(String polygon, int[] rgb, double delay,
                    Element panel, Element insertBeforeGroup, int seriesIdx, String transform,
                    String clipPath)
      {
         this.polygon = polygon;
         this.rgb = rgb;
         this.delay = delay;
         this.panel = panel;
         this.insertBeforeGroup = insertBeforeGroup;
         this.seriesIdx = seriesIdx;
         this.transform = transform;
         this.clipPath = clipPath;
      }
   }
}
