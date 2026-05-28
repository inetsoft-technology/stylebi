/*
 * Portal-specific Jest setup.
 * Runs after the testing framework is installed (setupFilesAfterEnv).
 */

// ResizeObserver is not implemented in JSDOM. Provide a minimal stub so that
// components using ResizedDirective (which calls new ResizeObserver(...)) do
// not throw "ReferenceError: ResizeObserver is not defined" during test setup.
(global as any).ResizeObserver = class ResizeObserver {
   observe() {}
   unobserve() {}
   disconnect() {}
};
