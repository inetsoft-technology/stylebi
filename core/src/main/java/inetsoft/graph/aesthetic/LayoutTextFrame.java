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
package inetsoft.graph.aesthetic;

import inetsoft.graph.CompositeLabel;
import inetsoft.graph.TextSegment;
import inetsoft.graph.TextSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.viewsheet.graph.TextLayout;
import inetsoft.uql.viewsheet.graph.TextLayoutItem;
import java.awt.Font;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * A render-time text frame that assembles multiple field values using a
 * {@link TextLayout} configuration — supporting per-field formats, interleaved
 * static text, and horizontal/vertical orientation.
 */
public class LayoutTextFrame extends MultiTextFrame {

   public LayoutTextFrame(String... fields) {
      super(fields);
   }

   /**
    * Get the TextLayout that controls how field values and static items are assembled.
    */
   public TextLayout getLayout() {
      return layout;
   }

   /**
    * Set the TextLayout that controls how field values and static items are assembled.
    */
   public void setLayout(TextLayout layout) {
      this.layout = layout;
   }

   /**
    * Get per-field format overrides. Element {@code i} is applied to field index {@code i};
    * a {@code null} element falls back to {@link #getValueFormat()}.
    */
   public Format[] getFieldFormats() {
      return fieldFormats;
   }

   /**
    * Set per-field format overrides.
    */
   public void setFieldFormats(Format[] fieldFormats) {
      this.fieldFormats = fieldFormats;
   }

   /**
    * Get per-field TextSpec overrides. Element {@code i} is applied to field at
    * textFields[] position {@code i}; a {@code null} element inherits the element-wide TextSpec.
    */
   public TextSpec[] getFieldTextSpecs() {
      return fieldTextSpecs;
   }

   /**
    * Set per-field TextSpec overrides.
    */
   public void setFieldTextSpecs(TextSpec[] fieldTextSpecs) {
      this.fieldTextSpecs = fieldTextSpecs;
   }

   /**
    * Get per-static-item TextSpec overrides. Element {@code i} is applied to the {@code i}-th
    * STATIC item in layout order; a {@code null} element falls back to
    * {@link #buildStaticTextSpec(TextLayoutItem)}.
    */
   public TextSpec[] getStaticItemSpecs() {
      return staticItemSpecs;
   }

   /**
    * Set per-static-item TextSpec overrides.
    */
   public void setStaticItemSpecs(TextSpec[] staticItemSpecs) {
      this.staticItemSpecs = staticItemSpecs;
   }

   /**
    * Get the text for the specified cell.
    * <p>
    * When {@link #layout} is {@code null} or trivial (single field, no statics),
    * delegates to the parent {@link MultiTextFrame} implementation. Otherwise,
    * resolves each field value, formats it, and returns a {@link CompositeLabel}
    * containing a {@link TextSegment} per visible layout item.
    *
    * @param data the dataset
    * @param col  the column name driving the visual element
    * @param row  the row index
    * @return a {@link CompositeLabel} for multi-field layouts, a plain {@code String}
    *         or other value for trivial/fallback cases, or {@code null} if no text
    */
   @Override
   public Object getText(DataSet data, String col, int row) {
      if(layout == null) {
         return super.getText(data, col, row);
      }

      // fieldFormats / fieldTextSpecs are indexed by the same field POSITION that a FIELD item's
      // getFieldIndex() references, so resolve them by index rather than by field name — keying by
      // name would collapse per-position overrides when two layout fields share a column name.
      String[] cols = getFields();

      List<TextSegment> segments = new ArrayList<>();
      int staticIdx = 0;

      for(TextLayoutItem item : layout.getAllItems()) {
         if(item.getType() == TextLayoutItem.FIELD) {
            int fi = item.getFieldIndex();
            if(fi < 0 || cols == null || fi >= cols.length || cols[fi] == null) {
               segments.add(new TextSegment("", null));
               continue;
            }
            String fieldName = cols[fi];
            TextSpec spec = fieldTextSpecs != null && fi < fieldTextSpecs.length
               ? fieldTextSpecs[fi] : null;

            Object raw = data.getData(fieldName, row);
            if(raw == null) {
               segments.add(new TextSegment("", spec));
               continue;
            }

            Format fmt = fieldFormats != null && fi < fieldFormats.length
               ? fieldFormats[fi] : null;
            String value;

            if(fmt != null) {
               try { value = fmt.format(raw); }
               catch(Exception ignored) { value = raw.toString(); }
            }
            else {
               value = raw.toString();
            }

            segments.add(new TextSegment(value, spec));
         }
         else if(item.getType() == TextLayoutItem.SPACING) {
            segments.add(new TextSegment("", null, item.getSpacingAmount()));
         }
         else {
            // STATIC item — always included (empty text renders as nothing) so segment
            // count stays aligned with VLabel's item-iteration count.
            String text = item.getText();
            // Use stub-based spec if available (Format panel); fallback to inline TextLayoutItem format.
            TextSpec staticSpec = (staticItemSpecs != null && staticIdx < staticItemSpecs.length
                                   && staticItemSpecs[staticIdx] != null)
                                  ? staticItemSpecs[staticIdx]
                                  : buildStaticTextSpec(item);
            staticIdx++;
            segments.add(new TextSegment(text != null ? text : "", staticSpec));
         }
      }

      if(segments.isEmpty()) {
         return null;
      }

      return new CompositeLabel(segments.toArray(new TextSegment[0]), layout);
   }

   /**
    * Builds a TextSpec from a static TextLayoutItem's inline styling fields (color, font).
    * Returns null if no styling is set on the item, indicating the element-wide TextSpec
    * should be inherited.
    */
   private static TextSpec buildStaticTextSpec(TextLayoutItem item) {
      java.awt.Color color = item.getColor();
      String family = item.getFontFamily();
      int size = item.getFontSize();
      boolean bold = item.isBold();
      boolean italic = item.isItalic();

      if(color == null && family == null && size < 0 && !bold && !italic) {
         return null;  // inherit element-wide TextSpec
      }

      TextSpec spec = new TextSpec();

      if(color != null) {
         spec.setColor(color);
      }

      if(family != null || size > 0 || bold || italic) {
         Font baseFont = GDefaults.DEFAULT_TEXT_FONT;
         String fam = family != null ? family : baseFont.getFamily();
         int sz = size > 0 ? size : baseFont.getSize();
         int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
         spec.setFont(new Font(fam, style, sz));
      }

      return spec;
   }

   @Override
   public boolean equals(Object o) {
      if(!super.equals(o)) {
         return false;
      }

      if(!(o instanceof LayoutTextFrame)) {
         return false;
      }

      LayoutTextFrame other = (LayoutTextFrame) o;
      return java.util.Objects.equals(layout, other.layout);
   }

   // hashCode is intentionally not overridden: the TextFrame/MultiTextFrame hierarchy overrides
   // equals without hashCode (identity hashing throughout), and visual frames are not used as
   // hash keys. Adding one only here would not make the hierarchy contract-consistent.

   private TextLayout layout;
   private Format[] fieldFormats;
   private TextSpec[] fieldTextSpecs;  // indexed by textFields[] position (same as fieldFormats)
   private TextSpec[] staticItemSpecs; // indexed by STATIC item order in layout.getAllItems()

   private static final long serialVersionUID = 1L;
}
