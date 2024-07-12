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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.report.composition.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * Move assembly event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class MoveVSAssemblyEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public MoveVSAssemblyEvent() {
      super();
   }

   /**
    * Constructor.
    * @param name assembly name.
    * @param position new postion.
    */
   public MoveVSAssemblyEvent(String name, Point position, boolean pixel) {
      this();
      put("name", name);
      put("x", "" + position.x);
      put("y", "" + position.y);
      put("pixel", "" + pixel);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Move Assembly");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return undoable;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      String tname = (String) get("name");
      String[] names = Tool.split(tname, '/');
      return names;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      String[] names = getAssemblies();
      int x = 0;
      int y = 0;
      boolean pixel = "true".equals(get("pixel"));

      try {
         x = Integer.parseInt((String) get("x"));
         y = Integer.parseInt((String) get("y"));
      }
      catch(NumberFormatException ex) {
         return;
      }

      VSAssemblyInfo oinfo = null;
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      VSAssembly assembly = null;

      if(names.length == 1) {
         String name = names[0];
         assembly = (VSAssembly) vs.getAssembly(name);

         if(assembly != null) {
            oinfo = (VSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
         }
      }

      ChangedAssemblyList clist =
         createList(false, this, command, rvs, getLinkURI());
      VSEventUtil.moveVSAssembly(rvs, names, x, y, command, pixel, true);
      VSEventUtil.layoutViewsheet(rvs, this, getID(), getLinkURI(), command,
                                  names, clist);

      if(oinfo != null) {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if(assembly instanceof FloatableVSAssembly) {
            Point pixelpos1 = vs.getPixelPosition(info);
            Point pixelpos2 = vs.getPixelPosition(oinfo);
            undoable = !Tool.equals(pixelpos1, pixelpos2);
         }
         else {
            Point pos1 = info.getPixelOffset();
            Point pos2 = oinfo.getPixelOffset();
            undoable = !Tool.equals(pos1, pos2);
         }
      }
   }

   private boolean undoable = true;
}
