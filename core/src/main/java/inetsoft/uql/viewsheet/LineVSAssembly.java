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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.model.VSViewsheetModel;

import java.awt.*;

/**
 * LineVSAssembly represents one oval assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LineVSAssembly extends ShapeVSAssembly {
   /**
    * Constructor.
    */
   public LineVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public LineVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.LINE_ASSET;
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new LineVSAssemblyInfo();
   }

   /**
    * Set start point.
    * @param pos the specified point.
    */
   public void setStartPos(Point pos) {
      ((LineVSAssemblyInfo) getInfo()).setStartPos(pos);
   }

   /**
    * Set end point.
    * @param pos the specified point.
    */
   public void setEndPos(Point pos) {
      ((LineVSAssemblyInfo) getInfo()).setEndPos(pos);
   }

   /**
    * Update the anchor positions.
    */
   public boolean updateAnchor(Viewsheet vs) {
      LineVSAssemblyInfo info = (LineVSAssemblyInfo) getInfo();
      boolean changed = false;

      if(info.getStartAnchorID() != null) {
         Point pt = getAnchorPos(vs, info, info.getStartAnchorID(), info.getStartAnchorPos());

         // anchor element deleted
         if(pt == null) {
            info.setStartAnchorID(null);
         }
         else if(!info.getStartPos().equals(pt)) {
            setStartPos(pt);
            changed = true;
         }
      }

      if(info.getEndAnchorID() != null) {
         Point pt = getAnchorPos(vs, info, info.getEndAnchorID(), info.getEndAnchorPos());

         // anchor element deleted
         if(pt == null) {
            info.setEndAnchorID(null);
         }
         else if(!info.getEndPos().equals(pt)) {
            setEndPos(pt);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Get the anchor position.
    */
   public Point getAnchorPos(Viewsheet vs, LineVSAssemblyInfo line, String id, int pos) {
      return getAnchorPos(vs, line, id, pos, true);
   }

   /**
    * Get the anchor position.
    */
   public Point getAnchorPos(Viewsheet vs, LineVSAssemblyInfo line,
      String id, int pos, boolean relative)
   {
      Assembly vsobj = vs.getAssembly(id);

      if(vsobj == null) {
         return null;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) vsobj.getInfo();
      Point pt0 = line.getLayoutPosition() != null ?
         line.getLayoutPosition() : vs.getPixelPosition(line);

      Point pt;
      Dimension size;

      //if assembly is a viewsheet, its height is actually the bound height plus its icon height as
      // of 12.3
      if(info instanceof ViewsheetVSAssemblyInfo) {
         VSViewsheetModel vm = new VSViewsheetModel((Viewsheet) vsobj,
                                                    (ViewsheetVSAssemblyInfo) info, null);
         pt = new Point(vm.getBounds().x, vm.getBounds().y);
         size = new Dimension(vm.getBounds().width,
                              vm.getBounds().height + vm.getIconHeight() + 5);
      }
      else {
         pt = info.getLayoutPosition() != null ?
            info.getLayoutPosition() : vs.getPixelPosition(info);
         size = info.getLayoutSize() != null ? info.getLayoutSize() : vs.getPixelSize(info);
      }

      int x, y;

      if((pos & LineVSAssemblyInfo.NORTH) != 0) {
         y = pt.y;
      }
      else if((pos & LineVSAssemblyInfo.SOUTH) != 0) {
         y = pt.y + size.height;
      }
      else {
         y = pt.y + size.height / 2;
      }

      if((pos & LineVSAssemblyInfo.WEST) != 0) {
         x = pt.x;
      }
      else if((pos & LineVSAssemblyInfo.EAST) != 0) {
         x = pt.x + size.width;
      }
      else {
         x = pt.x + size.width / 2;
      }

      if(relative) {
         return new Point(x - pt0.x, y - pt0.y);
      }
      else {
         return new Point(x, y);
      }
   }
}
