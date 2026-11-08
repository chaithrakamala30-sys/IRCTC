"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type Page<T> = { items: T[]; page: number; size: number; totalItems: number };
type Dashboard = {
  totalStations: number;
  activeStations: number;
  totalTrains: number;
  activeTrains: number;
  publishedRoutes: number;
  coachClasses: number;
  configuredCoaches: number;
};
type Station = {
  stationId: string;
  stationCode: string;
  stationName: string;
  alternateNames: string[];
  city: string;
  state: string;
  country: string;
  latitude: number | null;
  longitude: number | null;
  timezone: string;
  address: string | null;
  zoneId: string | null;
  divisionId: string | null;
  stationCategory: string | null;
  codeVerified: boolean;
  status: string;
  facilities: { facilityCode: string; details: Record<string, unknown>; active: boolean }[];
};
type StationVersion = {
  stationVersionId: string;
  stationId: string;
  versionNumber: number;
  versionType: string;
  snapshot: { stationCode?: string; stationName?: string; status?: string };
  changeReason: string | null;
  changedBy: string | null;
  createdAt: string;
};
type Lookup = { id: string; code: string; name: string; description: string | null; active: boolean };
type Train = {
  trainId: string;
  trainNumber: string;
  trainName: string;
  categoryId: string;
  categoryName: string;
  trainTypeId: string;
  trainTypeName: string;
  sourceStationId: string;
  sourceStationName: string;
  destinationStationId: string;
  destinationStationName: string;
  routeId: string | null;
  totalDistanceKm: number | null;
  description: string | null;
  status: string;
  effectiveFrom: string;
  effectiveTo: string | null;
};
type RouteStop = {
  stationId: string;
  stationName: string;
  stationCode: string;
  sequence: number;
  arrivalTime: string | null;
  departureTime: string | null;
  arrivalDayOffset: number;
  departureDayOffset: number;
  haltMinutes: number;
  distanceFromSourceKm: number;
  stopType: string;
};
type RouteStopDraft = {
  stationId: string;
  arrivalTime: string;
  departureTime: string;
  arrivalDayOffset: number;
  departureDayOffset: number;
  haltMinutes: number;
  distanceFromSourceKm: string;
};
type RouteVersion = {
  routeVersionId: string;
  routeId: string;
  routeName: string;
  versionNumber: number;
  sourceStationCode: string;
  destinationStationCode: string;
  totalDistanceKm: number;
  effectiveFrom: string;
  status: string;
  stops: RouteStop[];
};
type Schedule = {
  scheduleId: string;
  trainId: string;
  routeVersionId: string;
  versionNumber: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  timezone: string;
  status: string;
  operatingDays: number[];
};
type CoachConfiguration = {
  configurationId: string;
  configurationCode: string;
  configurationName: string;
  coachClassId: string;
  coachClassName: string;
  coachTypeId: string;
  coachTypeName: string;
  capacity: number;
  active: boolean;
  layoutVersion: number;
  layoutStatus: string | null;
  accommodations: {
    number: string;
    type: string;
    position: Record<string, unknown> | null;
    deckLevel: number | null;
    displayOrder: number;
    active: boolean;
  }[];
};
type Coach = {
  coachId: string;
  coachCode: string;
  configurationId: string;
  configurationName: string;
  className: string;
  coachTypeName: string;
  active: boolean;
};
type Composition = {
  currentVersionId: string;
  versionNumber: number;
  status: string;
  totalCapacity: number;
  coaches: { coachId: string; coachNumber: string; position: number; capacity: number }[];
};
type AuditEntry = {
  auditId: string;
  actorUserId: string | null;
  entityType: string;
  entityId: string;
  entityName: string | null;
  action: string;
  changeReason: string | null;
  occurredAt: string;
  correlationId: string;
};

const masterDataPath = "/api/v1/admin/master-data";
const weekdays = [
  { value: 1, label: "Mon" },
  { value: 2, label: "Tue" },
  { value: 3, label: "Wed" },
  { value: 4, label: "Thu" },
  { value: 5, label: "Fri" },
  { value: 6, label: "Sat" },
  { value: 7, label: "Sun" },
];

