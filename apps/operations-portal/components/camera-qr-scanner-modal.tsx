"use client";

import { useEffect, useRef, useState } from "react";
import jsQR from "jsqr";

type Props = {
  onScan: (decodedText: string) => void;
  onClose: () => void;
};

export function CameraQrScannerModal({ onScan, onClose }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [cameraError, setCameraError] = useState("");
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [scanning, setScanning] = useState(true);
  const animFrameId = useRef<number | null>(null);

  // Enumerate cameras (prefer environment / back camera for scanning tickets)
  useEffect(() => {
    let active = true;
    async function getCameras() {
      try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
          setCameraError("Camera device access not supported in this browser.");
          return;
        }
        const devList = await navigator.mediaDevices.enumerateDevices();
        const videoDevs = devList.filter((d) => d.kind === "videoinput");
        if (active) {
          setDevices(videoDevs);
          // Prefer environment (back) camera on phones
          const backCam = videoDevs.find((d) => /back|rear|environment/i.test(d.label));
          if (backCam) {
            setSelectedDeviceId(backCam.deviceId);
          } else if (videoDevs.length > 0) {
            setSelectedDeviceId(videoDevs[0].deviceId);
          }
        }
      } catch (err: unknown) {
        if (active) {
          setCameraError(err instanceof Error ? err.message : "Could not enumerate camera devices.");
        }
      }
    }
    void getCameras();
    return () => {
      active = false;
    };
  }, []);

  // Start video stream
  useEffect(() => {
    let stream: MediaStream | null = null;
    let cancelled = false;

    async function startCamera() {
      setCameraError("");
      try {
        const constraints: MediaStreamConstraints = {
          video: selectedDeviceId
            ? { deviceId: { exact: selectedDeviceId } }
            : { facingMode: { ideal: "environment" } },
          audio: false,
        };

        stream = await navigator.mediaDevices.getUserMedia(constraints);
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }

        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.setAttribute("playsinline", "true");
          await videoRef.current.play();
          setScanning(true);
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setCameraError(
            err instanceof Error
              ? `Camera error: ${err.message}. Please allow camera permissions in your browser.`
              : "Could not access camera. Please allow camera permissions."
          );
        }
      }
    }

    void startCamera();

    return () => {
      cancelled = true;
      if (animFrameId.current) cancelAnimationFrame(animFrameId.current);
      if (stream) {
        stream.getTracks().forEach((track) => track.stop());
      }
    };
  }, [selectedDeviceId]);

  // QR Scanning Loop using jsQR
  useEffect(() => {
    if (!scanning) return;

    function scanLoop() {
      if (videoRef.current && videoRef.current.readyState === videoRef.current.HAVE_ENOUGH_DATA) {
        const video = videoRef.current;
        const canvas = canvasRef.current;
        if (canvas) {
          canvas.width = video.videoWidth;
          canvas.height = video.videoHeight;
          const ctx = canvas.getContext("2d", { willReadFrequently: true });
          if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const code = jsQR(imageData.data, imageData.width, imageData.height, {
              inversionAttempts: "dontInvert",
            });

            if (code && code.data && code.data.trim()) {
              setScanning(false);
              onScan(code.data.trim());
              return;
            }
          }
        }
      }
      animFrameId.current = requestAnimationFrame(scanLoop);
    }

    animFrameId.current = requestAnimationFrame(scanLoop);

    return () => {
      if (animFrameId.current) cancelAnimationFrame(animFrameId.current);
    };
  }, [scanning, onScan]);

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(3, 7, 18, 0.85)",
        backdropFilter: "blur(6px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "1rem",
      }}
    >
      <div
        style={{
          background: "#0f172a",
          border: "1px solid #334155",
          borderRadius: "16px",
          width: "100%",
          maxWidth: "460px",
          overflow: "hidden",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.7)",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: "1rem 1.25rem",
            borderBottom: "1px solid #1e293b",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <div>
            <h3 style={{ margin: 0, color: "#f8fafc", fontSize: "1.05rem", fontWeight: 700 }}>
              Scan Ticket QR Code
            </h3>
            <p style={{ margin: "2px 0 0 0", color: "#94a3b8", fontSize: "0.75rem" }}>
              Align ticket QR code or verification token within the frame
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: "#1e293b",
              border: "1px solid #334155",
              color: "#94a3b8",
              borderRadius: "8px",
              width: "32px",
              height: "32px",
              cursor: "pointer",
              fontSize: "1rem",
              lineHeight: 1,
            }}
          >
            ✕
          </button>
        </div>

        {/* Viewfinder Body */}
        <div style={{ position: "relative", width: "100%", height: "320px", background: "#000" }}>
          {cameraError ? (
            <div
              style={{
                position: "absolute",
                inset: 0,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                padding: "1.5rem",
                textAlign: "center",
                color: "#f87171",
                fontSize: "0.85rem",
              }}
            >
              <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>📷</div>
              <div>{cameraError}</div>
              <p style={{ color: "#94a3b8", fontSize: "0.75rem", marginTop: "0.5rem" }}>
                Ensure your device has camera permissions enabled, or manually paste the QR token below.
              </p>
            </div>
          ) : (
            <>
              <video
                ref={videoRef}
                style={{
                  width: "100%",
                  height: "100%",
                  objectFit: "cover",
                }}
              />
              <canvas ref={canvasRef} style={{ display: "none" }} />

              {/* Viewfinder Reticle Overlay */}
              <div
                style={{
                  position: "absolute",
                  inset: 0,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  pointerEvents: "none",
                }}
              >
                <div
                  style={{
                    width: "220px",
                    height: "220px",
                    border: "2px solid #38bdf8",
                    borderRadius: "16px",
                    boxShadow: "0 0 0 4000px rgba(0, 0, 0, 0.4)",
                    position: "relative",
                  }}
                >
                  {/* Scanning beam animation line */}
                  <div
                    style={{
                      position: "absolute",
                      left: 0,
                      right: 0,
                      height: "2px",
                      background: "linear-gradient(90deg, transparent, #38bdf8, transparent)",
                      boxShadow: "0 0 8px #38bdf8",
                      animation: "scanBeam 2s ease-in-out infinite",
                    }}
                  />
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer & Camera Selector */}
        <div
          style={{
            padding: "0.85rem 1.25rem",
            borderTop: "1px solid #1e293b",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: "0.75rem",
            background: "#0b1329",
          }}
        >
          {devices.length > 1 ? (
            <select
              value={selectedDeviceId}
              onChange={(e) => setSelectedDeviceId(e.target.value)}
              style={{
                flex: 1,
                padding: "0.4rem 0.6rem",
                borderRadius: "6px",
                border: "1px solid #334155",
                background: "#1e293b",
                color: "#f8fafc",
                fontSize: "0.75rem",
              }}
            >
              {devices.map((d, idx) => (
                <option key={d.deviceId || idx} value={d.deviceId}>
                  {d.label || `Camera ${idx + 1}`}
                </option>
              ))}
            </select>
          ) : (
            <span style={{ fontSize: "0.75rem", color: "#64748b" }}>Phone / Web Camera active</span>
          )}

          <button
            type="button"
            onClick={onClose}
            style={{
              padding: "0.45rem 1rem",
              borderRadius: "6px",
              background: "#334155",
              color: "#f8fafc",
              border: "none",
              fontSize: "0.8rem",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Cancel
          </button>
        </div>
      </div>

      <style jsx>{`
        @keyframes scanBeam {
          0% {
            top: 5%;
          }
          50% {
            top: 95%;
          }
          100% {
            top: 5%;
          }
        }
      `}</style>
    </div>
  );
}
