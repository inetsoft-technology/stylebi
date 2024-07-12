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
package inetsoft.util.dep;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Resource;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Table style asset represents a table style type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class EmbeddedDataAsset extends AbstractXAsset {
   /**
    * Table style type XAsset.
    */
   public static final String EMBEDDEDDATA = "EMBEDDEDDATA";

   /**
    * Constructor.
    */
   public EmbeddedDataAsset() {
      super();
   }

   /**
    * Constructor.
    */
   public EmbeddedDataAsset(String data) {
      this();
      this.data = data;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      return new XAssetDependency[0];
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return data;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return EMBEDDEDDATA;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return null;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      data = identifier;
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      data = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + data;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      return true;
   }

   @Override
   public boolean exists() {
      return false;
   }

   @Override
   public Resource getSecurityResource() {
      return null;
   }

   private String data;
}
