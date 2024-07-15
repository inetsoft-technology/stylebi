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
package inetsoft.report.composition.command;

import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.ItemList;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;
import java.util.List;

/**
 * Load table data command.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LoadTableDataCommand extends WorksheetCommand {
   /**
    * Constructor.
    */
   public LoadTableDataCommand() {
      super();
   }

   /**
    * Constructor.
    */
   public LoadTableDataCommand(String name, List<ColumnInfo> infos,
                               XEmbeddedTable embedded, int mode,
                               int start, int num, boolean completed) {
      this();
      put("name", name);
      this.infos = new ItemList();
      this.embedded = embedded;

      for(ColumnInfo info : infos) {
         this.infos.addItem(info);
      }

      put("mode", "" + mode);
      put("start", "" + start);
      put("num", "" + num);
      put("completed", "" + completed);
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(infos != null) {
         writer.println("<infos>");
         infos.writeXML(writer);
         writer.println("</infos>");
      }

      if(embedded != null) {
         writer.println("<embedded>");
         embedded.writeXML(writer);
         writer.println("</embedded>");
      }
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeBoolean(infos == null);

         if(infos != null) {
            infos.writeData(dos);
         }

         dos.writeBoolean(embedded == null);

         if(embedded != null) {
            embedded.writeData(dos);
         }
      }
      catch(IOException e) {
      }
   }

   /**
    * Parse contents.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element infosNode = Tool.getChildNodeByTagName(tag, "infos");

      if(infosNode != null) {
         infos = new ItemList();
         infos.parseXML((Element) infosNode.getFirstChild());
      }

      Element embeddedNode = Tool.getChildNodeByTagName(tag, "embedded");

      if(embeddedNode != null) {
         embedded = new XEmbeddedTable();
         embedded.parseXML((Element) embeddedNode.getFirstChild());
      }
   }

   /**
    * Get table.
    */
   public XEmbeddedTable getTable() {
      return embedded;
   }

   /**
    * Get column infos.
    */
   public ItemList getColumnInfos() {
      return infos;
   }

   private ItemList infos;
   private XEmbeddedTable embedded;
}
