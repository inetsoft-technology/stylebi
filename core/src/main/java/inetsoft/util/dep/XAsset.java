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
package inetsoft.util.dep;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Resource;

import java.io.*;
import java.util.List;

/**
 * XAsset describes replets, datasources, worksheets etc. as assets, and
 * keeps their relationship.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public interface XAsset extends Cloneable, Serializable {
   /**
    * Represents a null string.
    */
   String NULL = "__NULL__";

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   XAssetDependency[] getDependencies(List<XAssetDependency> list);

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   String getPath();

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   String getType();

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   IdentityID getUser();

   /**
    * Get the last modified time of this asset.
    */
   long getLastModifiedTime();

   /**
    * Set the last modified time of this asset.
    */
   void setLastModifiedTime(long lastModifiedTime);

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier.
    */
   void parseIdentifier(String identifier);

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   void parseIdentifier(String path, IdentityID userIdentity);

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   String toIdentifier();

   /**
    * Get the string representation.
    * @return the string representation.
    */
   String toString();

   /**
    * Generate a hashcode for this object.
    * @return a hashcode for this object.
    */
   int hashCode();

   /**
    * Parse content of the specified asset from input stream.
    */
   void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception;

   /**
    * Write content of the specified asset to an output stream.
    */
   boolean writeContent(OutputStream output) throws Exception;

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return <code>true</code> if equals, <code>false</code> otherwise.
    */
   boolean equals(Object obj);

   /**
    * Check if this asset is visible to client users.
    */
   boolean isVisible();

   /**
    * Determines if the asset exists in the current server.
    */
   boolean exists();

   /**
    * Get the security resource of this asset if any.
    * @return the security resource of this asset if any.
    */
   Resource getSecurityResource();
}
