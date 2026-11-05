import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, apiFetch } from "../../apps/passenger-web/lib/api";

describe("apiFetch", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("fails explicitly when the API base URL is not configured", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");

    await expect(apiFetch("api/v1/health")).rejects.toThrow(
      "NEXT_PUBLIC_API_BASE_URL must be configured",
    );
  });

  it("rejects malformed API base URLs", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "not a URL");

    await expect(apiFetch("api/v1/health")).rejects.toThrow(
      "NEXT_PUBLIC_API_BASE_URL must be a valid absolute URL",
    );
  });

  it("rejects non-http API base URL protocols", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "javascript:alert(1)");

    await expect(apiFetch("api/v1/health")).rejects.toThrow(
      "NEXT_PUBLIC_API_BASE_URL must use HTTP or HTTPS",
    );
  });

  it("returns a valid success envelope", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost:8080/");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            data: { status: "UP" },
            error: null,
            timestamp: "2026-11-05T00:00:00Z",
            requestId: "test-request",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const response = await apiFetch<{ status: string }>("/api/v1/health");

    expect(response.data).toEqual({ status: "UP" });
    expect(fetch).toHaveBeenCalledTimes(1);
    const requestInit = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(requestInit?.credentials).toBe("include");
    expect(new Headers(requestInit?.headers).get("Accept")).toBe(
      "application/json",
    );
  });

  it("surfaces API errors with status and request ID", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            data: null,
            error: {
              code: "DEPENDENCY_UNAVAILABLE",
              message: "Try again later",
              details: [],
            },
            timestamp: "2026-11-05T00:00:00Z",
            requestId: "request-42",
          }),
          { status: 503, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    try {
      await apiFetch("api/v1/health");
      throw new Error("Expected apiFetch to reject");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiRequestError);
      expect(error).toMatchObject({
        status: 503,
        code: "DEPENDENCY_UNAVAILABLE",
        requestId: "request-42",
      });
    }
  });

  it("does not convert malformed JSON responses into success", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response("<html>error</html>", {
          status: 502,
          headers: { "Content-Type": "text/html" },
        }),
      ),
    );

    await expect(apiFetch("api/v1/health")).rejects.toMatchObject({
      status: 502,
      code: "INVALID_SERVER_RESPONSE",
    });
  });
});
