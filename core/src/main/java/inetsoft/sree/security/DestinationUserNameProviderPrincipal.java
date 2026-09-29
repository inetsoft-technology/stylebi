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
package inetsoft.sree.security;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.session.IgniteSessionRepository;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DestinationUserNameProviderPrincipal
   extends SRPrincipal implements DestinationUserNameProvider
{
   public DestinationUserNameProviderPrincipal() {
   }

   public DestinationUserNameProviderPrincipal(SRPrincipal principal) {
      super(principal);
   }

   public DestinationUserNameProviderPrincipal(SRPrincipal principal, ClientInfo client) {
      super(principal, client);
   }

   public DestinationUserNameProviderPrincipal(ClientInfo client, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID)
   {
      super(client, roles, groups, orgID, secureID);
   }

   public DestinationUserNameProviderPrincipal(ClientInfo client, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID, String alias)
   {
      super(client, roles, groups, orgID, secureID, alias);
   }

   public DestinationUserNameProviderPrincipal(IdentityID user, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID)
   {
      super(user, roles, groups, orgID, secureID);
   }

   @Override
   public String getDestinationUserName() {
      StringBuilder name = new StringBuilder();
      name.append(getName()).append('[').append(getSecureID()).append("]@");

      if(getUser() != null && getUser().getIPAddress() != null) {
         name.append(getUser().getIPAddress());
      }
      else {
         name.append("localhost");
      }

      return name.toString();
   }

   public void setHttpSessionId(String httpSessionId) {
      // first time? copy from local to distributed
      if(this.httpSessionId == null) {
         this.httpSessionId = httpSessionId;

         prop.forEach(this::setProperty);
         params.forEach((key, value) -> {
            setParameter(key, value, paramTS.get(key));
         });

         setLastAccess(super.getLastAccess());
         setProfiling(super.isProfiling());
      }
      else {
         this.httpSessionId = httpSessionId;
      }
   }

   /**
    * Binds this principal to an HTTP session like {@link #setHttpSessionId(String)}, and
    * records what it writes to the session attribute map until the binding is committed with
    * {@link #commitHttpSession()} or undone with {@link #unbindHttpSession()}.
    *
    * @param httpSessionId the HTTP session ID.
    *
    * @return {@code true} if this call bound the principal, or {@code false} if it was already
    *         bound and nothing is recorded.
    */
   public boolean bindHttpSession(String httpSessionId) {
      if(this.httpSessionId != null) {
         setHttpSessionId(httpSessionId);
         return false;
      }

      bindWrites = new HashMap<>();
      setHttpSessionId(httpSessionId);
      return true;
   }

   /**
    * Keeps the binding made by {@link #bindHttpSession(String)} and stops recording.
    */
   public void commitHttpSession() {
      bindWrites = null;
   }

   /**
    * Undoes {@link #bindHttpSession(String)}. The session attribute map is shared with any other
    * principal of the same HTTP session, including another login in progress, so each entry is
    * only put back to its previous value if it still holds the value this principal wrote.
    */
   public void unbindHttpSession() {
      Map<String, BindWrite> writes = bindWrites;
      DistributedMap<String, Object> map = getSessionAttributeMap();
      bindWrites = null;
      httpSessionId = null;

      if(map == null || writes == null) {
         return;
      }

      writes.forEach((key, write) -> {
         if(write.written() == null) {
            if(write.old() != null) {
               map.putIfAbsent(key, write.old());
            }
         }
         else if(write.old() == null) {
            map.remove(key, write.written());
         }
         else {
            map.replace(key, write.written(), write.old());
         }
      });
   }

   private void putSessionAttribute(DistributedMap<String, Object> map, String key, Object value) {
      recordBindWrite(map, key, value);
      map.put(key, value);
   }

   private void removeSessionAttribute(DistributedMap<String, Object> map, String key) {
      recordBindWrite(map, key, null);
      map.remove(key);
   }

   private void recordBindWrite(DistributedMap<String, Object> map, String key, Object value) {
      if(bindWrites != null) {
         BindWrite write = bindWrites.get(key);
         Object old = write != null ? write.old() : map.get(key);
         bindWrites.put(key, new BindWrite(old, value));
      }
   }

   @Override
   public void setProperty(String name, String val) {
      super.setProperty(name, val);

      // store local only
      if(SUtil.EM_USER.equals(name) || (!isEMPrincipal() && "curr_org_id".equals(name))) {
         return;
      }

      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return;
      }

      if(val == null || val.isEmpty()) {
         removeSessionAttribute(map, PROP_PREFIX + name);
      }
      else {
         putSessionAttribute(map, PROP_PREFIX + name, val);
      }
   }

   @Override
   public String getProperty(String name) {
      // for non em user, ignore the value of this property
      if(!isEMPrincipal() && "curr_org_id".equals(name)) {
         return null;
      }
      else if(SUtil.EM_USER.equals(name)) {
         return super.getProperty(name);
      }

      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getProperty(name);
      }

      return (String) map.get(PROP_PREFIX + name);
   }

   @Override
   public Set<String> getPropertyNames() {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getPropertyNames();
      }

      return map.keySet().stream()
         .filter(key -> key != null && key.startsWith(PROP_PREFIX))
         .map(key -> key.substring(PROP_PREFIX.length()))
         .collect(Collectors.toSet());
   }

   @Override
   protected void setParameter(String name, Object value, long ts) {
      super.setParameter(name, value, ts);
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return;
      }

      if(value == null) {
         removeSessionAttribute(map, PARAM_PREFIX + name);
      }
      else {
         putSessionAttribute(map, PARAM_PREFIX + name, JavaScriptEngine.unwrap(value));
         putSessionAttribute(map, PARAM_TS_PREFIX + name, ts);
      }
   }

   @Override
   public Object getParameter(String name) {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getParameter(name);
      }

      return map.get(PARAM_PREFIX + name);
   }

   @Override
   public long getParameterTS(String name) {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getParameterTS(name);
      }

      Long ts = (Long) map.get(PARAM_TS_PREFIX + name);
      return ts != null ? ts.longValue() : 0;
   }

   @Override
   public Set<String> getParameterNames() {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getParameterNames();
      }

      return map.keySet().stream()
         .filter(key -> key != null && key.startsWith(PARAM_PREFIX))
         .map(key -> key.substring(PARAM_PREFIX.length()))
         .collect(Collectors.toSet());
   }

   @Override
   public long getLastAccess() {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.getLastAccess();
      }

      Long ts = (Long) getDistributedField("lastAccess");
      return ts != null ? ts.longValue() : 0;
   }

   @Override
   public void setLastAccess(long accessed) {
      super.setLastAccess(accessed);
      setDistributedField("lastAccess", accessed);
   }

   @Override
   public boolean isProfiling() {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return super.isProfiling();
      }

      return Boolean.TRUE.equals(getDistributedField("profiling"));
   }

   @Override
   public void setProfiling(boolean profiling) {
      super.setProfiling(profiling);
      setDistributedField("profiling", profiling);
   }

   private Object getDistributedField(String name) {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map != null) {
         return map.get(FIELD_PREFIX + name);
      }

      return null;
   }

   private void setDistributedField(String name, Object value) {
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map != null) {
         putSessionAttribute(map, FIELD_PREFIX + name, value);
      }
   }

   private DistributedMap<String, Object> getSessionAttributeMap() {
      if(httpSessionId == null) {
         return null;
      }

      return IgniteSessionRepository.getSessionAttributeMap(httpSessionId);
   }

   @Override
   public void writeExternal(ObjectOutput out) throws IOException {
      super.writeExternal(out);
      writeStringExternal(httpSessionId, out);
   }

   @Override
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      super.readExternal(in);
      httpSessionId = readStringExternal(in);
   }

   private boolean isEMPrincipal() {
      return "true".equals(prop.get(SUtil.EM_USER));
   }

   private String httpSessionId;
   // what the first bind wrote to the session attribute map until it is committed or undone
   private Map<String, BindWrite> bindWrites;
   private static final String PROP_PREFIX = "DestinationUserNameProviderPrincipal.PROP.";
   private static final String PARAM_PREFIX = "DestinationUserNameProviderPrincipal.PARAM.";
   private static final String PARAM_TS_PREFIX = "DestinationUserNameProviderPrincipal.PARAM_TS.";
   private static final String FIELD_PREFIX = "DestinationUserNameProviderPrincipal.FIELD.";

   // the value an entry had before the first bind wrote it, and the value last written, where
   // null means the entry was absent or removed
   private record BindWrite(Object old, Object written) {
   }
}
