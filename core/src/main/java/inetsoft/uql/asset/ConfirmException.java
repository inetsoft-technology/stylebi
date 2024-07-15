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
package inetsoft.uql.asset;

import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Confirm exception, the exception may be confirmed.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ConfirmException extends RuntimeException implements Serializable {
   /**
    * OK level message.
    */
   public static final int OK = 0;
   /**
    * Trace level message.
    */
   public static final int TRACE = 1;
   /**
    * Debug level message.
    */
   public static final int DEBUG = 2;
   /**
    * Information level message.
    */
   public static final int INFO = 3;
   /**
    * Warning level message.
    */
   public static final int WARNING = 4;
   /**
    * Error level message.
    */
   public static final int ERROR = 5;
   /**
    * Confirm level message.
    */
   public static final int CONFIRM = 6;
   /**
    * Progress with cancellation level message.
    */
   public static final int PROGRESS = 7;
   /**
    * Override and refresh confirm level message.
    */
   public static final int OVERRIDE = 8;

   /**
    * Constructor.
    */
   public ConfirmException() {
      super();
   }

   /**
    * Constructor.
    */
   public ConfirmException(String message) {
      super(message);
   }

   /**
    * Constructor.
    */
   public ConfirmException(String message, Throwable cause) {
      super(message);
      this.cause = cause;
   }

   /**
    * Constructor.
    */
   public ConfirmException(String message, int level) {
      this(message, null, level);
   }

   /**
    * Constructor.
    */
   public ConfirmException(String message, Throwable cause, int level) {
      super(message);
      this.cause = cause;
      this.level = level;
   }

   /**
    * Constructor.
    */
   public ConfirmException(Throwable cause) {
      super(cause.getMessage());
      this.cause = cause;
   }

   /**
    * Constructor.
    */
   public ConfirmException(Throwable cause, int level) {
      this(cause.getMessage(), cause, level);
   }

   /**
    * Get wrapped throwable if any.
    */
   public Throwable getThrowable() {
      return cause;
   }

   /**
    * Get the keywords.
    * @return the keywords.
    */
   public String getKeywords() {
      return keywords;
   }

   /**
    * Set the keywords.
    */
   public void setKeywords(String keywords) {
      this.keywords = keywords;
   }

   /**
    * Set the event to be re-submitted after confirmation.
    */
   public void setEvent(AssetObject event) {
      this.event = event;
   }

   /**
    * Get the event to be re-submitted after confirmation.
    */
   public AssetObject getEvent() {
      return event;
   }

   /**
    * Get the level of the message.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set the level of the message..
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Set a property value.
    */
   public void setProperty(String key, Object val) {
      props.put(key, val);
   }

   /**
    * Get a property value.
    */
   public Object getProperty(String key) {
      return props.get(key);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ConfirmException)) {
         return false;
      }

      ConfirmException exc = (ConfirmException) obj;

      if(getLevel() !=  exc.getLevel()) {
         return false;
      }

      if(!Tool.equals(getEvent(), exc.getEvent())) {
         return false;
      }

      return true;
   }

   private Throwable cause;
   private String keywords;
   private int level = CONFIRM;
   private AssetObject event; // the event to re-submit
   private Map props = new HashMap();
}
