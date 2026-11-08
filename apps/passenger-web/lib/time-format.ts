export function formatLocalTime(value: string | null, timezone: string): string {
  if (!value) {
    return "—";
  }

  const localClock = value.match(/^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d)(?:\.\d+)?)?$/);
  if (localClock) {
    const clockDate = new Date(Date.UTC(
      2000,
      0,
      1,
      Number(localClock[1]),
      Number(localClock[2]),
      Number(localClock[3] ?? 0),
    ));
    return new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      timeZone: "UTC",
    }).format(clockDate);
  }

  const timestamp = new Date(value);
  if (!Number.isFinite(timestamp.getTime())) {
    return "—";
  }

  try {
    return new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      timeZone: timezone,
    }).format(timestamp);
  } catch (error) {
    if (error instanceof RangeError) {
      return "—";
    }
    throw error;
  }
}
