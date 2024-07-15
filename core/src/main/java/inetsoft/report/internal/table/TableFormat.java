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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.*;
import java.util.*;

/**
 * Table column or row attributes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableFormat implements XMLSerializable, Serializable, Cloneable {
   /**
    * Date format.
    */
   public static final String DATE_FORMAT = XConstants.DATE_FORMAT;

   /**
    * Time format.
    */
   public static final String TIME_FORMAT = XConstants.TIME_FORMAT;

   /**
    * TimeInstant format.
    */
   public static final String TIMEINSTANT_FORMAT = XConstants.TIMEINSTANT_FORMAT;

   /**
    * Decimal format.
    */
   public static final String DECIMAL_FORMAT = XConstants.DECIMAL_FORMAT;
   /**
    * Currency format.
    */
   public static final String CURRENCY_FORMAT = XConstants.CURRENCY_FORMAT;
   /**
    * Percent format.
    */
   public static final String PERCENT_FORMAT = XConstants.PERCENT_FORMAT;
   /**
    * Message format.
    */
   public static final String MESSAGE_FORMAT = XConstants.MESSAGE_FORMAT;

   /**
    * Duration format.
    */
   public static final String DURATION_FORMAT = XConstants.DURATION_FORMAT;
   /**
    * Duration format do not pad with zeros.
    */
   public static final String DURATION_FORMAT_PAD_NON = XConstants.DURATION_FORMAT_PAD_NON;
   /**
    * None defined.
    */
   public static final int NONE_DEFINED = 0;
   /**
    * Align defined.
    */
   public static final int ALIGN_DEFINED = 1;
   /**
    * Background defined.
    */
   public static final int BACKGROUND_DEFINED = 2;
   /**
    * Foreground defined.
    */
   public static final int FOREGROUND_DEFINED = 4;
   /**
    * Border color defined.
    */
   public static final int BORDER_COLOR_DEFINED = 8;
   /**
    * Borders defined.
    */
   public static final int BORDER_DEFINED = 0x10;
   /**
    * Font defined.
    */
   public static final int FONT_DEFINED = 0x20;
   /**
    * Format defined.
    */
   public static final int FORMAT_DEFINED = 0x40;
   /**
    * Presenter defined.
    */
   public static final int PRESENTER_DEFINED = 0x80;
   /**
    * Page before defined.
    */
   public static final int PAGE_BEFORE_DEFINED = 0x100;
   /**
    * Page after defined.
    */
   public static final int PAGE_AFTER_DEFINED = 0x200;
   /**
    * Apply all formt.
    */
   public static final int ALL = 0x3FF;

   /**
    * Get the String representation of an alignment constant. For example
    * getAlignmentName(StyleConstants.H_LEFT) returns the string "left".
    * @param i alignment constant
    */
   private static String getAlignmentName(int i) {
      switch(i) {
      case StyleConstants.H_LEFT:
         return "left";
      case StyleConstants.H_CENTER:
         return "center";
      case StyleConstants.H_RIGHT:
         return "right";
      case StyleConstants.H_CURRENCY:
         return "currency";
      case StyleConstants.V_TOP:
         return "top";
      case StyleConstants.V_CENTER:
         return "center";
      case StyleConstants.V_BOTTOM:
         return "bottom";
      case StyleConstants.V_BASELINE:
         return "baseline";
      default:
         return "left";
      }
   }

   /**
    * Create a format from the format specification.
    */
   public Format getFormat(Locale locale) {
      return getFormat(format, format_spec, locale);
   }

   /**
    * Create a format from the format specification.
    */
   public static Format getFormat(String format, String format_spec) {
      return getFormat(format, format_spec, Locale.getDefault());
   }

   /**
    * Create a format from the format specification.
    */
   public static Format getFormat(String format, String format_spec,
                                  Locale locale) {
      if(format == null) {
         return null;
      }

      String key = format + format_spec + locale;
      // @by jasons, don't cache the format directly, instead use a thread-local
      //             variable, so that we don't get data corruption from
      //             asynchronous use of formats.
      FormatThreadLocal local = formatCache.get(key);

      if(local == null) {
         Format fmt = null;

         try {
            if(format.equals(DATE_FORMAT) || format.equals(TIME_FORMAT)
               || format.equals(TIMEINSTANT_FORMAT))
            {
               if(format_spec == null) {
                  if(format.equals(TIME_FORMAT)) {
                     fmt = Tool.createDateFormat("HH:mm:ss", locale);
                  }
                  else if(format.equals(TIMEINSTANT_FORMAT)) {
                     fmt = Tool.createDateFormat("yyyy-MM-dd HH:mm:ss", locale);
                  }
                  else {
                     fmt = Tool.createDateFormat("yyyy-MM-dd", locale);
                  }
               }
               else if(format_spec.equals("FULL")) {
                  fmt = DateFormat.getDateInstance(DateFormat.FULL, locale);
               }
               else if(format_spec.equals("LONG")) {
                  fmt = DateFormat.getDateInstance(DateFormat.LONG, locale);
               }
               else if(format_spec.equals("MEDIUM")) {
                  fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
               }
               else if(format_spec.equals("SHORT")) {
                  fmt = DateFormat.getDateInstance(DateFormat.SHORT, locale);
               }
               else {
                  fmt = Tool.createDateFormat(format_spec, locale);
               }
            }
            else if(format.equals(DECIMAL_FORMAT)) {
               String rounding = SreeEnv.getProperty("format.number.round");

               if(format_spec == null || format_spec.length() == 0) {
                  fmt = NumberFormat.getInstance(locale);
               }
               else if(rounding != null) {
                  fmt = new RoundDecimalFormat(format_spec,
                                               new DecimalFormatSymbols(locale));
                  ((RoundDecimalFormat) fmt).setRoundingByName(rounding);
               }
               else {
                  fmt = new ExtendedDecimalFormat(format_spec,
                     new DecimalFormatSymbols(locale));
               }
            }
            else if(format.equals(CURRENCY_FORMAT)) {
               fmt = NumberFormat.getCurrencyInstance(locale);
            }
            else if(format.equals(PERCENT_FORMAT)) {
               String rounding = SreeEnv.getProperty("format.percent.round");

               if(rounding != null) {
                  // @by stephenwebster, For Bug #6026
                  // Handle special case where regular percent format causes
                  // loss of precision for rounding purposes.
                  DecimalFormat decimalFormat = new DecimalFormat("#,##0.#%");
                  fmt = new RoundDecimalFormat(decimalFormat.toPattern(),
                                               new DecimalFormatSymbols(locale));
                  ((RoundDecimalFormat) fmt).setRoundingByName(rounding);
               }
               else {
                  fmt = NumberFormat.getPercentInstance(locale);
                  ((NumberFormat) fmt).setRoundingMode(
                     Common.getRoundingByName(rounding));
               }
            }
            // @by billh, do not check whether format_spec is empty for bc.
            // Please see customer bug: bug1281545377312
            else if(format.equals(MESSAGE_FORMAT) && format_spec != null) {
               fmt = new MessageFormat(Tool.decodeNL(format_spec), locale);
            }
            else if(format.equals(DURATION_FORMAT)) {
               fmt = new DurationFormat(format_spec);
            }
            else if(format.equals(DURATION_FORMAT_PAD_NON)) {
               fmt = new DurationFormat(format_spec, false);
            }
         }
         catch(Exception ex) {
            String msg = "Failed to get format \"" + format + "\" for specification \"" +
               format_spec + "\" in locale " + locale;
            Tool.addUserMessage(msg);
            LOG.info(msg, ex);
         }

         if(formatCache.size() > 200) {
            formatCache.clear();
         }

         if(fmt != null) {
            formatCache.put(key, local = new FormatThreadLocal(fmt));
         }
      }

      return local == null ? null : local.get();
   }

   /**
    * Set the format.
    */
   public void setFormat(Format fmt) {
      if(fmt == null) {
         format = null;
         format_spec = null;
         return;
      }

      if(fmt instanceof SimpleDateFormat) {
         format = DATE_FORMAT;
         format_spec = ((SimpleDateFormat) fmt).toPattern();
      }
      else if(fmt instanceof DecimalFormat) {
         format = DECIMAL_FORMAT;
         format_spec = ((DecimalFormat) fmt).toPattern();
      }
      else if(fmt instanceof java.text.MessageFormat) {
         format = MESSAGE_FORMAT;
         format_spec = ((java.text.MessageFormat) fmt).toPattern();
      }
      else if(fmt instanceof MessageFormat) {
         format = MESSAGE_FORMAT;
         format_spec = ((MessageFormat) fmt).toPattern();
      }
   }

   /**
    * Get a presenter defined in this class.
    */
   public Presenter getPresenter() {
      if(presenter != null && presenter.getName() != null &&
         !Tool.equals(presenter.getName(), Catalog.getCatalog().getString("(none)")))
      {
         try {
            return presenter.createPresenter();
         }
         catch(Exception ex) {
            LOG.error("Failed to get presenter", ex);
         }
      }

      return null;
   }

   /**
    * Copy the attributes of another TableFormat object.
    * @param tableAttr the TableFormat object to copy from
    */
   public void copyAttributes(TableFormat tableAttr) {
      font = tableAttr.font;
      foreground = tableAttr.foreground;
      background = tableAttr.background;
      alignment = tableAttr.alignment;
      format = tableAttr.format;
      format_spec = tableAttr.format_spec;
      presenter = tableAttr.presenter != null ? (PresenterRef) tableAttr.presenter.clone() : null;
      linewrap = tableAttr.linewrap;
      suppressIfZero = tableAttr.suppressIfZero;
      suppressIfDuplicate = tableAttr.suppressIfDuplicate;
      borders = tableAttr.borders != null ? (Insets) tableAttr.borders.clone() : null;
      topBorderColor = tableAttr.topBorderColor;
      leftBorderColor = tableAttr.leftBorderColor;
      bottomBorderColor = tableAttr.bottomBorderColor;
      rightBorderColor = tableAttr.rightBorderColor;
      pageBefore = tableAttr.pageBefore;
      pageAfter = tableAttr.pageAfter;
      alpha = tableAttr.alpha;
   }

   /**
    * Clone this TableFormat object. Wrapper around clone method of object
    * which only allows protected access.
    */
   @Override
   public Object clone() {
      try {
         TableFormat fmt = (TableFormat) super.clone();

         if(borders != null) {
            fmt.borders = (Insets) borders.clone();
         }

         if(presenter != null) {
            fmt.presenter = (PresenterRef) presenter.clone();
         }

         return fmt;
      }
      catch(CloneNotSupportedException ex) {
      }

      return this;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableFormat)) {
         return false;
      }

      TableFormat fmt2 = (TableFormat) obj;

      if(!equals(background, fmt2.background)) {
         return false;
      }
      else if(!equals(foreground, fmt2.foreground)) {
         return false;
      }
      else if(!equals(font, fmt2.font)) {
         return false;
      }
      else if(!equals(alignment, fmt2.alignment)) {
         return false;
      }
      else if(!equals(format, fmt2.format)) {
         return false;
      }
      else if(!equals(format_spec, fmt2.format_spec)) {
         return false;
      }
      else if(!equals(presenter, fmt2.presenter)) {
         return false;
      }
      else if(!equals(linewrap, fmt2.linewrap)) {
         return false;
      }
      else if(suppressIfZero != fmt2.suppressIfZero) {
         return false;
      }
      else if(suppressIfDuplicate != fmt2.suppressIfDuplicate) {
         return false;
      }
      else if(!Tool.equals(borders, fmt2.borders)) {
         return false;
      }
      else if(!Tool.equals(topBorderColor, fmt2.topBorderColor)) {
         return false;
      }
      else if(!Tool.equals(leftBorderColor, fmt2.leftBorderColor)) {
         return false;
      }
      else if(!Tool.equals(bottomBorderColor, fmt2.bottomBorderColor)) {
         return false;
      }
      else if(!Tool.equals(rightBorderColor, fmt2.rightBorderColor)) {
         return false;
      }
      else if(pageBefore != fmt2.pageBefore) {
         return false;
      }
      else if(pageAfter != fmt2.pageAfter) {
         return false;
      }
      else if(!Objects.equals(alpha, fmt2.alpha)) {
         return false;
      }

      return true;
   }

   /**
    * Check if two objects are equal.
    */
   private boolean equals(Object o1, Object o2) {
      return o1 == null ? o1 == o2 : o1.equals(o2);
   }

   /**
    * Convenience method. Checks if this object contains all default values
    * @return true if this TableFormat object only contains default values
    */
   public boolean isDefault() {
      return alignment == null && format == null && format_spec == null &&
         presenter == null && equals(linewrap, Boolean.TRUE) &&
         !suppressIfZero &&
         !suppressIfDuplicate && font == null && foreground == null &&
         background == null && borders == null && topBorderColor == null &&
         leftBorderColor == null && bottomBorderColor == null &&
         rightBorderColor == null && !pageBefore && !pageAfter &&
         alpha == null;
   }

   /**
    * Merge the format setting. If a setting is not set in this format, it
    * is copied from the other table format.
    */
   public void merge(TableFormat tf) {
      if(tf == null) {
         return;
      }

      if(foreground == null) {
         foreground = tf.foreground;
      }

      if(background == null) {
         background = tf.background;
      }

      if(font == null) {
         font = tf.font;
      }

      if(alignment == null) {
         alignment = tf.alignment;
      }

      if(borders == null) {
         borders = tf.borders;
      }

      if(topBorderColor == null) {
         topBorderColor = tf.topBorderColor;
      }

      if(leftBorderColor == null) {
         leftBorderColor = tf.leftBorderColor;
      }

      if(bottomBorderColor == null) {
         bottomBorderColor = tf.bottomBorderColor;
      }

      if(rightBorderColor == null) {
         rightBorderColor = tf.rightBorderColor;
      }

      if(format == null) {
         format = tf.format;
         format_spec = tf.format_spec;
      }

      if(presenter == null) {
         presenter = tf.presenter;
      }

      if(linewrap == null) {
         linewrap = tf.linewrap;
      }

      if(alpha == null) {
         alpha = tf.alpha;
      }

      suppressIfZero = suppressIfZero || tf.suppressIfZero;
      suppressIfDuplicate = suppressIfDuplicate || tf.suppressIfDuplicate;
      pageBefore = pageBefore || tf.pageBefore;
      pageAfter = pageAfter || tf.pageAfter;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<tableFormat ");
      writeAttributes(writer);
      writer.println(">");

      if(presenter != null) {
         presenter.writeXML(writer);
      }

      writer.println("</tableFormat>");
   }

   /**
    * Writer the attributes of this TableFormat.
    */
   private void writeAttributes(PrintWriter writer) {
      if(font != null) {
         writer.print(" font=\"" + StyleFont.toString(font) + "\"");
      }

      if(foreground != null) {
         writer.print(" foreground=\"" + foreground.getRGB() + "\"");
      }

      if(background != null) {
         writer.print(" background=\"" + background.getRGB() + "\"");
      }

      if(alignment != null) {
         writer.print(" alignment=\"" + alignment + "\"");
      }

      if(borders != null) {
         writer.print(" topBorder=\"" + borders.top + "\"" +
                      " leftBorder=\"" + borders.left + "\"" +
                      " bottomBorder=\"" + borders.bottom + "\"" +
                      " rightBorder=\"" + borders.right + "\"");
      }

      if(topBorderColor != null) {
         writer.println(" topBorderColor=\"" + topBorderColor.getRGB() + "\"");
      }

      if(leftBorderColor != null) {
         writer.println(" leftBorderColor=\"" + leftBorderColor.getRGB() + "\"");
      }

      if(bottomBorderColor != null) {
         writer.println(" bottomBorderColor=\"" + bottomBorderColor.getRGB() + "\"");
      }

      if(rightBorderColor != null) {
         writer.println(" rightBorderColor=\"" + rightBorderColor.getRGB() + "\"");
      }

      if(format != null) {
         writer.print(" format=\"" + format + "\"");
      }

      if(format_spec != null) {
         writer.print(" format_spec=\"" + Tool.escape(format_spec) + "\"");
      }

      if(linewrap != null) {
         writer.print(" linewrap=\"" + linewrap + "\"");
      }

      if(alpha != null) {
         writer.print(" alpha=\"" + alpha + "\"");
      }

      writer.print(" suppressIfZero=\"" + suppressIfZero + "\"");
      writer.print(" suppressIfDuplicate=\"" + suppressIfDuplicate + "\"");
      writer.print(" pageBefore=\"" + pageBefore + "\"");
      writer.print(" pageAfter=\"" + pageAfter + "\"");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String str;

      if((str = Tool.getAttribute(tag, "font")) != null) {
         font = StyleFont.decode(str);
      }

      if((str = Tool.getAttribute(tag, "foreground")) != null) {
         foreground = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "background")) != null) {
         background = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "alignment")) != null) {
         alignment = Integer.valueOf(str);
      }

      if((str = Tool.getAttribute(tag, "topBorder")) != null) {
         borders = new Insets(0, 0, 0, 0);

         borders.top = Integer.parseInt(str);
         borders.left =
            Integer.parseInt(Tool.getAttribute(tag, "leftBorder"));
         borders.bottom =
            Integer.parseInt(Tool.getAttribute(tag, "bottomBorder"));
         borders.right =
            Integer.parseInt(Tool.getAttribute(tag, "rightBorder"));
      }

      if((str = Tool.getAttribute(tag, "topBorderColor")) != null) {
         topBorderColor = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "leftBorderColor")) != null) {
         leftBorderColor = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "bottomBorderColor")) != null) {
         bottomBorderColor = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "rightBorderColor")) != null) {
         rightBorderColor = new Color(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(tag, "linewrap")) != null) {
         linewrap = Boolean.valueOf(str);
      }

      if((str = Tool.getAttribute(tag, "suppressIfZero")) != null) {
         suppressIfZero = str.equalsIgnoreCase("true");
      }

      if((str = Tool.getAttribute(tag, "suppressIfDuplicate")) != null) {
         suppressIfDuplicate = str.equalsIgnoreCase("true");
      }

      if((str = Tool.getAttribute(tag, "pageBefore")) != null) {
         pageBefore = str.equalsIgnoreCase("true");
      }

      if((str = Tool.getAttribute(tag, "pageAfter")) != null) {
         pageAfter = str.equalsIgnoreCase("true");
      }

      format = Tool.getAttribute(tag, "format");
      format_spec = Tool.getAttribute(tag, "format_spec");

      Element tag2 = Tool.getChildNodeByTagName(tag, "presenter");

      if(tag2 != null) {
         presenter = new PresenterRef();
         presenter.parseXML(tag2);
      }

      if((str = Tool.getAttribute(tag, "alpha")) != null) {
         alpha = Integer.valueOf(str);
      }
   }

   /**
    * Get a html string with all setting.
    */
   public String getDescription() {
      StringBuilder buf = new StringBuilder();

      if(foreground != null) {
         buf.append("foreground: " + colorString(foreground) + "<br>");
      }

      if(background != null) {
         buf.append("background: " + colorString(background) + "<br>");
      }

      if(font != null) {
         buf.append("font: " + StyleFont.toString(font) + "<br>");
      }

      if(alignment != null) {
         int h = alignment & StyleConstants.H_ALIGN_MASK;
         int v = alignment & StyleConstants.V_ALIGN_MASK;

         buf.append("alignment: " + getAlignmentName(h) +
                    "-" + getAlignmentName(v) + "<br>");
      }

      if(borders != null) {
         buf.append("borders: " + Util.getLineStyleName(borders.top) + ", " +
                    Util.getLineStyleName(borders.left) + ", " +
                    Util.getLineStyleName(borders.bottom) + ", " +
                    Util.getLineStyleName(borders.right) + "<br>");
      }

      if(topBorderColor != null) {
         buf.append("topBorderColor: " + colorString(topBorderColor) + "<br>");
      }

      if(leftBorderColor != null) {
         buf.append("leftBorderColor: " + colorString(leftBorderColor) +"<br>");
      }

      if(bottomBorderColor != null) {
         buf.append("bottomBorderColor: " + colorString(bottomBorderColor) +
                    "<br>");
      }

      if(rightBorderColor != null) {
         buf.append("rightBorderColor: " + colorString(rightBorderColor) +
                    "<br>");
      }

      if(format != null) {
         buf.append("format: " + format);

         if(format_spec != null) {
            buf.append("[" + format_spec + "]");
         }

         buf.append("<br>");
      }

      if(presenter != null) {
         buf.append("presenter: " + presenter + "<br>");
      }

      if(linewrap != null) {
         buf.append("linewrap: " + linewrap + "<br>");
      }

      if(pageBefore) {
         buf.append("pageBefore : " + pageBefore + "<br>");
      }

      if(pageAfter) {
         buf.append("pageAfter : " + pageAfter + "<br>");
      }

      if(suppressIfZero) {
         buf.append("suppressIfZero : " + suppressIfZero + "<br>");
      }

      if(suppressIfDuplicate) {
         buf.append("suppressIfDuplicate : " + suppressIfDuplicate + "<br>");
      }

      if(alpha != null) {
         buf.append("alpha: " + alpha + "<br>");
      }

      return buf.toString();
   }

   /**
    * Get a text description of the format.
    */
   public String toString() {
      return "TableFormat{" + getDescription() + "}";
   }

   /**
    * Get a string representation of color.
    */
   private static String colorString(Color color) {
      return "#" + Integer.toString(0xFFFFFF & color.getRGB(), 16);
   }

   /**
    * Make a clone of this format with only the visual attributes.
    */
   public TableFormat cloneVisual() {
      TableFormat fmt = (TableFormat) clone();

      fmt.format = null;
      fmt.format_spec = null;
      fmt.presenter = null;
      fmt.pageBefore = false;
      fmt.pageAfter = false;
      fmt.suppressIfZero = false;
      fmt.suppressIfDuplicate = false;

      return fmt;
   }

   public Color foreground;
   public Color background;
   public Font font;
   public Integer alignment;
   public Insets borders;
   public Color topBorderColor;
   public Color leftBorderColor;
   public Color bottomBorderColor;
   public Color rightBorderColor;
   public Integer alpha;

   public String format;
   public String format_spec;

   public PresenterRef presenter;

   public boolean pageBefore = false;
   public boolean pageAfter = false;

   public Boolean linewrap = Boolean.TRUE;
   public boolean suppressIfZero = false;
   public boolean suppressIfDuplicate = false;

   private static final Hashtable<String, FormatThreadLocal> formatCache = new Hashtable<>();

   private static final Logger LOG = LoggerFactory.getLogger(TableFormat.class);
}
