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
package inetsoft.uql.asset.sync;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * This class keeps information to update the dependencies of the datasource
 * when table option changed.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
 public class ChangeTableOptionInfo extends RenameInfo {
   public ChangeTableOptionInfo() {
      super();
      this.type = DATA_SOURCE_OPTION;
   }

   /**
    * Constructor
    * @param source  the database name.
    * @param ooption the old table option of the database.
    * @param noption the new table option of the database.
    */
   public ChangeTableOptionInfo(String source, int ooption, int noption)
   {
      this();
      this.source = source;
      this.ooption = ooption;
      this.noption = noption;
   }

   public int getOldOption() {
      return ooption;
   }

   public void setOldOption(int otableOption) {
      this.ooption = otableOption;
   }

   public int getNewOption() {
      return noption;
   }

   public void setNewOption(int ntableOption) {
      this.noption = ntableOption;
   }

   @Override
   public void writeStartXml(PrintWriter writer) {
      writer.println("<changeTableOptionInfo class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" ooption=\"" + ooption + "\"");
      writer.print(" noption=\"" + noption + "\"");
   }

   @Override
   public void writeEndXml(PrintWriter writer) {
      writer.println("</changeTableOptionInfo>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      super.parseXML(elem);

      String val = Tool.getAttribute(elem, "ooption");

      if(val != null) {
         try {
            ooption = Integer.parseInt(val);
         }
         catch(NumberFormatException ignore) {
         }
      }

      val = Tool.getAttribute(elem, "noption");

      if(val != null) {
         try {
            noption = Integer.parseInt(val);
         }
         catch(NumberFormatException ignore) {
         }
      }
   }

   private int ooption;
   private int noption;
}
