"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiRequestError } from "../lib/api";
import { BookingHistoryPanel } from "./booking-history";

type DeviceExperience = "mobile" | "laptop";
type AuthMode = "login" | "register";

type IdentityUser = {
  userId: string;
  fullName: string;
  email: string;
  mobileNumber: string;
  status: string;
  emailVerified: boolean;
  mobileVerified: boolean;
  mustChangePassword: boolean;
};

type SessionInfo = {
  sessionId: string;
  createdAt: string;
  lastSeenAt: string;
  userAgent: string;
  current: boolean;
};
type RegistrationResult = { requestReceived: boolean; message: string };

export function PassengerIdentityExperience({
  device,
  mode,
}: {
  device: DeviceExperience;
  mode: AuthMode;
}) {
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const registering = mode === "register";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setBusy(true);
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const password = String(form.get("password") ?? "");
    try {
      if (registering) {
        const confirmPassword = String(form.get("confirmPassword") ?? "");
        if (password !== confirmPassword) {
          setError("Passwords do not match.");
          return;
        }
        const response = await apiFetch<RegistrationResult>("/api/v1/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            fullName: form.get("fullName"),
            email: form.get("email"),
            mobileNumber: form.get("mobileNumber"),
            password,
            confirmPassword,
            termsAccepted: form.get("termsAccepted") === "on",
            privacyAccepted: form.get("privacyAccepted") === "on",
          }),
        });
        setMessage(response.data?.message ?? "Registration request received.");
        formElement.reset();
      } else {
        await apiFetch<IdentityUser>("/api/v1/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            identifier: form.get("identifier"),
            password,
          }),
        });
        window.location.assign(`/${device}/account`);
        return;
      }
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "The request could not be completed. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function requestRecovery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setBusy(true);
    const form = new FormData(event.currentTarget);
    try {
      await apiFetch<void>("/api/v1/auth/password/forgot", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: form.get("identifier") }),
      });
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "Password recovery is not available yet.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={`identity-page identity-page--${device}`}>
      <header className="identity-header">
        <Link className="identity-brand" href={`/${device}`}>
          RailConnect
        </Link>
        <nav aria-label="Account navigation">
          <Link href={`/${device}/login`}>Sign in</Link>
          <Link href={`/${device}/register`}>Create account</Link>
        </nav>
      </header>
      <main className="identity-layout">
        {device === "laptop" && (
          <aside className="identity-aside">
            <p className="eyebrow">Passenger identity</p>
            <h1>One account for your RailConnect experience.</h1>
            <p>
              Your identity account is separate from future journey and
              reservation records.
            </p>
          </aside>
        )}
        <section aria-labelledby="identity-title" className="identity-card">
          <p className="eyebrow">{registering ? "New passenger" : "Welcome back"}</p>
          <h1 id="identity-title">{registering ? "Create your account" : "Sign in"}</h1>
          <p className="identity-intro">
            {registering
              ? "Create your account with email and password. Supabase will send an email confirmation if it is enabled for this project. Your phone number is saved to your profile."
              : "Sign in with your account email and password."}
          </p>

          <form className="identity-form" onSubmit={submit}>
            {registering ? (
              <>
                <label>
                  Full name
                  <input autoComplete="name" maxLength={120} minLength={2} name="fullName" required />
                </label>
                <label>
                  Email address
                  <input
                    autoComplete="email"
                    maxLength={254}
                    name="email"
                    required
                    type="email"
                  />
                </label>
                <label>
                  Mobile number with country code
                  <input
                    autoComplete="tel"
                    name="mobileNumber"
                    pattern="^\+[1-9](?:[0-9]|\s|\x28|\x29|\.|\x2d){6,20}$"
                    placeholder="+14155552671"
                    required
                    type="tel"
                  />
                </label>
              </>
            ) : (
              <label>
                Email address
                <input
                  autoComplete="email"
                  name="identifier"
                  required
                  type="email"
                />
              </label>
            )}

            <label>
              Password
              <span className="password-input">
                <input
                  autoComplete={registering ? "new-password" : "current-password"}
                  maxLength={128}
                  minLength={registering ? 12 : 1}
                  name="password"
                  required
                  type={showPassword ? "text" : "password"}
                />
                <button
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="password-toggle"
                  onClick={() => setShowPassword((visible) => !visible)}
                  type="button"
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </span>
              {registering && (
                <small>Use 12 or more characters and at least three character types.</small>
              )}
            </label>

            {registering && (
              <>
                <label>
                  Confirm password
                  <input
                    autoComplete="new-password"
                    maxLength={128}
                    minLength={12}
                    name="confirmPassword"
                    required
                    type={showPassword ? "text" : "password"}
                  />
                </label>
                <label className="identity-check">
                  <input name="termsAccepted" required type="checkbox" />
                  <span>
                    I accept the <Link href="/terms" target="_blank">passenger terms of service</Link>.
                  </span>
                </label>
                <label className="identity-check">
                  <input name="privacyAccepted" required type="checkbox" />
                  <span>
                    I acknowledge the <Link href="/privacy" target="_blank">privacy notice</Link>.
                  </span>
                </label>
              </>
            )}

            {error && (
              <p className="identity-alert identity-alert--error" role="alert">
                {error}
              </p>
            )}
            {message && (
              <p className="identity-alert identity-alert--success" role="status">
                {message}
              </p>
            )}
            <button className="button button--primary identity-submit" disabled={busy} type="submit">
              {busy ? "Please wait…" : registering ? "Create account" : "Sign in securely"}
            </button>
          </form>

          {mode === "login" && (
            <form className="recovery-form" onSubmit={requestRecovery}>
              <details>
                <summary>Forgot your password?</summary>
                <p>
                  Self-service recovery is not available. RailConnect has no
                  approved email or SMS delivery channel, so no reset message
                  will be sent.
                </p>
                <label>
                  Account email
                  <input autoComplete="email" name="identifier" required type="email" />
                </label>
                <button className="button button--secondary" disabled={busy} type="submit">
                  Check recovery availability
                </button>
              </details>
            </form>
          )}

          <p className="identity-switch">
            {registering ? "Already registered?" : "New to RailConnect?"}{" "}
            <Link href={`/${device}/${registering ? "login" : "register"}`}>
              {registering ? "Sign in" : "Create an account"}
            </Link>
          </p>
        </section>
      </main>
      <footer className="site-footer">
        RailConnect is an independent passenger platform and is not an official railway service.
      </footer>
    </div>
  );
}

