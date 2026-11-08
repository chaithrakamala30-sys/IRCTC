"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type FareVersion = {
  fareRuleId: string;
  ruleName: string;
  fareRuleVersionId: string;
  versionNumber: number;
  status: string;
  trainId: string | null;
  trainNumber: string | null;
  categoryId: string | null;
  categoryCode: string | null;
  coachClassId: string | null;
  coachClassCode: string | null;
  pricingModel: string;
  currency: string;
  baseAmount: string;
  perKmRate: string;
  priority: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  changeReason: string;
  createdBy: string;
  createdAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  publishedBy: string | null;
  publishedAt: string | null;
};

type Slab = {
  minimumDistanceKm: string;
  maximumDistanceKm: string;
  baseAmount: string;
  perKmRate: string;
};
type Component = {
  passengerCategoryId: string;
  componentCode: string;
  displayName: string;
  componentType: string;
  calculationMethod: string;
  amount: string;
  maximumAmount: string;
  minimumDistanceKm: string;
  calculationOrder: number;
  enabled: boolean;
};
type PassengerAdjustment = {
  passengerCategoryId: string;
  adjustmentType: string;
  adjustmentValue: string;
  maximumAmount: string;
};
type RuleConfig = {
  ruleName: string;
  changeReason: string;
  trainId: string | null;
  categoryId: string | null;
  coachClassId: string | null;
  pricingModel: string;
  baseAmount: string;
  perKmRate: string;
  priority: number;
  effectiveFrom: string;
  effectiveTo: string;
  distanceSlabs: Slab[];
  components: Component[];
  passengerAdjustments: PassengerAdjustment[];
};
type Lookup = { id: string; code: string; name: string };
type Options = {
  trains: { trainId: string; trainNumber: string; trainName: string; categoryId: string }[];
  trainCategories: Lookup[];
  coachClasses: Lookup[];
  stations: Lookup[];
};
type PassengerCategory = {
  passengerCategoryId: string;
  categoryCode: string;
  categoryName: string;
  minimumAge: number | null;
  maximumAge: number | null;
  active: boolean;
};
type FareItem = { code: string; label: string; type: string; amount: string };
type PassengerFare = {
  categoryCode: string;
  categoryName: string;
  items: FareItem[];
  totalAmount: string;
};
type Quote = {
  fareQuoteId: string | null;
  status: string;
  currency: string;
  distanceKm: string;
  trainNumber: string;
  trainName: string;
  originName: string;
  destinationName: string;
  journeyDate: string;
  coachClassCode: string;
  coachClassName: string;
  passengers: PassengerFare[];
  totalAmount: string;
  expiresAt: string | null;
  fareRuleVersionNumber: number;
  quoteOnlyNotice: string;
};
type AuditEvent = {
  auditEventId: string;
  actorName: string;
  eventType: string;
  details: string;
  occurredAt: string;
};

const emptyConfig = (): RuleConfig => ({
  ruleName: "",
  changeReason: "",
  trainId: null,
  categoryId: null,
  coachClassId: null,
  pricingModel: "BASE_PLUS_DISTANCE",
  baseAmount: "",
  perKmRate: "",
  priority: 0,
  effectiveFrom: new Date().toLocaleDateString("en-CA"),
  effectiveTo: "",
  distanceSlabs: [],
  components: [],
  passengerAdjustments: [],
});

function editorConfig(config: RuleConfig): RuleConfig {
  return {
    ...config,
    baseAmount: String(config.baseAmount),
    perKmRate: String(config.perKmRate),
    distanceSlabs: config.distanceSlabs.map((slab) => ({
      minimumDistanceKm: String(slab.minimumDistanceKm),
      maximumDistanceKm: slab.maximumDistanceKm == null ? "" : String(slab.maximumDistanceKm),
      baseAmount: String(slab.baseAmount),
      perKmRate: String(slab.perKmRate),
    })),
    components: config.components.map((component) => ({
      ...component,
      amount: String(component.amount),
      maximumAmount: component.maximumAmount == null ? "" : String(component.maximumAmount),
      minimumDistanceKm: String(component.minimumDistanceKm),
    })),
    passengerAdjustments: config.passengerAdjustments.map((adjustment) => ({
      ...adjustment,
      adjustmentValue: String(adjustment.adjustmentValue),
      maximumAmount: adjustment.maximumAmount == null ? "" : String(adjustment.maximumAmount),
    })),
  };
}

