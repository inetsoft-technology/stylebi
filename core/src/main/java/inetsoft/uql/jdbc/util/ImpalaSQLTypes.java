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
package inetsoft.uql.jdbc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of <tt>SQLTypes</tt> that provides support for Impala DB
 * connections.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
public class ImpalaSQLTypes extends SQLTypes {
   /**
    * Creates a new instance of <tt>ImpalaSQLTypes</tt>.
    */
   public ImpalaSQLTypes() {
      // default constructor
   }

   private static final Logger LOG = LoggerFactory.getLogger(ImpalaSQLTypes.class);
}