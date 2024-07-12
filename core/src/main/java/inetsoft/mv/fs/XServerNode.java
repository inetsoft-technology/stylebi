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
package inetsoft.mv.fs;

import inetsoft.mv.fs.internal.AbstractFileSystem;

import java.util.*;

/**
 * XServerNode, the server node in the distributed file system.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XServerNode implements FSConfigObserver {
   /**
    * Create an instance of XServerNode.
    */
   public XServerNode(FSConfig config) {
      super();

      this.config = config;
      this.fs = AbstractFileSystem.createFileSystem(this);
   }

   /**
    * Get the FSConfig instance.
    */
   public FSConfig getConfig() {
      return config;
   }

   /**
    * Get the file system.
    */
   public XFileSystem getFSystem() {
      return fs;
   }

   private FSConfig config;
   private AbstractFileSystem fs;
}
