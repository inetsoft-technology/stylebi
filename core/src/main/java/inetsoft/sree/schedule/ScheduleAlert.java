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
package inetsoft.sree.schedule;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Class that encapsulates the element and highlight used to define an alert
 * condition.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
public final class ScheduleAlert
   implements Comparable<ScheduleAlert>, Serializable, XMLSerializable, Cloneable
{
   /**
    * Gets the identifier of the element that contains the highlight.
    *
    * @return the element identifier.
    */
   public String getElementId() {
      return elementId;
   }

   /**
    * Sets the identifier of the element that contains the highlight.
    *
    * @param elementId the element identifier.
    */
   public void setElementId(String elementId) {
      this.elementId = elementId;
   }

   /**
    * Gets the name of the highlight that provides the alert condition.
    *
    * @return the highlight name.
    */
   public String getHighlightName() {
      return highlightName;
   }

   /**
    * Sets the name of the highlight that provides the alert condition.
    *
    * @param highlightName the highlight name.
    */
   public void setHighlightName(String highlightName) {
      this.highlightName = highlightName;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<Alert element=\"%s\" highlight=\"%s\"/>%n",
         Tool.byteEncode(getElementId()),
         Tool.byteEncode(getHighlightName()));
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      setElementId(Tool.byteDecode(tag.getAttribute("element")));
      setHighlightName(Tool.byteDecode(tag.getAttribute("highlight")));
   }

   @Override
   public int compareTo(ScheduleAlert o) {
      if(elementId == null && o.elementId != null) {
         return 1;
      }

      if(elementId != null && o.elementId == null) {
         return -1;
      }

      if(elementId != null) {
         int result = elementId.compareTo(o.elementId);

         if(result != 0) {
            return result;
         }
      }

      if(highlightName == null && o.highlightName != null) {
         return 1;
      }

      if(highlightName != null && o.highlightName == null) {
         return -1;
      }

      if(highlightName != null) {
         return highlightName.compareTo(o.highlightName);
      }

      return 0;
   }

   @Override
   protected Object clone() {
      Object copy = null;

      try {
         copy = super.clone();
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone object", e);
      }

      return copy;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleAlert alert = (ScheduleAlert) o;
      return Tool.equals(elementId, alert.elementId) &&
         Tool.equals(highlightName, alert.highlightName);
   }

   @Override
   public int hashCode() {
      int result = elementId != null ? elementId.hashCode() : 0;
      result = 31 * result +
         (highlightName != null ? highlightName.hashCode() : 0);
      return result;
   }

   @Override
   public String toString() {
      return "Alert{" +
         "elementId='" + elementId + '\'' +
         ", highlightName='" + highlightName + '\'' +
         '}';
   }

   private String elementId;
   private String highlightName;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleAlert.class);
   private static final long serialVersionUID = 1L;
}
