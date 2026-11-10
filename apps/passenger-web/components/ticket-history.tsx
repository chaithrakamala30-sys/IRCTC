"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiDownload, apiFetch } from "../lib/api";

type Summary = {
  ticketId: string;
  pnr: string;
  bookingReference: string;
  ticketStatus: string;
  trainNumber: string;
  trainName: string;
  journeyDate: string;
  boardingStation: string;
  destinationStation: string;
  passengerCount: number;
  issuedAt: string;
};
type Page = { items: Summary[]; page: number; size: number; total: number };
type Ticket = Omit<Summary, "passengerCount"> & {
  passengers: {
    ticketPassengerId: string;
    passengerNumber: number;
    passengerName: string;
    coachNumber: string;
    berthNumber: string;
    berthType: string;
  }[];
};

function message(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "Tickets could not be loaded.";
}

export function TicketHistoryPanel({ device }: { device: "mobile" | "laptop" }) {
  const [tickets, setTickets] = useState<Summary[]>([]);
  const [selected, setSelected] = useState<Ticket | null>(null);
  const [pnr, setPnr] = useState("");
  const [query, setQuery] = useState("");
  const [journey, setJourney] = useState("");
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      if (query) {
        const response = await apiFetch<Ticket>(`/api/v1/tickets/pnr/${encodeURIComponent(query)}`);
        setTickets(response.data
          ? [{ ...response.data, passengerCount: response.data.passengers.length }]
          : []);
        setTotal(response.data ? 1 : 0);
      } else {
        const params = new URLSearchParams({ page: String(page), size: "10" });
        if (journey) params.set("journey", journey);
        const response = await apiFetch<Page>(`/api/v1/tickets?${params}`);
        setTickets(response.data?.items ?? []);
        setTotal(response.data?.total ?? 0);
      }
    } catch (caught) {
      setTickets([]);
      setError(caught instanceof ApiRequestError && caught.status === 404 && query
        ? "No ticket with that PNR belongs to this account."
        : message(caught));
    } finally {
      setLoading(false);
    }
  }, [journey, page, query]);

  useEffect(() => {
    void load();
  }, [load]);

  async function open(ticketId: string) {
    setError("");
    try {
      const response = await apiFetch<Ticket>(`/api/v1/tickets/${encodeURIComponent(ticketId)}`);
      setSelected(response.data);
    } catch (caught) {
      setError(message(caught));
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setSelected(null);
    setQuery(pnr.trim().toUpperCase());
  }

  return (
    <section className={`ticket-history ticket-history--${device}`} aria-labelledby="ticket-history-heading">
      <div className="booking-history-heading">
        <div>
          <p className="eyebrow">Issued by RailConnect</p>
          <h3 id="ticket-history-heading">My tickets</h3>
        </div>
        <button className="button button--secondary" onClick={() => void load()} type="button">Refresh</button>
      </div>
      <form className="booking-history-filters" onSubmit={search}>
        <label>RailConnect PNR
          <input
            maxLength={24}
            onChange={(event) => setPnr(event.target.value)}
            placeholder="RC-TK-…"
            value={pnr}
          />
        </label>
        <label>Journey period
          <select onChange={(event) => { setPage(0); setJourney(event.target.value); setQuery(""); }} value={journey}>
            <option value="">All journeys</option>
            <option value="UPCOMING">Upcoming</option>
            <option value="PAST">Past</option>
          </select>
        </label>
        <button className="button button--primary" type="submit">Search tickets</button>
        {query && <button className="button button--secondary" onClick={() => { setPnr(""); setQuery(""); }} type="button">Clear</button>}
      </form>
      {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}
      {loading && <p role="status">Loading tickets…</p>}
      {!loading && tickets.length === 0 && !error && <p>No issued RailConnect tickets yet.</p>}
      {!loading && tickets.length > 0 && device === "laptop" && (
        <div className="booking-history-table-wrap">
          <table className="booking-history-table">
            <thead><tr><th>RailConnect PNR</th><th>Journey</th><th>Date</th><th>Passengers</th><th>Status</th><th /></tr></thead>
            <tbody>{tickets.map((ticket) => (
              <tr key={ticket.ticketId}>
                <td><strong>{ticket.pnr}</strong></td>
                <td>{ticket.trainNumber} · {ticket.boardingStation} to {ticket.destinationStation}</td>
                <td>{ticket.journeyDate}</td>
                <td>{ticket.passengerCount}</td>
                <td>{ticket.ticketStatus}</td>
                <td><button className="text-button" onClick={() => void open(ticket.ticketId)} type="button">Details</button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {!loading && tickets.length > 0 && device === "mobile" && (
        <ul className="booking-history-cards ticket-history-cards">
          {tickets.map((ticket) => (
            <li key={ticket.ticketId}>
              <strong className="ticket-pnr">{ticket.pnr}</strong>
              <span>{ticket.trainNumber} · {ticket.trainName}</span>
              <span>{ticket.boardingStation} to {ticket.destinationStation} · {ticket.journeyDate}</span>
              <span>{ticket.passengerCount} passenger{ticket.passengerCount === 1 ? "" : "s"} · {ticket.ticketStatus}</span>
              <button className="button button--secondary" onClick={() => void open(ticket.ticketId)} type="button">View ticket</button>
            </li>
          ))}
        </ul>
      )}
      <div className="booking-history-pagination">
        <span>{total} ticket{total === 1 ? "" : "s"}</span>
        {!query && (
          <div>
            <button className="button button--secondary" disabled={page === 0 || loading} onClick={() => setPage((value) => value - 1)} type="button">Previous</button>
            <button className="button button--secondary" disabled={(page + 1) * 10 >= total || loading} onClick={() => setPage((value) => value + 1)} type="button">Next</button>
          </div>
        )}
      </div>
      {selected && (
        <article className="ticket-summary-card" aria-label={`Ticket ${selected.pnr}`}>
          <p className="eyebrow">RAILCONNECT PNR</p>
          <strong className="ticket-pnr">{selected.pnr}</strong>
          <p>{selected.trainNumber} · {selected.trainName} · {selected.journeyDate}</p>
          <p>{selected.boardingStation} to {selected.destinationStation}</p>
          <ol className="ticket-passenger-list">
            {selected.passengers.map((passenger) => (
              <li key={passenger.ticketPassengerId}>
                <strong>{passenger.passengerName}</strong>
                <span>{passenger.coachNumber} · berth {passenger.berthNumber} ({passenger.berthType.replaceAll("_", " ")})</span>
              </li>
            ))}
          </ol>
          <button
            className="button button--primary"
            onClick={() => void apiDownload(
              `/api/v1/tickets/${encodeURIComponent(selected.ticketId)}/download`,
              `${selected.pnr}.pdf`,
            ).catch((caught: unknown) => setError(message(caught)))}
            type="button"
          >
            Download e-ticket
          </button>
        </article>
      )}
      <p className="identity-note">RailConnect PNRs are not official Indian Railways or IRCTC PNRs.</p>
    </section>
  );
}
