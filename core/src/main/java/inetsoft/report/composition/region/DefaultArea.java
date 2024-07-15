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
package inetsoft.report.composition.region;

import inetsoft.graph.Visualizable;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;
import inetsoft.util.CoreTool;
import inetsoft.util.DurationFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.text.*;
import java.util.regex.Pattern;

/**
 * DefaultArea defines the method of write Region(s) information to an
 * OutputStream and parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class DefaultArea extends AbstractArea {
   /**
    * Constructor.
    * @param vobj Visualizable object.
    */
   public DefaultArea(Visualizable vobj, AffineTransform trans) {
      super(trans);
      this.vobj = vobj;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(vobj == null ? -1 : vobj.getZIndex());
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      Point2D p = getRelPos();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(getBounds(vobj), trans);
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Get the visualizable object.
    */
   public Visualizable getVisualizable() {
      return vobj;
   }

   /**
    * Get the bounds of an object.
    */
   protected Rectangle2D getBounds(Visualizable vobj) {
      return vobj.getBounds();
   }

   /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   public boolean contains(Point point) {
      if(regions == null || regions.length == 0) {
         return false;
      }

      for(int i = 0; i < regions.length; i++) {
         if(regions[i].contains(point.x, point.y)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Method to test whether the specified rectangle intersect with regions.
    * @param rectangle the specified rectangle.
    */
   public boolean intersects(Rectangle rectangle) {
      if(regions == null || regions.length == 0) {
         return false;
      }

      for(int i = 0; i < regions.length; i++) {
         if(regions[i].intersects(rectangle)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Format an item object with the specified format.
    */
   static String formatItemValue(Format fmt, Object obj) {
      String vtext = CoreTool.getDataString(obj);
      String regex = "\\d{4}-\\d{1,2}-\\d{1,2}";
      Pattern p = Pattern.compile(regex);

      if(fmt != null && obj != null) {
         if(fmt instanceof DateFormat && !(obj instanceof Number)) {
            try {
               if(obj instanceof java.util.Date) {
                  vtext = fmt.format(obj);
               }
               else {
                  //fix bug#19874 by sunnyhe, The judgment obj + "" is in "yyyy-mm-dd" format
                  if(obj != null && p.matcher(obj + "").matches()) {
                     vtext = fmt.format(Date.valueOf(obj + ""));
                  }
               }
            }
            catch(IllegalArgumentException ex) {
               LOG.debug("Failed to format date value: " + obj, ex);
            }
         }
         else if(fmt instanceof DecimalFormat || fmt instanceof DurationFormat) {
            if(obj != null && !(obj instanceof Number)) {
               try {
                  obj = Double.valueOf(obj.toString());
                  vtext = fmt.format(obj);
               }
               catch(NumberFormatException ex) {
                  // ignore
               }
               catch(IllegalArgumentException ex) {
                  LOG.debug("Failed to format number: "+ obj, ex);
               }
            }
            else {
               vtext = fmt.format(obj);
            }
         }
         else {
            if(obj != null) {
               vtext = fmt.format(obj);
            }
         }
      }

      return vtext;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DefaultArea.class);

   protected IndexedSet<String> palette;
   protected Visualizable vobj;
   protected Region[] regions;
}
