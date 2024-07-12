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
package inetsoft.web.admin.content.database;

/**
 * Class that encapsulates the network location of a database server.
 */
public final class NetworkLocation {
   /**
    * Gets the host name of the server on which the database is running.
    *
    * @return the host name or IP address.
    */
   public String getHostName() {
      return hostName;
   }

   /**
    * Sets the host name of the server on which the database is running.
    *
    * @param hostName the host name or IP address.
    */
   public void setHostName(String hostName) {
      this.hostName = hostName;
   }

   /**
    * Gets the port number on which the database is listening.
    *
    * @return the port number.
    */
   public int getPortNumber() {
      return portNumber;
   }

   /**
    * Sets the port number on which the database is listening.
    *
    * @param portNumber the port number.
    */
   public void setPortNumber(int portNumber) {
      this.portNumber = portNumber;
   }

   private String hostName;
   private int portNumber;
}
