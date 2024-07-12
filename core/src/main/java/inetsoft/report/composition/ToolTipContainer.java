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
package inetsoft.report.composition;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;

/**
 * This is a container of ToolTips.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ToolTipContainer implements DataSerializable, AssetObject {
   /**
    * Get the tooltip for the source binding.
    */
   public String getSource() {
      return source;
   }
   
   /**
    * Set the tooltip for the source binding.
    */
   public void setSource(String source) {
      this.source = source;
   }
   
   /**
    * Get tooltip of aggregate.
    */
   public String getAggregate() {
      return aggregate;
   }
   
   /**
    * Set tooltip of aggregate.
    */
   public void setAggregate(String aggr) {
      this.aggregate = aggr;
   }

   /**
    * Get tooltip of sort.
    */
   public String getSort() {
      return sort;
   }

   /**
    * Set tooltip of sort.
    */
   public void setSort(String sort) {
      this.sort = sort;
   }

   /**
    * Get tooltip of condition.
    */
   public String getCondition() {
      return condition;
   }
   
   /**
    * Set tooltip of condition.
    */
   public void setCondition(String cond) {
      this.condition = cond;
   }

   /**
    * Get tooltip of join.
    */
   public String getJoin() {
      return join;
   }
   
   /**
    * Set tooltip of join.
    */
   public void setJoin(String join) {
      this.join = join;
   }

   /**
    * Check if tooltip of source is defineded.
    * @return <tt>true</tt> is defineded, 
    * <tt>false</tt> otherwise.
    */
   public boolean isSourceDefined() {
      return source != null && source.length() > 0;
   }

   /**
    * Check if tooltip of condition is defineded.
    * @return <tt>true</tt> is defineded, 
    * <tt>false</tt> otherwise.
    */
   public boolean isConditionDefined() {
      return condition != null && condition.length() > 0;
   }

   /**
    * Check if tooltip of aggregate is defineded.
    * @return <tt>true</tt> is defineded, 
    * <tt>false</tt> otherwise.
    */
   public boolean isAggregateDefined() {
      return aggregate != null && aggregate.length() > 0;
   }

   /**
    * Check if tooltip of join is defineded.
    * @return <tt>true</tt> is defineded, 
    * <tt>false</tt> otherwise.
    */
   public boolean isJoinDefined() {
      return join != null && join.length() > 0;
   }
   
   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) {
      try {
         output.writeBoolean(source == null);
         
         if(source != null) {
            output.writeUTF(source);
         }

         output.writeBoolean(aggregate == null);
         
         if(aggregate != null) {
            output.writeUTF(aggregate);
         }
         
         output.writeBoolean(condition == null);
         
         if(condition != null) {
            output.writeUTF(condition);
         }
         
         output.writeBoolean(join == null);
         
         if(join != null) {
            output.writeUTF(join);
         }
      }
      catch (IOException e) {
      }
   }
   
   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }
   
   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ToolTipContainer)) {
         return false;
      }

      String objsrc = ((ToolTipContainer) obj).getSource();
      String objaggr = ((ToolTipContainer) obj).getAggregate();
      String objcond = ((ToolTipContainer) obj).getCondition();
      String objjoin = ((ToolTipContainer) obj).getJoin();

      return Tool.equals(objsrc, source) && Tool.equals(objaggr, aggregate) &&
         Tool.equals(objcond, condition) && Tool.equals(objjoin, join);
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }
      
      this.source = Tool.getChildValueByTagName(elem, "source");
      this.aggregate = Tool.getChildValueByTagName(elem, "aggregate");
      this.condition = Tool.getChildValueByTagName(elem, "condition");
      this.join = Tool.getChildValueByTagName(elem, "join");
   }
   
   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println(
         "<ToolTipContainer class=\"" + getClass().getName() + "\">");
         
      if(source != null) {
         writer.print("<source>");
         writer.print("<![CDATA[" + source + "]]>");
         writer.print("</source>");
      }
         
      if(aggregate != null) {
         writer.print("<aggregate>");
         writer.print("<![CDATA[" + aggregate + "]]>");
         writer.print("</aggregate>");
      }
      
      if(condition != null) {
         writer.print("<condition>");
         writer.print("<![CDATA[" + condition + "]]>");
         writer.print("</condition>");
      }
      
      if(join != null) {
         writer.print("<join>");
         writer.print("<![CDATA[" + join + "]]>");
         writer.print("</join>");
      }

      writer.println("</ToolTipContainer>");
   }
   
   @Override
   public Object clone() {
      return null;
   }
   
   private String source;
   private String aggregate;
   private String condition;
   private String join;
   private String sort;
}
