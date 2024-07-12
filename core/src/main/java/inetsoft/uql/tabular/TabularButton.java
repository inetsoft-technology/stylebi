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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores button information
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TabularButton implements XMLSerializable {
   /**
    * Gets the type of this button
    *
    * @return the button type
    */
   public ButtonType getType() {
      return type;
   }

   /**
    * Sets the type of this button
    *
    * @param type the button type
    */
   public void setType(ButtonType type) {
      this.type = type;
   }

   /**
    * Gets the style for this button.
    *
    * @return the button style.
    */
   public ButtonStyle getStyle() {
      return style;
   }

   /**
    * Sets the style for this button.
    *
    * @param style the button style.
    */
   public void setStyle(ButtonStyle style) {
      this.style = style;
   }

   /**
    * Gets the url that this button points to
    *
    * @return the url
    */
   public String getUrl() {
      return url;
   }

   /**
    * Sets the url that this button points to
    *
    * @param url the url
    */
   public void setUrl(String url) {
      this.url = url;
   }

   /**
    * Gets the name of the method to call on click
    *
    * @return the method name
    */
   public String getMethod() {
      return method;
   }

   /**
    * Sets the name of the method to call on click
    *
    * @param method the method name
    */
   public void setMethod(String method) {
      this.method = method;
   }

   /**
    * Gets the name of the pre-configured service to use for authorization.
    *
    * @return the service name.
    */
   public String getOauthServiceName() {
      return oauthServiceName;
   }

   /**
    * Sets the name of the pre-configured service to use for authorization.
    *
    * @param oauthServiceName the service name.
    */
   public void setOauthServiceName(String oauthServiceName) {
      this.oauthServiceName = oauthServiceName;
   }

   public String getOauthUser() {
      return oauthUser;
   }

   public void setOauthUser(String oauthUser) {
      this.oauthUser = oauthUser;
   }

   public String getOauthPassword() {
      return oauthPassword;
   }

   public void setOauthPassword(String oauthPassword) {
      this.oauthPassword = oauthPassword;
   }

   /**
    * Gets the name of the property containing the OAuth client ID.
    *
    * @return the property name.
    */
   public String getOauthClientId() {
      return oauthClientId;
   }

   /**
    * Sets the name of the property containing the OAuth client ID.
    *
    * @param oauthClientId the property name.
    */
   public void setOauthClientId(String oauthClientId) {
      this.oauthClientId = oauthClientId;
   }

   /**
    * Gets the name of the property containing the OAuth client secret.
    *
    * @return the property name.
    */
   public String getOauthClientSecret() {
      return oauthClientSecret;
   }

   /**
    *SGets the name of the property containing the OAuth client secret.
    *
    * @param oauthClientSecret the property name.
    */
   public void setOauthClientSecret(String oauthClientSecret) {
      this.oauthClientSecret = oauthClientSecret;
   }

   /**
    * Gets the name of the property containing the OAuth scope.
    *
    * @return the property name.
    */
   public String getOauthScope() {
      return oauthScope;
   }

   /**
    * Sets the name of the property containing the OAuth scope.
    *
    * @param oauthScope the property name.
    */
   public void setOauthScope(String oauthScope) {
      this.oauthScope = oauthScope;
   }

   /**
    * Gets the name of the property containing the OAuth authorization URI.
    *
    * @return the property name.
    */
   public String getOauthAuthorizationUri() {
      return oauthAuthorizationUri;
   }

   /**
    * Sets the name of the property containing the OAuth authorization URI.
    *
    * @param oauthAuthorizationUri the property name.
    */
   public void setOauthAuthorizationUri(String oauthAuthorizationUri) {
      this.oauthAuthorizationUri = oauthAuthorizationUri;
   }

   /**
    * Gets the name of the property containing the OAuth token URI.
    *
    * @return the property name.
    */
   public String getOauthTokenUri() {
      return oauthTokenUri;
   }

   /**
    * Sets the name of the property containing the OAuth token URI.
    *
    * @param oauthTokenUri the property name.
    */
   public void setOauthTokenUri(String oauthTokenUri) {
      this.oauthTokenUri = oauthTokenUri;
   }

   /**
    * Gets the name of the property containing the OAuth flags.
    *
    * @return the property name.
    */
   public String getOauthFlags() {
      return oauthFlags;
   }

   /**
    * Sets the name of the property containing the OAuth flags.
    *
    * @param oauthFlags the property name.
    */
   public void setOauthFlags(String oauthFlags) {
      this.oauthFlags = oauthFlags;
   }

   /**
    * Gets the map of additional OAuth parameters. These are the parameters received on the redirect
    * request from the OAuth authorize endpoint that should be included in the request to the OAuth
    * token endpoint.
    *
    * @return the parameter map.
    */
   public Map<String, String> getOauthAdditionalParameters() {
      return oauthAdditionalParameters;
   }

   /**
    * Sets the map of additional OAuth parameters.
    *
    * @param oauthAdditionalParameters the parameter map.
    */
   public void setOauthAdditionalParameters(Map<String, String> oauthAdditionalParameters) {
      this.oauthAdditionalParameters = oauthAdditionalParameters;
   }

   /**
    * Gets the flag that determines whether the button has been clicked
    */
   public boolean isClicked() {
      return clicked;
   }

   /**
    * Sets the flag that determines whether the button has been clicked
    */
   public void setClicked(boolean clicked) {
      this.clicked = clicked;
   }

   /**
    * Gets the names of the properties on which the editor depends.
    */
   public String[] getDependsOn() {
      return dependsOn;
   }

   /**
    * Sets the names of the properties on which the editor depends.
    */
   public void setDependsOn(String[] dependsOn) {
      this.dependsOn = dependsOn;
   }

   /**
    * Get the method to call to set the enabled status.
    */
   public String getEnabledMethod() {
      return enabledMethod;
   }

   /**
    * Set the method to call to set the enabled status.
    */
   public void setEnabledMethod(String enabledMethod) {
      this.enabledMethod = enabledMethod;
   }

   /**
    * Get the enabled status.
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Set the enabled status.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tabularButton>");
      writer.format("<type><![CDATA[%s]]></type>%n", type);
      writer.format("<style><![CDATA[%s]]></style>%n", style);
      writer.format("<url><![CDATA[%s]]></url>%n", url);

      if(method != null) {
         writer.format("<method><![CDATA[%s]]></method>%n", method);
      }

      if(oauthServiceName != null) {
         writer.format("<oauthServiceName><![CDATA[%s]]></oauthServiceName>%n", oauthServiceName);
      }

      if(oauthUser != null) {
         writer.format("<oauthUsername><![CDATA[%s]]></oauthUsername>%n", oauthUser);
      }

      if(oauthPassword != null) {
         writer.format("<oauthPassword><![CDATA[%s]]></oauthPassword>%n", oauthPassword);
      }

      if(oauthClientId != null) {
         writer.format("<oauthClientId><![CDATA[%s]]></oauthClientId>%n", oauthClientId);
      }

      if(oauthClientSecret != null) {
         writer.format("<oauthClientSecret><![CDATA[%s]]></oauthClientSecret>%n", oauthClientSecret);
      }

      if(oauthScope != null) {
         writer.format("<oauthScope><![CDATA[%s]]></oauthScope>%n", oauthScope);
      }

      if(oauthAuthorizationUri != null) {
         writer.format(
            "<oauthAuthorizationUri><![CDATA[%s]]></oauthAuthorizationUri>%n",
            oauthAuthorizationUri);
      }

      if(oauthTokenUri != null) {
         writer.format("<oauthTokenUri><![CDATA[%s]]></oauthTokenUri>%n", oauthTokenUri);
      }

      if(oauthFlags != null) {
         writer.format("<oauthFlags><![CDATA[%s]]></oauthFlags>%n", oauthFlags);
      }

      if(oauthAdditionalParameters != null) {
         writer.println("<oauthAdditionalParameters>");

         for(Map.Entry<String, String> e : oauthAdditionalParameters.entrySet()) {
            writer.format("<from><![CDATA[%s]]></from>%n", e.getKey());
            writer.format("<to><![CDATA[%s]]></to>%n", e.getValue());
         }

         writer.println("</oauthAdditionalParameters>");
      }

      writer.format("<clicked><![CDATA[%s]]></clicked>%n", clicked);
      writer.println("</tabularButton>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      type = ButtonType.valueOf(Tool.getChildValueByTagName(tag, "type"));
      style = ButtonStyle.valueOf(Tool.getChildValueByTagName(tag, "style"));
      url = Tool.getChildValueByTagName(tag, "url");
      method = Tool.getChildValueByTagName(tag, "method");
      oauthServiceName = Tool.getChildValueByTagName(tag, "oauthServiceName");
      oauthClientId = Tool.getChildValueByTagName(tag, "oauthClientId");
      oauthClientSecret = Tool.getChildValueByTagName(tag, "oauthClientSecret");
      oauthScope = Tool.getChildValueByTagName(tag, "oauthScope");
      oauthAuthorizationUri = Tool.getChildValueByTagName(tag, "oauthAuthorizationUri");
      oauthTokenUri = Tool.getChildValueByTagName(tag, "oauthTokenUri");
      oauthFlags = Tool.getChildValueByTagName(tag, "oauthFlags");
      clicked = Boolean.parseBoolean(Tool.getChildValueByTagName(tag, "clicked"));

      oauthAdditionalParameters = new HashMap<>();
      Element element = Tool.getChildNodeByTagName(tag, "oauthAdditionalParameters");

      if(element != null) {
         NodeList nodes = Tool.getChildNodesByTagName(element, "parameter");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element parameter = (Element) nodes.item(i);
            oauthAdditionalParameters.put(
               Tool.getChildValueByTagName(parameter, "from"),
               Tool.getChildValueByTagName(parameter, "to"));
         }
      }
   }

   private ButtonType type;
   private ButtonStyle style;
   private String url;
   private String method;
   private String oauthServiceName;
   private String oauthUser;
   private String oauthPassword;
   private String oauthClientId;
   private String oauthClientSecret;
   private String oauthScope;
   private String oauthAuthorizationUri;
   private String oauthTokenUri;
   private String oauthFlags;
   private Map<String, String> oauthAdditionalParameters;
   private boolean clicked;
   private String enabledMethod;
   private String[] dependsOn;
   private boolean enabled = true;
   private boolean visible = true;
}
