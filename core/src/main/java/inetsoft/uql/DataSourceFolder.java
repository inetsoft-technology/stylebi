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
package inetsoft.uql;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Data source folder in data source registry to store data source folder and
 * data source.
 * @author InetSoft Technology Corp
 */
public class DataSourceFolder implements java.io.Serializable, Cloneable, XMLSerializable, Comparable<DataSourceFolder>
{
   public DataSourceFolder() {
   }

   public DataSourceFolder(String name, LocalDateTime time, String user) {
      this.name = name;
      this.createdDate = time;
      this.createdUsername = user;
   }

   /**
    * Set current data source folder name.
    * @param name the name of current data source folder.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get current data source folder display name.
    * @return current data source folder display name.
    */
   public String getName() {
      return getDisplayName(name);
   }

   /**
    * Get the current data source folder absolute name.
    */
   public String getFullName() {
      return name;
   }

   /**
    * Get the created date
    * @return  the created date
    */
   public LocalDateTime getCreatedDate() {
      return createdDate;
   }

   /**
    * Set the created date
    * @param createdDate   the created date
    */
   public void setCreatedDate(LocalDateTime createdDate) {
      this.createdDate = createdDate;
   }

   /**
    * Get the created username
    * @return  the created username
    */
   public String getCreatedUsername() {
      return createdUsername;
   }

   /**
    * Set the created username
    * @param createdUsername  the created username
    */
   public void setCreatedUsername(String createdUsername) {
      this.createdUsername = createdUsername;
   }

   /**
    * Get the specified full name's parent name.
    */
   public static String getParentName(String name) {
      if(name == null || name.trim().length() == 0) {
         return null;
      }

      if(name.contains("/")) {
         return name.substring(0, name.lastIndexOf("/"));
      }

      return null;
   }

   /**
    * Get the simple name with the specified full name.
    */
   public static String getDisplayName(String name) {
      if(name == null || name.trim().length() == 0) {
         return name;
      }

      if(name.contains("/")) {
         return name.substring(name.lastIndexOf("/") + 1);
      }

      return name;
   }

   @Override
   public void parseXML(Element element) {
      String val;

      if((val = Tool.getAttribute(element, "name")) != null) {
         name = val;
      }

      createdUsername = Tool.getAttribute(element, "createdUsername");

      if((val = Tool.getAttribute(element, "createdDate")) != null) {
         createdDate = Tool.getDateTimeOfTimestamp(Long.parseLong(val));
      }
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<datasourcefolder name=\"%s\"", Tool.escape(name));

      if(createdUsername != null) {
         writer.format(" createdUsername=\"%s\"", createdUsername);
      }

      if(createdDate != null) {
         writer.format(" createdDate=\"%d\"", Tool.getTimestampOfDateTime(createdDate));
      }

      writer.println("></datasourcefolder>");
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         return null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      DataSourceFolder that = (DataSourceFolder) o;
      return Objects.equals(name, that.name);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name);
   }

   @Override
   public String toString() {
      return "DataSourceFolder{" +
         "name='" + name + '\'' +
         ", createdUsername='" + createdUsername + '\'' +
         ", createdDate=" + createdDate +
         '}';
   }

   /**
    * Compare to another object.
    * @param obj the specified object.
    * @return compare result.
    */
   @Override
   public int compareTo(DataSourceFolder obj) {
      if(obj == null) {
         return 1;
      }

      String order = SreeEnv.getProperty("repository.tree.sort");
      int delta = order.equals("Ascending") || order.equals("none") ? 1 : -1;
      String name = getName() == null ? "" : getName();
      int result = name.compareToIgnoreCase(obj.getName());
      return result * delta;
   }

   private String name;
   private String createdUsername;
   private LocalDateTime createdDate;
}
