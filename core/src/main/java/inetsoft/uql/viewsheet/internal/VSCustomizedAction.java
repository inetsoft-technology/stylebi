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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * VSCustomizedAction represent a customized viewsheet action data.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class VSCustomizedAction implements XMLSerializable {
   public VSCustomizedAction() {
      super();
   }

   public VSCustomizedAction(String icon, String label, String script) {
      setIcon(icon);
      setLabel(label);
      setScript(script);
   }

   public void setIcon(String icon) {
      this.icon = icon;
   }

   public String getIcon() {
      return icon;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getLabel() {
      return label;
   }

   public void setScript(String script) {
      this.script = script;
   }

   public String getScript() {
      return script;
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof VSCustomizedAction)) {
         return false;
      }

      VSCustomizedAction action = (VSCustomizedAction) obj;
      return Tool.equals(icon, action.icon) &&
             Tool.equals(label, action.label) &&
             Tool.equals(script, action.script);
   }

   public int hashCode() {
      return icon.hashCode() + 7 * label.hashCode() + 11 * script.hashCode();
   }

   public String toString() {
      return "VSCustomizedAction[" + label + "," + icon + "," + script + "]";
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<VSCustomizedAction>");
      writer.print("<icon><![CDATA[" + Tool.encodeCDATA(getIcon()) + "]]>");
      writer.println("</icon>");
      writer.print("<label><![CDATA[" + Tool.encodeCDATA(getLabel()) + "]]>");
      writer.println("</label>");
      writer.print("<script><![CDATA[" + Tool.encodeCDATA(getScript()) + "]]>");
      writer.println("</script>");
      writer.println("</VSCustomizedAction>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "icon");
      icon = Tool.getValue(node);
      icon = Tool.decodeCDATA(icon);
      node = Tool.getChildNodeByTagName(tag, "label");
      label = Tool.getValue(node);
      label = Tool.decodeCDATA(label);
      node = Tool.getChildNodeByTagName(tag, "script");
      script = Tool.getValue(node);
      script = Tool.decodeCDATA(script);
   }

   private String icon;
   private String label;
   private String script;
}
