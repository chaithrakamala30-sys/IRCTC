import { describe, expect, it } from "vitest";
import { formatLocalTime } from "../../apps/passenger-web/lib/time-format";

describe("formatLocalTime", () => {
  it("formats a route stop clock time without interpreting it as a timestamp", () => {
    expect(formatLocalTime("08:30:00", "Asia/Kolkata")).toMatch(/8:30/);
  });

  it("converts a full timestamp into the station timezone", () => {
    expect(formatLocalTime("2026-11-08T00:00:00Z", "Asia/Kolkata")).toMatch(/5:30/);
  });

  it("returns a placeholder for invalid times or timezones instead of throwing", () => {
    expect(formatLocalTime("not-a-time", "Asia/Kolkata")).toBe("—");
    expect(formatLocalTime("2026-11-08T00:00:00Z", "Invalid/Timezone")).toBe("—");
  });
});
