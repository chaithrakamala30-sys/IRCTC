type LoadingIndicatorProps = {
  label?: string;
};

export function LoadingIndicator({
  label = "Loading",
}: LoadingIndicatorProps) {
  return (
    <div aria-live="polite" className="loading-indicator" role="status">
      <span aria-hidden="true" className="loading-spinner" />
      <span>{label}</span>
    </div>
  );
}
