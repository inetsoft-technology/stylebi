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
package inetsoft.web.admin.logviewer;

import java.io.Serializable;
import java.util.List;

public class GetLogFilesResponse implements Serializable {
   public GetLogFilesResponse() {
   }

   public GetLogFilesResponse(List<LogFileModel> logFiles) {
      this.logFiles = logFiles;
   }

   public List<LogFileModel> getLogFiles() {
      return logFiles;
   }

   public void setLogFiles(List<LogFileModel> logFiles) {
      this.logFiles = logFiles;
   }

   @Override
   public String toString() {
      return "GetLogFilesResponse{" +
         "logFiles=" + logFiles +
         '}';
   }

   private List<LogFileModel> logFiles;
}
