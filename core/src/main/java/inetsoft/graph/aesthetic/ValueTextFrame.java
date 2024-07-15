/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.aesthetic;

import inetsoft.graph.TextSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.util.MessageFormat;

import java.text.Format;

/**
 * This text frame handles combined text from show-values and text binding.
 *
 * @hidden
 * @version 13.7
 * @author InetSoft Technology
 */
public class ValueTextFrame extends TextFrame {
   public ValueTextFrame(TextFrame textFrame, TextFrame valueFrame,
                         Format textFormat, Format valueFormat)
   {
      this.textFrame = textFrame;
      this.valueFrame = valueFrame;
      this.textFormat = textFormat;
      this.valueFormat = valueFormat;
   }

   /**
    * Get the text binding frame.
    */
   public TextFrame getTextFrame() {
      return textFrame;
   }

   /**
    * Get the show values frame.
    */
   public TextFrame getValueFrame() {
      return valueFrame;
   }

   /**
    * Get the text binding format.
    */
   public Format getTextFormat() {
      return textFormat;
   }

   /**
    * Get the show valuesformat.
    */
   public Format getValueFormat() {
      return valueFormat;
   }

   public void setValueFormat(Format fmt) {
      this.valueFormat = fmt;
   }

   public TextFrame getStackTextFrame() {
      if(textFrame instanceof StackTextFrame) {
         return textFrame;
      }

      if(valueFrame instanceof StackTextFrame) {
         return valueFrame;
      }

      return null;
   }

   @Override
   public Object getText(DataSet data, String col, int row) {
      Object textValue = textFrame == null ? null : textFrame.getText(data, col, row);
      Object voValue = valueFrame == null ? null : valueFrame.getText(data, col, row);

      if(textValue == null && voValue == null) {
         return null;
      }

      if(isTextCombined()) {
         if(valueFormat != null && MessageFormat.getFormats(textFormat)[1] == null) {
            MessageFormat.setFormatByArgumentIndex(textFormat, 1, valueFormat);
         }

         return textFormat.format(new Object[]{ textValue, voValue });
      }

      return new Object[] { textValue, voValue };
   }

   /**
    * Check if the labels are combined into one string with a MessageFormat.
    */
   public boolean isTextCombined() {
      return (textFormat != null || valueFormat != null) &&
         MessageFormat.isMessageFormat(textFormat) &&
         MessageFormat.getFormats(textFormat).length > 1;
   }

   public TextSpec applyCombinedFormat(TextSpec spec) {
      if(msgFmt == null || spec.getFormat() != msgFmt) {
         msgFmt = new java.text.MessageFormat("{0}\n{1}");
         msgFmt.setFormats(new Format[]{ getTextFormat(), getValueFormat() });
         spec = spec.clone();
         spec.setFormat(msgFmt);
      }

      return spec;
   }

   public TextSpec applyTextFormat(TextSpec spec) {
      if(msgFmt == null || spec.getFormat() != textFormat) {
         spec = spec.clone();
         spec.setFormat(textFormat);
      }

      return spec;
   }

   public TextSpec applyValueFormat(TextSpec spec) {
      if(msgFmt == null || spec.getFormat() != valueFormat) {
         spec = spec.clone();
         spec.setFormat(valueFormat);
      }

      return spec;
   }

   private java.text.MessageFormat msgFmt;
   private final Format textFormat;
   private Format valueFormat;
   private final TextFrame textFrame;
   private final TextFrame valueFrame;
}
