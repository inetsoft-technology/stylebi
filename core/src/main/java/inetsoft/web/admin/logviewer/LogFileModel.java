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
package inetsoft.web.admin.logviewer;

import java.io.Serializable;
import java.util.Objects;

public final class LogFileModel implements Serializable {
   public LogFileModel() {
   }

   public LogFileModel(String clusterNode, String logFile, boolean rotateSupported) {
      this.clusterNode = clusterNode;
      this.logFile = logFile;
      this.rotateSupported = rotateSupported;
   }

   public String getClusterNode() {
      return clusterNode;
   }

   public void setClusterNode(String clusterNode) {
      this.clusterNode = clusterNode;
   }

   public String getLogFile() {
      return logFile;
   }

   public void setLogFile(String logFile) {
      this.logFile = logFile;
   }

   public boolean isRotateSupported() {
      return rotateSupported;
   }

   public void setRotateSupported(boolean rotateSupported) {
      this.rotateSupported = rotateSupported;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof LogFileModel that)) {
         return false;
      }

      return rotateSupported == that.rotateSupported &&
         Objects.equals(clusterNode, that.clusterNode) &&
         Objects.equals(logFile, that.logFile);
   }

   @Override
   public int hashCode() {
      return Objects.hash(clusterNode, logFile, rotateSupported);
   }

   @Override
   public String toString() {
      return "LogFileModel{" +
         "clusterNode='" + clusterNode + '\'' +
         ", logFile='" + logFile + '\'' +
         ", rotateSupported=" + rotateSupported +
         '}';
   }

   private String clusterNode;
   private String logFile;
   private boolean rotateSupported;
}