const errorMessage = (error: unknown) =>
  error instanceof ApiRequestError
    ? error.message
    : error instanceof Error
      ? error.message
      : "Fare administration could not be completed.";

const money = (currency: string, value: string) =>
  new Intl.NumberFormat(undefined, { style: "currency", currency }).format(Number(value));

export function AdminFareConsole({ roles }: { roles: string[] }) {
  const [rules, setRules] = useState<FareVersion[]>([]);
  const [options, setOptions] = useState<Options>({
    trains: [],
    trainCategories: [],
    coachClasses: [],
    stations: [],
  });
  const [categories, setCategories] = useState<PassengerCategory[]>([]);
  const [config, setConfig] = useState<RuleConfig>(emptyConfig);
  const [editingVersionId, setEditingVersionId] = useState<string | null>(null);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [auditVersion, setAuditVersion] = useState<string | null>(null);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [simulationVersionId, setSimulationVersionId] = useState("");
  const [simulation, setSimulation] = useState({
    trainId: "",
    fromStationId: "",
    toStationId: "",
    journeyDate: new Date().toLocaleDateString("en-CA"),
    coachClassCode: "",
    categoryCode: "ADULT",
    age: "",
  });
  const [quote, setQuote] = useState<Quote | null>(null);

  const canManage = roles.some((role) => ["SUPER_ADMIN", "RAILWAY_ADMIN", "FINANCE_ADMIN"].includes(role));
  const canApprove = roles.some((role) => ["SUPER_ADMIN", "FINANCE_ADMIN"].includes(role));
  const canPublish = canApprove;

  const loadConsole = useCallback(async () => {
    setError("");
    try {
      const [ruleResponse, optionResponse, categoryResponse] = await Promise.all([
        apiFetch<FareVersion[]>("/api/v1/admin/fares/rules"),
        apiFetch<Options>("/api/v1/admin/fares/options"),
        apiFetch<PassengerCategory[]>("/api/v1/admin/fares/passenger-categories"),
      ]);
      const nextRules = ruleResponse.data ?? [];
      setRules(nextRules);
      setOptions(optionResponse.data ?? {
        trains: [],
        trainCategories: [],
        coachClasses: [],
        stations: [],
      });
      setCategories(categoryResponse.data ?? []);
      if (nextRules.length) {
        setSimulationVersionId((current) => current || nextRules[0].fareRuleVersionId);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }, []);

  useEffect(() => {
    void loadConsole();
  }, [loadConsole]);

  function resetEditor() {
    setConfig(emptyConfig());
    setEditingVersionId(null);
    setSelectedRuleId(null);
    setError("");
    setNotice("");
  }

  async function editVersion(versionId: string, asNewVersion: boolean) {
    setError("");
    setNotice("");
    try {
      const response = await apiFetch<{
        version: FareVersion;
        configuration: RuleConfig;
      }>(`/api/v1/admin/fares/versions/${encodeURIComponent(versionId)}`);
      if (!response.data) return;
      const nextConfig = editorConfig(response.data.configuration);
      setConfig(nextConfig);
      setSelectedRuleId(response.data.version.fareRuleId);
      setEditingVersionId(asNewVersion ? null : versionId);
      if (asNewVersion) {
        setConfig({
          ...nextConfig,
          changeReason: `New version based on version ${response.data.version.versionNumber}`,
        });
        setNotice("A new draft will be created from the selected version.");
      }
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function saveRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    const body = {
      ...config,
      baseAmount: config.pricingModel === "DISTANCE_SLABS" || config.pricingModel === "DISTANCE_RATE"
        ? "0"
        : config.baseAmount || "0",
      perKmRate: config.pricingModel === "DISTANCE_SLABS" ? "0" : config.perKmRate || "0",
      effectiveTo: config.effectiveTo || null,
      distanceSlabs: config.distanceSlabs.map((slab) => ({
        ...slab,
        maximumDistanceKm: slab.maximumDistanceKm || null,
      })),
      components: config.components.map((component) => ({
        ...component,
        passengerCategoryId: component.passengerCategoryId || null,
        maximumAmount: component.maximumAmount || null,
      })),
      passengerAdjustments: config.passengerAdjustments.map((adjustment) => ({
        ...adjustment,
        maximumAmount: adjustment.maximumAmount || null,
      })),
    };
    try {
      const path = editingVersionId
        ? `/api/v1/admin/fares/versions/${encodeURIComponent(editingVersionId)}`
        : selectedRuleId
          ? `/api/v1/admin/fares/rules/${encodeURIComponent(selectedRuleId)}/versions`
          : "/api/v1/admin/fares/rules";
      await apiFetch(path, {
        method: editingVersionId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      resetEditor();
      setNotice("Draft fare-rule version saved. It is not active until a different authorized user approves and publishes it.");
      await loadConsole();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function transition(versionId: string, action: "submit" | "approve" | "publish" | "retire") {
    setError("");
    setNotice("");
    try {
      await apiFetch(`/api/v1/admin/fares/versions/${encodeURIComponent(versionId)}/${action}`, {
        method: "POST",
      });
      setNotice(`Fare-rule version ${action} action completed.`);
      await loadConsole();
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function viewAudit(versionId: string) {
    setError("");
    try {
      const response = await apiFetch<AuditEvent[]>(
        `/api/v1/admin/fares/versions/${encodeURIComponent(versionId)}/audit`,
      );
      setAudit(response.data ?? []);
      setAuditVersion(versionId);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function addPassengerCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError("");
    try {
      await apiFetch("/api/v1/admin/fares/passenger-categories", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryCode: form.get("categoryCode"),
          categoryName: form.get("categoryName"),
          minimumAge: form.get("minimumAge") ? Number(form.get("minimumAge")) : null,
          maximumAge: form.get("maximumAge") ? Number(form.get("maximumAge")) : null,
        }),
      });
      formElement.reset();
      setNotice("Passenger category created. No concession is applied unless a fare rule explicitly configures one.");
      await loadConsole();
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function simulate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setQuote(null);
    try {
      const response = await apiFetch<Quote>("/api/v1/admin/fares/simulate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fareRuleVersionId: simulationVersionId,
          journey: {
            trainId: simulation.trainId,
            fromStationId: simulation.fromStationId,
            toStationId: simulation.toStationId,
            journeyDate: simulation.journeyDate,
            coachClassCode: simulation.coachClassCode,
            passengers: [{
              categoryCode: simulation.categoryCode,
              age: simulation.age === "" ? null : Number(simulation.age),
            }],
          },
        }),
      });
      setQuote(response.data ?? null);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  function updateConfig<K extends keyof RuleConfig>(key: K, value: RuleConfig[K]) {
    setConfig((current) => ({ ...current, [key]: value }));
  }

  if (!canManage) return null;

  const activeCategories = categories.filter((category) => category.active);
  return (
    <section aria-labelledby="fare-console-title" className="fare-console" id="fare-management">
      <div className="admin-title-row fare-console-title">
        <div>
          <p className="eyebrow">Phase 5 · Fare engine</p>
          <h1 id="fare-console-title">Fare rules and quote simulator</h1>
          <p>All prices come from approved rules. No default fare, tax, or discount is assumed.</p>
        </div>
        <button className="button button--secondary" onClick={resetEditor} type="button">New fare rule</button>
      </div>
      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}

      <section aria-labelledby="fare-rule-editor-title" className="admin-panel">
        <h2 id="fare-rule-editor-title">{editingVersionId ? "Edit draft version" : selectedRuleId ? "Create rule version" : "Create fare rule"}</h2>
        <p>Submitted or approved versions cannot be edited. Creating a version does not publish or activate it.</p>
        <form className="master-data-form fare-form" onSubmit={(event) => void saveRule(event)}>
          <label>Rule name<input maxLength={160} onChange={(event) => updateConfig("ruleName", event.target.value)} required value={config.ruleName} /></label>
          <label>Change reason<input maxLength={500} onChange={(event) => updateConfig("changeReason", event.target.value)} required value={config.changeReason} /></label>
          <label>Train scope (optional)<select onChange={(event) => updateConfig("trainId", event.target.value || null)} value={config.trainId ?? ""}>
            <option value="">All trains</option>{options.trains.map((train) => <option key={train.trainId} value={train.trainId}>{train.trainNumber} — {train.trainName}</option>)}
          </select></label>
          <label>Train category scope (optional)<select onChange={(event) => updateConfig("categoryId", event.target.value || null)} value={config.categoryId ?? ""}>
            <option value="">All categories</option>{options.trainCategories.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}
          </select></label>
          <label>Coach class scope (optional)<select onChange={(event) => updateConfig("coachClassId", event.target.value || null)} value={config.coachClassId ?? ""}>
            <option value="">All classes</option>{options.coachClasses.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}
          </select></label>
          <label>Pricing model<select onChange={(event) => updateConfig("pricingModel", event.target.value)} value={config.pricingModel}>
            <option value="DISTANCE_RATE">Distance rate</option>
            <option value="BASE_PLUS_DISTANCE">Base plus distance</option>
            <option value="DISTANCE_SLABS">Distance slabs</option>
          </select></label>
          {config.pricingModel !== "DISTANCE_SLABS" && (
            <>
              {config.pricingModel === "BASE_PLUS_DISTANCE" && <label>Base amount (INR)<input min="0" onChange={(event) => updateConfig("baseAmount", event.target.value)} required step="0.01" type="number" value={config.baseAmount} /></label>}
              <label>Rate per km (INR)<input min="0" onChange={(event) => updateConfig("perKmRate", event.target.value)} required step="0.0001" type="number" value={config.perKmRate} /></label>
            </>
          )}
          {config.pricingModel === "DISTANCE_SLABS" && (
            <fieldset className="fare-child-editor">
              <legend>Distance slabs (lower bound inclusive, upper bound exclusive)</legend>
              {config.distanceSlabs.map((slab, index) => (
                <div className="fare-child-row" key={`slab-${index}`}>
                  <label>From km<input min="0" onChange={(event) => updateConfig("distanceSlabs", config.distanceSlabs.map((item, row) => row === index ? { ...item, minimumDistanceKm: event.target.value } : item))} required step="0.01" type="number" value={slab.minimumDistanceKm} /></label>
                  <label>To km (blank = no upper limit)<input min="0.01" onChange={(event) => updateConfig("distanceSlabs", config.distanceSlabs.map((item, row) => row === index ? { ...item, maximumDistanceKm: event.target.value } : item))} step="0.01" type="number" value={slab.maximumDistanceKm} /></label>
                  <label>Base amount (INR)<input min="0" onChange={(event) => updateConfig("distanceSlabs", config.distanceSlabs.map((item, row) => row === index ? { ...item, baseAmount: event.target.value } : item))} required step="0.01" type="number" value={slab.baseAmount} /></label>
                  <label>Rate per km (INR)<input min="0" onChange={(event) => updateConfig("distanceSlabs", config.distanceSlabs.map((item, row) => row === index ? { ...item, perKmRate: event.target.value } : item))} required step="0.0001" type="number" value={slab.perKmRate} /></label>
                  <button className="button button--secondary" onClick={() => updateConfig("distanceSlabs", config.distanceSlabs.filter((_, row) => row !== index))} type="button">Remove slab</button>
                </div>
              ))}
              <button className="button button--secondary" onClick={() => updateConfig("distanceSlabs", [...config.distanceSlabs, { minimumDistanceKm: "", maximumDistanceKm: "", baseAmount: "", perKmRate: "" }])} type="button">Add distance slab</button>
            </fieldset>
          )}
          <label>Priority (higher wins)<input max="999" min="0" onChange={(event) => updateConfig("priority", Number(event.target.value))} required type="number" value={config.priority} /></label>
          <label>Effective from<input onChange={(event) => updateConfig("effectiveFrom", event.target.value)} required type="date" value={config.effectiveFrom} /></label>
          <label>Effective to (optional)<input onChange={(event) => updateConfig("effectiveTo", event.target.value)} type="date" value={config.effectiveTo} /></label>

          <fieldset className="fare-child-editor">
            <legend>Configured charges, taxes, and discounts</legend>
            <p>Items are applied in calculation order. Percentage-of-subtotal uses the running passenger subtotal.</p>
            {config.components.map((component, index) => (
              <div className="fare-child-row" key={`component-${index}`}>
                <label>Code<input maxLength={40} onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, componentCode: event.target.value.toUpperCase() } : item))} required value={component.componentCode} /></label>
                <label>Display name<input maxLength={100} onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, displayName: event.target.value } : item))} required value={component.displayName} /></label>
                <label>Type<select onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, componentType: event.target.value } : item))} value={component.componentType}>
                  <option value="CHARGE">Charge</option><option value="TAX">Tax</option><option value="DISCOUNT">Discount</option>
                </select></label>
                <label>Calculation<select onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, calculationMethod: event.target.value } : item))} value={component.calculationMethod}>
                  <option value="FIXED">Fixed INR</option><option value="PER_KM">Per km</option><option value="PERCENT_OF_BASE">% of base fare</option><option value="PERCENT_OF_SUBTOTAL">% of running subtotal</option>
                </select></label>
                <label>Amount<input min="0" onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, amount: event.target.value } : item))} required step="0.0001" type="number" value={component.amount} /></label>
                <label>Max amount (optional)<input min="0" onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, maximumAmount: event.target.value } : item))} step="0.01" type="number" value={component.maximumAmount} /></label>
                <label>Only category (optional)<select onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, passengerCategoryId: event.target.value } : item))} value={component.passengerCategoryId}>
                  <option value="">Every category</option>{activeCategories.map((category) => <option key={category.passengerCategoryId} value={category.passengerCategoryId}>{category.categoryName}</option>)}
                </select></label>
                <label>Minimum distance km<input min="0" onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, minimumDistanceKm: event.target.value } : item))} required step="0.01" type="number" value={component.minimumDistanceKm} /></label>
                <label>Calculation order<input max="999" min="0" onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, calculationOrder: Number(event.target.value) } : item))} required type="number" value={component.calculationOrder} /></label>
                <label className="fare-checkbox"><input checked={component.enabled} onChange={(event) => updateConfig("components", config.components.map((item, row) => row === index ? { ...item, enabled: event.target.checked } : item))} type="checkbox" /> Enabled</label>
                <button className="button button--secondary" onClick={() => updateConfig("components", config.components.filter((_, row) => row !== index))} type="button">Remove item</button>
              </div>
            ))}
            <button className="button button--secondary" onClick={() => updateConfig("components", [...config.components, {
              passengerCategoryId: "", componentCode: "", displayName: "", componentType: "CHARGE",
              calculationMethod: "FIXED", amount: "", maximumAmount: "", minimumDistanceKm: "0",
              calculationOrder: config.components.length, enabled: true,
            }])} type="button">Add configured item</button>
          </fieldset>

          <fieldset className="fare-child-editor">
            <legend>Passenger-category adjustments</legend>
            <p>Categories have no concession unless an adjustment is explicitly configured here.</p>
            {config.passengerAdjustments.map((adjustment, index) => (
              <div className="fare-child-row" key={`adjustment-${index}`}>
                <label>Passenger category<select onChange={(event) => updateConfig("passengerAdjustments", config.passengerAdjustments.map((item, row) => row === index ? { ...item, passengerCategoryId: event.target.value } : item))} required value={adjustment.passengerCategoryId}>
                  <option value="">Choose category</option>{activeCategories.map((category) => <option key={category.passengerCategoryId} value={category.passengerCategoryId}>{category.categoryName}</option>)}
                </select></label>
                <label>Adjustment type<select onChange={(event) => updateConfig("passengerAdjustments", config.passengerAdjustments.map((item, row) => row === index ? { ...item, adjustmentType: event.target.value } : item))} value={adjustment.adjustmentType}>
                  <option value="DISCOUNT_PERCENT">Discount percent</option><option value="DISCOUNT_FIXED">Fixed discount</option>
                  <option value="CHARGE_PERCENT">Charge percent</option><option value="CHARGE_FIXED">Fixed charge</option>
                </select></label>
                <label>Value<input min="0" onChange={(event) => updateConfig("passengerAdjustments", config.passengerAdjustments.map((item, row) => row === index ? { ...item, adjustmentValue: event.target.value } : item))} required step="0.0001" type="number" value={adjustment.adjustmentValue} /></label>
                <label>Maximum amount (optional)<input min="0" onChange={(event) => updateConfig("passengerAdjustments", config.passengerAdjustments.map((item, row) => row === index ? { ...item, maximumAmount: event.target.value } : item))} step="0.01" type="number" value={adjustment.maximumAmount} /></label>
                <button className="button button--secondary" onClick={() => updateConfig("passengerAdjustments", config.passengerAdjustments.filter((_, row) => row !== index))} type="button">Remove adjustment</button>
              </div>
            ))}
            <button className="button button--secondary" onClick={() => updateConfig("passengerAdjustments", [...config.passengerAdjustments, {
              passengerCategoryId: "", adjustmentType: "DISCOUNT_PERCENT", adjustmentValue: "", maximumAmount: "",
            }])} type="button">Add passenger adjustment</button>
          </fieldset>
          <div className="fare-form-actions">
            <button className="button button--primary" disabled={busy} type="submit">{busy ? "Saving…" : editingVersionId ? "Save draft changes" : selectedRuleId ? "Create new version" : "Create draft rule"}</button>
            <button className="button button--secondary" onClick={resetEditor} type="button">Clear form</button>
          </div>
        </form>
      </section>

      <section aria-labelledby="fare-rules-title" className="admin-panel">
        <div className="admin-panel-heading">
          <div><h2 id="fare-rules-title">Fare-rule versions</h2><p>Only published versions are available to passenger quotes.</p></div>
          <button className="button button--secondary" onClick={() => void loadConsole()} type="button">Refresh list</button>
        </div>
        <div className="admin-table-wrap">
          <table className="admin-table fare-table">
            <thead><tr><th>Rule</th><th>Scope</th><th>Policy</th><th>Effective</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{rules.map((version) => (
              <tr key={version.fareRuleVersionId}>
                <td><strong>{version.ruleName} · v{version.versionNumber}</strong><span>By {version.createdBy}</span></td>
                <td>{version.trainNumber ?? "Any train"} · {version.categoryCode ?? "Any category"} · {version.coachClassCode ?? "Any class"}</td>
                <td>{version.pricingModel.replaceAll("_", " ")} · priority {version.priority}</td>
                <td>{version.effectiveFrom}{version.effectiveTo ? ` to ${version.effectiveTo}` : " onward"}</td>
                <td><span className={`admin-status admin-status--${version.status.toLowerCase()}`}>{version.status}</span></td>
                <td><div className="admin-row-actions fare-actions">
                  {version.status === "DRAFT" && canManage && <>
                    <button className="button button--secondary" onClick={() => void editVersion(version.fareRuleVersionId, false)} type="button">Edit</button>
                    <button className="button button--secondary" onClick={() => void transition(version.fareRuleVersionId, "submit")} type="button">Submit</button>
                  </>}
                  {version.status === "SUBMITTED" && canApprove && <button className="button button--secondary" onClick={() => void transition(version.fareRuleVersionId, "approve")} type="button">Approve</button>}
                  {version.status === "APPROVED" && canPublish && <button className="button button--secondary" onClick={() => void transition(version.fareRuleVersionId, "publish")} type="button">Publish</button>}
                  {version.status === "PUBLISHED" && canPublish && <button className="button button--secondary" onClick={() => void transition(version.fareRuleVersionId, "retire")} type="button">Retire</button>}
                  <button className="button button--secondary" onClick={() => void editVersion(version.fareRuleVersionId, true)} type="button">New version</button>
                  <button className="button button--secondary" onClick={() => void viewAudit(version.fareRuleVersionId)} type="button">Audit</button>
                </div></td>
              </tr>
            ))}{rules.length === 0 && <tr><td colSpan={6}>No fare rules configured. Quotes remain unavailable until an approved rule is published.</td></tr>}</tbody>
          </table>
        </div>
        {auditVersion && <section aria-label="Fare rule audit history" className="fare-audit">
          <div className="admin-panel-heading"><h3>Version audit history</h3><button className="button button--secondary" onClick={() => setAuditVersion(null)} type="button">Close</button></div>
          <ul>{audit.map((entry) => <li key={entry.auditEventId}><strong>{entry.eventType}</strong> · {entry.actorName} · {new Date(entry.occurredAt).toLocaleString()}<code>{entry.details}</code></li>)}</ul>
        </section>}
      </section>

      <section aria-labelledby="fare-simulator-title" className="admin-panel">
        <h2 id="fare-simulator-title">Journey fare simulator</h2>
        <p>Simulation reads a draft or published version but never creates a passenger quote or changes rule state.</p>
        <form className="master-data-form" onSubmit={(event) => void simulate(event)}>
          <label>Rule version<select onChange={(event) => setSimulationVersionId(event.target.value)} required value={simulationVersionId}>
            <option value="">Choose version</option>{rules.map((item) => <option key={item.fareRuleVersionId} value={item.fareRuleVersionId}>{item.ruleName} v{item.versionNumber} · {item.status}</option>)}
          </select></label>
          <label>Train<select onChange={(event) => setSimulation({ ...simulation, trainId: event.target.value })} required value={simulation.trainId}>
            <option value="">Choose train</option>{options.trains.map((item) => <option key={item.trainId} value={item.trainId}>{item.trainNumber} — {item.trainName}</option>)}
          </select></label>
          <label>Origin<select onChange={(event) => setSimulation({ ...simulation, fromStationId: event.target.value })} required value={simulation.fromStationId}>
            <option value="">Choose origin</option>{options.stations.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}
          </select></label>
          <label>Destination<select onChange={(event) => setSimulation({ ...simulation, toStationId: event.target.value })} required value={simulation.toStationId}>
            <option value="">Choose destination</option>{options.stations.filter((station) => station.id !== simulation.fromStationId).map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}
          </select></label>
          <label>Journey date<input onChange={(event) => setSimulation({ ...simulation, journeyDate: event.target.value })} required type="date" value={simulation.journeyDate} /></label>
          <label>Coach class<select onChange={(event) => setSimulation({ ...simulation, coachClassCode: event.target.value })} required value={simulation.coachClassCode}>
            <option value="">Choose class</option>{options.coachClasses.map((item) => <option key={item.id} value={item.code}>{item.code} — {item.name}</option>)}
          </select></label>
          <label>Passenger category<select onChange={(event) => setSimulation({ ...simulation, categoryCode: event.target.value })} required value={simulation.categoryCode}>
            {activeCategories.map((item) => <option key={item.passengerCategoryId} value={item.categoryCode}>{item.categoryName}</option>)}
          </select></label>
          {(() => {
            const category = activeCategories.find((item) => item.categoryCode === simulation.categoryCode);
            return category && (category.minimumAge !== null || category.maximumAge !== null) ? (
              <label>Passenger age<input max="120" min="0" onChange={(event) => setSimulation({ ...simulation, age: event.target.value })} required type="number" value={simulation.age} /></label>
            ) : null;
          })()}
          <button className="button button--primary" type="submit">Calculate simulation</button>
        </form>
        {quote && <div className="fare-quote-card" role="status">
          <h3>{quote.trainNumber} · {quote.originName} to {quote.destinationName}</h3>
          <p>{quote.journeyDate} · {quote.coachClassCode} · {quote.distanceKm} km · version {quote.fareRuleVersionNumber}</p>
          {quote.passengers.map((passenger, index) => <div key={`${passenger.categoryCode}-${index}`}>
            <strong>{passenger.categoryName}</strong>
            <ul>{passenger.items.map((item, itemIndex) => <li key={`${item.code}-${itemIndex}`}><span>{item.label}</span><strong>{money(quote.currency, item.amount)}</strong></li>)}</ul>
            <p>Passenger total <strong>{money(quote.currency, passenger.totalAmount)}</strong></p>
          </div>)}
          <p className="fare-total">Simulated total <strong>{money(quote.currency, quote.totalAmount)}</strong></p>
          <p>{quote.quoteOnlyNotice}</p>
        </div>}
      </section>

      <section aria-labelledby="passenger-category-title" className="admin-panel">
        <h2 id="passenger-category-title">Passenger categories</h2>
        <p>Age boundaries and concessions are configurable; no discount is implied by a category name.</p>
        <div className="fare-category-list">{categories.map((category) => <span key={category.passengerCategoryId}>
          <strong>{category.categoryCode}</strong> · {category.categoryName} · {category.active ? "Active" : "Inactive"}
          {category.minimumAge !== null || category.maximumAge !== null
            ? ` · ages ${category.minimumAge ?? 0}–${category.maximumAge ?? "open"}`
            : " · no age restriction"}
        </span>)}</div>
        <form className="master-data-form fare-category-form" onSubmit={(event) => void addPassengerCategory(event)}>
          <label>Category code<input maxLength={32} name="categoryCode" pattern="[A-Z][A-Z0-9_]{1,31}" placeholder="CHILD" required /></label>
          <label>Display name<input maxLength={100} name="categoryName" required /></label>
          <label>Minimum age (optional)<input max="120" min="0" name="minimumAge" type="number" /></label>
          <label>Maximum age (optional)<input max="120" min="0" name="maximumAge" type="number" /></label>
          <button className="button button--secondary" type="submit">Add passenger category</button>
        </form>
      </section>
    </section>
  );
}
