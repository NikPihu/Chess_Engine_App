// Adds DOM matchers like toBeInTheDocument() to Vitest's expect.
import '@testing-library/jest-dom/vitest';

// jsdom does not implement scrolling; components call it to keep the current move visible.
Element.prototype.scrollIntoView = () => {};
