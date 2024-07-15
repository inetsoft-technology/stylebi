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
package inetsoft.util.script;

/**
 * ScriptException, the runtime exception will be thrown
 * when script execution found error.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class ScriptException extends RuntimeException {
   /**
    * Create an instance of <tt>ScriptException</tt>.
    */
   public ScriptException() {
      super();
   }

   /**
    * Create an instance of <tt>ScriptException</tt>.
    */
   public ScriptException(String msg) {
      super(msg);
   }

   /**
    * Create an instance of <tt>ScriptException</tt>.
    */
   public ScriptException(Throwable cause) {
      super(cause);
   }

   /**
    * Create an instance of <tt>ScriptException</tt>.
    */
   public ScriptException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
