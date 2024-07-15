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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public abstract class ClickableInputVSAssemblyInfo extends InputVSAssemblyInfo {
   /**
    * Get the onClick script.
    */
   public String getOnClick() {
      return onClick;
   }

   /**
    * Set the onClick script.
    */
   public void setOnClick(String onClick) {
      this.onClick = onClick;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(onClick != null) {
         writer.print("<onClick><![CDATA[" + onClick + "]]></onClick>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      onClick = Tool.getChildValueByTagName(elem, "onClick");
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      ClickableInputVSAssemblyInfo oinfo =
         (ClickableInputVSAssemblyInfo) info;

      if(!Tool.equals(onClick, oinfo.onClick)) {
         onClick = oinfo.onClick;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   // input data
   private String onClick;
}
