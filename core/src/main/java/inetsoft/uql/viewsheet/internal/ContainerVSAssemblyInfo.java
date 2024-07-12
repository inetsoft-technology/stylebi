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

import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.List;

/**
 * ContainerVSAssemblyInfo stores container assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ContainerVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Constructor.
    */
   public ContainerVSAssemblyInfo() {
      super();
      this.assemblies = new String[0];
   }

   /**
    * Get the dynamic property values for output properties.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getOutputDynamicValues() {
      List<DynamicValue> list = super.getOutputDynamicValues();
      list.add(getEnabledDynamicValue());
      return list;
   }

   /**
    * Get the assemblies.
    * @return the assemblies of the tab assembly.
    */
   public String[] getAssemblies() {
      return assemblies;
   }

   /**
    * Get the child assemblies' absolute name.
    * @return the child assemblies absolute name.
    */
   public String[] getAbsoluteAssemblies() {
      String aname = getAbsoluteName();
      int dot = aname.lastIndexOf(".");
      String prefix = dot >= 0 ? aname.substring(0, dot + 1) : "";
      String[] aAssemblies = new String[assemblies.length];

      for(int i = 0; i < assemblies.length; i++) {
         String name = assemblies[i];

         if(name.indexOf(".") < 0) {
            name = prefix + name;
         }

         aAssemblies[i] = name;
      }

      return aAssemblies;
   }

   /**
    * Set the assemblies.
    * @param assemblies the specified value.
    */
   public void setAssemblies(String[] assemblies) {
      this.assemblies = assemblies;
   }

   /**
    * Get the assemblies count.
    * @return the assemblies of the tab assembly.
    */
   public int getAssemblyCount() {
      return assemblies.length;
   }

   /**
    * Check if this container contains any child assembly.
    */
   public boolean isEmpty() {
      return getAssemblyCount() == 0;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ContainerVSAssemblyInfo clone(boolean shallow) {
      try {
         ContainerVSAssemblyInfo info = (ContainerVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(assemblies != null) {
               info.assemblies = assemblies.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ContainerVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<assemblies>");

      for(int i = 0; i < assemblies.length; i++) {
         writer.print("<assembly>");
         writer.print("<![CDATA[" + assemblies[i] + "]]>");
         writer.println("</assembly>");
      }

      writer.println("</assemblies>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element node = Tool.getChildNodeByTagName(elem, "assemblies");

      if(node != null) {
         NodeList list = Tool.getChildNodesByTagName(node, "assembly");
         assemblies = new String[list.getLength()];

         for(int i = 0; i < list.getLength(); i++) {
            assemblies[i] = Tool.getValue(list.item(i));
         }
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);

      if(deep) {
         ContainerVSAssemblyInfo tinfo = (ContainerVSAssemblyInfo) info;

         if(!Tool.equals(assemblies, tinfo.assemblies)) {
            assemblies = tinfo.assemblies;
            result = true;
         }
      }

      return result;
   }

   private String[] assemblies;

   protected static final Logger LOG =
      LoggerFactory.getLogger(ContainerVSAssemblyInfo.class);
}
