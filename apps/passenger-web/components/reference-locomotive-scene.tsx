"use client";

import { useEffect, useRef } from "react";

type WAP7CanvasProps = {
  isMobile?: boolean;
};

export function ReferenceLocomotiveScene({ isMobile = false }: WAP7CanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animId: number;
    let width = (canvas.width = canvas.parentElement?.clientWidth || (isMobile ? 390 : 1380));
    let height = (canvas.height = canvas.parentElement?.clientHeight || (isMobile ? 380 : 540));

    const onResize = () => {
      if (!canvas.parentElement) return;
      width = canvas.width = canvas.parentElement.clientWidth;
      height = canvas.height = canvas.parentElement.clientHeight;
    };
    window.addEventListener("resize", onResize);

    // Track particles / sparks
    const sparks = Array.from({ length: isMobile ? 25 : 60 }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 2 + 0.6,
      vx: -(Math.random() * 1.8 + 0.8),
      vy: (Math.random() - 0.5) * 0.5,
      alpha: Math.random() * 0.7 + 0.3,
    }));

    let trackStep = 0;
    let glowPhase = 0;

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // 1. Cinematic Night Sky & Station Atmospheric Glow
      const bg = ctx.createLinearGradient(0, 0, 0, height);
      bg.addColorStop(0, "#040914");
      bg.addColorStop(0.4, "#061224");
      bg.addColorStop(0.7, "#081b36");
      bg.addColorStop(1, "#050b18");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, width, height);

      // Distant Historic Monument Glow (India Gate silhouette in background)
      if (!isMobile) {
        ctx.save();
        ctx.fillStyle = "rgba(255, 175, 80, 0.08)";
        ctx.beginPath();
        // Arch shape
        ctx.rect(width * 0.38, height * 0.28, 70, 70);
        ctx.arc(width * 0.38 + 35, height * 0.28 + 25, 20, Math.PI, 0, false);
        ctx.fill();
        ctx.restore();
      }

      // 2. Platform Roof Truss & Station Overhead Cantilever
      ctx.save();
      ctx.strokeStyle = "rgba(40, 70, 110, 0.4)";
      ctx.lineWidth = 2;
      // Roof trusses angled from top right
      const roofStartX = width * (isMobile ? 0.3 : 0.45);
      for (let x = roofStartX; x < width + 100; x += isMobile ? 40 : 60) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x - 50, height * 0.35);
        ctx.stroke();
      }
      // Horizontal girders
      ctx.beginPath();
      ctx.moveTo(roofStartX - 40, height * 0.12);
      ctx.lineTo(width, height * 0.12);
      ctx.moveTo(roofStartX - 80, height * 0.24);
      ctx.lineTo(width, height * 0.24);
      ctx.stroke();
      ctx.restore();

      // Station Board ("नई दिल्ली / NEW DELHI" yellow backlit board)
      const boardX = width * (isMobile ? 0.62 : 0.76);
      const boardY = height * (isMobile ? 0.18 : 0.16);
      ctx.save();
      ctx.fillStyle = "#ffcc00";
      ctx.shadowColor = "#ffb300";
      ctx.shadowBlur = 18;
      ctx.roundRect(boardX, boardY, isMobile ? 110 : 160, isMobile ? 38 : 50, [6]);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Board border & text
      ctx.strokeStyle = "#000000";
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.fillStyle = "#000000";
      ctx.textAlign = "center";
      ctx.font = `bold ${isMobile ? 10 : 13}px sans-serif`;
      ctx.fillText("नई दिल्ली", boardX + (isMobile ? 55 : 80), boardY + (isMobile ? 16 : 20));
      ctx.font = `bold ${isMobile ? 12 : 15}px sans-serif`;
      ctx.fillText("NEW DELHI", boardX + (isMobile ? 55 : 80), boardY + (isMobile ? 32 : 40));
      ctx.restore();

      // 3. Railway Tracks & Wet Platform Concrete
      const horizonY = height * 0.62;
      const trackBaseY = height * 0.96;
      const trackLeftX = width * (isMobile ? 0.05 : 0.22);
      const trackRightX = width * (isMobile ? 0.95 : 0.88);

      // Rails
      ctx.lineWidth = isMobile ? 3 : 5;
      ctx.strokeStyle = "#258bff";
      ctx.shadowBlur = 14;
      ctx.shadowColor = "#73dfff";

      // Left steel rail
      ctx.beginPath();
      ctx.moveTo(width * 0.44, horizonY);
      ctx.lineTo(trackLeftX, trackBaseY);
      ctx.stroke();

      // Right steel rail
      ctx.beginPath();
      ctx.moveTo(width * 0.56, horizonY);
      ctx.lineTo(trackRightX, trackBaseY);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Cross ties
      ctx.lineWidth = 2;
      ctx.strokeStyle = "rgba(70, 100, 140, 0.4)";
      trackStep = (trackStep + 1.4) % 1;
      const ties = isMobile ? 12 : 22;
      for (let i = 0; i < ties; i++) {
        const factor = Math.pow((i + trackStep) / ties, 2.4);
        const y = horizonY + factor * (trackBaseY - horizonY);
        const lx = width * 0.44 - factor * (width * 0.44 - trackLeftX);
        const rx = width * 0.56 + factor * (trackRightX - width * 0.56);
        ctx.beginPath();
        ctx.moveTo(lx - 10, y);
        ctx.lineTo(rx + 10, y);
        ctx.stroke();
      }

      // 4. Detailed Indian Locomotive (WAP-7 & Vande Bharat Mix)
      const locoScale = isMobile ? 0.78 : 1.25;
      const locoX = width * (isMobile ? 0.52 : 0.55);
      const locoY = horizonY + (trackBaseY - horizonY) * 0.48;

      ctx.save();
      ctx.translate(locoX, locoY);
      ctx.scale(locoScale, locoScale);

      // Volumetric Headlight Cones
      glowPhase += 0.04;
      const glowIntensity = 0.85 + Math.sin(glowPhase) * 0.12;

      const beamL = ctx.createRadialGradient(-35, -20, 8, -120, 160, 240);
      beamL.addColorStop(0, `rgba(255, 255, 255, ${0.9 * glowIntensity})`);
      beamL.addColorStop(0.3, `rgba(115, 223, 255, ${0.5 * glowIntensity})`);
      beamL.addColorStop(1, "rgba(5, 11, 24, 0)");

      ctx.beginPath();
      ctx.moveTo(-35, -20);
      ctx.lineTo(-240, 300);
      ctx.lineTo(40, 300);
      ctx.closePath();
      ctx.fillStyle = beamL;
      ctx.fill();

      // Locomotive Main Shell (WAP-7 White/Cream body with Red & Yellow stripes)
      ctx.shadowBlur = 20;
      ctx.shadowColor = "rgba(37, 139, 255, 0.45)";

      // Cab Roof (Dark navy/grey)
      ctx.fillStyle = "#1e293b";
      ctx.beginPath();
      ctx.roundRect(-85, -110, 170, 70, [20, 20, 4, 4]);
      ctx.fill();

      // Main Body
      const bodyGrad = ctx.createLinearGradient(0, -90, 0, 20);
      bodyGrad.addColorStop(0, "#f8fafc");
      bodyGrad.addColorStop(0.65, "#e2e8f0");
      bodyGrad.addColorStop(1, "#cbd5e1");
      ctx.fillStyle = bodyGrad;
      ctx.beginPath();
      ctx.roundRect(-90, -70, 180, 95, [14, 14, 6, 6]);
      ctx.fill();

      // Red Indian Railways Band
      ctx.fillStyle = "#dc2626";
      ctx.fillRect(-90, -32, 180, 14);

      // Yellow Accent Stripe
      ctx.fillStyle = "#facc15";
      ctx.fillRect(-90, -18, 180, 7);

      // "INDIAN RAILWAYS" Bold Text on cab
      ctx.fillStyle = "#0f172a";
      ctx.font = "bold 9px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("INDIAN RAILWAYS", 0, -42);

      // Locomotive Code ("WAP-7  37259")
      ctx.fillStyle = "#1e293b";
      ctx.font = "bold 8px monospace";
      ctx.fillText("WAP-7  37259", 0, -5);

      // Front Dual Windshields
      ctx.fillStyle = "#020617";
      ctx.strokeStyle = "#38bdf8";
      ctx.lineWidth = 2;
      ctx.roundRect(-75, -96, 68, 38, [6]);
      ctx.roundRect(7, -96, 68, 38, [6]);
      ctx.fill();
      ctx.stroke();

      // Glass Cyan Reflection
      ctx.fillStyle = "rgba(115, 223, 255, 0.35)";
      ctx.beginPath();
      ctx.moveTo(-65, -90);
      ctx.lineTo(-20, -90);
      ctx.lineTo(-40, -65);
      ctx.lineTo(-65, -65);
      ctx.closePath();
      ctx.fill();

      // Twin Powerful LED Headlights
      ctx.shadowBlur = 28;
      ctx.shadowColor = "#ffffff";
      ctx.fillStyle = "#ffffff";
      ctx.beginPath();
      ctx.arc(-35, -20, 7, 0, Math.PI * 2);
      ctx.arc(35, -20, 7, 0, Math.PI * 2);
      ctx.fill();

      // Lower Cowcatcher / Pilot (Plow)
      ctx.fillStyle = "#0f172a";
      ctx.strokeStyle = "#facc15";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(-85, 25);
      ctx.lineTo(0, 42);
      ctx.lineTo(85, 25);
      ctx.lineTo(75, 48);
      ctx.lineTo(-75, 48);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      ctx.restore();

      // 5. Environmental Ambient Particles / Sparks
      sparks.forEach((s) => {
        s.x += s.vx;
        s.y += s.vy;
        if (s.x < 0) s.x = width;
        if (s.y < 0) s.y = height;
        if (s.y > height) s.y = 0;

        ctx.beginPath();
        ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(115, 223, 255, ${s.alpha})`;
        ctx.fill();
      });

      animId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener("resize", onResize);
    };
  }, [isMobile]);

  return (
    <div className="ref-canvas-stage" aria-hidden="true">
      <canvas ref={canvasRef} className="ref-loco-canvas" />
      <div className="ref-canvas-vignette" />
    </div>
  );
}

