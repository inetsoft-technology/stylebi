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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.DimMember;
import inetsoft.uql.xmla.Measure;

import java.io.PrintWriter;
import java.util.*;

/**
 * Cube table assembly represents a cube.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CubeTableAssembly extends BoundTableAssembly {
   /**
    * Constructor.
    */
   public CubeTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CubeTableAssembly(Worksheet ws, String name) {
      super(ws, name);

      fixColumnSelection(name);
   }

   /**
    * Set public column selection for cube. To cube worksheet, remove entity and
    * caption in public column selection, so to process as normal table.
    */
   @Override
   protected void setPublicColumnSelection(ColumnSelection selection) {
      if(isWorksheetCube()) {
         for(int i = 0; i < selection.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) selection.getAttribute(i);

            if(!column.isVisible()) {
               continue;
            }

            DataRef ref = column.getDataRef();

            if(ref instanceof AttributeRef) {
               AttributeRef ref2 = new AttributeRef(ref.getEntity(), column.getAttribute());
               ref2.setDataType(ref.getDataType());
               column.setDataRef(ref2);
               column.setAlias(column.getAlias());
            }
         }
      }

      super.setPublicColumnSelection(selection);
   }

   private boolean isWorksheetCube() {
      return getName().indexOf(Assembly.CUBE_VS) < 0;
   }

   /**
    * Set the dimension hierarchy expanded paths. For example, if
    * the 'State' and 'City' are show, and only 'NJ' is expanded (cities in
    * NJ visible), the map contains: ["State", ["NJ"]]
    */
   public void setExpandedPaths(Map<String,Set<String>> expanded) {
      this.expanded = expanded;
   }

   /**
    * Get the dimension hierarchy expanded paths.
    */
   public Map<String,Set<String>> getExpandedPaths() {
      return expanded;
   }

   /**
    * Print table property as cache key if necessary.
    */
   @Override
   protected void printProperties(PrintWriter writer) throws Exception {
      writer.print(",");
      writer.print(getProperty("noEmpty"));
   }

   /**
    * Fix column selection for the specified cube.
    */
   private void fixColumnSelection(String name) {
      XCube cube = AssetUtil.getCube(null, name);

      if(cube == null) {
         return;
      }

      Enumeration dims = cube.getDimensions();
      ColumnSelection columns = new ColumnSelection();

      while(dims.hasMoreElements()) {
         XDimension dim = (XDimension) dims.nextElement();

         for(int i = 0; i < dim.getLevelCount(); i++) {
            XCubeMember member = (XCubeMember) dim.getLevelAt(i);
            AttributeRef aref = new AttributeRef(dim.getName(),
                                                 member.getName());

            String label = member instanceof DimMember ?
               ((DimMember) member).getCaption() : member.getName();
            aref.setRefType(getDimensionType(dim));
            aref.setCaption(AssetUtil.getFullCaption(dim) + "." + label);
            ColumnRef column = new ColumnRef(aref);
            column.setAlias(aref.getName());
            column.setDataType(member.getType());
            columns.addAttribute(column);
         }
      }

      Enumeration meas = cube.getMeasures();

      while(meas.hasMoreElements()) {
         XCubeMember mea = (XCubeMember) meas.nextElement();

         String label = mea instanceof Measure ?
            ((Measure) mea).getCaption() : mea.getName();

         AttributeRef aref = new AttributeRef(null, mea.getName());
         aref.setRefType(DataRef.CUBE_MEASURE);
         aref.setCaption(label);
         ColumnRef column = new ColumnRef(aref);
         column.setAlias(aref.getName());
         column.setDataType(mea.getType());
         columns.addAttribute(column);
      }

      setColumnSelection(columns);
   }

   /**
    * Get dimension type.
    */
   private int getDimensionType(XDimension dim) {
      int type = dim.getType();

      if(type == 1) {
         return DataRef.CUBE_TIME_DIMENSION;
      }
      else if(type == 2 || type == 7) {
         return DataRef.CUBE_DIMENSION;
      }

      return type;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      boolean success = super.printKey(writer);

      if(!success) {
         return false;
      }

      writer.print("CBT[" + expanded + "]");
      return true;
   }

   private Map<String,Set<String>> expanded;
}
