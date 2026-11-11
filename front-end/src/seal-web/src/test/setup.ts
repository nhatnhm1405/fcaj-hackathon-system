// Vitest global setup — adds @testing-library/jest-dom matchers
// (toBeDisabled, toBeEmptyDOMElement, ...) and clears the DOM between tests.
import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

afterEach(() => {
  cleanup();
});
