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
package inetsoft.graph.mxgraph.util.svg;

/**
 * This interface represents a parser.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public interface Parser {

   /**
    * Parses the given string
    */
   void parse(String s) throws ParseException;

   /**
    * Allows an application to register an error event handler.
    *
    * <p>If the application does not register an error event handler,
    * all error events reported by the parser will cause an exception
    * to be thrown.
    *
    * <p>Applications may register a new or different handler in the
    * middle of a parse, and the parser must begin using the new
    * handler immediately.</p>
    *
    * @param handler The error handler.
    */
   void setErrorHandler(ErrorHandler handler);
}
