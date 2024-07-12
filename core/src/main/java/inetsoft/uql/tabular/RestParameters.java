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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * {@code RestParameters} encapsulates a list of {@link RestParameter} objects.
 */
public class RestParameters implements Serializable, XMLSerializable, Cloneable {
   /**
    * Gets the parameter list.
    *
    * @return the parameters. This will never be {@code null}.
    */
   public List<RestParameter> getParameters() {
      if(parameters == null) {
         parameters = new ArrayList<>();
      }

      return parameters;
   }

   /**
    * Sets the parameter list.
    *
    * @param parameters the parameters.
    */
   public void setParameters(List<RestParameter> parameters) {
      this.parameters = parameters;
   }

   /**
    * Finds the parameter with the specified name.
    *
    * @param name the name of the parameter.
    *
    * @return the matching parameter or {@code null} if it is not found.
    */
   public RestParameter findParameter(String name) {
      return getParameters().stream()
         .filter(p -> Objects.equals(p.getName(), name))
         .findFirst()
         .orElse(null);
   }

   /**
    * Gets the name of the endpoint to which the parameters apply.
    *
    * @return the endpoint name.
    */
   public String getEndpoint() {
      return endpoint;
   }

   /**
    * Sets the name of the endpoint to which the parameters apply.
    *
    * @param endpoint the endpoint name.
    */
   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
   }

   /**
    * Get a parameter value from the current parameter list or the previous parameter values.
    */
   public String getKnownParameterValue(String name) {
      RestParameter param = findParameter(name);

      if(param != null) {
         return param.getValue();
      }

      return allValues.get(name);
   }

   /**
    * Save the parameter values so when end points are switched, the previous values
    * are not lost. This values are only used during design session and not made persistent.
    */
   public void copyParameterValues(RestParameters params) {
      this.allValues.putAll(params.allValues);

      if(params.parameters != null) {
         for(RestParameter param : params.parameters) {
            this.allValues.put(param.getName(), param.getValue());
         }
      }
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<restParameters>");

      if(endpoint != null) {
         writer.format("<endpoint><![CDATA[%s]]></endpoint>%n", endpoint);
      }

      writer.println("<parameters>");

      for(RestParameter parameter : getParameters()) {
         parameter.writeXML(writer);
      }

      writer.println("</parameters>");
      writer.println("</restParameters>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      List<RestParameter> list = new ArrayList<>();
      endpoint = Tool.getChildValueByTagName(tag, "endpoint");
      Element elem = Tool.getChildNodeByTagName(tag, "parameters");

      if(elem != null) {
         NodeList nodes = Tool.getChildNodesByTagName(elem, "restParameter");

         for(int i = 0; i < nodes.getLength(); i++) {
            RestParameter parameter = new RestParameter();
            parameter.parseXML((Element) nodes.item(i));
            list.add(parameter);
         }
      }

      setParameters(list);
   }

   @Override
   public Object clone() {
      try {
         RestParameters copy = (RestParameters) super.clone();

         if(parameters != null) {
            copy.parameters = new ArrayList<>();

            for(RestParameter parameter : parameters) {
               copy.parameters.add((RestParameter) parameter.clone());
            }
         }

         return copy;
      }
      catch(Exception e) {
         LOG.error("Failed to clone REST parameters", e);
      }

      return null;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof RestParameters)) {
         return false;
      }

      RestParameters that = (RestParameters) o;
      return Objects.equals(parameters, that.parameters) &&
         Objects.equals(endpoint, that.endpoint);
   }

   @Override
   public int hashCode() {
      return Objects.hash(parameters, endpoint);
   }

   @Override
   public String toString() {
      return "RestParameters{" +
         "parameters=" + parameters +
         ", endpoint='" + endpoint + '\'' +
         '}';
   }

   private List<RestParameter> parameters;
   private String endpoint;
   // used to store parameter values so when user switches between end points, the
   // parameter values are not lost
   private Map<String, String> allValues = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(RestParameters.class);
}
