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
package inetsoft.web.vswizard.model;

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import inetsoft.web.vswizard.model.recommender.VSRecommendType;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

public class VSWizardOriginalModel implements Serializable, XMLSerializable {
   public VSWizardOriginalModel() {
   }

   public void setOriginalName(String originalName) {
      this.originalName = originalName;
   }

   public String getOriginalName() {
      return originalName;
   }

   public void setOriginalType(VSRecommendType originalType) {
      this.originalType = originalType;
   }

   public VSRecommendType getOriginalType() {
      return originalType;
   }

   /**
    * get empty assembly
    */
   public boolean isEmptyAssembly() {
      return emptyAssembly;
   }

   /**
    * when the source of assembly is null, set assembly is true.
    */
   public void setEmptyAssembly(boolean emptyAssembly) {
      this.emptyAssembly = emptyAssembly;
   }

   /**
    * get original binding assembly
    */
   public ChartVSAssembly getTempBinding() {
      return tempBinding;
   }

   /**
    * set original binding assembly to keep the binding info for original assembly
    */
   public void setTempBinding(ChartVSAssembly originalBinding) {
      this.tempBinding = originalBinding;
   }

   /**
    * Get the chart info of the (existing chart) this wizard is opened from.
    */
   public ChartInfo getOriginalChartInfo() {
      return origInfo;
   }

   public void setOriginalChartInfo(ChartInfo info) {
      origInfo = info;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<vsWizardOriginalModel class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</vsWizardOriginalModel>");
   }

   protected void writeAttributes(PrintWriter writer) {
      if(originalName != null) {
         writer.print(" originalName=\"" + originalName + "\"");
      }

      if(originalType != null) {
         writer.print(" originalType=\"" + originalType.name() + "\"");
      }

      writer.print(" emptyAssembly=\"" + emptyAssembly + "\"");
   }

   protected void writeContents(PrintWriter writer) {
      if(tempBinding != null) {
         tempBinding.writeXML(writer);
      }

      if(origInfo != null) {
         origInfo.writeXML(writer);
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   protected void parseAttributes(Element elem) {
      originalName = Tool.getAttribute(elem, "originalName");
      emptyAssembly = "true".equals(Tool.getAttribute(elem, "emptyAssembly"));
      String originalTypeVal = Tool.getAttribute(elem, "originalType");

      if(!Tool.isEmptyString(originalTypeVal)) {
         originalType = VSRecommendType.valueOf(originalTypeVal);
      }
   }

   protected void parseContents(Element elem) throws Exception {
      Element item = Tool.getChildNodeByTagName(elem, "assembly");

      if(item != null) {
         tempBinding = (ChartVSAssembly) AbstractVSAssembly.createVSAssembly(item, null);
      }

      item = Tool.getChildNodeByTagName(elem, "VSChartInfo");

      if(item != null) {
         origInfo = VSChartInfo.createVSChartInfo(item);
      }
   }

   private String originalName;
   private VSRecommendType originalType;
   private boolean emptyAssembly;//the source of assembly is null
   private ChartVSAssembly tempBinding;// to keep the binding information for originalAssembly
   private ChartInfo origInfo;
}
