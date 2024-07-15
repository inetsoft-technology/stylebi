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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Arrays;

public class ChartDataValue extends AnnotationCellValue {
   /**
    * The type of annotation cell.
    */
   @Override
   public int getType() {
      return CHART;
   }

   /**
    * Get measure name.
    * @return measure name.
    */
   public String getMeasureName() {
      return measureName;
   }

   /**
    * Set measure name.
    * @param measureName the specified measure name.
    */
   public void setMeasureName(String measureName) {
      this.measureName = measureName;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<measureName><![CDATA[" + measureName +
                   "]]></measureName>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      measureName = Tool.getChildValueByTagName(elem, "measureName");
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof ChartDataValue)) {
         return false;
      }

      ChartDataValue value = (ChartDataValue) obj;

      return super.equals(obj) && Tool.equals(measureName, value.measureName);
   }

   @Override
   public String toString() {
      return super.toString() + "[" + measureName + ":" + Arrays.toString(getValues()) + "]";
   }

   private String measureName;
}
