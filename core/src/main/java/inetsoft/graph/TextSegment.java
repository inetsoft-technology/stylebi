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
package inetsoft.graph;

import java.io.Serializable;

/**
 * One styled run of text within a {@link CompositeLabel}.
 * <p>
 * A FIELD segment carries the formatted field value and its per-field {@link TextSpec}.
 * A STATIC segment carries a literal separator/label; its TextSpec may be null (inherit
 * the element-wide TextSpec) or carry inline color/font set in the Layout Designer.
 */
public class TextSegment implements Serializable {
   private final String text;
   private final TextSpec textSpec;  // null = inherit element-wide TextSpec
   private final double spacingAmount;  // gap size for SPACING segments (may be 0)
   private final boolean spacing;      // true only for segments built from SPACING items

   public TextSegment(String text, TextSpec textSpec) {
      this.text = text;
      this.textSpec = textSpec;
      this.spacingAmount = 0.0;
      this.spacing = false;
   }

   /** Constructs a SPACING segment; {@code spacingAmount} may legitimately be 0. */
   public TextSegment(String text, TextSpec textSpec, double spacingAmount) {
      this.text = text;
      this.textSpec = textSpec;
      this.spacingAmount = spacingAmount;
      this.spacing = true;
   }

   public String getText() {
      return text;
   }

   /**
    * The TextSpec for this segment. May be null, meaning the rendering code should
    * fall back to the element-wide TextSpec for font and color.
    */
   public TextSpec getTextSpec() {
      return textSpec;
   }

   /**
    * The spacing amount for SPACING segments. Only positive for segments created
    * from {@code TextLayoutItem.SPACING} items; zero for FIELD and STATIC segments.
    */
   public double getSpacingAmount() {
      return spacingAmount;
   }

   /**
    * Returns true if this segment represents a spacing gap (built from a SPACING item), even when
    * its amount is 0 — classification is by item type, not by the amount.
    */
   public boolean isSpacing() {
      return spacing;
   }

   private static final long serialVersionUID = 1L;
}
