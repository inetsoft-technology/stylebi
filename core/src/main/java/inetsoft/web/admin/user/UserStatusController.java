/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.user;

import inetsoft.web.admin.monitoring.AbstractMonitoringController;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.factory.RemainingPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
public class UserStatusController extends AbstractMonitoringController {
   @Autowired
   public UserStatusController(UserService userService, MonitoringDataService monitoringDataService)
   {
      this.userService = userService;
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping(value = { "/monitoring/user/get-session-grid",
                               "/monitoring/user/get-session-grid/{address}" })
   public List<UserSessionMonitoringTableModel> subscribeSessionModel(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return userService.getServerSessionModel(address.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @SubscribeMapping(value = { "/monitoring/user/get-failed-grid",
                               "/monitoring/user/get-failed-grid/{address}" })
   public List<UserFailedLoginMonitoringTableModel> subscribeFailedLoginGrid(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return userService.getServerFailedModel(address.orElse(null));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @PostMapping(value = { "/api/em/monitor/user/logout", "/api/em/monitor/user/logout/**" })
   public void logout(@RequestBody String[] sessionIds, @RemainingPath() String server) {
      userService.logoutSession(getServerClusterNode(server), sessionIds);
   }

   @SubscribeMapping(value = { "/monitoring/user/get-top-five-users-grid/",
                               "/monitoring/user/get-top-five-users-grid/{address}" })
   public List<TopUsersMonitoringTableModel> subscribeTop5UsersGrid(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return userService.getTopNUsersModel(address.orElse(null), 5);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   private final UserService userService;
   private final MonitoringDataService monitoringDataService;
}
