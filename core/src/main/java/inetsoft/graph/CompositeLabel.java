/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 * ...license header...
 */
package inetsoft.graph;

import inetsoft.uql.viewsheet.graph.TextLayout;
import java.io.Serializable;

/**
 * Returned by {@code LayoutTextFrame.getText()} for multi-field text binding.
 * <p>
 * Holds an ordered list of {@link TextSegment} objects (one per TextLayoutItem that
 * produced a non-null value) plus the {@link TextLayout} config that defines their
 * orientation (HORIZONTAL/VERTICAL) and spacing.
 * <p>
 * {@code VLabel.paint()} detects this type and renders each segment independently
 * with its own font/color at the correct position. All VO classes (BarVO, PointVO,
 * LineVO, etc.) store this as their label value without modification.
 * <p>
 * {@link #toString()} concatenates all segment texts with spaces for legacy callers
 * (tooltips, SVG text fallback, hit-testing).
 */
public class CompositeLabel implements Serializable {
   private final TextSegment[] segments;
   private final TextLayout layout;

   public CompositeLabel(TextSegment[] segments, TextLayout layout) {
      this.segments = segments;
      this.layout = layout;
   }

   public TextSegment[] getSegments() {
      return segments;
   }

   public TextLayout getLayout() {
      return layout;
   }

   /**
    * Fallback plain-text representation: all segment texts joined by a space.
    * Used by tooltip, SVG export, and any code path that expects a String label.
    */
   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();

      for(TextSegment seg : segments) {
         if(sb.length() > 0) {
            sb.append(' ');
         }

         sb.append(seg.getText());
      }

      return sb.toString();
   }

   private static final long serialVersionUID = 1L;
}
