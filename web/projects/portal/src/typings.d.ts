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
/* eslint-disable */
/* SystemJS module definition */
declare var module: NodeModule;
interface NodeModule {
   id: string;
}

/* JSPlumb definitions */
declare namespace JSPlumb {
   export interface JSPlumbLib {
      jsPlumb: JSPlumbModule;
   }

   export interface JSPlumbModule {
      getInstance(): JSPlumbInstance;
   }

   export interface JSPlumbInstance {
      addEndpoint(el: string | any, params?: Object, referenceParams?: Object): any | Array<any>;
      addToDragSelection(elements: any[]): void;
      removeFromDragSelection(elements: any[]): void;
      addToPosse(elements: any, posse: any): void;
      bind(event: string, callbackFn: Function): void;
      clearDragSelection(): void;
      connect(params: Object, referenceParams?: Object): any;
      deleteConnection(connection: Object): void;
      deleteEveryEndpoint(): void;
      deleteEveryConnection(params?: Object): void;
      draggable(el: string | any, options?: Object): JSPlumbInstance;
      getAllConnections(): any;
      getConnections(options: string | Object, flat?: boolean): Array<any> | Map<any,any>;
      getContainer(): Element;
      getInstance(config?: Object): any;
      importDefaults(defaults: Object): JSPlumbInstance;
      isSource(el: string | any): boolean;
      isSuspendDrawing(): boolean;
      makeSource(el: string | any, params?: Object): void;
      makeTarget(el: string | any, params?: Object): void;
      on(el: string | any, event: string, fn: Function): JSPlumbInstance;
      ready(fn: Function): void;
      registerEndpointTypes(types: Object): void;
      registerConnectionTypes(types: Object): void;
      remove(el: string | any): void;
      removeAllEndpoints(element: string | any): JSPlumbInstance;
      removeFromAllPosses(elements: any): void;
      repaintEverything(clearEdits?: boolean): JSPlumbInstance;
      reset(): void;
      restoreDefaults(): JSPlumbInstance;
      selectEndpoints(params?: {scope?: string, source?: any, target?: any, element?: any}): any;
      setContainer(element: string | any): void;
      setSourceEnabled(el: string | any, state: boolean, connectionType?: string): JSPlumbInstance;
      setSuspendDrawing(val: boolean, repaintAfterwards?: boolean): boolean;
      unmakeEverySource(): void;
      unmakeSource(element: string | any): void;
      unmakeTarget(element: string | any): void;
      setZoom(zoom: number): void;
   }
}

declare namespace Stomp {
   export function over(socket: any): Stomp.Client;

   export interface Client {
      connect(headers: any, connectCallback: () => any, errorCallback?: (error: any) => any): void;
      disconnect(disconnectCallback: () => any): void;
      send(destination: string, headers: any, body: string): void;
      subscribe(destination: string, callback: (message: Stomp.Frame) => any, headers?: any): Stomp.Subscription;
      maxWebSocketFrameSize: number;
      connected: boolean;
      debug: any;
      ws: any; // SockJS
   }

   export interface Subscription {
      id: string;
      unsubscribe(): void;
   }

   export interface Frame {
      command: string;
      headers: any;
      body: string;
   }
}

declare class DocumentTouch {
}

declare module 'fscreen' {
   type FScreen = {
      fulscreenEnabled: unknown;
      fullscreenElement: unknown;
      requestFullscreen: (element: unknown) => unknown;
      requestFullscreenFunction: (element: unknown) => unknown;
      exitFullscreen: () => unknown;
      onfullscreenchange: unknown;
      addEventListener: (type: unknown, handler: unknown, options?: unknown) => unknown;
      removeEventListener: (type: unknown, handler: unknown, options?: unknown) => unknown;
      onfullscreenerror: unknown;
      fullscreenPseudoClass: unknown;
   }

   const fscreen: FScreen;
   export default fscreen;
}

declare module "*.json" {
   const value: any;
   export default value;
}

/* eslint-enable */
