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
package inetsoft.web.admin.schedule;

import inetsoft.web.admin.viewsheet.ViewsheetModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ScheduleViewsheetsStatus implements Serializable {
   public List<ViewsheetModel> getOpenViewsheets() {
      if(openViewsheets == null) {
         openViewsheets = new ArrayList<>();
      }

      return openViewsheets;
   }

   public void setOpenViewsheets(List<ViewsheetModel> openViewsheets) {
      this.openViewsheets = openViewsheets;
   }

   public List<ViewsheetModel> getExecutingViewsheets() {
      if(executingViewsheets == null) {
         executingViewsheets = new ArrayList<>();
      }

      return executingViewsheets;
   }

   public void setExecutingViewsheets(List<ViewsheetModel> executingViewsheets) {
      this.executingViewsheets = executingViewsheets;
   }

   @Override
   public String toString() {
      return "ScheduleViewsheetsStatus{" +
         "openViewsheets=" + openViewsheets +
         ", executingViewsheets=" + executingViewsheets +
         '}';
   }

   List<ViewsheetModel> openViewsheets;
   List<ViewsheetModel> executingViewsheets;
}
