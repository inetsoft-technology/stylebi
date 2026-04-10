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
    */
   public static void injectAnimation(Element svgRoot, String animHint) {
      Document doc = svgRoot.getOwnerDocument();

      String base = animHint.split(":")[0];

      appendHoverCSS(svgRoot, doc);

      if(SVGSupport.ANIMATION_PIE.equals(base)) {
         injectPieAnimation(svgRoot, doc);
      }
      else if(SVGSupport.ANIMATION_LINE.equals(base)) {
         injectLineAnimation(svgRoot, doc);
      }
      else if(SVGSupport.ANIMATION_POINT.equals(base)) {
         injectPointAnimation(svgRoot, doc);
      }
      else {
         boolean fadeOnly = SVGSupport.ANIMATION_FADE.equals(base);
         List<Element> annotBars = collectAnnotationGroups(svgRoot, SVGSupport.ANNOTATION_BAR);
         injectBarAnimationFromAnnotations(annotBars, svgRoot, doc, fadeOnly);
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
    * No color or shape heuristics — every annotation group gets the grow animation.
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

      double baseline = fadeOnly ? 0.0
         : SVGAnimationInjector.findBarBaseline(allBounds, horizontal);
      String growAnim = horizontal ? "inetsoft-bar-grow-x" : "inetsoft-bar-grow-y";

      for(int i = 0; i < annotBars.size(); i++) {
         Element g    = annotBars.get(i);
         double[] b   = allBounds.get(i);
         double pos   = horizontal ? (b[1] + b[3]) / 2.0 : (b[0] + b[2]) / 2.0;
         int colIdx   = sortedUnique.indexOf(pos);
         double delay = Math.min(colIdx * 0.12, 2.0);

         if(fadeOnly) {
            mergeStyle(g, SVGAnimationInjector.buildFadeStyle(delay));
         }
         else {
            // Per-bar transform-origin anchored at the baseline.
            double dimMin  = horizontal ? b[0] : b[1];
            double dimSize = horizontal ? (b[2] - b[0]) : (b[3] - b[1]);
            double p = dimSize > 0 ? (baseline - dimMin) / dimSize * 100.0 : 0.0;
            String barOrigin = horizontal
               ? String.format(java.util.Locale.US, "%.2f%% 50%%", p)
               : String.format(java.util.Locale.US, "50%% %.2f%%", p);
            mergeStyle(g, SVGAnimationInjector.buildAnimStyle(barOrigin, growAnim, delay));
         }
      }

      // Fade text label groups that appear after the bars.
      double dotDelay = Math.min(sortedUnique.size() * 0.12, 2.0) + 1.2;
      List<Element> valueLabelGroups = new ArrayList<>();
      collectTextGroups(svgRoot, valueLabelGroups);

      for(Element labelG : valueLabelGroups) {
         mergeStyle(labelG, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-bar-fade 0.35s ease-out %.2fs both", dotDelay));
      }

      // Annotate value labels with inetsoft-bar-label and matching data-row/data-col so the
      // server-side hover CSS can pair each label with its bar. DOM order matches bar order.
      for(int i = 0; i < Math.min(annotBars.size(), valueLabelGroups.size()); i++) {
         Element label = valueLabelGroups.get(i);
         Element bar   = annotBars.get(i);
         label.setAttribute("class", SVGSupport.ANNOTATION_LABEL);
         label.setAttribute("data-" + SVGSupport.ATTR_ROW,
            bar.getAttribute("data-" + SVGSupport.ATTR_ROW));
         label.setAttribute("data-" + SVGSupport.ATTR_COL,
            bar.getAttribute("data-" + SVGSupport.ATTR_COL));
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
      final double SLICE_DUR = 0.25; // seconds per slice

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
               "opacity:0;animation:inetsoft-pie-fade 0.5s ease %.2fs both", si * SLICE_DUR));
         }

         double centerTextDelay = slices.size() * SLICE_DUR + 0.1;

         for(Element textGroup : textGroups) {
            mergeStyle(textGroup, String.format(java.util.Locale.US,
               "opacity:0;animation:inetsoft-pie-fade 0.4s ease %.2fs both", centerTextDelay));
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
            "opacity:0;animation:inetsoft-pie-fade 0.5s ease %.2fs both", delay));
      }

      if(!cssKeyframes.isEmpty()) {
         appendStyle(svgRoot, doc, cssKeyframes.toString());
      }

      // Center text (donut label/value): fade in after ALL slices have finished sweeping.
      double centerTextDelay = totalAnimEndTime + 0.1;

      for(Element textGroup : textGroups) {
         mergeStyle(textGroup, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-pie-fade 0.4s ease %.2fs both", centerTextDelay));
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
      // Dots and value labels appear after all series have started their line animation.
      double dotDelay = Math.max(numSeries - 1, 0) * 0.15 + 1.2;

      String css =
         "@keyframes inetsoft-line-draw{from{stroke-dashoffset:var(--len,2000)}to{stroke-dashoffset:0}}" +
         "@keyframes inetsoft-line-wipe{from{clip-path:inset(0 100% 0 0)}to{clip-path:inset(0 0% 0 0)}}" +
         "@keyframes inetsoft-line-fade{from{opacity:0}to{opacity:1}}" +
         "@keyframes inetsoft-area-fade{from{opacity:0}to{opacity:1}}";
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

         double delay     = seriesIdx * 0.15;
         boolean isDashed = "true".equals(g.getAttribute("data-" + SVGSupport.ATTR_DASHED));

         if(isDashed) {
            // Clip-path wipe — leaves stroke-dasharray pattern intact.
            // clip-path:inset(0 100% 0 0) mirrors the from-keyframe to prevent a flash on
            // the first paint frame before fill-mode takes effect.
            mergeStyle(path, String.format(
               "clip-path:inset(0 100%% 0 0);animation:inetsoft-line-wipe 1.2s cubic-bezier(0.4,0,0.2,1) %.2fs both",
               delay));
         }
         else {
            // Stroke-dashoffset draw-on.
            // Add 2 to the ceiling so the dasharray is always strictly longer than the actual path.
            long len = (long) Math.ceil(SVGAnimationInjector.computePathLength(path.getAttribute("d"))) + 2;
            path.setAttribute("stroke-dasharray", len + " " + len);
            path.setAttribute("stroke-dashoffset", String.valueOf(len));
            mergeStyle(path, String.format(
               "stroke-dashoffset:%d;--len:%d;animation:inetsoft-line-draw 1.2s cubic-bezier(0.4,0,0.2,1) %.2fs both",
               len, len, delay));
         }

         // Ghost fill — color comes from data-color annotation, not from SVG stroke parsing.
         // The transform lives on Batik's inner style group (path's parent), not on the annotation
         // group itself.
         if(!isAreaChart) {
            String pathFill = path.getAttribute("fill");
            boolean hasFill = !pathFill.isEmpty() && !"none".equals(pathFill) && !pathFill.startsWith("url(");

            if(!hasFill) {
               int[] rgb = parseColorData(g.getAttribute("data-" + SVGSupport.ATTR_COLOR));
               String polygon = SVGAnimationInjector.buildFillPolygon(path.getAttribute("d"));

               if(rgb != null && polygon != null && !polygon.isEmpty()) {
                  String transform = ((Element) path.getParentNode()).getAttribute("transform");
                  String clipPath  = path.getAttribute("clip-path");
                  ghostFills.add(new GhostFillInfo(polygon, rgb, delay, 1.2,
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

      // Area fill groups — delay is reversed so the innermost (smallest) polygon fades in first.
      // Rank by color (DOM order) rather than data-series; see areaColorRank comment above.
      for(Element g : annotAreas) {
         Element path = firstDescendantPath(g);

         if(path == null) {
            continue;
         }

         String color     = g.getAttribute("data-" + SVGSupport.ATTR_COLOR);
         int    seriesIdx = areaColorRank.getOrDefault(color, 0);
         double delay     = (numAreaSeries - 1 - seriesIdx) * 0.15;
         mergeStyle(path, String.format("opacity:0;animation:inetsoft-area-fade 0.6s ease %.2fs both", delay));
      }

      // Inject ghost fills in reverse so earlier (lower-indexed) series render behind later ones.
      for(int gi = ghostFills.size() - 1; gi >= 0; gi--) {
         GhostFillInfo gf = ghostFills.get(gi);
         injectGhostFill(doc, gf.panel, gf.insertBeforeGroup, gf.polygon,
                         gf.rgb, gf.delay, gf.duration, numSeries, gf.seriesIdx, gf.transform,
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

            // Skip annotated groups (they have an inetsoft-* class) and non-<g> elements.
            if(!"g".equals(sibling.getLocalName()) || !sibling.getAttribute("class").isEmpty()) {
               continue;
            }

            if(classifyLineGroup(sibling) == LineGroupType.DOTS) {
               for(Element circle : childCircles(sibling)) {
                  mergeStyle(circle, String.format(
                     "opacity:0;animation:inetsoft-line-fade 0.35s ease-out %.2fs both", dotDelay));
               }
            }
         }
      }

      // Pass 5: data value label groups — delay until after all lines have drawn.
      List<Element> valueLabelGroups = new ArrayList<>();
      collectTextGroups(svgRoot, valueLabelGroups);

      for(Element labelG : valueLabelGroups) {
         mergeStyle(labelG, String.format(java.util.Locale.US,
            "opacity:0;animation:inetsoft-line-fade 0.35s ease-out %.2fs both", dotDelay));
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
    */
   private static void injectGhostFill(Document doc, Element panel, Element insertBefore,
                                       String polygon, int[] rgb, double lineDelay, double duration,
                                       int numSeries, int seriesIdx, String transform,
                                       String clipPath)
   {
      double opacity = 0.05 + (numSeries - 1 - seriesIdx) * 0.015;
      opacity = Math.min(opacity, 0.15);
      String fill = String.format("rgba(%d,%d,%d,%.3f)", rgb[0], rgb[1], rgb[2], opacity);

      double delay = Math.max(0, lineDelay + duration * 0.5);

      Element ghostPath = doc.createElementNS(SVG_NS, "path");
      ghostPath.setAttribute("d", polygon);
      ghostPath.setAttribute("fill", fill);
      ghostPath.setAttribute("stroke", "none");
      if(transform != null && !transform.isEmpty()) {
         ghostPath.setAttribute("transform", transform);
      }
      if(clipPath != null && !clipPath.isEmpty()) {
         ghostPath.setAttribute("clip-path", clipPath);
      }
      ghostPath.setAttribute("style", String.format(
         "opacity:0;animation:inetsoft-area-fade 0.5s ease %.2fs both", delay));

      panel.insertBefore(ghostPath, insertBefore);
   }

   // -------------------------------------------------------------------------
   // Point / scatter chart animation
   // -------------------------------------------------------------------------

   /**
    * Fade in point markers staggered largest-first (primary sort), left-to-right (tiebreaker).
    * Each {@code inetsoft-point} annotation group receives an {@code animation} inline style;
    * a single {@code @keyframes inetsoft-point-fade} block is appended to a {@code <style>}.
    *
    * <p>Delays are distributed uniformly across 0–0.6 s regardless of marker count so that
    * large scatter plots do not produce a slow trailing tail.
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
      // Spread delays evenly across 0–1.2 s so large charts still feel snappy.
      // Each marker fades over 0.9 s, giving a total experience of ~2.1 s for the last point.
      double maxDelay = 1.2;
      double step = n > 1 ? maxDelay / (n - 1) : 0;

      for(int i = 0; i < n; i++) {
         double delay = i * step;
         String animStyle = String.format(java.util.Locale.US,
            "animation:inetsoft-point-fade 0.9s ease-out %.2fs both", delay);
         // Apply the animation to the inner content group(s), not the outer annotation group.
         // The outer .inetsoft-point group must have no animation on its own opacity so that
         // the hover CSS rule (opacity:.2!important) can take effect without a cascade conflict
         // between !important-level and animation-level declarations.
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

   // -------------------------------------------------------------------------
   // Hover CSS (server-injected; JS toggles inetsoft-active)
   // -------------------------------------------------------------------------

   /**
    * Inject CSS that dims non-hovered bars/labels when any bar carries the
    * {@code inetsoft-active} class.  JS only needs to toggle that one class;
    * the CSS handles opacity and transitions for the whole chart.
    *
    * <p>The {@code :has()} relational pseudo-class requires Chrome 105+, Firefox 121+,
    * Safari 15.4+.  On older browsers the dimming rule is silently ignored (no error);
    * hover highlighting simply has no visual effect.  StyleBI targets modern browsers only.
    */
   private static void appendHoverCSS(Element svgRoot, Document doc) {
      appendStyle(svgRoot, doc,
         ".inetsoft-bar,.inetsoft-bar-label,.inetsoft-point{transition:opacity .15s}" +
         "svg:has(.inetsoft-bar.inetsoft-active) .inetsoft-bar:not(.inetsoft-active)," +
         "svg:has(.inetsoft-bar.inetsoft-active) .inetsoft-bar-label:not(.inetsoft-active)" +
         "{opacity:.2!important}" +
         "svg:has(.inetsoft-point.inetsoft-active) .inetsoft-point:not(.inetsoft-active)" +
         "{opacity:.2!important}");
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
            result.add(c);
         }
         else {
            collectTextGroups(c, result);
         }
      }
   }

   private static final class GhostFillInfo {
      final String polygon;
      final int[] rgb;
      final double delay;
      final double duration;
      final Element panel;
      final Element insertBeforeGroup;
      final int seriesIdx;
      final String transform;
      final String clipPath;

      GhostFillInfo(String polygon, int[] rgb, double delay, double duration,
                    Element panel, Element insertBeforeGroup, int seriesIdx, String transform,
                    String clipPath)
      {
         this.polygon = polygon;
         this.rgb = rgb;
         this.delay = delay;
         this.duration = duration;
         this.panel = panel;
         this.insertBeforeGroup = insertBeforeGroup;
         this.seriesIdx = seriesIdx;
         this.transform = transform;
         this.clipPath = clipPath;
      }
   }
}
