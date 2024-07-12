/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.TextFormat;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * TextFormat holds the attributes to format text.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AllTextFormat extends TextFormat {
   public static TextFormat fixFormat(TextFormat fmt) {
      if(!(fmt instanceof AllTextFormat)) {
         return fmt;
      }

      AllTextFormat afmt = (AllTextFormat) fmt;
      return afmt.fmts.get(0);
   }

   /**
    * Constructor.
    */
   public AllTextFormat(List<TextFormat> fmts) {
      this.fmts = fmts;
   }

   /**
    * Set the color for this text format.
    * @param color color format information.
    */
   @Override
   public void setColor(Color color) {
      setColor(color, true);
   }

   /**
    * Set the color for this text format.
    * @param color color format information.
    */
   @Override
   public void setColor(Color color, boolean defined) {
      applySet("setColor", new Object[] {color, defined},
               new Class[] {Color.class, boolean.class});
   }

   /**
    * Get the color for this text format.
    * @return the color format information.
    */
   @Override
   public Color getColor() {
      return (Color) applyGet("getColor");
   }

   /**
    * Set the background for this text format.
    */
   @Override
   public void setBackground(Color bg) {
      setBackground(bg, true);
   }

   /**
    * Set the background for this text format.
    */
   @Override
   public void setBackground(Color bg, boolean defined) {
      applySet("setBackground", new Object[] {bg, defined},
               new Class[] {Color.class, boolean.class});
   }

   /**
    * Get the background for this text format.
    */
   @Override
   public Color getBackground() {
      return (Color) applyGet("getBackground");
   }

   /**
    * Set the alpha of the fill color.
    * @param alpha 0-100 (100 == opaque).
    */
   @Override
   public void setAlpha(int alpha) {
      setAlpha(alpha, true);
   }

   /**
    * Set the alpha of the fill color.
    */
   @Override
   public void setAlpha(int alpha, boolean defined) {
      applySet("setAlpha", new Object[] {alpha, defined},
               new Class[] {int.class, boolean.class});
   }

   /**
    * Get the alpha of the fill color.
    */
   @Override
   public int getAlpha() {
      return (Integer) applyGet("getAlpha");
   }

   /**
    * Set the font for this text format.
    * @param font font format information
    */
   @Override
   public void setFont(Font font) {
      setFont(font, true);
   }

   /**
    * Set the font for this text format.
    * @param font font format information
    */
   @Override
   public void setFont(Font font, boolean defined) {
      applySet("setFont", new Object[] {font, defined},
               new Class[] {Font.class, boolean.class});
   }

   /**
    * Get the font for this text format.
    * @return the font format information.
    */
   @Override
   public Font getFont() {
      return (Font) applyGet("getFont");
   }

   /**
    * Set the format information for this text format.
    * @param fmt represents a column's format information.
    */
   @Override
   public void setFormat(XFormatInfo fmt) {
      applySet("setFormat", fmt, XFormatInfo.class);
   }

   /**
    * Get the XFormatInfo for this processor.
    * @return the format information.
    */
   @Override
   public XFormatInfo getFormat() {
      return (XFormatInfo) applyGet("getFormat");
   }

   /**
    * Set the rotation for this text format.
    * @param rotation presents text whirling angle.
    */
   @Override
   public void setRotation(Number rotation) {
      setRotation(rotation, true);
   }

   /**
    * Set the rotation for this text format.
    * @param rotation presents text whirling angle.
    */
   @Override
   public void setRotation(Number rotation, boolean defined) {
      applySet("setRotation", new Object[] {rotation, defined},
               new Class[] {Number.class, boolean.class});
   }

   /**
    * Get the rotation for this text format.
    * @return the text rotation.
    */
   @Override
   public Number getRotation() {
      return (Number) applyGet("getRotation");
   }

   /**
    * Set the alignment for this text format.
    */
   @Override
   public void setAlignment(int align) {
      setAlignment(align, true);
   }

   /**
    * Set the align for this text format.
    */
   @Override
   public void setAlignment(int align, boolean defined) {
      applySet("setAlignment", new Object[] {align, defined},
               new Class[] {int.class, boolean.class});
   }

   /**
    * Get the align for this text format.
    */
   @Override
   public int getAlignment() {
      return (Integer) applyGet("getAlignment");
   }

   /**
    * Check if font defined.
    */
   @Override
   public boolean isFontDefined() {
      return (Boolean) applyGet("isFontDefined");
   }

   /**
    * Check if alignment defined.
    */
   @Override
   public boolean isAlignmentDefined() {
      return (Boolean) applyGet("isAlignmentDefined");
   }

   /**
    * Check if color defined.
    */
   @Override
   public boolean isColorDefined() {
      return (Boolean) applyGet("isColorDefined");
   }

   /**
    * Check if background defined.
    */
   @Override
   public boolean isBackgroundDefined() {
      return (Boolean) applyGet("isBackgroundDefined");
   }

   /**
    * Check if rotation defined.
    */
   @Override
   public boolean isRotationDefined() {
      return (Boolean) applyGet("isRotationDefined");
   }

   /**
    * Check if alpha is defined.
    */
   @Override
   public boolean isAlphaDefined() {
      return (Boolean) applyGet("isAlphaDefined");
   }

   /**
    * Retrieve property from multiple objects.
    */
   private Object applyGet(String funcName) {
      if(fmts.size() == 0) {
         return null;
      }

      try {
         Method func = TextFormat.class.getMethod(funcName, new Class[0]);
         return func.invoke(fmts.get(0));
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Make function call on all objects.
    */
   private void applySet(String funcName, Object param, Class type) {
      applySet(funcName, param, new Class[] {type});
   }

   private void applySet(String funcName, Object param, Class[] types) {
      try {
         Method func = TextFormat.class.getMethod(funcName, types);

         for(int i = 0; i < fmts.size(); i++) {
            if(param instanceof XFormatInfo) {
               param = ((XFormatInfo) param).clone();
            }

            if(param instanceof Object[]) {
               func.invoke(fmts.get(i), (Object[]) param);
            }
            else {
               func.invoke(fmts.get(i), param);
            }
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         List<TextFormat> fmts0 = new ArrayList();

         for(TextFormat fmt : fmts) {
            fmts0.add((TextFormat) fmt.clone());
         }

         return new AllTextFormat(fmts0);
      }
      catch(Exception e) {
         LOG.error("Failed to clone text format", e);
         return null;
      }
   }

   @Override
   public void writeXML(java.io.PrintWriter writer) {
      throw new RuntimeException("Unsupported method \"writeXML\" call!");
   }

   @Override
   public void parseXML(org.w3c.dom.Element tag) throws Exception {
      throw new RuntimeException("Unsupported method \"parseXML\" call!");
   }

   private List<TextFormat> fmts;
}
