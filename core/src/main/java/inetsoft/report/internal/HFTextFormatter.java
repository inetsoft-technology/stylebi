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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.util.Tool;

import java.util.Date;
import java.util.Locale;

/**
 * Header/footer text formatter. It tracks page number and total and
 * perform the formatting of header/footer text strings.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HFTextFormatter implements java.io.Serializable {
   /**
    * @param now processing time.
    * @param start starting page number.
    * @param total starting total page number.
    */
   public HFTextFormatter(Date now, int start, int total) {
      this.now = now;
      this.start = start;
      this.total = total;
   }

   /**
    * Called at the end of a page.
    */
   public void nextPage(boolean more) {
      this.more = more;
      pgidx++;
      total++;
   }

   /**
    * Set the page numbering starting page index.
    */
   public void setPageNumberingStart(int start) {
      // @by larryl, recalculate the pgnum and total so it matches the setting.
      // The starting can only be pushed back (getting bigger otherwise the
      // page index would not make sense).
      total += this.start - start;
      this.start = start;
   }

   /**
    * Get the page numbering starting page index.
    */
   public int getPageNumberingStart() {
      return start;
   }

   /**
    * Get the current page number.
    */
   public int getPageNumber() {
      return pgidx + 1 - start;
   }

   /**
    * Get the current page index, always based on 0.
    */
   public int getPageIndex() {
      return pgidx;
   }

   /**
    * Create a HeaderTextLens.
    */
   public TextLens create(final String fmt) {
      HTextLens lens = new HTextLens(null);
      lens.setText(fmt);
      return lens;
   }

   /**
    * Create a HeaderTextLens.
    */
   public TextLens create(final TextLens lens) {
      return new HTextLens(lens);
   }

   class HTextLens extends HeaderTextLens {
      public HTextLens(TextLens lens) {
         // make sure no infinite loop
         while(lens instanceof HTextLens) {
            lens = ((HTextLens) lens).lens;
         }

         this.lens = lens;
      }

      /**
       * Get the DisplayText.
       */
      @Override
      public String getDisplayText(Locale locale) {
         return getDisplayText(locale, true);
      }

      /**
       * Get the DisplayText.
       * @param formatExpre format the expression or not, such as {P} to {1}.
       */
      @Override
      public String getDisplayText(Locale locale, boolean formatExpre) {
         // @by billh, if pgnum2 is zero, we should not start to show {P}/{N}
         // in preview mode, but in design mode, we have to show it for users
         // to design, in this case, show 0/0 instead
         final int tmp = pgnum2 <= 0 ? 0 : total;
         final String text = getText();

         // @by mikec, since the extra space only used to expand space for
         // allocate page number, if no page number contains, do not expand.
         final boolean number = text.indexOf("{N") >= 0 ||
            text.indexOf("{n") >= 0;

         String str = StyleCore.format(getText(),
            pgnum2, tmp, now, locale, formatExpre);

         // if not finished printing, add extra space so if the total page
         // number grows, we have enough space to hold 99999 pages in {N}
         if(number && more && tmp < 1000) {
            // @by larryl, add one extra space because the space may take
            // less space than a digit, so the width would not be enough
            int adj = 5 - Integer.toString(tmp < 0 ? 0 : tmp).length() + 4;
            str += "                    ".substring(0, adj);
         }

         return str;
      }

      @Override
      public String getText() {
         final String result = (text == null) ?
            (lens.getText() == null ? "" : lens.getText()) :
            text;

         return replaceSpecialCharWithSpace(result);
      }

      @Override
      public void setText(String txt) {
         this.text = txt;
      }

      /**
       * Get the internal textlens.
       */
      public TextLens getTextLens() {
         return lens;
      }

      private String replaceSpecialCharWithSpace(final String text) {
         // we do not support special characters in header and footer,
         // replace them with space if any
         String result = text;

         result = Tool.replaceAll(result, "\n", " ");
         result = Tool.replaceAll(result, "\r", " ");
         result = Tool.replaceAll(result, "\t", " ");
         return result;
      }

      TextLens lens;
      int pgnum2 = getPageNumber();
      String text = null;
   }

   private Date now;
   private int pgidx = 0; // current page number starts from 0
   private int total = 0; // total page count ignores pages before start
   private int start = 0; // start page number
   private boolean more = true; // more pages to follow
}

