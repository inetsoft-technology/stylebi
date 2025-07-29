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
package inetsoft.util;

import inetsoft.sree.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * Class that stores contextual information for the current thread.
 *
 * @since 12.3
 */
public final class ThreadContext {
   /**
    * Creates a new instance of <tt>ThreadContext</tt>.
    */
   private ThreadContext() {
      // prevent instantiation
   }

   /**
    * Gets the principal associated with the current thread.
    *
    * @return the principal or <tt>null</tt> if not set.
    */
   public static Principal getPrincipal() {
      return PRINCIPAL.get();
   }

   /**
    * Sets the principal associated with the current thread.
    *
    * @param principal the principal.
    */
   public static void setPrincipal(Principal principal) {
      if(principal == null) {
         PRINCIPAL.remove();
      }
      else {
         if(LOG.isDebugEnabled() &&
            Organization.getSelfOrganizationID().equals(OrganizationManager.getInstance().getCurrentOrgID(principal)))
         {
            LOG.debug("Setting self-registered user: {}", principal, new Exception("Stack trace"));
         }

         PRINCIPAL.set(principal);
      }
   }

   /**
    * Gets the principal associated with the current thread.
    *
    * @return the principal or <tt>null</tt> if none is set.
    */
   public static Principal getContextPrincipal() {
      Thread thread = Thread.currentThread();
      Principal principal = null;

      if(thread instanceof GroupedThread) {
         principal = ((GroupedThread) thread).getPrincipal();
      }

      if(principal == null) {
         principal = getPrincipal();
      }

      return principal;
   }

   /**
    * Sets the principal associated with the current thread.
    *
    * @param principal the principal.
    */
   public static void setContextPrincipal(Principal principal) {
      Thread thread = Thread.currentThread();

      if(thread instanceof GroupedThread) {
         ((GroupedThread) thread).setPrincipal(principal);
      }
      else {
         setPrincipal(principal);
      }
   }

   /**
    * Gets the locale associated with the current thread.
    *
    * @return the locale.
    */
   public static Locale getLocale() {
      Locale locale = LOCALE.get();
      Thread current = Thread.currentThread();

      if(locale == null && current instanceof GroupedThread) {
         Principal user = ((GroupedThread) current).getPrincipal();

         if(user instanceof SRPrincipal) {
            locale = ((SRPrincipal) user).getLocale();
            setLocale(locale);
         }
      }

      return locale == null ? Locale.getDefault() : locale;
   }

   /**
    * Sets the locale associated with the current thread.
    *
    * @param locale the locale.
    */
   public static void setLocale(Locale locale) {
      if(locale == null) {
         LOCALE.remove();
      }
      else {
         LOCALE.set(locale);
      }
   }

   /**
    * Set a value in the current user session. Ignored if there is no user session in
    * current thread.
    */
   public static void setSessionInfo(String name, Object value) {
      synchronized(sessionInfos) {
         Thread thread = Thread.currentThread();
         Map<String, Object> infos = sessionInfos.get(thread);

         if(value != null) {
            getSessionOrCreate(thread).put(name, value);
         }
         else if(infos != null) {
            infos.remove(name);
         }
      }
   }

   /**
    * Get the value set in the current user session. This is not the web session value.
    * Only value set with setSessionInfo() is accessible.
    */
   public static Object getSessionInfo(String name) {
      return getSessionInfo(name, Thread.currentThread());
   }

   /**
    * Get the value set in the current user session.
    * @param sessionThread the thread which the session is associated with. If this is called
    *                      from a different thread (e.g. timer, thread pool), the original
    *                      thread should be used to retrieve session info.
    */
   public static Object getSessionInfo(String name, Thread sessionThread) {
      synchronized(sessionInfos) {
         Map<String, Object> infos = sessionInfos.get(sessionThread);
         return infos != null ? infos.get(name) : null;
      }
   }

   /**
    * Inherit (and share) session info from the specified thread.
    */
   public static void inheritSession(Thread from) {
      synchronized(sessionInfos) {
         sessionInfos.put(Thread.currentThread(), getSessionOrCreate(from));
      }
   }

   private static Map<String, Object> getSessionOrCreate(Thread thread) {
      synchronized(sessionInfos) {
         Map<String, Object> session = sessionInfos.get(thread);

         if(session == null) {
            sessionInfos.put(thread, session = new HashMap<>());
         }

         return session;
      }
   }

   private static final ThreadLocal<Principal> PRINCIPAL = new InheritableThreadLocal<>();
   private static final ThreadLocal<Locale> LOCALE = new InheritableThreadLocal<>();
   private static final Map<Thread,Map<String,Object>> sessionInfos = new WeakHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(ThreadContext.class);
}
