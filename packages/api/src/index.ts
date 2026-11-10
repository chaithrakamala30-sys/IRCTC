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
    readonly details: ApiErrorDetail[] = [],
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

let csrfToken: string | null = null;
let csrfTokenRequest: Promise<string> | null = null;

function getApiBaseUrl(): string {
  const configuredUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  if (!configuredUrl) return "";

  let url: URL;
  try {
    url = new URL(configuredUrl);
  } catch {
    throw new ApiRequestError(
      "This website's API address is invalid. Please contact the site administrator.",
      0,
      "API_CONFIGURATION_INVALID",
    );
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new ApiRequestError(
      "This website's API address is invalid. Please contact the site administrator.",
      0,
      "API_CONFIGURATION_INVALID",
    );
  }

  return url.toString().replace(/\/$/, "");
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<ApiEnvelope<T>> {
  const baseUrl = getApiBaseUrl();
  const normalizedPath = path.replace(/^\/+/, "");
  const requestUrl = baseUrl
    ? `${baseUrl}/${normalizedPath}`
    : `/${normalizedPath}`;
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    if (!csrfToken) {
      csrfTokenRequest ??= fetchCsrfToken(baseUrl).catch((error: unknown) => {
        csrfTokenRequest = null;
        throw error;
      });
      csrfToken = await csrfTokenRequest;
    }
    headers.set("X-XSRF-TOKEN", csrfToken);
  }
  let response: Response;
  try {
    response = await fetch(requestUrl, {
      ...init,
      credentials: "include",
      headers,
    });
  } catch {
    throw new ApiRequestError(
      "RailConnect's server could not be reached. Please try again later.",
      0,
      "API_UNREACHABLE",
    );
  }

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
      body.error?.details ?? [],
    );
  }

  return body;
}

export async function apiDownload(path: string, filename: string): Promise<void> {
  const baseUrl = getApiBaseUrl();
  const normalizedPath = path.replace(/^\/+/, "");
  const requestUrl = baseUrl ? `${baseUrl}/${normalizedPath}` : `/${normalizedPath}`;
  let response: Response;
  try {
    response = await fetch(requestUrl, {
      credentials: "include",
      headers: { Accept: "application/pdf" },
    });
  } catch {
    throw new ApiRequestError(
      "RailConnect's server could not be reached. Please try again later.",
      0,
      "API_UNREACHABLE",
    );
  }
  if (!response.ok) {
    let body: unknown;
    try {
      body = await response.json();
    } catch {
      throw new ApiRequestError("The ticket could not be downloaded.", response.status, "DOWNLOAD_FAILED");
    }
    if (isApiEnvelope<unknown>(body)) {
      throw new ApiRequestError(
        body.error?.message ?? "The ticket could not be downloaded.",
        response.status,
        body.error?.code ?? "DOWNLOAD_FAILED",
        body.requestId,
        body.error?.details ?? [],
      );
    }
    throw new ApiRequestError("The ticket could not be downloaded.", response.status, "DOWNLOAD_FAILED");
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename.replace(/[^A-Za-z0-9._-]/g, "_");
  link.click();
  URL.revokeObjectURL(objectUrl);
}

async function fetchCsrfToken(baseUrl: string): Promise<string> {
  let response: Response;
  try {
    const csrfUrl = baseUrl
      ? `${baseUrl}/api/v1/auth/csrf`
      : "/api/v1/auth/csrf";
    response = await fetch(
      csrfUrl,
      {
      credentials: "include",
      headers: { Accept: "application/json" },
      },
    );
  } catch {
    throw new ApiRequestError(
      "RailConnect's server could not be reached. Please try again later.",
      0,
      "API_UNREACHABLE",
    );
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiRequestError(
      "The server could not prepare a secure request.",
      response.status,
      "CSRF_TOKEN_UNAVAILABLE",
    );
  }
  if (
    !response.ok ||
    !isApiEnvelope<{ token: string }>(body) ||
    !body.data ||
    typeof body.data.token !== "string"
  ) {
    throw new ApiRequestError(
      "The server could not prepare a secure request.",
      response.status,
      "CSRF_TOKEN_UNAVAILABLE",
    );
  }
  return body.data.token;
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
