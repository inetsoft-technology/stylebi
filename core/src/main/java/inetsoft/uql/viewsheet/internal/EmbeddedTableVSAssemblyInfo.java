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

import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * EmbeddedTableVSAssemblyInfo, the assembly info of an embedded table assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class EmbeddedTableVSAssemblyInfo extends TableVSAssemblyInfo {
   /**
    * Constructor.
    */
   public EmbeddedTableVSAssemblyInfo() {
      super();
   }

   /**
    * Get the sort info.
    * @return the sort info.
    */
   @Override
   public SortInfo getSortInfo() {
      return isForm() ? super.getSortInfo() : null;
   }

   /**
    * Set whether it is a summary table.
    * @param summary <tt>true</tt> if it is a summary table,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setSummaryTable(boolean summary) {
      if(summary) {
         throw new RuntimeException("Summary option is not supported!");
      }

      super.setSummaryTable(summary);
   }

   /**
    * Check if the selection should be submitted on change at runtime.
    */
   public boolean isSubmitOnChange() {
      return Boolean.valueOf(submitValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if the selection should be submitted on change at runtime.
    */
   public void setSubmitOnChange(boolean submit) {
      submitValue.setRValue(submit);
   }

   /**
    * Set if the selection should be submitted on change design time.
    */
   public void setSubmitOnChangeValue(boolean submit) {
      submitValue.setDValue(submit + "");
   }

   /**
    * Get whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public String getSubmitOnChangeValue() {
      return submitValue.getDValue();
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" submitOnChange=\"" + isSubmitOnChange() + "\"");
      writer.print(" submitOnChangeValue=\"" + submitValue.getDValue() + "\"");
   }

   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      submitValue.setDValue(getAttributeStr(elem, "submitOnChange", "true"));
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);

      if(deep) {
         EmbeddedTableVSAssemblyInfo sinfo = (EmbeddedTableVSAssemblyInfo) info;

         if(!Tool.equals(submitValue, sinfo.submitValue) ||
            !Tool.equals(isSubmitOnChange(), sinfo.isSubmitOnChange()))
         {
            submitValue = sinfo.submitValue;
            result = true;
         }
      }

      return result;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();
      submitValue.setRValue(null);
   }

   // view
   private DynamicValue submitValue = new DynamicValue("true", XSchema.BOOLEAN);
}
