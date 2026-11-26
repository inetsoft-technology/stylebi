/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.wiz.service.WizScheduleService;
import inetsoft.web.wiz.service.WizScheduleService.FormatOptions;
import inetsoft.web.wiz.service.WizScheduleService.TaskRequest;
import inetsoft.web.wiz.service.WizScheduleService.TaskView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * The caller's own dashboard schedule tasks for the composer-chat plugin, with only the login
 * authorization (Redmine #82413). Not the admin task scope: see {@link WizScheduleService} for
 * how the Schedule permissions and task ownership are enforced on the real user.
 */
@RestController
@RequestMapping("/api/wiz/v1/schedule")
public class WizScheduleController {
   public WizScheduleController(WizScheduleService service) {
      this.service = service;
   }

   @GetMapping(value = "/formats", produces = MediaType.APPLICATION_JSON_VALUE)
   public FormatOptions formats(Principal principal) {
      return service.getFormatOptions(principal);
   }

   @GetMapping(value = "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<TaskView> list(@RequestParam(value = "path", required = false) String path,
                              @RequestParam(value = "scope", required = false) String scope,
                              Principal principal) throws Exception
   {
      return service.list(path, scope, principal);
   }

   @GetMapping(value = "/tasks/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
   public TaskView get(@PathVariable("name") String name, Principal principal) {
      return service.get(name, principal);
   }

   @PostMapping(value = "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
   public TaskView create(@RequestBody TaskRequest request, Principal principal,
                          @LinkUri String linkUri) throws Exception
   {
      return service.create(request, principal, linkUri);
   }

   @PutMapping(value = "/tasks/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
   public TaskView update(@PathVariable("name") String name, @RequestBody TaskRequest request,
                          Principal principal, @LinkUri String linkUri) throws Exception
   {
      return service.update(name, request, principal, linkUri);
   }

   @DeleteMapping(value = "/tasks/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, Object> delete(@PathVariable("name") String name, Principal principal)
      throws Exception
   {
      service.delete(name, principal);
      return Map.of("deleted", true, "name", name);
   }

   private final WizScheduleService service;
}