export function AdminMasterDataConsole() {
  const [activeSection, setActiveSection] = useState("overview");
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [stations, setStations] = useState<Station[]>([]);
  const [categories, setCategories] = useState<Lookup[]>([]);
  const [trainTypes, setTrainTypes] = useState<Lookup[]>([]);
  const [trains, setTrains] = useState<Train[]>([]);
  const [routes, setRoutes] = useState<RouteVersion[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [coachClasses, setCoachClasses] = useState<Lookup[]>([]);
  const [coachTypes, setCoachTypes] = useState<Lookup[]>([]);
  const [configurations, setConfigurations] = useState<CoachConfiguration[]>([]);
  const [coaches, setCoaches] = useState<Coach[]>([]);
  const [selectedCoachIds, setSelectedCoachIds] = useState<string[]>([]);
  const [composition, setComposition] = useState<Composition | null>(null);
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
  const [showAudit, setShowAudit] = useState(false);
  const [editingStation, setEditingStation] = useState<Station | null>(null);
  const [editingTrain, setEditingTrain] = useState<Train | null>(null);
  const [stationHistory, setStationHistory] = useState<{ station: Station; versions: StationVersion[] } | null>(null);
  const [editingRoute, setEditingRoute] = useState<RouteVersion | null>(null);
  const [editingSchedule, setEditingSchedule] = useState<Schedule | null>(null);
  const [editingConfiguration, setEditingConfiguration] = useState<CoachConfiguration | null>(null);
  const [routeStopDrafts, setRouteStopDrafts] = useState<RouteStopDraft[]>([
    { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "0" },
    { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "" },
  ]);
  const [compositionTrainId, setCompositionTrainId] = useState("");
  const [stationSearch, setStationSearch] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [
        dashboardResponse,
        stationResponse,
        categoryResponse,
        typeResponse,
        trainResponse,
        routeResponse,
        scheduleResponse,
        classResponse,
        coachTypeResponse,
        configurationResponse,
        coachResponse,
      ] = await Promise.all([
        apiFetch<Dashboard>(`${masterDataPath}/dashboard`),
        apiFetch<Page<Station>>(`${masterDataPath}/stations?page=0&size=100`),
        apiFetch<Lookup[]>(`${masterDataPath}/categories`),
        apiFetch<Lookup[]>(`${masterDataPath}/train-types`),
        apiFetch<Page<Train>>(`${masterDataPath}/trains?page=0&size=100`),
        apiFetch<Page<RouteVersion>>(`${masterDataPath}/routes?page=0&size=100`),
        apiFetch<Page<Schedule>>(`${masterDataPath}/schedules?page=0&size=100`),
        apiFetch<Lookup[]>(`${masterDataPath}/coach-classes`),
        apiFetch<Lookup[]>(`${masterDataPath}/coach-types`),
        apiFetch<CoachConfiguration[]>(`${masterDataPath}/coach-configurations`),
        apiFetch<Coach[]>(`${masterDataPath}/coaches`),
      ]);
      setDashboard(dashboardResponse.data);
      setStations(stationResponse.data?.items ?? []);
      setCategories(categoryResponse.data ?? []);
      setTrainTypes(typeResponse.data ?? []);
      setTrains(trainResponse.data?.items ?? []);
      setRoutes(routeResponse.data?.items ?? []);
      setSchedules(scheduleResponse.data?.items ?? []);
      setCoachClasses(classResponse.data ?? []);
      setCoachTypes(coachTypeResponse.data ?? []);
      setConfigurations(configurationResponse.data ?? []);
      setCoaches(coachResponse.data ?? []);
    } catch (caught) {
      setError(caught instanceof ApiRequestError
        ? caught.message
        : "Railway master data could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredStations = useMemo(() => {
    const query = stationSearch.trim().toLowerCase();
    return query
      ? stations.filter((station) =>
        `${station.stationCode} ${station.stationName} ${station.city} ${station.state}`
          .toLowerCase().includes(query))
      : stations;
  }, [stationSearch, stations]);

  function parseCoordinate(rawValue: string | null, axis: "latitude" | "longitude") {
    const text = rawValue?.trim() ?? "";
    if (text === "") {
      return null;
    }

    const formattedCoordinate = text.match(/^(\d+(?:\.\d+)?)\s*°\s*([NSEW])$/i);
    let numericValue: number;
    if (formattedCoordinate) {
      const direction = formattedCoordinate[2].toUpperCase();
      const validDirections = axis === "latitude" ? ["N", "S"] : ["E", "W"];
      if (!validDirections.includes(direction)) {
        throw new Error(`${axis === "latitude" ? "Latitude" : "Longitude"} must end with ${validDirections.join(" or ")}.`);
      }

      numericValue = Number(formattedCoordinate[1]);
      if (direction === "S" || direction === "W") {
        numericValue *= -1;
      }
    } else if (/^-?(?:\d+(?:\.\d+)?|\.\d+)$/.test(text)) {
      numericValue = Number(text);
    } else {
      throw new Error(`Enter ${axis} as decimal degrees with a direction, such as ${axis === "latitude" ? "12.8634° N" : "77.5946° E"}.`);
    }

    if (!Number.isFinite(numericValue)) {
      throw new Error(`${axis === "latitude" ? "Latitude" : "Longitude"} must be a valid decimal number.`);
    }

    if (axis === "latitude" && (numericValue < -90 || numericValue > 90)) {
      throw new Error("Latitude must be between -90 and 90.");
    }

    if (axis === "longitude" && (numericValue < -180 || numericValue > 180)) {
      throw new Error("Longitude must be between -180 and 180.");
    }

    return numericValue;
  }

  function formatCoordinate(value: number | null, axis: "latitude" | "longitude") {
    if (value === null) {
      return "";
    }

    const direction = axis === "latitude"
      ? value < 0 ? "S" : "N"
      : value < 0 ? "W" : "E";
    return `${Math.abs(value)}° ${direction}`;
  }

  async function submit(
    event: FormEvent<HTMLFormElement>,
    action: (form: FormData) => Promise<void>,
    successMessage: string,
  ) {
    event.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    const formElement = event.currentTarget;
    try {
      await action(new FormData(formElement));
      formElement.reset();
      setNotice(successMessage);
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : caught instanceof Error ? caught.message : "The master-data change could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  async function createStation(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      const stationId = editingStation?.stationId;
      const latitude = parseCoordinate(String(form.get("latitude") ?? ""), "latitude");
      const longitude = parseCoordinate(String(form.get("longitude") ?? ""), "longitude");
      await apiFetch<Station>(stationId
        ? `${masterDataPath}/stations/${stationId}`
        : `${masterDataPath}/stations`, {
        method: stationId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          stationCode: form.get("stationCode"),
          stationName: form.get("stationName"),
          alternateNames: String(form.get("alternateNames") ?? "")
            .split(",").map((name) => name.trim()).filter(Boolean),
          city: form.get("city"),
          state: form.get("state"),
          country: form.get("country"),
          timezone: form.get("timezone"),
          latitude,
          longitude,
          address: form.get("address") || null,
          zoneId: editingStation?.zoneId ?? null,
          divisionId: editingStation?.divisionId ?? null,
          stationCategory: form.get("stationCategory") || editingStation?.stationCategory || null,
          facilities: editingStation?.facilities ?? [],
        }),
      });
      setEditingStation(null);
    }, editingStation ? "Station updated." : "Station saved as a draft.");
  }

  async function createLookup(event: FormEvent<HTMLFormElement>, endpoint: string, label: string) {
    await submit(event, async (form) => {
      await apiFetch(`${masterDataPath}/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          code: form.get("code"),
          name: form.get("name"),
          description: form.get("description"),
        }),
      });
    }, `${label} created.`);
  }

  async function createTrain(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      const trainId = editingTrain?.trainId;
      const routeId = String(form.get("routeId") ?? "");
      const distance = String(form.get("totalDistanceKm") ?? "");
      await apiFetch<Train>(trainId
        ? `${masterDataPath}/trains/${trainId}`
        : `${masterDataPath}/trains`, {
        method: trainId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trainNumber: form.get("trainNumber"),
          trainName: form.get("trainName"),
          categoryId: form.get("categoryId"),
          trainTypeId: form.get("trainTypeId"),
          sourceStationId: form.get("sourceStationId"),
          destinationStationId: form.get("destinationStationId"),
          routeId: routeId || null,
          totalDistanceKm: distance ? Number(distance) : null,
          effectiveFrom: form.get("effectiveFrom"),
          effectiveTo: form.get("effectiveTo") || null,
          description: form.get("description") || null,
        }),
      });
      setEditingTrain(null);
    }, editingTrain ? "Train updated." : "Train created as a draft.");
  }

  async function createRoute(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      if (routeStopDrafts.length < 2) throw new Error("A route needs an origin and a terminal.");
      const sourceStationId = routeStopDrafts[0].stationId;
      const destinationStationId = routeStopDrafts[routeStopDrafts.length - 1].stationId;
      const distance = Number(form.get("totalDistanceKm"));
      await apiFetch<RouteVersion>(editingRoute
        ? `${masterDataPath}/routes/${editingRoute.routeId}/versions`
        : `${masterDataPath}/routes`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          routeName: form.get("routeName"),
          sourceStationId,
          destinationStationId,
          totalDistanceKm: distance,
          effectiveFrom: form.get("effectiveFrom"),
          changeReason: form.get("changeReason"),
          stops: routeStopDrafts.map((stop, index) => ({
            stationId: stop.stationId,
            sequence: index + 1,
            arrivalTime: index === 0 || !stop.arrivalTime ? null : stop.arrivalTime,
            departureTime: index === routeStopDrafts.length - 1 || !stop.departureTime ? null : stop.departureTime,
            arrivalDayOffset: index === 0 ? 0 : Number(stop.arrivalDayOffset),
            departureDayOffset: index === 0 ? 0 : Number(stop.departureDayOffset),
            haltMinutes: Number(stop.haltMinutes),
            distanceFromSourceKm: index === routeStopDrafts.length - 1 && !stop.distanceFromSourceKm
              ? distance
              : Number(stop.distanceFromSourceKm),
            stopType: index === 0 ? "ORIGIN" : index === routeStopDrafts.length - 1 ? "TERMINAL" : "REGULAR",
          })),
        }),
      });
      setEditingRoute(null);
      setRouteStopDrafts([
        { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "0" },
        { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "" },
      ]);
    }, editingRoute ? "New route version created as a draft." : "Route version created as a draft.");
  }

  function beginRouteVersion(route: RouteVersion | null) {
    setEditingRoute(route);
    setRouteStopDrafts(route
      ? route.stops.map((stop) => ({
        stationId: stop.stationId,
        arrivalTime: stop.arrivalTime ?? "",
        departureTime: stop.departureTime ?? "",
        arrivalDayOffset: stop.arrivalDayOffset,
        departureDayOffset: stop.departureDayOffset,
        haltMinutes: stop.haltMinutes,
        distanceFromSourceKm: String(stop.distanceFromSourceKm),
      }))
      : [
        { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "0" },
        { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "" },
      ]);
  }

  async function createSchedule(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      const routeVersionId = String(form.get("routeVersionId"));
      const route = routes.find((candidate) => candidate.routeVersionId === routeVersionId);
      const trainId = String(form.get("trainId"));
      if (!route || route.status !== "PUBLISHED") {
        throw new Error("Choose a published route version.");
      }
      const train = trains.find((candidate) => candidate.trainId === trainId);
      if (!train || train.routeId !== route.routeId) {
        throw new Error("Choose a train assigned to the selected route.");
      }
      await apiFetch<Schedule>(`${masterDataPath}/schedules`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trainId,
          routeVersionId,
          timezone: "Asia/Kolkata",
          effectiveFrom: form.get("effectiveFrom"),
          effectiveTo: form.get("effectiveTo") || null,
          operatingDays: form.getAll("operatingDays").map(Number),
          exceptions: form.get("exceptionDate")
            ? [{
              serviceDate: form.get("exceptionDate"),
              serviceStatus: form.get("exceptionStatus"),
              changeReason: form.get("exceptionReason") || null,
            }]
            : [],
        }),
      });
      setEditingSchedule(null);
    }, "Schedule version created as a draft.");
  }

  async function createCoachConfiguration(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      const configurationId = editingConfiguration?.configurationId;
      const capacity = editingConfiguration?.capacity ?? Number(form.get("capacity"));
      const columns = Math.max(1, Number(form.get("columns") ?? 4));
      const accommodationType = String(form.get("accommodationType"));
      const accommodations = Array.from({ length: capacity }, (_, index) => ({
        number: String(index + 1),
        type: accommodationType,
        position: { row: Math.floor(index / columns) + 1, column: (index % columns) + 1 },
        deckLevel: null,
        displayOrder: index + 1,
        active: true,
      }));
      await apiFetch<CoachConfiguration>(configurationId
        ? `${masterDataPath}/coach-configurations/${configurationId}/layouts`
        : `${masterDataPath}/coach-configurations`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(configurationId ? {
          changeReason: form.get("changeReason"),
          accommodations,
        } : {
          configurationCode: form.get("configurationCode"),
          configurationName: form.get("configurationName"),
          coachClassId: form.get("coachClassId"),
          coachTypeId: form.get("coachTypeId"),
          capacity,
          changeReason: form.get("changeReason"),
          accommodations,
        }),
      });
      setEditingConfiguration(null);
    }, editingConfiguration ? "New coach layout version created as a draft." : "Coach layout created as a draft. Publish it before creating coach units.");
  }

  async function createCoach(event: FormEvent<HTMLFormElement>) {
    await submit(event, async (form) => {
      await apiFetch<Coach>(`${masterDataPath}/coaches`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          coachCode: form.get("coachCode"),
          configurationId: form.get("configurationId"),
        }),
      });
    }, "Coach unit created.");
  }

  async function setStationStatus(station: Station) {
    setError("");
    setNotice("");
    try {
      const status = station.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
      await apiFetch(`${masterDataPath}/stations/${station.stationId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status, reason: "Updated from the admin portal" }),
      });
      setNotice(`Station ${station.stationCode} is now ${status.toLowerCase()}.`);
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Station status could not be changed.");
    }
  }

  async function verifyStationCode(station: Station) {
    setError("");
    setNotice("");
    try {
      await apiFetch(`${masterDataPath}/stations/${station.stationId}/verification`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ verified: true, reason: "Station code reviewed by an administrator" }),
      });
      setNotice(`Station code ${station.stationCode} was marked verified.`);
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Station code could not be verified.");
    }
  }

  async function setTrainStatus(train: Train) {
    setError("");
    setNotice("");
    try {
      const status = train.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
      await apiFetch(`${masterDataPath}/trains/${train.trainId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status, reason: "Updated from the admin portal" }),
      });
      setNotice(`Train ${train.trainNumber} is now ${status.toLowerCase()}.`);
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Train status could not be changed.");
    }
  }

  async function publish(path: string, label: string) {
    setError("");
    setNotice("");
    try {
      await apiFetch(path, { method: "POST" });
      setNotice(`${label} published.`);
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : `${label} could not be published.`);
    }
  }

  async function loadComposition(trainId: string) {
    setCompositionTrainId(trainId);
    setComposition(null);
    setSelectedCoachIds([]);
    if (!trainId) return;
    try {
      const response = await apiFetch<Composition>(`${masterDataPath}/trains/${trainId}/composition`);
      setComposition(response.data);
    } catch (caught) {
      if (!(caught instanceof ApiRequestError) || caught.status !== 404) {
        setError(caught instanceof ApiRequestError ? caught.message : "Composition could not be loaded.");
      }
    }
  }

  async function loadAudit() {
    setError("");
    try {
      const response = await apiFetch<Page<AuditEntry>>(`${masterDataPath}/audit?page=0&size=50`);
      setAuditEntries(response.data?.items ?? []);
      setShowAudit(true);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Master-data audit history could not be loaded.");
    }
  }

  async function loadStationHistory(station: Station) {
    setError("");
    try {
      const response = await apiFetch<StationVersion[]>(
        `${masterDataPath}/stations/${station.stationId}/versions`,
      );
      setStationHistory({ station, versions: response.data ?? [] });
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Station version history could not be loaded.");
    }
  }

  async function saveComposition(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    const selected = coaches.filter((coach) => selectedCoachIds.includes(coach.coachId));
    if (!compositionTrainId || selected.length === 0) {
      setError("Choose a train and at least one coach unit.");
      return;
    }
    setBusy(true);
    try {
      const response = await apiFetch<Composition>(`${masterDataPath}/trains/${compositionTrainId}/composition`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          coaches: selected.map((coach, index) => ({
            coachId: coach.coachId,
            coachNumber: coach.coachCode,
            position: index + 1,
          })),
        }),
      });
      setComposition(response.data);
      setNotice("Composition version saved as a draft.");
      await loadData();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Composition could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <section className="admin-panel" id="master-data" role="status">Loading railway master data…</section>;
  }

  return (
    <section className="master-data-console" id="master-data" aria-labelledby="master-data-title">
      <div className="admin-title-row master-data-title">
        <div>
          <p className="eyebrow">Railway operations</p>
          <h1 id="master-data-title">Master data</h1>
          <p>Manage stations, train definitions, published route/schedule versions, and coach compositions.</p>
        </div>
        <button className="button button--secondary" onClick={() => void loadData()} type="button">Refresh data</button>
      </div>
      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}
      <nav aria-label="Master data sections" className="master-data-tabs">
        {[
          ["overview", "Overview"],
          ["stations", "Stations"],
          ["trains", "Trains"],
          ["routes", "Routes"],
          ["schedules", "Schedules"],
          ["composition", "Coaches"],
        ].map(([key, label]) => (
          <button
            aria-current={activeSection === key ? "page" : undefined}
            className={activeSection === key ? "master-data-tab is-active" : "master-data-tab"}
            key={key}
            onClick={() => setActiveSection(key)}
            type="button"
          >
            {label}
          </button>
        ))}
      </nav>

      {activeSection === "overview" && (
        <div className="master-data-stats" aria-label="Master data summary">
          {[
            ["Stations", dashboard?.totalStations, `${dashboard?.activeStations ?? 0} active`],
            ["Trains", dashboard?.totalTrains, `${dashboard?.activeTrains ?? 0} active`],
            ["Published routes", dashboard?.publishedRoutes, "Versioned route records"],
            ["Coach classes", dashboard?.coachClasses, `${dashboard?.configuredCoaches ?? 0} coach units`],
          ].map(([label, count, detail]) => (
            <article className="admin-panel master-data-stat" key={String(label)}>
              <p>{label}</p><strong>{count ?? 0}</strong><span>{detail}</span>
            </article>
          ))}
          <div className="admin-panel master-data-note master-data-release-controls">
            <h2>Version and release controls</h2>
            <p>New routes, schedules, layouts, and compositions start as drafts. Publish is a separate permission-gated action. Booking and seat availability are not part of this phase.</p>
            <button className="button button--secondary" onClick={() => void loadAudit()} type="button">View master-data audit history</button>
          </div>
          {showAudit && (
            <section aria-label="Master-data audit history" className="admin-panel master-data-note">
              <div className="admin-panel-heading">
                <div><h2>Recent master-data changes</h2><p>Entity names are shown where available; hover to view the ID.</p></div>
                <button className="button button--secondary" onClick={() => setShowAudit(false)} type="button">Close</button>
              </div>
              <div className="admin-table-wrap"><table className="admin-table">
                <thead><tr><th>When</th><th>Entity</th><th>Action</th><th>Reason</th><th>Correlation</th></tr></thead>
                <tbody>{auditEntries.map((entry) => (
                  <tr key={entry.auditId}>
                    <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                    <td title={entry.entityId}>{entry.entityType} · {entry.entityName ?? entry.entityId}</td>
                    <td>{entry.action}</td>
                    <td>{entry.changeReason ?? "—"}</td>
                    <td>{entry.correlationId}</td>
                  </tr>
                ))}{auditEntries.length === 0 && <tr><td colSpan={5}>No audit entries.</td></tr>}</tbody>
              </table></div>
            </section>
          )}
        </div>
      )}

      {activeSection === "stations" && (
        <div className="master-data-columns">
          <section className="admin-panel">
            <h2>{editingStation ? `Edit ${editingStation.stationCode}` : "Add station"}</h2>
            <p>New station entries are saved as drafts. A changed station code must be verified before activation. Enter coordinates as decimal degrees with a direction, such as 12.8634° N.</p>
            <form className="master-data-form" key={editingStation?.stationId ?? "new-station"} onSubmit={(event) => void createStation(event)}>
              <label>Station code<input autoComplete="off" defaultValue={editingStation?.stationCode ?? ""} maxLength={20} name="stationCode" required /></label>
              <label>Station name<input defaultValue={editingStation?.stationName ?? ""} maxLength={160} name="stationName" required /></label>
              <label>Alternate names<input defaultValue={editingStation?.alternateNames.join(", ") ?? ""} maxLength={1000} name="alternateNames" /></label>
              <label>City<input defaultValue={editingStation?.city ?? ""} maxLength={120} name="city" required /></label>
              <label>State<input defaultValue={editingStation?.state ?? ""} maxLength={120} name="state" required /></label>
              <label>Country<input defaultValue={editingStation?.country ?? "India"} maxLength={80} name="country" required /></label>
              <label>Time zone<input defaultValue={editingStation?.timezone ?? "Asia/Kolkata"} maxLength={64} name="timezone" required /></label>
              <label>Latitude<input autoComplete="off" defaultValue={formatCoordinate(editingStation?.latitude ?? null, "latitude")} name="latitude" placeholder="12.8634° N" type="text" /></label>
              <label>Longitude<input autoComplete="off" defaultValue={formatCoordinate(editingStation?.longitude ?? null, "longitude")} name="longitude" placeholder="77.5946° E" type="text" /></label>
              <label>Station category<input defaultValue={editingStation?.stationCategory ?? ""} maxLength={40} name="stationCategory" /></label>
              <label className="master-data-form-wide">Address<input defaultValue={editingStation?.address ?? ""} maxLength={1000} name="address" /></label>
              <button className="button button--primary" disabled={busy} type="submit">{editingStation ? "Save station changes" : "Save station draft"}</button>
              {editingStation && <button className="button button--secondary" onClick={() => setEditingStation(null)} type="button">Cancel edit</button>}
            </form>
          </section>
          <section className="admin-panel">
            <div className="admin-panel-heading">
              <div><h2>Stations</h2><p>{stations.length} records loaded</p></div>
              <input aria-label="Search stations" onChange={(event) => setStationSearch(event.target.value)} placeholder="Search stations" value={stationSearch} />
            </div>
            <div className="admin-table-wrap"><table className="admin-table">
              <thead><tr><th>Station</th><th>Location</th><th>Code verification</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{filteredStations.map((station) => (
                <tr key={station.stationId}>
                  <td><strong>{station.stationCode}</strong><span>{station.stationName}</span></td>
                  <td>{station.city}, {station.state}</td>
                  <td>{station.codeVerified ? "Verified" : "Needs review"}</td>
                  <td><span className={`admin-status admin-status--${station.status.toLowerCase()}`}>{station.status}</span></td>
                  <td><div className="admin-row-actions">
                    <button className="button button--secondary" onClick={() => setEditingStation(station)} type="button">Edit</button>
                    <button className="button button--secondary" onClick={() => void loadStationHistory(station)} type="button">History</button>
                    {!station.codeVerified && station.status !== "RETIRED" && <button className="button button--secondary" onClick={() => void verifyStationCode(station)} type="button">Verify code</button>}
                    {station.status !== "RETIRED" && (station.status === "ACTIVE" || station.codeVerified) && <button className="button button--secondary" onClick={() => void setStationStatus(station)} type="button">{station.status === "ACTIVE" ? "Deactivate" : "Activate"}</button>}
                  </div></td>
                </tr>
              ))}{filteredStations.length === 0 && <tr><td colSpan={5}>No stations found.</td></tr>}</tbody>
            </table></div>
            {stationHistory && (
              <section aria-label={`Version history for ${stationHistory.station.stationCode}`} className="master-data-history">
                <div className="admin-panel-heading">
                  <div><h3>{stationHistory.station.stationCode} version history</h3><p>Snapshots are append-only and created with the audited change.</p></div>
                  <button className="button button--secondary" onClick={() => setStationHistory(null)} type="button">Close</button>
                </div>
                <div className="admin-table-wrap"><table className="admin-table">
                  <thead><tr><th>Version</th><th>Station</th><th>Change</th><th>Reason</th><th>When</th></tr></thead>
                  <tbody>{stationHistory.versions.map((version) => (
                    <tr key={version.stationVersionId}>
                      <td>{version.versionNumber}</td>
                      <td>{version.snapshot.stationCode} · {version.snapshot.stationName} ({version.snapshot.status})</td>
                      <td>{version.versionType}</td>
                      <td>{version.changeReason ?? "—"}</td>
                      <td>{new Date(version.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}</tbody>
                </table></div>
              </section>
            )}
          </section>
        </div>
      )}

      {activeSection === "trains" && (
        <div className="master-data-columns">
          <section className="admin-panel">
            <h2>Train categories and types</h2>
            <div className="master-data-mini-forms">
              <form className="master-data-form" onSubmit={(event) => void createLookup(event, "categories", "Train category")}>
                <h3>New category</h3>
                <label>Code<input maxLength={40} name="code" required /></label>
                <label>Name<input maxLength={100} name="name" required /></label>
                <label>Description<input maxLength={400} name="description" /></label>
                <button className="button button--secondary" disabled={busy} type="submit">Add category</button>
              </form>
              <form className="master-data-form" onSubmit={(event) => void createLookup(event, "train-types", "Train type")}>
                <h3>New train type</h3>
                <label>Code<input maxLength={40} name="code" required /></label>
                <label>Name<input maxLength={100} name="name" required /></label>
                <label>Description<input maxLength={400} name="description" /></label>
                <button className="button button--secondary" disabled={busy} type="submit">Add train type</button>
              </form>
            </div>
          </section>
          <section className="admin-panel">
            <h2>{editingTrain ? `Edit ${editingTrain.trainNumber}` : "Add train"}</h2>
            <form className="master-data-form" key={editingTrain?.trainId ?? "new-train"} onSubmit={(event) => void createTrain(event)}>
              <label>Train number<input defaultValue={editingTrain?.trainNumber ?? ""} maxLength={32} name="trainNumber" required /></label>
              <label>Train name<input defaultValue={editingTrain?.trainName ?? ""} maxLength={160} name="trainName" required /></label>
              <label>Category<select defaultValue={editingTrain?.categoryId ?? ""} name="categoryId" required><option value="">Select category</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
              <label>Train type<select defaultValue={editingTrain?.trainTypeId ?? ""} name="trainTypeId" required><option value="">Select type</option>{trainTypes.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
              <label>Source station<select defaultValue={editingTrain?.sourceStationId ?? ""} name="sourceStationId" required><option value="">Select station</option>{stations.filter((station) => station.status === "ACTIVE").map((item) => <option key={item.stationId} value={item.stationId}>{item.stationCode} — {item.stationName}</option>)}</select></label>
              <label>Destination station<select defaultValue={editingTrain?.destinationStationId ?? ""} name="destinationStationId" required><option value="">Select station</option>{stations.filter((station) => station.status === "ACTIVE").map((item) => <option key={item.stationId} value={item.stationId}>{item.stationCode} — {item.stationName}</option>)}</select></label>
              <label>Assigned route<select defaultValue={editingTrain?.routeId ?? ""} name="routeId"><option value="">No route assigned</option>{routes.filter((route, index, list) => route.status === "PUBLISHED" && list.findIndex((candidate) => candidate.routeId === route.routeId && candidate.status === "PUBLISHED") === index).map((route) => <option key={route.routeId} value={route.routeId}>{route.routeName} v{route.versionNumber}</option>)}</select></label>
              <label>Distance (km)<input defaultValue={editingTrain?.totalDistanceKm ?? ""} min="0.01" name="totalDistanceKm" step="0.01" type="number" /></label>
              <label>Effective from<input defaultValue={editingTrain?.effectiveFrom ?? ""} name="effectiveFrom" required type="date" /></label>
              <label>Effective to<input defaultValue={editingTrain?.effectiveTo ?? ""} name="effectiveTo" type="date" /></label>
              <label className="master-data-form-wide">Description<input defaultValue={editingTrain?.description ?? ""} maxLength={1000} name="description" /></label>
              <button className="button button--primary" disabled={busy} type="submit">{editingTrain ? "Save train changes" : "Save train draft"}</button>
              {editingTrain && <button className="button button--secondary" onClick={() => setEditingTrain(null)} type="button">Cancel edit</button>}
            </form>
          </section>
          <section className="admin-panel master-data-wide-panel">
            <h2>Train definitions</h2>
            <div className="admin-table-wrap"><table className="admin-table">
              <thead><tr><th>Train</th><th>Endpoints</th><th>Category / type</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{trains.map((train) => (
                <tr key={train.trainId}>
                  <td><strong>{train.trainNumber}</strong><span>{train.trainName}</span></td>
                  <td>{train.sourceStationName} → {train.destinationStationName}</td>
                  <td>{train.categoryName} / {train.trainTypeName}</td>
                  <td><span className={`admin-status admin-status--${train.status.toLowerCase()}`}>{train.status}</span></td>
                  <td><div className="admin-row-actions">
                    {train.status !== "RETIRED" && <button className="button button--secondary" onClick={() => setEditingTrain(train)} type="button">Edit</button>}
                    {train.status !== "RETIRED" && <button className="button button--secondary" onClick={() => void setTrainStatus(train)} type="button">{train.status === "ACTIVE" ? "Suspend" : "Activate"}</button>}
                  </div></td>
                </tr>
              ))}{trains.length === 0 && <tr><td colSpan={5}>No trains configured.</td></tr>}</tbody>
            </table></div>
          </section>
        </div>
      )}

      {activeSection === "routes" && (
        <div className="master-data-columns">
          <section className="admin-panel">
            <h2>{editingRoute ? `Create version from ${editingRoute.routeName}` : "Create route version"}</h2>
            <p>Define every ordered stop, day offset, halt, and distance. Publishing is separate and preserves prior versions.</p>
            <form className="master-data-form" key={editingRoute?.routeVersionId ?? "new-route"} onSubmit={(event) => void createRoute(event)}>
              <label>Route name<input defaultValue={editingRoute?.routeName ?? ""} maxLength={160} name="routeName" required /></label>
              <label>Total distance (km)<input defaultValue={editingRoute?.totalDistanceKm ?? ""} min="0.01" name="totalDistanceKm" required step="0.01" type="number" /></label>
              <label>Effective from<input defaultValue={editingRoute?.effectiveFrom ?? ""} name="effectiveFrom" required type="date" /></label>
              <label>Change reason<input maxLength={500} name="changeReason" /></label>
              <fieldset className="master-data-route-stops">
                <legend>Ordered route stops</legend>
                {routeStopDrafts.map((stop, index) => {
                  const isOrigin = index === 0;
                  const isTerminal = index === routeStopDrafts.length - 1;
                  return (
                    <div className="master-data-route-stop" key={`${editingRoute?.routeVersionId ?? "new"}-${index}`}>
                      <div className="master-data-route-stop-heading">
                        <strong>{isOrigin ? "Origin" : isTerminal ? "Terminal" : `Stop ${index + 1}`}</strong>
                        {!isOrigin && !isTerminal && <button className="button button--secondary" onClick={() => setRouteStopDrafts((current) => current.filter((_, stopIndex) => stopIndex !== index))} type="button">Remove stop</button>}
                      </div>
                      <label>Station<select onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, stationId: event.target.value } : item))} required value={stop.stationId}>
                        <option value="">Select station</option>{stations.filter((station) => station.status === "ACTIVE").map((station) => <option key={station.stationId} value={station.stationId}>{station.stationCode} — {station.stationName}</option>)}
                      </select></label>
                      {!isOrigin && <label>Arrival<input onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, arrivalTime: event.target.value } : item))} required type="time" value={stop.arrivalTime} /></label>}
                      {!isTerminal && <label>Departure<input onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, departureTime: event.target.value } : item))} required type="time" value={stop.departureTime} /></label>}
                      {!isOrigin && <label>Arrival day offset<input min="0" onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, arrivalDayOffset: Number(event.target.value) } : item))} type="number" value={stop.arrivalDayOffset} /></label>}
                      {!isOrigin && !isTerminal && <label>Departure day offset<input min="0" onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, departureDayOffset: Number(event.target.value) } : item))} type="number" value={stop.departureDayOffset} /></label>}
                      {!isOrigin && !isTerminal && <label>Halt (minutes)<input min="0" onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, haltMinutes: Number(event.target.value) } : item))} type="number" value={stop.haltMinutes} /></label>}
                      <label>Distance from origin (km)<input min="0" onChange={(event) => setRouteStopDrafts((current) => current.map((item, stopIndex) => stopIndex === index ? { ...item, distanceFromSourceKm: event.target.value } : item))} required={!isTerminal || Boolean(stop.distanceFromSourceKm)} step="0.01" type="number" value={stop.distanceFromSourceKm} /></label>
                    </div>
                  );
                })}
                <button className="button button--secondary" onClick={() => setRouteStopDrafts((current) => [
                  ...current.slice(0, -1),
                  { stationId: "", arrivalTime: "", departureTime: "", arrivalDayOffset: 0, departureDayOffset: 0, haltMinutes: 0, distanceFromSourceKm: "" },
                  current[current.length - 1],
                ])} type="button">Add intermediate stop</button>
              </fieldset>
              <button className="button button--primary" disabled={busy} type="submit">{editingRoute ? "Save new route version" : "Save route draft"}</button>
              {editingRoute && <button className="button button--secondary" onClick={() => beginRouteVersion(null)} type="button">Cancel version</button>}
            </form>
          </section>
          <section className="admin-panel">
            <h2>Route versions</h2>
            <div className="admin-table-wrap"><table className="admin-table">
              <thead><tr><th>Route / version</th><th>Endpoints</th><th>Distance</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{routes.map((route) => (
                <tr key={route.routeVersionId}>
                  <td><strong>{route.routeName}</strong><span>Version {route.versionNumber}</span></td>
                  <td>{route.sourceStationCode} → {route.destinationStationCode}</td>
                  <td>{route.totalDistanceKm} km</td>
                  <td><span className={`admin-status admin-status--${route.status.toLowerCase()}`}>{route.status}</span></td>
                  <td><div className="admin-row-actions">
                    {route.status === "DRAFT" && <button className="button button--secondary" onClick={() => void publish(`${masterDataPath}/route-versions/${route.routeVersionId}/publish`, "Route version")} type="button">Publish</button>}
                    {route.versionNumber === Math.max(...routes.filter((item) => item.routeId === route.routeId).map((item) => item.versionNumber)) && <button className="button button--secondary" onClick={() => beginRouteVersion(route)} type="button">New version</button>}
                  </div></td>
                </tr>
              ))}{routes.length === 0 && <tr><td colSpan={5}>No route versions configured.</td></tr>}</tbody>
            </table></div>
          </section>
        </div>
      )}

      {activeSection === "schedules" && (
        <div className="master-data-columns">
          <section className="admin-panel">
            <h2>{editingSchedule ? `New version for schedule ${editingSchedule.versionNumber}` : "Create schedule version"}</h2>
            <p>Schedules can only use a published route version assigned to the selected train.</p>
            <form className="master-data-form" key={editingSchedule?.scheduleId ?? "new-schedule"} onSubmit={(event) => void createSchedule(event)}>
              <label>Train<select defaultValue={editingSchedule?.trainId ?? ""} name="trainId" required><option value="">Select train</option>{trains.map((train) => <option key={train.trainId} value={train.trainId}>{train.trainNumber} — {train.trainName}</option>)}</select></label>
              <label>Published route version<select defaultValue={editingSchedule?.routeVersionId ?? ""} name="routeVersionId" required><option value="">Select route version</option>{routes.filter((route) => route.status === "PUBLISHED").map((route) => <option key={route.routeVersionId} value={route.routeVersionId}>{route.routeName} v{route.versionNumber}</option>)}</select></label>
              <label>Effective from<input defaultValue={editingSchedule?.effectiveFrom ?? ""} name="effectiveFrom" required type="date" /></label>
              <label>Effective to<input defaultValue={editingSchedule?.effectiveTo ?? ""} name="effectiveTo" type="date" /></label>
              <fieldset className="master-data-days">
                <legend>Operating days</legend>
                {weekdays.map((day) => <label key={day.value}><input defaultChecked={editingSchedule ? editingSchedule.operatingDays.includes(day.value) : true} name="operatingDays" type="checkbox" value={day.value} />{day.label}</label>)}
              </fieldset>
              <label>Exception date (optional)<input name="exceptionDate" type="date" /></label>
              <label>Exception status<select name="exceptionStatus"><option value="CANCEL">Cancel service</option><option value="RUN">Run service</option></select></label>
              <label className="master-data-form-wide">Exception reason<input maxLength={300} name="exceptionReason" /></label>
              <button className="button button--primary" disabled={busy} type="submit">Save schedule draft</button>
              {editingSchedule && <button className="button button--secondary" onClick={() => setEditingSchedule(null)} type="button">Cancel version</button>}
            </form>
          </section>
          <section className="admin-panel">
            <h2>Schedule versions</h2>
            <div className="admin-table-wrap"><table className="admin-table">
              <thead><tr><th>Train</th><th>Version</th><th>Effective from</th><th>Days</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{schedules.map((schedule) => {
                const train = trains.find((item) => item.trainId === schedule.trainId);
                return (
                  <tr key={schedule.scheduleId}>
                    <td>{train?.trainNumber ?? "Train unavailable"}</td>
                    <td>{schedule.versionNumber}</td>
                    <td>{schedule.effectiveFrom}</td>
                    <td>{schedule.operatingDays.map((day) => weekdays.find((item) => item.value === day)?.label).join(", ")}</td>
                    <td><span className={`admin-status admin-status--${schedule.status.toLowerCase()}`}>{schedule.status}</span></td>
                    <td><div className="admin-row-actions">
                      {schedule.status === "DRAFT" && <button className="button button--secondary" onClick={() => void publish(`${masterDataPath}/schedules/${schedule.scheduleId}/publish`, "Schedule")} type="button">Publish</button>}
                      {schedule.status === "PUBLISHED" && <button className="button button--secondary" onClick={() => setEditingSchedule(schedule)} type="button">New version</button>}
                    </div></td>
                  </tr>
                );
              })}{schedules.length === 0 && <tr><td colSpan={6}>No schedules configured.</td></tr>}</tbody>
            </table></div>
          </section>
        </div>
      )}

      {activeSection === "composition" && (
        <div className="master-data-columns">
          <section className="admin-panel">
            <h2>Coach classes and types</h2>
            <div className="master-data-mini-forms">
              <form className="master-data-form" onSubmit={(event) => void submit(event, async (form) => {
                await apiFetch(`${masterDataPath}/coach-classes`, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    classCode: form.get("classCode"), className: form.get("className"),
                    accommodationType: form.get("accommodationType"), fareCategory: form.get("fareCategory"),
                    description: form.get("description"), active: true,
                  }),
                });
              }, "Coach class created.")}>
                <h3>New class</h3>
                <label>Code<input maxLength={32} name="classCode" required /></label>
                <label>Name<input maxLength={100} name="className" required /></label>
                <label>Accommodation<select name="accommodationType"><option>SEATING</option><option>SLEEPING</option><option>MIXED</option><option>OTHER</option></select></label>
                <label>Fare category<input maxLength={40} name="fareCategory" /></label>
                <label>Description<input maxLength={400} name="description" /></label>
                <button className="button button--secondary" disabled={busy} type="submit">Add class</button>
              </form>
              <form className="master-data-form" onSubmit={(event) => void submit(event, async (form) => {
                await apiFetch(`${masterDataPath}/coach-types`, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({ typeCode: form.get("typeCode"), typeName: form.get("typeName"), description: form.get("description"), active: true }),
                });
              }, "Coach type created.")}>
                <h3>New coach type</h3>
                <label>Code<input maxLength={32} name="typeCode" required /></label>
                <label>Name<input maxLength={100} name="typeName" required /></label>
                <label>Description<input maxLength={400} name="description" /></label>
                <button className="button button--secondary" disabled={busy} type="submit">Add coach type</button>
              </form>
            </div>
          </section>
          <section className="admin-panel">
            <h2>{editingConfiguration ? `New layout version: ${editingConfiguration.configurationName}` : "Coach configuration and physical layout"}</h2>
            <p>Creates a numbered physical layout. Review the accommodation types and capacity before publishing.</p>
            <form className="master-data-form" key={editingConfiguration?.configurationId ?? "new-configuration"} onSubmit={(event) => void createCoachConfiguration(event)}>
              {!editingConfiguration && <>
                <label>Configuration code<input maxLength={40} name="configurationCode" required /></label>
                <label>Configuration name<input maxLength={120} name="configurationName" required /></label>
                <label>Coach class<select name="coachClassId" required><option value="">Select class</option>{coachClasses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
                <label>Coach type<select name="coachTypeId" required><option value="">Select type</option>{coachTypes.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
                <label>Capacity<input max="300" min="1" name="capacity" required type="number" /></label>
              </>}
              {editingConfiguration && <label>Capacity<input defaultValue={editingConfiguration.capacity} name="capacity" readOnly type="number" /></label>}
              <label>Columns per row<input defaultValue="4" max="20" min="1" name="columns" required type="number" /></label>
              <label>Default accommodation<select defaultValue={editingConfiguration?.accommodations[0]?.type ?? "SEAT"} name="accommodationType"><option>SEAT</option><option>LOWER_BERTH</option><option>MIDDLE_BERTH</option><option>UPPER_BERTH</option><option>SIDE_LOWER_BERTH</option><option>SIDE_UPPER_BERTH</option><option>CHAIR</option></select></label>
              <label className="master-data-form-wide">Change reason<input maxLength={500} name="changeReason" /></label>
              <button className="button button--primary" disabled={busy} type="submit">{editingConfiguration ? "Save new layout version" : "Create layout draft"}</button>
              {editingConfiguration && <button className="button button--secondary" onClick={() => setEditingConfiguration(null)} type="button">Cancel layout version</button>}
            </form>
            <div className="admin-table-wrap"><table className="admin-table">
              <thead><tr><th>Configuration</th><th>Class / type</th><th>Capacity</th><th>Layout</th><th>Action</th></tr></thead>
              <tbody>{configurations.map((configuration) => (
                <tr key={configuration.configurationId}>
                  <td><strong>{configuration.configurationCode}</strong><span>{configuration.configurationName}</span></td>
                  <td>{configuration.coachClassName} / {configuration.coachTypeName}</td>
                  <td>{configuration.capacity}</td>
                  <td>{configuration.layoutVersion ? `v${configuration.layoutVersion} ${configuration.layoutStatus}` : "Missing"}</td>
                  <td><div className="admin-row-actions">
                    {configuration.layoutStatus === "DRAFT" && <button className="button button--secondary" onClick={() => void publish(`${masterDataPath}/coach-configurations/${configuration.configurationId}/layouts/publish`, "Coach layout")} type="button">Publish layout</button>}
                    <button className="button button--secondary" onClick={() => setEditingConfiguration(configuration)} type="button">New layout version</button>
                  </div></td>
                </tr>
              ))}{configurations.length === 0 && <tr><td colSpan={5}>No coach configurations.</td></tr>}</tbody>
            </table></div>
          </section>
          <section className="admin-panel">
            <h2>Coach units</h2>
            <form className="master-data-form" onSubmit={(event) => void createCoach(event)}>
              <label>Coach code<input maxLength={40} name="coachCode" required /></label>
              <label>Published configuration<select name="configurationId" required><option value="">Select configuration</option>{configurations.filter((item) => item.active && item.layoutStatus === "PUBLISHED").map((item) => <option key={item.configurationId} value={item.configurationId}>{item.configurationCode} — {item.configurationName}</option>)}</select></label>
              <button className="button button--primary" disabled={busy} type="submit">Add coach unit</button>
            </form>
            <ul className="master-data-coach-list">{coaches.map((coach) => <li key={coach.coachId}><strong>{coach.coachCode}</strong><span>{coach.configurationName} · {coach.className}</span></li>)}</ul>
          </section>
          <section className="admin-panel">
            <h2>Train composition</h2>
            <label className="master-data-select">Train<select onChange={(event) => void loadComposition(event.target.value)} value={compositionTrainId}><option value="">Select train</option>{trains.map((train) => <option key={train.trainId} value={train.trainId}>{train.trainNumber} — {train.trainName}</option>)}</select></label>
            {compositionTrainId && (
              <>
                <p>Select coaches in the order they should appear. Saving creates a new draft version and preserves published history.</p>
                <form className="master-data-form" onSubmit={(event) => void saveComposition(event)}>
                  <fieldset className="master-data-coach-options">
                    <legend>Available coach units</legend>
                    {coaches.filter((coach) => coach.active).map((coach) => (
                      <label key={coach.coachId}>
                        <input
                          checked={selectedCoachIds.includes(coach.coachId)}
                          onChange={(event) => setSelectedCoachIds((current) => event.target.checked
                            ? [...current, coach.coachId]
                            : current.filter((id) => id !== coach.coachId))}
                          type="checkbox"
                        />
                        {coach.coachCode} · {coach.configurationName}
                      </label>
                    ))}
                    {coaches.length === 0 && <p>Create coach units above before building a composition.</p>}
                  </fieldset>
                  <button className="button button--primary" disabled={busy} type="submit">Save draft composition</button>
                </form>
                {composition && (
                  <div className="master-data-composition-summary">
                    <p>Version {composition.versionNumber} · {composition.status} · capacity {composition.totalCapacity}</p>
                    {composition.status === "DRAFT" && <button className="button button--secondary" onClick={() => void publish(`${masterDataPath}/trains/${compositionTrainId}/composition/versions/${composition.currentVersionId}/publish`, "Composition")} type="button">Publish composition</button>}
                    <ol>{composition.coaches.map((coach) => <li key={coach.coachId}>{coach.position}. {coach.coachNumber} — {coach.capacity} places</li>)}</ol>
                  </div>
                )}
              </>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
