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
package inetsoft.uql.erm;

import inetsoft.uql.*;
import inetsoft.uql.service.XHandler;

import java.security.Principal;

/**
 * An XModelHandler translates a selection of attributes from a data model
 * into a format that can be used to generate a result set from the associated
 * data source. In order for a data source type to be able to use a data model,
 * it must have a corresponding implementation of XModelHandler.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public abstract class XModelHandler {
   /**
    * Create an XModelHandler that uses the specified <code>XHandler</code>.
    * @param handler the <code>XHandler</code> used to actually execute the
    * generated query.
    */
   public XModelHandler(XHandler handler) {
      this.handler = handler;
   }

   /**
    * Executes a selection of attributes from the specified data model and
    * returns the result set in the form of an <code>XNode</code>. Subclasses
    * should translate the selection into a form usable by the
    * corresponding <code>XHandler</code> implementation and use that class to
    * get the result.
    * @param session the session object
    * @param selection a group of attributes and conditions defining a query
    * @param model the data model from which the attributes were taken
    * @param vars variable values for the query.
    * @param user the user executing the query.
    * @return the resulting <code>XNode</code> object
    * @exception Exception if an error is encountered while executing the
    * query
    */
   public abstract XNode execute(Object session, XDataSelection selection,
                                 XDataModel model, VariableTable vars, 
                                 Principal user)
      throws Exception;

   /**
    * Get the <code>XHandler</code> used to execute the generated query.
    * @return the handler
    */
   public XHandler getHandler() {
      return handler;
   }

   private XHandler handler;
}

