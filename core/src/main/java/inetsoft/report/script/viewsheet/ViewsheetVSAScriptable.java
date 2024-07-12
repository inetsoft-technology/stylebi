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
package inetsoft.report.script.viewsheet;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.Scriptable;

import java.util.Date;

/**
 * The viewsheet scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ViewsheetVSAScriptable extends VSAScriptable {
   /**
    * Create a viewsheet assembly scriptable.
    *
    * @param box the specified viewsheet sandbox.
    */
   public ViewsheetVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ViewsheetVSA";
   }

   /**
    * Initialize the assembly properties.
    */
   @Override
   protected void addProperties() {
      // add to ids
      addProperty("updateTime", null);
      addProperty("viewsheetName", null);
      addProperty("viewsheetPath", null);
      addProperty("viewsheetAlias", null);

      if(getInfo().isEmbedded()) {
         addProperty("visible", "isVisible", "setVisible", String.class, getClass(), this);
      }
   }

   /**
    * Get the assembly info of current element.
    */
   private ViewsheetVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ViewsheetVSAssemblyInfo) {
         return (ViewsheetVSAssemblyInfo) getVSAssemblyInfo();
      }

      return null;
   }

   protected VSAssemblyInfo getVSAssemblyInfo() {
      Viewsheet vs = box.getViewsheet();

      if(vs.isEmbedded()) {
         return vs.getVSAssemblyInfo();
      }

      return super.getVSAssemblyInfo();
   }

   /**
    * Get a property value.
    */
   @Override
   public Object get(String id, Scriptable start) {
      if("updateTime".equals(id)) {
         return getUpdateTime();
      }
      else if("viewsheetName".equals(id)) {
         return getName();
      }
      else if("viewsheetPath".equals(id)) {
         return getPath();
      }
      else if("viewsheetAlias".equals(id)) {
         return getAlias();
      }
      else if("taskName".equals(id)) {
         String taskName = getTaskName();

         return StringUtils.isEmpty(taskName) ? super.get(id, start) : taskName;
      }
      else if("currentBookmark".equals(id)) {
         return getCurrentBookmark();
      }

      return super.get(id, start);
   }

   /**
    * Get the query execution or MV creation time.
    */
   private Date getUpdateTime() {
      if(box.isMVEnabled()) {
         AssetEntry ventry = box.getAssetEntry();
         final String vsId = ventry.toIdentifier();
         MVManager manager = MVManager.getManager();
         MVDef[] list = manager.list(false,
                                     def -> !def.isWSMV() && def.getMetaData().isRegistered(vsId));

         if(list.length > 0) {
            long last = 0;

            for(MVDef def : list) {
               last = Math.max(last, def.lastModified());
            }

            return new Date(last);
         }
      }

      return new Date(box.getLastExecutionTime());
   }

   /**
    * Get the Viewsheet name.
    */
   private String getName() {
      return getEntry().getName();
   }

   /**
    * Get the Viewsheet path.
    */
   private String getPath() {
      return getEntry().getUser() != null ?
         Tool.MY_DASHBOARD + "/" + getEntry().getPath() : getEntry().getPath();
   }

   /**
    * Get the Viewsheet alias.
    */
   private String getAlias() {
      return getEntry().getAlias();
   }

   /**
    * get the viewsheet task name
    */
   private String getTaskName() {
      return getEntry().getProperty("taskName");
   }

   private String getCurrentBookmark() {
      VSBookmarkInfo bookmark = box.getOpenedBookmark();
      String currentBookmark = bookmark == null ? null : bookmark.getName();
      return currentBookmark == null ? VSBookmark.HOME_BOOKMARK : currentBookmark;
   }

   private AssetEntry getEntry() {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);
      return vassembly instanceof Viewsheet ? ((Viewsheet) vassembly).getEntry()
         : box.getAssetEntry();
   }
}
