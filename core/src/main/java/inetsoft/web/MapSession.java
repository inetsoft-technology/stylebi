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
package inetsoft.web;

import inetsoft.util.ThreadContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import org.springframework.session.Session;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * A {@link Session} implementation that is backed by a {@link java.util.Map}. The
 * defaults for the properties are:
 * </p>
 * <ul>
 * <li>id - a secure random generated id</li>
 * <li>creationTime - the moment the {@link org.springframework.session.MapSession} was instantiated</li>
 * <li>lastAccessedTime - the moment the {@link org.springframework.session.MapSession} was instantiated</li>
 * <li>maxInactiveInterval - 30 minutes</li>
 * </ul>
 * <p>
 * <p>
 * This implementation has no synchronization, so it is best to use the copy constructor
 * when working on multiple threads.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public final class MapSession implements Session, Serializable {
   /**
    * Creates a new instance with a secure randomly generated identifier.
    */
   public MapSession(ServletContext servletContext) {
      this.id = UUID.randomUUID().toString();
      this.sessionAttrs = new ConcurrentHashMap<>();
      this.servletContext = servletContext;
   }

   /**
    * Creates a new instance from the provided {@link Session}.
    *
    * @param session the {@link Session} to initialize this {@link Session} with. Cannot
    *                be null.
    */
   public MapSession(Session session) {
      if(session == null) {
         throw new IllegalArgumentException("session cannot be null");
      }

      this.id = session.getId();
      this.sessionAttrs = getAttributeMap(session);
      this.servletContext =
         session instanceof MapSession ? ((MapSession) session).servletContext : null;
      this.lastAccessedTime = session.getLastAccessedTime();
      this.creationTime = session.getCreationTime();
      this.maxInactiveInterval = session.getMaxInactiveInterval();
   }

   @Override
   public void setLastAccessedTime(Instant lastAccessedTime) {
      Object refreshDisabled = ThreadContext.getSessionInfo("session.refresh.disabled");

      if(Boolean.TRUE.equals(refreshDisabled)) {
         return;
      }

      this.lastAccessedTime = lastAccessedTime;
   }

   @Override
   public Instant getCreationTime() {
      return this.creationTime;
   }

   @Override
   public String getId() {
      return this.id;
   }

   @Override
   public Instant getLastAccessedTime() {
      return this.lastAccessedTime;
   }

   @Override
   public void setMaxInactiveInterval(Duration interval) {
      if(interval.getSeconds() < 0) {
         this.maxInactiveInterval = null;
      }
      else {
         this.maxInactiveInterval = interval;
      }
   }

   @Override
   public Duration getMaxInactiveInterval() {
      return this.maxInactiveInterval;
   }

   @Override
   public boolean isExpired() {
      return isExpired(Instant.now());
   }

   boolean isExpired(Instant now) {
      if(this.maxInactiveInterval == null) {
         return false;
      }

      return now.minus(maxInactiveInterval).isAfter(lastAccessedTime);
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> T getAttribute(String attributeName) {
      return (T) this.sessionAttrs.get(attributeName);
   }

   @Override
   public Set<String> getAttributeNames() {
      return this.sessionAttrs.keySet();
   }

   @Override
   public void setAttribute(String attributeName, Object attributeValue) {
      if(attributeValue == null) {
         removeAttribute(attributeName);
      }
      else {
         Object oldValue = this.sessionAttrs.put(attributeName, attributeValue);
         modifiedAttributes.put(attributeName, new Object[] { oldValue, attributeValue });

         if(oldValue != attributeValue) {
            if(oldValue instanceof HttpSessionBindingListener) {
               HttpSessionBindingEvent event =
                  new HttpSessionBindingEvent(new HttpSessionAdapter(), attributeName, oldValue);
               ((HttpSessionBindingListener) oldValue).valueUnbound(event);
            }

            if(attributeValue instanceof HttpSessionBindingListener) {
               HttpSessionBindingEvent event =
                  new HttpSessionBindingEvent(new HttpSessionAdapter(), attributeName, oldValue);
               ((HttpSessionBindingListener) attributeValue).valueBound(event);
            }
         }
      }
   }

   @Override
   public void removeAttribute(String attributeName) {
      Object oldValue = this.sessionAttrs.remove(attributeName);

      if(oldValue instanceof HttpSessionBindingListener) {
         HttpSessionBindingEvent event =
            new HttpSessionBindingEvent(new HttpSessionAdapter(), attributeName, oldValue);
         ((HttpSessionBindingListener) oldValue).valueUnbound(event);
      }

      modifiedAttributes.put(attributeName, new Object[] { oldValue, null });
   }

   @Override
   public String changeSessionId() {
      id = UUID.randomUUID().toString();
      return id;
   }

   /**
    * Sets the time that this {@link Session} was created in milliseconds since midnight
    * of 1/1/1970 GMT. The default is when the {@link Session} was instantiated.
    *
    * @param creationTime the time that this {@link Session} was created in milliseconds
    *                     since midnight of 1/1/1970 GMT.
    */
   public void setCreationTime(Instant creationTime) {
      this.creationTime = creationTime;
   }

   /**
    * Sets the identifier for this {@link Session}. The id should be a secure random
    * generated value to prevent malicious users from guessing this value. The default is
    * a secure random generated identifier.
    *
    * @param id the identifier for this session.
    */
   public void setId(String id) {
      this.id = id;
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof Session && this.id.equals(((Session) obj).getId());
   }

   @Override
   public int hashCode() {
      return this.id.hashCode();
   }

   private static Map<String, Object> getAttributeMap(Session session) {
      Map<String, Object> map;

      if(session instanceof MapSession) {
         map = ((MapSession) session).sessionAttrs;
      }
      else {
         map = new ConcurrentHashMap<>(session.getAttributeNames().size());

         for(String name : session.getAttributeNames()) {
            map.put(name, session.getAttribute(name));
         }
      }

      return map;
   }

   private String id;
   private final Map<String, Object> sessionAttrs;
   private final ServletContext servletContext;
   private Instant creationTime = Instant.now();
   private Instant lastAccessedTime = this.creationTime;
   private Duration maxInactiveInterval =
      Duration.of(DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS, ChronoUnit.SECONDS);
   final Map<String, Object[]> modifiedAttributes = new ConcurrentHashMap<>();

   /**
    *
    * Default {@link #setMaxInactiveInterval(Duration)} (30 minutes).
    */
   public static final int DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS = 1800;
   private static final long serialVersionUID = 7160779239673823561L;

   private final class HttpSessionAdapter implements HttpSession {
      @Override
      public long getCreationTime() {
         return MapSession.this.getCreationTime().toEpochMilli();
      }

      @Override
      public String getId() {
         return MapSession.this.getId();
      }

      @Override
      public long getLastAccessedTime() {
         return MapSession.this.getLastAccessedTime().toEpochMilli();
      }

      @Override
      public ServletContext getServletContext() {
         return MapSession.this.servletContext;
      }

      @Override
      public void setMaxInactiveInterval(int interval) {
         MapSession.this.setMaxInactiveInterval(Duration.of(interval, ChronoUnit.SECONDS));
      }

      @Override
      public int getMaxInactiveInterval() {
         return (int) MapSession.this.getMaxInactiveInterval().getSeconds();
      }

      @Override
      public Object getAttribute(String name) {
         return MapSession.this.getAttribute(name);
      }

      @Override
      public Enumeration<String> getAttributeNames() {
         return Collections.enumeration(MapSession.this.getAttributeNames());
      }

      @Override
      public void setAttribute(String name, Object value) {
         MapSession.this.setAttribute(name, value);
      }

      @Override
      public void removeAttribute(String name) {
         MapSession.this.removeAttribute(name);
      }

      @Override
      public void invalidate() {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean isNew() {
         return false;
      }
   }
}
