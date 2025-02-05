/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.tabular;

import inetsoft.uql.AdditionalConnectionDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * This is the base class for defining a tabular data source.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularDataSource<SELF extends TabularDataSource<SELF>>
   extends AdditionalConnectionDataSource<SELF>
{
   public TabularDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
      credential = createCredential(!supportCredentialId());
   }

   protected boolean supportCredentialId() {
      return true;
   }

   protected abstract CredentialType getCredentialType();

   private Credential createCredential() {
      return createCredential(false);
   }

   protected Credential createCredential(boolean forceLocal) {
      CredentialType type = getCredentialType();
      return type == null ? null : CredentialService.newCredential(type, forceLocal);
   }

   public Credential getCredential() {
      return credential;
   }

   public void setCredential(Credential credential) {
      this.credential = credential;
   }

   @Override
   public String getDescription() {
      String description = super.getDescription();

      if(description != null && !description.isEmpty()) {
         return description;
      }

      try {
         String baseName = getClass().getPackage().getName() + ".Bundle";
         Locale locale = ThreadContext.getLocale();
         ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);

         String key = getClass().getName() + ".description";
         description = bundle.getString(key);
      }
      catch(MissingResourceException ignore) {
         description = "";
      }

      return description;
   }

   /**
    * Get the data source connection parameters.
    */
   @Override
   public UserVariable[] getParameters() {
      List<UserVariable> list = TabularUtil.findVariables(this);
      return list.toArray(new UserVariable[0]);
   }

   /**
    * Check if type conversion is supported. If true, the column type can be changed on the
    * GUI. Each tabular data source is responsible for converting the data to the specified
    * type.
    */
   public boolean isTypeConversionSupported() {
      return false;
   }

   /**
    * Check validity of the data source. May or may not test the connection.
    * Throws an exception if it fails.
    */
   public void checkValidity() {
      // no op
   }

   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<ds_" + getType() + " ");
      writeAttributes(writer);
      writer.println(">");

      super.writeXML(writer);
      writeContents(writer);
      writer.println("</ds_" + getType() + ">");
   }

   @Override
   public final void parseXML(Element root) throws Exception {
      super.parseXML(root);
      parseAttributes(root);
      parseContents(root);
   }

   /**
    * Write the attributes of the XML tag.
    */
   @SuppressWarnings("UnusedParameters")
   protected void writeAttributes(PrintWriter writer) {
   }

   /**
    * Write the contents of the XML tag.
    */
   protected void writeContents(PrintWriter writer) {
      if(credential != null) {
         credential.writeXML(writer);
      }
   }

   /**
    * Parse the attributes of the XML tag.
    */
   @SuppressWarnings("UnusedParameters")
   protected void parseAttributes(Element tag) throws Exception {
   }

   /**
    * Parse the contents of the XML tag.
    */
   protected void parseContents(Element tag) throws Exception {
      Element credentialNode = Tool.getChildNodeByTagName(tag, "PasswordCredential");

      if(credentialNode != null) {
         boolean cloud = "true".equals(credentialNode.getAttribute("cloud"));

         // if force use local credential.
         if(credential instanceof CloudCredential && !cloud) {
            credential = createCredential(true);
         }
         else if(!(credential instanceof CloudCredential) && cloud) {
            // if local secret config, ignore the saved cloud credential.
            if(!Tool.isCloudSecrets()) {
               return;
            }

            credential = createCredential(false);
         }

         credential.parseXML(credentialNode);
      }
   }

   public boolean supportToggleCredential() {
      return supportCredentialId() && Tool.isCloudSecrets();
   }

   public boolean useCredential() {
      return !isUseCredentialId();
   }

   public boolean authorizeEnabled() {
      if(credential != null && credential instanceof ClientCredentials client) {
         return !Tool.isEmptyString(client.getClientId()) && !Tool.isEmptyString(client.getClientSecret());
      }

      return false;
   }

   /**
    * Check whether the authentication credential is used.
    */
   @Property(label="Use Secret ID")
   public boolean isUseCredentialId() {
      return credential != null && credential instanceof AbstractCloudCredential;
   }

   /**
    * Set whether the authentication credential is used.
    */
   public void setUseCredentialId(boolean useCredentialId) {
      useCredentialId = useCredentialId && supportToggleCredential();

      if(!Tool.equals(isUseCredentialId(), useCredentialId)) {
         credential = createCredential(!useCredentialId);
      }
   }

   /**
    * get credential id
    *
    * @return credential id
    */
   @Property(label="Secret ID", required=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getCredentialId() {
      return credential == null ? null : credential.getId();
   }

   /**
    * set credential id
    *
    * @param credentialId credential id
    */
   public void setCredentialId(String credentialId) {
      if(credential == null) {
         return;
      }

      credential.setId(credentialId);

      if(credential instanceof CloudCredential) {
         ((CloudCredential) credential).fetchCredential();
      }
   }

   @Override
   public boolean equals(Object obj) {
      if(obj == null || getClass() != obj.getClass()) {
         return false;
      }

      TabularDataSource ds = (TabularDataSource) obj;
      return Objects.equals(getCredential(), ds.getCredential());
   }

   private Credential credential;
}