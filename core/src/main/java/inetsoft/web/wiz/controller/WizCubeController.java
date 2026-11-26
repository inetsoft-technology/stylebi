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

import inetsoft.web.wiz.WizUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XCube;
import inetsoft.uql.XCubeMember;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XDimension;
import inetsoft.uql.XDomain;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.*;
import inetsoft.util.Tool;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.model.WizCubeList;
import inetsoft.web.wiz.model.WizCubeSettingsResult;
import inetsoft.web.wiz.request.WizCubeSettingsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

/**
 * Read-only cube discovery for an XMLA data source, for the wiz bridge.
 *
 * <p>Lists the cubes of a data source's stored domain and describes one cube (dimensions >
 * hierarchies > levels, measures). It reads what {@code refresh-cubes} stored; it never contacts the
 * XMLA endpoint. The data source needs READ, as the Composer's data tree requires, and each cube
 * needs READ on its {@code CUBE} resource, the check {@code AssetEventUtil.getXCube} makes: a cube
 * the caller may not read is absent from the list and answered as unknown, never named.</p>
 *
 * <p>Dimensions, hierarchies, levels and measures are filtered and grouped exactly as
 * {@code AssetEventUtil.getDimensionNode} and {@code appendCubeMeasures} build the Composer data
 * tree, and the names are the tree entries' {@code entity} and {@code attribute}, so a name read
 * here is the one the binding tree and the worksheet cube drop use. Like its sibling controllers
 * this class must not declare a local {@code @ExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizCubeController {
   public WizCubeController(SecurityEngine securityEngine, XRepository xrepository,
                            XmlaDatasourceService xmlaDatasourceService)
   {
      this.securityEngine = securityEngine;
      this.xrepository = xrepository;
      this.xmlaDatasourceService = xmlaDatasourceService;
      this.gates = new WizDatasourceGates(securityEngine);
   }

   /**
    * Lists the readable cubes of an XMLA data source.
    *
    * @throws ResponseStatusException 404 for an unknown data source and 422 for one that is not
    *                                 XMLA; both name the XMLA data sources the caller can read.
    */
   @GetMapping(value = "/xmla/cubes", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.READ
      )
   })
   public WizCubeList listCubes(@PermissionPath @RequestParam("path") String path,
                                Principal principal)
      throws Exception
   {
      String source = requirePath(path);
      gates.requireDataSourcePermission(source, ResourceAction.READ, principal);
      XDomain domain = requireXmla(source, principal);
      List<Cube> cubes = readableCubes(source, domain, principal);
      List<WizCubeList.Entry> entries = new ArrayList<>();

      for(Cube cube : cubes) {
         entries.add(new WizCubeList.Entry(
            cube.getName(), cube.getCaption(), cube.getType(),
            dimensionsOf(cube).size(), measuresOf(cube).size()));
      }

      String note = null;

      if(entries.isEmpty()) {
         note = "No cube metadata is stored for \"" + source + "\" (or none is readable by you). " +
            "Run refresh_data_source_metadata on it to load the cubes from the XMLA endpoint.";
      }

      return new WizCubeList(source, entries, note);
   }

   /**
    * Describes one cube of an XMLA data source.
    *
    * <p>The cube is matched by name, then by caption, then case-insensitively when that is
    * unambiguous; anything else is a 404 listing the readable cube names.</p>
    */
   @GetMapping(value = "/xmla/cube", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.READ
      )
   })
   public WizCubeDetails getCube(@PermissionPath @RequestParam("path") String path,
                                 @RequestParam(value = "cube", required = false) String cubeName,
                                 Principal principal)
      throws Exception
   {
      String source = requirePath(path);

      if(cubeName == null || cubeName.isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cube is required");
      }

      gates.requireDataSourcePermission(source, ResourceAction.READ, principal);
      XDomain domain = requireXmla(source, principal);
      List<Cube> cubes = readableCubes(source, domain, principal);
      Cube cube = resolveCube(cubes, cubeName.trim());

      if(cube == null) {
         throw unknownCube(source, cubeName.trim(), cubes);
      }

      return describe(source, cube);
   }

   private static ResponseStatusException unknownCube(String source, String cubeName,
                                                      List<Cube> cubes)
   {
      List<String> names = new ArrayList<>();

      for(Cube c : cubes) {
         names.add(c.getName());
      }

      return new ResponseStatusException(
         HttpStatus.NOT_FOUND,
         "cube: no cube \"" + cubeName + "\" in data source \"" + source + "\"; " +
            (names.isEmpty() ? "it has no readable cubes (run refresh_data_source_metadata " +
               "to load them)" : "valid cubes: " + names));
   }

   /**
    * Edits the Portal's "OLAP Data" settings of one cube: Keep Original Order per dimension and As
    * Date, Database Locale and Parse Pattern per level.
    *
    * <p>Needs WRITE on the data source, as the Portal save that stores the same domain does, and
    * the cube must be readable. The stored domain is patched directly, see
    * {@link WizCubeSettingsPatcher}; an unknown name or an invalid value is a 400 that names the
    * field and lists the valid values, and nothing is saved. The answer is read back from the
    * saved domain, and a difference from what was applied is a 500.</p>
    */
   @PostMapping(value = "/xmla/cube-settings", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizCubeSettingsResult updateCubeSettings(@RequestBody WizCubeSettingsRequest request,
                                                   Principal principal)
      throws Exception
   {
      if(request == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasource is required");
      }

      String source = requirePath(request.path());

      if(request.cube() == null || request.cube().isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cube is required");
      }

      gates.requireDataSourcePermission(source, ResourceAction.WRITE, principal);
      XDomain domain = requireXmla(source, principal);
      List<Cube> cubes = readableCubes(source, domain, principal);
      Cube cube = resolveCube(cubes, request.cube().trim());

      if(cube == null) {
         throw unknownCube(source, request.cube().trim(), cubes);
      }

      String cubeName = cube.getName();
      WizCubeSettingsResult[] applied = new WizCubeSettingsResult[1];
      XDomain saved;

      try {
         saved = xmlaDatasourceService.updateStoredDomain(source, copy -> {
            applied[0] = WizCubeSettingsPatcher.apply((Cube) copy.getCube(cubeName), source,
                                                      request);
         });
      }
      catch(IllegalArgumentException ex) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
      }
      catch(FileNotFoundException ex) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
      }

      XCube savedCube = saved == null ? null : saved.getCube(cubeName);
      WizCubeSettingsResult readBack = savedCube instanceof Cube c ?
         WizCubeSettingsPatcher.snapshot(source, c, applied[0]) : null;

      if(!applied[0].equals(readBack)) {
         throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The cube settings of \"" + source + "\" were not saved as given (applied " +
               applied[0] + ", read back " + readBack + ").");
      }

      return readBack;
   }

   /** Builds the cube description. */
   public static WizCubeDetails describe(String source, Cube cube) {
      return new WizCubeDetails(source, cube.getName(), cube.getCaption(), cube.getType(),
                                dimensionsOf(cube), measuresOf(cube));
   }

   /**
    * The dimensions as {@code AssetEventUtil.appendCubeDimensions} lists them: when every
    * dimension is a hierarchy (the usual XMLA shape) a folder per parent dimension holding its
    * hierarchies, otherwise a folder per dimension holding itself. Names that are also a measure
    * and dimensions without a level are left out; folders and hierarchies are in caption order.
    */
   public static List<WizCubeDetails.Dimension> dimensionsOf(XCube cube) {
      List<WizCubeDetails.Dimension> result = new ArrayList<>();
      Map<?, ?> groups = XMLAUtil.getDimensionGroups(cube);

      if(groups != null) {
         for(Map.Entry<?, ?> group : groups.entrySet()) {
            String name = (String) group.getKey();

            if(cube.getMeasure(name) != null) {
               continue;
            }

            List<WizCubeDetails.Hierarchy> hierarchies = new ArrayList<>();
            boolean time = false;
            boolean originalOrder = false;

            for(Object item : (Collection<?>) group.getValue()) {
               XDimension dim = (XDimension) item;
               WizCubeDetails.Hierarchy hierarchy = hierarchyOf(cube, dim);

               if(hierarchy != null) {
                  hierarchies.add(hierarchy);
                  time |= isTime(dim);
                  originalOrder |= dim instanceof Dimension d && d.isOriginalOrder();
               }
            }

            hierarchies.sort(Comparator.comparing(
               WizCubeDetails.Hierarchy::caption, String.CASE_INSENSITIVE_ORDER));
            result.add(new WizCubeDetails.Dimension(name, name, time, originalOrder, hierarchies));
         }
      }
      else {
         Enumeration<XDimension> dims = cube.getDimensions();

         while(dims.hasMoreElements()) {
            XDimension dim = dims.nextElement();
            WizCubeDetails.Hierarchy hierarchy = hierarchyOf(cube, dim);

            if(hierarchy != null) {
               result.add(new WizCubeDetails.Dimension(
                  dim.getName(), hierarchy.caption(), isTime(dim),
                  dim instanceof Dimension d && d.isOriginalOrder(), List.of(hierarchy)));
            }
         }
      }

      result.removeIf(d -> d.hierarchies().isEmpty());
      result.sort(Comparator.comparing(
         WizCubeDetails.Dimension::caption, String.CASE_INSENSITIVE_ORDER));

      return result;
   }

   /** One hierarchy, or null when the tree would not list it (a measure's name, or no level). */
   private static WizCubeDetails.Hierarchy hierarchyOf(XCube cube, XDimension dim) {
      if(dim == null || cube.getMeasure(dim.getName()) != null || dim.getLevelCount() == 0) {
         return null;
      }

      List<WizCubeDetails.Level> levels = new ArrayList<>();

      for(int i = 0; i < dim.getLevelCount(); i++) {
         XCubeMember member = dim.getLevelAt(i);
         String caption = member instanceof DimMember dm ? dm.getCaption() : null;
         caption = caption == null ? member.getName() : caption;
         XMetaInfo meta = member.getXMetaInfo();
         String pattern = meta == null || Tool.isEmptyString(meta.getDatePattern()) ? null :
            meta.getDatePattern();
         String locale = meta == null || meta.getLocale() == null ? null :
            XMetaInfo.formatLocale(meta.getLocale());
         levels.add(new WizCubeDetails.Level(
            member.getName(), caption, member.getType(), i, meta != null && meta.isAsDate(),
            locale, pattern));
      }

      String caption = AssetUtil.getCaption(dim);

      return new WizCubeDetails.Hierarchy(
         dim.getName(), caption == null ? dim.getName() : caption,
         dim instanceof HierDimension h && h.isUserDefined(),
         dim instanceof Dimension d && d.isOriginalOrder(), levels);
   }

   /**
    * The measures as {@code AssetEventUtil.appendCubeMeasures} lists them: a member that is also a
    * dimension is a measure only when its data reference says so. In caption order.
    */
   public static List<WizCubeDetails.Measure> measuresOf(XCube cube) {
      List<WizCubeDetails.Measure> result = new ArrayList<>();
      Enumeration<?> measures = cube.getMeasures();

      while(measures.hasMoreElements()) {
         XCubeMember member = (XCubeMember) measures.nextElement();

         if(cube.getDimension(member.getName()) != null && member.getDataRef() != null &&
            (member.getDataRef().getRefType() & DataRef.MEASURE) == 0)
         {
            continue;
         }

         boolean measure = member instanceof Measure;
         String caption = measure ? ((Measure) member).getCaption() : null;
         caption = caption == null ? member.getName() : caption;
         result.add(new WizCubeDetails.Measure(
            member.getName(), caption, member.getType(),
            measure && ((Measure) member).isCalcMeasure(), member.getFolder()));
      }

      result.sort(Comparator.comparing(
         WizCubeDetails.Measure::caption, String.CASE_INSENSITIVE_ORDER));

      return result;
   }

   private static boolean isTime(XDimension dim) {
      return (dim.getType() & DataRef.CUBE_TIME_DIMENSION) == DataRef.CUBE_TIME_DIMENSION;
   }

   /** The name, then the caption, then a unique case-insensitive match of either. */
   public static Cube resolveCube(List<Cube> cubes, String name) {
      for(Cube cube : cubes) {
         if(name.equals(cube.getName())) {
            return cube;
         }
      }

      for(Cube cube : cubes) {
         if(name.equals(cube.getCaption())) {
            return cube;
         }
      }

      Cube match = null;

      for(Cube cube : cubes) {
         if(name.equalsIgnoreCase(cube.getName()) || name.equalsIgnoreCase(cube.getCaption())) {
            if(match != null) {
               return null;
            }

            match = cube;
         }
      }

      return match;
   }

   /** Cubes of the domain the caller may read, as {@code AssetEventUtil.getXCube} decides. */
   private List<Cube> readableCubes(String source, XDomain domain, Principal principal)
      throws Exception
   {
      List<Cube> cubes = new ArrayList<>();

      if(domain == null || !(domain instanceof Domain)) {
         return cubes;
      }

      Enumeration<?> all = domain.getCubes();

      while(all.hasMoreElements()) {
         Object next = all.nextElement();

         if(next instanceof Cube cube && WizUtil.checkPermission(securityEngine,
            principal, ResourceType.CUBE, source + "::" + cube.getName(), ResourceAction.READ))
         {
            cubes.add(cube);
         }
      }

      return cubes;
   }

   /** The stored domain of an XMLA data source, or the error naming what would have worked. */
   private XDomain requireXmla(String source, Principal principal) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(source);

      if(dataSource == null) {
         throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "datasource: no data source at \"" + source + "\"; XMLA data sources: " +
               xmlaSources(principal));
      }

      if(!(dataSource instanceof XMLADataSource)) {
         throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "datasource: \"" + source + "\" is a " + dataSource.getType() +
               " data source, not an XMLA (cube) data source; XMLA data sources: " +
               xmlaSources(principal));
      }

      return xrepository.getDomain(source);
   }

   private List<String> xmlaSources(Principal principal) throws Exception {
      List<String> names = new ArrayList<>();

      for(String name : xrepository.getDataSourceFullNames()) {
         if(xrepository.getDataSource(name) instanceof XMLADataSource &&
            WizUtil.checkPermission(securityEngine,
               principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ))
         {
            names.add(name);
         }
      }

      return names;
   }

   private static String requirePath(String path) {
      if(path == null || path.isBlank() || "/".equals(path)) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasource is required");
      }

      return path;
   }

   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
   private final XmlaDatasourceService xmlaDatasourceService;
   private final WizDatasourceGates gates;
}
