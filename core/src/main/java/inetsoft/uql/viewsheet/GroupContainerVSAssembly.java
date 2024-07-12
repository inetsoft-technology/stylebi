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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.util.ArrayList;

/**
 * GroupContainerVSAssembly represents one container assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class GroupContainerVSAssembly extends AbstractContainerVSAssembly {
   /**
    * Constructor.
    */
   public GroupContainerVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public GroupContainerVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.GROUPCONTAINER_ASSET;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new GroupContainerVSAssemblyInfo();
   }

   /**
    * Get container assembly info.
    * @return the container assembly info.
    */
   @Override
   protected GroupContainerVSAssemblyInfo getContainerInfo() {
      return (GroupContainerVSAssemblyInfo) info;
   }

   /**
    * Layout the Container Assembly.
    * @return the names of the assemblies relocated.
    */
   @Override
   public Assembly[] layout() {
      String[] children = getAssemblies();
      ArrayList arr = new ArrayList();

      for(int i = 0; i < children.length; i++) {
         Assembly assembly = getViewsheet().getAssembly(children[i]);

         if(assembly != null && !(assembly instanceof FloatableVSAssembly)) {
            arr.add(assembly);
         }
      }

      Assembly[] changed = getViewsheet().layout(true, arr);
      updateGridSize();
      return changed;
   }

   /**
    * Get the background image.
    * @return the background image of the group container assembly.
    */
   public String getBackgroundImage() {
      return getContainerInfo().getBackgroundImage();
   }

   /**
    * Update the container size.
    */
   public void updateGridSize() {
      Viewsheet vs = getViewsheet();
      String[] children = getAssemblies();
      // both use pixel to caculate, to make sure the floatable
      // child assembly will work correct
      Point[] locs = getUpperLeftAndBottomRight(vs, children);
      Point upperLeft = locs[0];
      Point bottomRight = locs[1];
      // don't call super.setPosition() to avoid the positions being
      // calculated again in AbstractContainerVSAssembly
      info.setPixelOffset(new Point(upperLeft.x, upperLeft.y));
      info.setPixelSize(new Dimension(bottomRight.x - upperLeft.x, bottomRight.y - upperLeft.y));
   }

   /**
    * Calc the sub component z index.
    */
   @Override
   public void calcChildZIndex() {
      String[] arr = getAssemblies();
      Assembly[] assemblies = new Assembly[arr.length];

      for(int i = 0; i < arr.length; i++) {
         assemblies[i] = getViewsheet().getAssembly(arr[i]);
      }

      VSUtil.calcChildZIndex(assemblies, info.getZIndex());
   }

   /**
    * Get the container's upperLeft and bottomRight points.
    * @return the upperLeft and bottomRight position as an array.
    */
   public static Point[] getUpperLeftAndBottomRight(Viewsheet vs, String[] children) {
      Point upperLeft = null;
      Point bottomRight = null;

      if(children != null ) {
         for(String child : children) {
            Assembly assembly = vs.getAssembly(child);

            // calculate size from tab contents as well
            if(assembly instanceof TabVSAssembly) {
               final String[] assemblies = ((TabVSAssembly) assembly).getAssemblies();
               final Point[] points = getUpperLeftAndBottomRight(vs, assemblies);
               final Point containerUpperLeft = points[0];
               final Point containerBottomRight = points[1];

               // if upperLeft is null bottomRight should also be null
               if(upperLeft == null) {
                  upperLeft = containerUpperLeft;
                  bottomRight = containerBottomRight;
               }
               else {
                  upperLeft.x = Math.min(upperLeft.x, containerUpperLeft.x);
                  upperLeft.y = Math.min(upperLeft.y, containerUpperLeft.y);
                  bottomRight.x = Math.max(bottomRight.x, containerBottomRight.x);
                  bottomRight.y = Math.max(bottomRight.y, containerBottomRight.y);
               }
            }
            else if(assembly instanceof VSAssembly) {
               VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();
               // take care, getPixelPosition is relative to the top viewsheet,
               // not parent viewsheet, use getPixelPositionInViewsheet instead,
               // fix bug1268996242236
               Point pos = vs.getPixelPositionInViewsheet(info);
               Dimension size = vs.getPixelSize(info);

               if(upperLeft == null) {
                  upperLeft = pos;
                  bottomRight = new Point(pos.x + size.width, pos.y + size.height);
               }
               else {
                  upperLeft.x = Math.min(upperLeft.x, pos.x);
                  upperLeft.y = Math.min(upperLeft.y, pos.y);
                  bottomRight.x = Math.max(bottomRight.x, pos.x + size.width);
                  bottomRight.y = Math.max(bottomRight.y, pos.y + size.height);
               }
            }
         }
      }

      if(upperLeft == null) {
         upperLeft = new Point(0, 0);
         bottomRight = new Point(AssetUtil.defw, AssetUtil.defh);
      }

      return new Point[] {upperLeft, bottomRight};
   }
}
