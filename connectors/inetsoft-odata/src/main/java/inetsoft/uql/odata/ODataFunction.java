/*
 * inetsoft-odata - StyleBI is a business intelligence web application.
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
package inetsoft.uql.odata;

import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

public class ODataFunction implements Serializable {

   ODataFunction(String name, ReturnType returnType) {
      this.name = name;
      this.returnType = returnType;
   }

   public String getName() {
      return name;
   }

   public ReturnType getReturnType() {
      return returnType;
   }

   public void setParameters(HttpParameter[] functionParameters) {
      this.functionParameters = functionParameters;
   }

   public HttpParameter[] getParameters() {
      return functionParameters;
   }

   public void setParameterTypes(Boolean[] parameterString) {
      this.parameterString = parameterString;
   }

   public Boolean[] getParameterTypes() {
      return parameterString;
   }

   public void setBound(boolean isBound) {
      this.isBound = isBound;
   }

   public boolean isBound() {
      return isBound;
   }

   public void setBoundCollection(boolean isBoundCollection) {
      this.isBoundCollection = isBoundCollection;
   }

   public boolean isBoundCollection() {
      return isBoundCollection;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ODataFunction)) {
         return false;
      }

      ODataFunction fun2 = (ODataFunction) obj;

      return name.equals(fun2.name) && returnType.equals(fun2.returnType) &&
         isBound == fun2.isBound && isBoundCollection == fun2.isBoundCollection;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, returnType, functionParameters, parameterString, isBound, isBoundCollection);
   }

   public void writeXML(PrintWriter writer) {
      writer.print("<function isBound=\"" + isBound + "\" isBoundCollection=\"" + isBoundCollection + "\">");

      if(name != null) {
         writer.println("<name><![CDATA[" + name + "]]></name>");
      }

      if(returnType != null) {
         writer.println("<returnType><![CDATA[" + returnType + "]]></returnType>");
      }

      if(functionParameters != null && functionParameters.length > 0) {
         writer.println("<functionParameters>");

         for(HttpParameter parameter : functionParameters) {
            parameter.writeXML(writer);
         }

         writer.println("</functionParameters>");
      }

      if(functionParameters != null && functionParameters.length > 0) {
         writer.println("<parameterStrings>");

         for(Boolean isString : parameterString) {
            writer.println("<parameterString isString=\"" + isString + "\"></parameterString>");
         }

         writer.println("</parameterStrings>");
      }

      writer.println("</function>");
   }

   public void parseXML(Element tag) throws Exception {
      String prop;

      Element node = Tool.getChildNodeByTagName(tag, "name");
      name = Tool.getValue(node);

      node = Tool.getChildNodeByTagName(tag, "returnType");
      returnType = ReturnType.valueOf(Tool.getValue(node));

      if((prop = Tool.getAttribute(tag, "isBound")) != null) {
         isBound = Boolean.parseBoolean(prop);
      }

      if((prop = Tool.getAttribute(tag, "isBoundCollection")) != null) {
         isBoundCollection = Boolean.parseBoolean(prop);
      }

      if((node = Tool.getChildNodeByTagName(tag, "functionParameters")) != null) {
         NodeList nodes = Tool.getChildNodesByTagName(node, "httpParameter");
         functionParameters = new HttpParameter[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            functionParameters[i] = new HttpParameter();
            functionParameters[i].parseXML((Element) nodes.item(i));
         }
      }
      else {
         functionParameters = new HttpParameter[0];
      }

      if((node = Tool.getChildNodeByTagName(tag, "parameterStrings")) != null) {
         NodeList nodes = Tool.getChildNodesByTagName(node, "parameterString");
         parameterString = new Boolean[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            if((prop = Tool.getAttribute((Element) nodes.item(i), "isString")) != null) {
               parameterString[i] = Boolean.parseBoolean(prop);
            }
            else {
               parameterString[i] = false;
            }
         }
      }
   }


   private String name;
   private ReturnType returnType;
   private HttpParameter[] functionParameters;
   private Boolean[] parameterString;
   private boolean isBound;
   private boolean isBoundCollection;

   public enum ReturnType {
      ENTITY, ENTITYSET, PROPERTY
   }
}
