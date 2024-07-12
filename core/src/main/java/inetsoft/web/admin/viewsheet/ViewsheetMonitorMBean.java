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
package inetsoft.web.admin.viewsheet;

import inetsoft.report.internal.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;

import javax.management.openmbean.*;
import java.util.Date;
import java.util.List;

@Component
@ManagedResource
public class ViewsheetMonitorMBean {
   @Autowired()
   public ViewsheetMonitorMBean(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ManagedAttribute
   public int getActiveCount() {
      return viewsheetService.getCount(ViewsheetModel.State.OPEN);
   }

   @ManagedAttribute
   public int getExecutingCount() {
      return viewsheetService.getCount(ViewsheetModel.State.EXECUTING);
   }

   /**
    * Destroy the viewsheet.
    */
   @ManagedOperation
   public void destroy(String id) throws Exception {
      viewsheetService.destroy(id);
   }

   /**
    * Destroy the viewsheets.
    */
   @ManagedOperation
   public void destroy(String[] ids) throws Exception {
      viewsheetService.destroy(ids);
   }

   @ManagedOperation
   public TabularData getThreads(String id) throws OpenDataException {
      String[] names = { "ID", "StartTime", "Age" };
      OpenType[] types = { SimpleType.STRING, SimpleType.DATE, SimpleType.STRING };
      CompositeType rowType = new CompositeType(
         "ThreadInfo", "Information about a viewsheet execution thread", names, names, types);
      TabularType tabularType = new TabularType(
         "ThreadInfos", "Information about the viewsheet execution threads", rowType ,names);
      TabularDataSupport data = new TabularDataSupport(tabularType);

      for(ViewsheetThreadModel thread : viewsheetService.getThreads(id)) {
         data.put(new CompositeDataSupport(rowType, names, new Object[] {
            thread.id(), new Date(thread.startTime()), Util.formatAge(new Date(thread.startTime()), false)
         }));
      }

      return data;
   }

   @ManagedAttribute
   public TabularData getActiveViewsheets() throws OpenDataException {
      return getViewsheetData(viewsheetService.getViewsheets(ViewsheetModel.State.OPEN));
   }

   @ManagedAttribute
   public TabularData getExecutingViewsheets() throws OpenDataException {
      return getViewsheetData(viewsheetService.getViewsheets(ViewsheetModel.State.EXECUTING));
   }

   private TabularData getViewsheetData(List<ViewsheetModel> viewsheets) throws OpenDataException {
      String[] names = { "ID", "Name", "User", "Task", "DateCreated", "DateAccessed" };
      OpenType[] types = {
         SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
         SimpleType.DATE, SimpleType.DATE
      };
      CompositeType rowType = new CompositeType(
         "ViewsheetInfo", "Information about a viewsheet", names, names, types);
      TabularType tabularType = new TabularType(
         "ViewsheetInfos", "Information about viewsheets", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tabularType);

      for(ViewsheetModel vs : viewsheets) {
         data.put(new CompositeDataSupport(rowType, names, new Object[] {
            vs.id(), vs.name(), vs.monitorUser(), vs.task(), new Date(vs.dateCreated()),
            new Date(vs.dateAccessed())
         }));
      }

      return data;
   }

   private final ViewsheetService viewsheetService;
}
