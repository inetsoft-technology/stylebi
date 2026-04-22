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

/**
 * Shared timing constants for all SVG chart entrance animations.
 *
 * <p>Values conform to the chart design spec:
 * <ul>
 *   <li>Duration: {@value #DURATION}s for all chart types
 *   <li>Easing: {@value #EASING} for all chart types
 *   <li>Stagger: elements distributed across a fixed {@value #STAGGER_WINDOW}s window so the
 *       last element always begins by {@value #STAGGER_WINDOW}s regardless of element count
 * </ul>
 *
 * <p>To re-enable the legacy bar grow (scaleY/scaleX spring-from-baseline) animation, set
 * {@link #BAR_GROW_ENABLED} to {@code true}.  All grow-related constants are preserved here
 * so the full implementation remains available.
 */
public final class AnimationConstants {
   private AnimationConstants() {}

   // -------------------------------------------------------------------------
   // Spec-mandated values (non-negotiable per design spec)
   // -------------------------------------------------------------------------

   /** Fade duration for all animated elements (seconds). */
   public static final double DURATION = 0.8;

   /** CSS timing function applied to all animations. */
   public static final String EASING = "ease-out";

   /**
    * Total stagger spread (seconds).  The first element starts at 0s and the last always starts
    * at this value, regardless of element count.
    */
   public static final double STAGGER_WINDOW = 2.0;

   // -------------------------------------------------------------------------
   // Hover constants
   // -------------------------------------------------------------------------

   /** Opacity applied to dimmed (non-active) elements during hover. */
   public static final double HOVER_DIM_OPACITY = 0.20;

   /** CSS transition property applied to hoverable annotation groups once animation completes. */
   public static final String HOVER_TRANSITION = "opacity .2s ease";

   /**
    * Seconds added after the last animation ends before the {@code .ready} class is applied to
    * the SVG root.  The {@code .ready} class gates all hover CSS rules so dimming never fires
    * during the entrance animation.
    */
   public static final double READY_BUFFER = 0.1;

   // -------------------------------------------------------------------------
   // Bar grow toggle (disabled by default for spec compliance)
   // -------------------------------------------------------------------------

   /**
    * When {@code false} (default), bars fade in opacity-only per the design spec.
    * When {@code true}, bars use the legacy scaleY/scaleX spring-from-baseline grow animation.
    * Toggle this constant to switch between the two modes; no other code changes are needed.
    */
   public static final boolean BAR_GROW_ENABLED = false;

   /** Spring easing used for the bar grow animation (applies only when BAR_GROW_ENABLED). */
   public static final String BAR_GROW_EASING = "cubic-bezier(0.34,1.4,0.64,1)";

   /** Duration of the bar grow animation (applies only when BAR_GROW_ENABLED). */
   public static final double BAR_GROW_DURATION = 1.2;

   /** Duration of the accompanying bar fade when grow is enabled. */
   public static final double BAR_GROW_FADE_DURATION = 0.45;

   // -------------------------------------------------------------------------
   // Pie animation constants
   // -------------------------------------------------------------------------

   /**
    * Proportional sweep budget per arc group (seconds).  The total pie animation time equals
    * {@code numGroups * PIE_SLICE_DURATION}; each slice's share is then scaled by its sweep
    * angle so that the arc tip moves at a constant angular velocity across all slices.
    */
   public static final double PIE_SLICE_DURATION = 0.25;

   /**
    * Fade duration for pie slices when arc-sweep animation is not available (bezier fallback
    * or when no pie center is found).  Intentionally shorter than {@link #DURATION} because
    * slices are staggered sequentially — each slice starts as the previous one is finishing.
    */
   public static final double PIE_FADE_DURATION = 0.5;

   /**
    * CSS timing function for pie fallback fades.  Uses {@code "ease"} rather than
    * {@link #EASING} ({@code "ease-out"}) because the sequential per-slice fade looks
    * more natural with a symmetric ease; the arc-sweep path already has its own timing.
    */
   public static final String PIE_FADE_EASING = "ease";

   /**
    * Fade duration for the donut center text / label group.  Appears after all arc sweeps
    * finish, so a slightly shorter fade feels snappier without overlapping slice animation.
    */
   public static final double PIE_TEXT_DURATION = 0.4;

   // -------------------------------------------------------------------------
   // Sunburst label glyph-matching constants
   // -------------------------------------------------------------------------

   /**
    * Number of DOM neighbors to scan on each side when correcting glyph-arc mismatches for
    * sunburst label hover tagging.  Batik renders each character as a separate glyph group;
    * glyphs of the same label are always adjacent in DOM order.
    */
   public static final int SUNBURST_GLYPH_WINDOW = 5;

   /**
    * Maximum SVG-unit distance between two glyph reference points for them to be considered
    * part of the same label.  Glyphs of the same label are always close together; glyphs from
    * different labels are separated by more than this distance.
    */
   public static final double SUNBURST_GLYPH_MAX_DIST = 100.0;

   // -------------------------------------------------------------------------
   // Stagger helper
   // -------------------------------------------------------------------------

   /**
    * Compute the animation delay for element {@code index} in a group of {@code count} elements
    * distributed evenly across the {@link #STAGGER_WINDOW}.
    *
    * <p>Examples:
    * <ul>
    *   <li>count=1 → always 0 s
    *   <li>count=5, index=0 → 0 s; index=4 → {@link #STAGGER_WINDOW} s
    *   <li>count=2, index=0 → 0 s; index=1 → {@link #STAGGER_WINDOW} s
    * </ul>
    *
    * @param index 0-based position of this element in sorted stagger order
    * @param count total number of elements in the stagger group
    * @return delay in seconds (≥ 0)
    */
   public static double staggerDelay(int index, int count) {
      if(count <= 1) {
         return 0;
      }

      return index * (STAGGER_WINDOW / (count - 1));
   }
}
