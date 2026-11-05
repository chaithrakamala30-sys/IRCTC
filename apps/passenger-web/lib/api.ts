export type ApiErrorDetail = {
  field?: string;
  message: string;
};

export type ApiErrorBody = {
  code: string;
  message: string;
  details: ApiErrorDetail[];
};

export type ApiEnvelope<T> = {
  success: boolean;
  data: T | null;
  error: ApiErrorBody | null;
  timestamp: string;
  requestId: string;
};

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly requestId?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

function getApiBaseUrl(): string {
  const configuredUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!configuredUrl) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL must be configured before making API requests.",
    );
  }

  let url: URL;
  try {
    url = new URL(configuredUrl);
  } catch {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must be a valid absolute URL.");
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must use HTTP or HTTPS.");
  }

  return url.toString().replace(/\/$/, "");
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<ApiEnvelope<T>> {
  const baseUrl = getApiBaseUrl();
  const normalizedPath = path.replace(/^\/+/, "");
  const response = await fetch(`${baseUrl}/${normalizedPath}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
  });

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiRequestError(
      "The server returned an invalid response.",
      response.status,
      "INVALID_SERVER_RESPONSE",
    );
  }

  if (!isApiEnvelope<T>(body)) {
    throw new ApiRequestError(
      "The server response did not match the API contract.",
      response.status,
      "INVALID_SERVER_RESPONSE",
    );
  }

  if (!response.ok || !body.success) {
    throw new ApiRequestError(
      body.error?.message ?? "The request could not be completed.",
      response.status,
      body.error?.code ?? "REQUEST_FAILED",
      body.requestId,
    );
  }

  return body;
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;

  return (
    typeof candidate.success === "boolean" &&
    typeof candidate.timestamp === "string" &&
    typeof candidate.requestId === "string" &&
    ("data" in candidate || "error" in candidate)
  );
}
