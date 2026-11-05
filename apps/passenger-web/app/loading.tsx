import { LoadingIndicator } from "@railconnect/ui";

export default function Loading() {
  return (
    <main aria-label="Page loading" className="page-message">
      <LoadingIndicator label="Loading the passenger portal" />
    </main>
  );
}
