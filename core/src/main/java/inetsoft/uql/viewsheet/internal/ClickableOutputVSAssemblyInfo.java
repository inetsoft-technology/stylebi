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

import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ClickableOutputVSAssemblyInfo defines the common API for onClick.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class ClickableOutputVSAssemblyInfo extends OutputVSAssemblyInfo
                  implements PopVSAssemblyInfo {
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
      ClickableOutputVSAssemblyInfo oinfo =
         (ClickableOutputVSAssemblyInfo) info;

      if(!Tool.equals(onClick, oinfo.onClick)) {
         onClick = oinfo.onClick;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Get the run time pop option.
    */
   @Override
   public int getPopOption() {
      return popOptionValue.getIntValue(false, NO_POP_OPTION);
   }

   /**
    * Set the run time pop option.
    */
   @Override
   public void setPopOption(int popOption) {
      popOptionValue.setRValue(popOption);
   }

   /**
    * Get the design time pop option.
    */
   @Override
   public int getPopOptionValue() {
      return popOptionValue.getIntValue(true, NO_POP_OPTION);
   }

   /**
    * Set the design time pop option.
    */
   @Override
   public void setPopOptionValue(int popOption) {
      popOptionValue.setDValue(popOption + "");
   }

   /**
    * Get the run time pop component.
    */
   @Override
   public String getPopComponent() {
      Object popComponent = popComponentValue.getRValue();
      return popComponent == null ? null : popComponent + "";
   }

   /**
    * Set the run time pop component.
    */
   @Override
   public void setPopComponent(String popComponent) {
      popComponentValue.setRValue(popComponent);
   }

   /**
    * Get the design time pop component.
    */
   @Override
   public String getPopComponentValue() {
      return popComponentValue.getDValue();
   }

   /**
    * Set the design time pop component.
    */
   @Override
   public void setPopComponentValue(String popComponent) {
      popComponentValue.setDValue(popComponent);
   }

   /**
    * Set the design time pop Location.
    */
   @Override
   public void setPopLocationValue(PopLocation popLocation) {

      if (popLocation != null) {
         this.popLocationValue.setDValue(popLocation.value);
      }
      else {
         this.popLocationValue.setDValue(null);
      }
   }

   /**
    * Set the runtime pop Location.
    */
   @Override
   public void setPopLocation(PopLocation popLocation) {
      this.popLocationValue.setRValue(popLocation.value);
   }

   /**
    * Set the design time pop Location.
    */
   @Override
   public PopLocation getPopLocationValue() {

      if("CENTER".equals(popLocationValue.getDValue())) {
         return PopLocation.CENTER;
      }
      else {
         return PopLocation.MOUSE; //default if not set
      }
   }

   /**
    * Set the runtime time pop Location.
    */
   @Override
   public PopLocation getPopLocation() {
      if (popLocationValue.getRValue() != null) {

         if("CENTER".equals(popLocationValue.getRValue().toString())) {
            return PopLocation.CENTER;
         }
         else if("MOUSE".equals(popLocationValue.getRValue().toString())) {
            return PopLocation.MOUSE;
         }
      }

      return getPopLocationValue();
   }

   @Override
   public String getAlpha() {
      Object alpha = alphaValue.getRValue();
      return alpha == null ? null : alpha + "";
   }

   /**
    * Set the run time alpha.
    */
   @Override
   public void setAlpha(String alpha) {
      alphaValue.setRValue(alpha);
   }

   /**
    * Get the design time alpha.
    */
   @Override
   public String getAlphaValue() {
      return alphaValue.getDValue();
   }

   /**
    * Set the design time alpha.
    */
   @Override
   public void setAlphaValue(String alpha) {
      alphaValue.setDValue(alpha);
   }


   // input data
   private String onClick;
   protected DynamicValue2 popOptionValue;
   protected DynamicValue popComponentValue = new DynamicValue();
   protected DynamicValue popLocationValue = new DynamicValue();
   protected DynamicValue alphaValue = new DynamicValue();

}
