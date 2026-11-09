/**
 * Catches rendering errors so one broken component shows a friendly message (with a way back)
 * instead of React unmounting the whole app and leaving a black screen. The error is logged to
 * the console for debugging.
 */
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Gambyte UI error:', error, info.componentStack);
  }

  componentDidUpdate(prev: { resetKey?: string }) {
    // Navigating to another page clears the error.
    if (this.state.error && prev.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="mx-auto flex min-h-[50vh] max-w-lg flex-col items-center justify-center text-center">
        <p className="eyebrow">Something broke</p>
        <h1 className="mt-3 text-3xl font-light tracking-tight">This page hit an error</h1>
        <p className="mt-3 font-mono text-xs text-noir-400">{this.state.error.message}</p>
        <div className="mt-6 flex gap-2">
          <button className="btn-primary" onClick={() => location.reload()}>Reload</button>
          <a className="btn-secondary" href="/">Back to the lobby</a>
        </div>
      </div>
    );
  }
}
