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
package inetsoft.report.composition.command;

import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.List;
import java.util.Vector;

/**
 * Refresh worksheet object command.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RefreshWSObjectCommand extends WorksheetCommand {
   /**
    * Constructor.
    */
   public RefreshWSObjectCommand() {
      super();
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param connectors the connectors.
    */
   public RefreshWSObjectCommand(WSAssemblyInfo info, String src, List des) {
      this();
      put("info", info);
      put("src", src);

      this.des = des;
   }

   /**
    * Write contents of asset variable.
    * @writer the specified output stream.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(des != null) {
         writer.println("<destinations>");

         for(int i = 0; i < des.size(); i++) {
            writer.println("<destination>" + "<![CDATA[" + des.get(i) +
               "]]></destination>");
         }

         writer.println("</destinations>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element destinationNode = Tool.getChildNodeByTagName(tag, "destinations");

      if(destinationNode != null) {
         des = new Vector();
         NodeList list2 =
            Tool.getChildNodesByTagName(destinationNode, "destination");

         for(int k = 0; k < list2.getLength(); k++) {
            des.add(Tool.getValue((Element) list2.item(k)));
         }
      }
   }

   private List des;
}
