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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import java.io.PrintWriter;

public class CalcTableCellValue extends AnnotationCellValue {
   /**
    * The type of annotation cell.
    */
   @Override
   public int getType() {
      return CALC_TABLE;
   }

   public Integer getRepRowIdx() {
      return repRowIdx;
   }

   @Nullable
   public void setRepRowIdx(Integer repRowIdx) {
      this.repRowIdx = repRowIdx;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(repRowIdx != null) {
         writer.print("<repRowIdx><![CDATA[" + repRowIdx + "]]></repRowIdx>");
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "repRowIdx");

      if(node != null) {
         String value = Tool.getValue(node);

         if("null".equals(value)) {
            return;
         }

         repRowIdx = Integer.parseInt(value);
      }
   }

   @Override
   public boolean equals(Object obj) {
      return super.equals(obj) && this.repRowIdx == repRowIdx;
   }

   private Integer repRowIdx;
}
