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
 * This class encapsulates a general parse error or warning.
 *
 * <p>This class can contain basic error or warning information from
 * either the parser or the application.
 *
 * <p>If the application needs to pass through other types of
 * exceptions, it must wrap those exceptions in a ParseException.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public class ParseException extends RuntimeException {

   /**
    * @serial The embedded exception if tunnelling, or null.
    */
   protected Exception exception;

   /**
    * @serial The line number.
    */
   protected int lineNumber;

   /**
    * @serial The column number.
    */
   protected int columnNumber;

   /**
    * Creates a new ParseException.
    *
    * @param message The error or warning message.
    * @param line    The line of the last parsed character.
    * @param column  The column of the last parsed character.
    */
   public ParseException(String message, int line, int column)
   {
      super(message);
      exception = null;
      lineNumber = line;
      columnNumber = column;
   }

   /**
    * Creates a new ParseException wrapping an existing exception.
    *
    * <p>The existing exception will be embedded in the new
    * one, and its message will become the default message for
    * the ParseException.
    *
    * @param e The exception to be wrapped in a ParseException.
    */
   public ParseException(Exception e)
   {
      exception = e;
      lineNumber = -1;
      columnNumber = -1;
   }

   /**
    * Creates a new ParseException from an existing exception.
    *
    * <p>The existing exception will be embedded in the new
    * one, but the new exception will have its own message.
    *
    * @param message The detail message.
    * @param e       The exception to be wrapped in a SAXException.
    */
   public ParseException(String message, Exception e)
   {
      super(message);
      this.exception = e;
   }

   /**
    * Return a detail message for this exception.
    *
    * <p>If there is a embedded exception, and if the ParseException
    * has no detail message of its own, this method will return
    * the detail message from the embedded exception.
    *
    * @return The error or warning message.
    */
   public String getMessage()
   {
      String message = super.getMessage();

      if(message == null && exception != null) {
         return exception.getMessage();
      }
      else {
         return message;
      }
   }

   /**
    * Return the embedded exception, if any.
    *
    * @return The embedded exception, or null if there is none.
    */
   public Exception getException()
   {
      return exception;
   }

   /**
    * Returns the line of the last parsed character.
    */
   public int getLineNumber()
   {
      return lineNumber;
   }

   /**
    * Returns the column of the last parsed character.
    */
   public int getColumnNumber()
   {
      return columnNumber;
   }
}
