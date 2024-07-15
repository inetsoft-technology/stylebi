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
package inetsoft.util.audit;

import java.io.Serializable;

/**
 * This interface defines the API for an audit record. An audit record is
 * an object that represents a row in audit db tables.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5, 5/19/2006
 */
public interface AuditRecord extends Serializable, Cloneable {
   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   boolean isValid();
}