export function PassengerAccountExperience({
  device,
}: {
  device: DeviceExperience;
}) {
  const [user, setUser] = useState<IdentityUser | null>(null);
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);

  const loadAccount = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [profile, activeSessions] = await Promise.all([
        apiFetch<IdentityUser>("/api/v1/users/me"),
        apiFetch<SessionInfo[]>("/api/v1/auth/sessions"),
      ]);
      setUser(profile.data);
      setSessions(activeSessions.data ?? []);
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "Your account could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAccount();
  }, [loadAccount]);

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await apiFetch<IdentityUser>("/api/v1/users/me", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fullName: form.get("fullName") }),
      });
      setUser(response.data);
      setNotice("Your profile name was updated.");
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The profile could not be updated.");
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    const form = new FormData(event.currentTarget);
    try {
      await apiFetch("/api/v1/auth/password/change", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          currentPassword: form.get("currentPassword"),
          newPassword: form.get("newPassword"),
        }),
      });
      setNotice("Password changed. Your active sessions were revoked; sign in again to continue.");
      setSessions([]);
      setUser(null);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The password could not be changed.");
    }
  }

  async function revokeSession(sessionId: string) {
    setError("");
    const isCurrentSession = sessions.some(
      (session) => session.sessionId === sessionId && session.current,
    );
    try {
      await apiFetch(`/api/v1/auth/sessions/${encodeURIComponent(sessionId)}`, {
        method: "DELETE",
      });
      setSessions((active) => active.filter((session) => session.sessionId !== sessionId));
      if (isCurrentSession) {
        window.location.assign(`/${device}/login`);
      }
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The session could not be revoked.");
    }
  }

  async function logout() {
    setError("");
    try {
      await apiFetch("/api/v1/auth/logout", { method: "POST" });
      window.location.assign(`/${device}/login`);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Sign out could not be completed.");
    }
  }

  return (
    <div className={`identity-page identity-page--${device}`}>
      <header className="identity-header">
        <Link className="identity-brand" href={`/${device}`}>RailConnect</Link>
        <nav aria-label="Account navigation">
          <Link href={`/${device}`}>Passenger home</Link>
          <button className="text-button" onClick={logout} type="button">Sign out</button>
        </nav>
      </header>
      <main className="account-layout">
        {device === "laptop" && (
          <aside className="account-sidebar">
            <p className="eyebrow">Account settings</p>
            <a href="#profile">Personal information</a>
            <a href="#security">Password and security</a>
            <a href="#sessions">Active sessions</a>
            <a href="#bookings">My bookings</a>
          </aside>
        )}
        <section className="account-content" aria-labelledby="account-title">
          <p className="eyebrow">Passenger account</p>
          <h1 id="account-title">Your account</h1>
          {loading && <p role="status">Loading your account…</p>}
          {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}
          {notice && <p className="identity-alert identity-alert--success" role="status">{notice}</p>}
          {!loading && !user && (
            <p>
              Sign in to view account settings.{" "}
              <Link href={`/${device}/login`}>Go to sign in</Link>
            </p>
          )}
          {user && (
            <>
              {user.mustChangePassword && (
                <p className="identity-alert identity-alert--warning" role="alert">
                  Change your temporary password before using other account features.
                </p>
              )}
              <section className="account-panel" id="profile">
                <h2>Personal information</h2>
                <form className="identity-form" onSubmit={updateProfile}>
                  <label>
                    Full name
                    <input defaultValue={user.fullName} maxLength={120} minLength={2} name="fullName" required />
                  </label>
                  <label>
                    Email address
                    <input readOnly value={`${user.email} — ${user.emailVerified ? "verified" : "not verified"}`} />
                  </label>
                  <label>
                    Mobile number
                    <input readOnly value={`${user.mobileNumber} — ${user.mobileVerified ? "verified" : "not verified"}`} />
                  </label>
                  <button className="button button--primary" type="submit">Save name</button>
                </form>
                <p className="identity-note">
                  Email and mobile changes are unavailable until a verification delivery method is approved.
                </p>
              </section>

              <section className="account-panel" id="security">
                <h2>Password and security</h2>
                <form className="identity-form" onSubmit={changePassword}>
                  <label>
                    Current password
                    <input autoComplete="current-password" maxLength={128} name="currentPassword" required type="password" />
                  </label>
                  <label>
                    New password
                    <input autoComplete="new-password" maxLength={128} minLength={12} name="newPassword" required type="password" />
                  </label>
                  <button className="button button--primary" type="submit">Change password</button>
                </form>
                <p className="identity-note">
                  Authenticator-app MFA is not available yet. No SMS-based MFA is used.
                </p>
              </section>

              <section className="account-panel" id="sessions">
                <h2>Active sessions</h2>
                {sessions.length === 0 ? (
                  <p>No active sessions were found.</p>
                ) : (
                  <ul className="session-list">
                    {sessions.map((session) => (
                      <li key={session.sessionId}>
                        <div>
                          <strong>{session.current ? "This browser" : "Other browser"}</strong>
                          <span>{session.userAgent || "Browser details unavailable"}</span>
                          <span>Last active {new Date(session.lastSeenAt).toLocaleString()}</span>
                        </div>
                        <button
                          className="button button--secondary"
                          onClick={() => void revokeSession(session.sessionId)}
                          type="button"
                        >
                          Revoke
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <BookingHistoryPanel device={device} />
            </>
          )}
        </section>
      </main>
      <footer className="site-footer">
        Account controls use the same backend for mobile and laptop browsers.
      </footer>
    </div>
  );
}
