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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The line viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class LineVSAScriptable extends ShapeVSAScriptable {
   /**
    * Create a line viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public LineVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "LineVSA";
   }

   protected boolean supportsFill() {
      return false;
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      LineVSAssemblyInfo info = getInfo();

      addProperty("beginArrowStyle", "getBeginArrowStyle", "setBeginArrowStyle",
                  int.class, info.getClass(), info);
      addProperty("endArrowStyle", "getEndArrowStyle", "setEndArrowStyle",
                  int.class, info.getClass(), info);
   }

   /**
    * Get the assembly info of current line.
    */
   private LineVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof LineVSAssemblyInfo) {
         return (LineVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new LineVSAssemblyInfo();
   }

   @Override
   public void setSize(Dimension dim) {
      LineVSAssemblyInfo info = getInfo();

      if(info == null) {
         LOG.warn("Failed to set size, the assembly info is null");
         return;
      }

      if(dim.height <= 0 || dim.width <= 0) {
         LOG.warn("Failed to set size, the dimension is invalid: " + dim);
         return;
      }

      if(box.isRuntime()) {
         info.setPixelSize(dim);
         info.setStartPos(new Point(dim.width, dim.height));
   }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(LineVSAScriptable.class);
}
