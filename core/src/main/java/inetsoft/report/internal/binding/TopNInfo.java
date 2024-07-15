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
package inetsoft.report.internal.binding;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * TopN Info save information of Summary TopN.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TopNInfo implements Cloneable, Serializable, XMLSerializable {
   /**
    * Create a blank topN info.
    */
   public TopNInfo() {
   }

   /**
    * Clear the setting.
    */
   public void clear() {
      topN = 0;
      sumCol = -1;
      topNReverse = false;
      others = false;
   }

   /**
    * Check if the topn definition is blank.
    *
    * @return <code>true</code> if is blank
    */
   public boolean isBlank() {
      return topN <= 0 || sumCol == -1;
   }

   /**
    * Set the column index used for comparison when creating topN table.
    *
    * @param i the summary column
    */
   public void setTopNSummaryCol(int i) {
      sumCol = i;
   }

   /**
    * Get the column used in topN calculation.
    *
    * @return the summary column
    */
   public int getTopNSummaryCol() {
      return sumCol;
   }

   /**
    * Set the N in topN.
    *
    * @param n the N
    */
   public void setTopN(int n) {
      topN = n;
   }

   /**
    * Get the N in topN.
    *
    * @return the N in topN
    */
   public int getTopN() {
      return topN;
   }

   /**
    * Set to true to get bottom N groups.
    *
    * @param bottom <code>true</code> if bottom N groups
    */
   public void setTopNReverse(boolean bottom) {
      topNReverse = bottom;
   }

   /**
    * Check if getting bottom N groups.
    *
    * @return <code>true</code> if bottom N groups
    */
   public boolean isTopNReverse() {
      return topNReverse;
   }

   /**
    * Set other groups option.
    * @param others other group option.
    */
   public void setOthers(boolean others) {
      this.others = others;
   }

   /**
    * Get other groups option.
    * @return other group option.
    */
   public boolean isOthers() {
      return others;
   }

   /**
    * Deep clone this summary info.
    *
    * @return the cloned object
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
         LOG.error("Failed to clone top-N info", e);
      }

      return null;
   }

   /**
    * Write the xml segment to the destination writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      //xml header part
      writer.print("<topn class=\"" + DataAttr.getClassName(getClass()) +
         "\" ");
      writeAttributes(writer);
      writer.println(">");

      //xml content part
      writeContents(writer);

      //xml footer
      writer.println("</topn>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print("topN=\"" + topN + "\" topNReverse=\"" +
         topNReverse + "\" others=\"" + others + "\" ");
      writer.print("summaryColumn=\"" + sumCol + "\" ");
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      clear();
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Test if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TopNInfo)) {
         return false;
      }

      TopNInfo tinfo = (TopNInfo) obj;
      return this.sumCol == tinfo.sumCol && this.topN == tinfo.topN &&
         this.topNReverse == tinfo.topNReverse && this.others == tinfo.others;
   }

   /**
    * Parse the attribute part.
    */
   protected void parseAttributes(Element tag)
      throws Exception {
      String val;

      if((val = Tool.getAttribute(tag, "topN")) != null) {
         setTopN(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(tag, "topNReverse")) != null) {
         setTopNReverse(val.equalsIgnoreCase("true"));
      }

      if((val = Tool.getAttribute(tag, "others")) != null) {
         setOthers(val.equalsIgnoreCase("true"));
      }

      if((val = Tool.getAttribute(tag, "summaryColumn")) != null) {
         setTopNSummaryCol(Integer.parseInt(val));
      }
   }

   /**
    * Parse other contents.
    */
   protected void parseContents(Element tag) throws Exception {
   }

   private int sumCol = -1;
   private int topN = 0; // N in topN
   private boolean topNReverse = false; // true to get bottom N
   private boolean others = false; // true to append others

   private static final Logger LOG =
      LoggerFactory.getLogger(TopNInfo.class);
}
