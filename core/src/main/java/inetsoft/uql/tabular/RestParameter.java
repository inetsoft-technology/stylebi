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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code RestParameter} represents a parameter in a REST request URL.
 */
public class RestParameter implements Serializable, XMLSerializable, Cloneable,
   Comparable<RestParameter>
{
   /**
    * Gets the name of the parameter. This is the non-localized label for the parameter.
    *
    * @return the parameter name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the parameter. This is the non-localized label for the parameter.
    *
    * @param name the parameter name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the display label for the parameter. This is the localized label for {@link #getName()}.
    *
    * @return the parameter label.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Sets the display label for the parameter. This is the localized label for {@link #getName()}.
    *
    * @param label the parameter label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Gets the placeholder for the parameter, if any. This should provide information about the
    * expected format of the value, e.g. "0" or "yyyy-MM-dd".
    *
    * @return the parameter placeholder or {@code null} if there is none.
    */
   public String getPlaceholder() {
      return placeholder;
   }

   /**
    * Sets the placeholder for the parameter, if any. This should provide information about the
    * expected format of the value, e.g. "0" or "yyyy-MM-dd".
    *
    * @param placeholder the parameter placeholder or {@code null} if there is none.
    */
   public void setPlaceholder(String placeholder) {
      this.placeholder = placeholder;
   }

   /**
    * Gets the flag that indicates if the parameter is required.
    *
    * @return {@code true} if required or {@code false} if optional.
    */
   public boolean isRequired() {
      return required;
   }

   /**
    * Sets the flag that indicates if the parameter is required.
    *
    * @param required {@code true} if required or {@code false} if optional.
    */
   public void setRequired(boolean required) {
      this.required = required;
   }

   /**
    * Gets the flag that indicates if the value is a comma-separated string that should be split
    * into separate query parameters.
    *
    * @return {@code true} if the value should be split; {@code false} if not.
    */
   public boolean isSplit() {
      return split;
   }

   /**
    * Sets the flag that indicates if the value is a comma-separated string that should be split
    * into separate query parameters.
    *
    * @param split {@code true} if the value should be split; {@code false} if not.
    */
   public void setSplit(boolean split) {
      this.split = split;
   }

   /**
    * Gets the value of the parameter.
    *
    * @return the parameter value.
    */
   public String getValue() {
      return value;
   }

   /**
    * Sets the value of the parameter.
    *
    * @param value the parameter value.
    */
   public void setValue(String value) {
      this.value = value;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<restParameter required=\"%s\" split=\"%s\">%n", required, split);
      writer.format("<name><![CDATA[%s]]></name>%n", name);

      if(label != null) {
         writer.format("<label><![CDATA[%s]]></label>%n", label);
      }

      if(placeholder != null) {
         writer.format("<placeholder><![CDATA[%s]]></placeholder>%n", placeholder);
      }

      if(value != null) {
         writer.format("<value><![CDATA[%s]]></value>%n", value);
      }

      writer.println("</restParameter>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      required = "true".equals(Tool.getAttribute(tag, "required"));
      split = "true".equals(Tool.getAttribute(tag, "split"));
      name = Tool.getChildValueByTagName(tag, "name");
      label = Tool.getChildValueByTagName(tag, "label");
      placeholder = Tool.getChildValueByTagName(tag, "placeholder");
      value = Tool.getChildValueByTagName(tag, "value");
   }

   /**
    * Creates the REST template URL token for the parameter.
    *
    * @return the template URL token.
    */
   public String toToken() {
      StringBuilder token = new StringBuilder().append('{').append(name);

      if(!required) {
         token.append('?');
      }

      if(split) {
         token.append(',');
      }

      if(placeholder != null) {
         token.append(':').append(placeholder);
      }

      return token.append('}').toString();
   }

   /**
    * Creates a new {@code RestParameter} for the specified REST template URL token.
    *
    * @param token the token to parse.
    *
    * @return a new parameter instance.
    */
   public static RestParameter fromToken(String token) {
      if(token == null) {
         throw new IllegalArgumentException("The token must not be null");
      }

      Pattern pattern = Pattern.compile("^\\{([^?:,}]+)([?,]+)?(?::([^}]+))?}$");
      Matcher matcher = pattern.matcher(token);

      if(matcher.matches()) {
         RestParameter parameter = new RestParameter();
         parameter.setName(matcher.group(1));
         parameter.setLabel(parameter.getName());
         parseFlags(matcher.group(2), parameter);
         parameter.setPlaceholder(matcher.group(3));
         return parameter;
      }
      else {
         throw new IllegalArgumentException("Invalid token: " + token);
      }
   }

   /**
    * Parses the flags from a parameter token.
    *
    * @param flags     the flags to parse.
    * @param parameter the parameter on which to set the flags.
    */
   private static void parseFlags(String flags, RestParameter parameter) {
      parameter.setRequired(flags == null || !flags.contains("?"));
      parameter.setSplit(flags != null && flags.contains(","));
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException e) {
         LOG.warn("Failed to copy REST parameter", e);
      }

      return null;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof RestParameter)) {
         return false;
      }

      RestParameter that = (RestParameter) o;
      return required == that.required &&
         split == that.split &&
         Objects.equals(name, that.name) &&
         Objects.equals(label, that.label) &&
         Objects.equals(placeholder, that.placeholder) &&
         Objects.equals(value, that.value);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, label, placeholder, required, split, value);
   }

   @Override
   public String toString() {
      return "RestParameter{" +
         "name='" + name + '\'' +
         ", label='" + label + '\'' +
         ", placeholder='" + placeholder + '\'' +
         ", required=" + required +
         ", split=" + split +
         ", value='" + value + '\'' +
         '}';
   }

   @Override
   public int compareTo(RestParameter param) {
      return Tool.compare(name, param.name, false, true);
   }

   private String name;
   private String label;
   private String placeholder;
   private boolean required;
   private boolean split;
   private String value;

   private static final Logger LOG = LoggerFactory.getLogger(RestParameter.class);
}
