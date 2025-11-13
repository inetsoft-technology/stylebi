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
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.session.IgniteSessionRepository;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;

import java.io.*;
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

   @Override
   public void setProperty(String name, String val) {
      super.setProperty(name, val);
      DistributedMap<String, Object> map = getSessionAttributeMap();

      if(map == null) {
         return;
      }

      if(val == null || val.isEmpty()) {
         map.remove(PROP_PREFIX + name);
      }
      else {
         map.put(PROP_PREFIX + name, val);
      }
   }

   @Override
   public String getProperty(String name) {
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
         map.remove(PARAM_PREFIX + name);
      }
      else {
         map.put(PARAM_PREFIX + name, JavaScriptEngine.unwrap(value));
         map.put(PARAM_TS_PREFIX + name, ts);
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
         map.put(FIELD_PREFIX + name, value);
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

   private String httpSessionId;
   private static final String PROP_PREFIX = "DestinationUserNameProviderPrincipal.PROP.";
   private static final String PARAM_PREFIX = "DestinationUserNameProviderPrincipal.PARAM.";
   private static final String PARAM_TS_PREFIX = "DestinationUserNameProviderPrincipal.PARAM_TS.";
   private static final String FIELD_PREFIX = "DestinationUserNameProviderPrincipal.FIELD.";
}
