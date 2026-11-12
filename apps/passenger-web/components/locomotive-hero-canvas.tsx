"use client";

import { useEffect, useRef, useState } from "react";

type LocomotiveCanvasProps = {
  isMobile?: boolean;
};

export function LocomotiveHeroCanvas({ isMobile = false }: LocomotiveCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [webglSupported, setWebglSupported] = useState(true);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // Check 2D / WebGL support
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      setWebglSupported(false);
      return;
    }

    let animationFrameId: number;
    let width = (canvas.width = canvas.parentElement?.clientWidth || (isMobile ? 380 : 1200));
    let height = (canvas.height = canvas.parentElement?.clientHeight || (isMobile ? 320 : 540));

    const handleResize = () => {
      if (!canvas.parentElement) return;
      width = canvas.width = canvas.parentElement.clientWidth;
      height = canvas.height = canvas.parentElement.clientHeight;
    };

    window.addEventListener("resize", handleResize);

    // Track state for particles & atmospheric glow
    const particlesCount = isMobile ? 35 : 90;
    const particles = Array.from({ length: particlesCount }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 2 + 0.5,
      speedX: -(Math.random() * 1.5 + 0.5),
      speedY: (Math.random() - 0.5) * 0.4,
      opacity: Math.random() * 0.6 + 0.2,
    }));

    let trainOffset = 0;
    let lightPulse = 0;

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // 1. Cinematic Deep Navy Gradient
      const skyGradient = ctx.createLinearGradient(0, 0, 0, height);
      skyGradient.addColorStop(0, "#050b18");
      skyGradient.addColorStop(0.5, "#08152c");
      skyGradient.addColorStop(1, "#0a1b38");
      ctx.fillStyle = skyGradient;
      ctx.fillRect(0, 0, width, height);

      // 2. Distant Starfield & Ambient Particles
      particles.forEach((p) => {
        p.x += p.speedX;
        p.y += p.speedY;
        if (p.x < 0) p.x = width;
        if (p.y < 0) p.y = height;
        if (p.y > height) p.y = 0;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(115, 223, 255, ${p.opacity})`;
        ctx.shadowBlur = 4;
        ctx.shadowColor = "#73dfff";
        ctx.fill();
        ctx.shadowBlur = 0;
      });

      // 3. Railway Track & Perspective Horizon
      const horizonY = height * (isMobile ? 0.65 : 0.62);
      const trackBaseY = height * 0.94;
      const trackLeftX = width * (isMobile ? 0.05 : 0.15);
      const trackRightX = width * (isMobile ? 0.95 : 0.85);

      // Ballast / Ground Glow
      const groundGrad = ctx.createLinearGradient(0, horizonY, 0, height);
      groundGrad.addColorStop(0, "rgba(8, 23, 48, 0.4)");
      groundGrad.addColorStop(1, "rgba(5, 11, 24, 0.95)");
      ctx.fillStyle = groundGrad;
      ctx.fillRect(0, horizonY, width, height - horizonY);

      // Steel Rails with Cyber Blue Lighting
      ctx.lineWidth = isMobile ? 3 : 5;
      ctx.strokeStyle = "#1d67be";
      ctx.shadowBlur = 12;
      ctx.shadowColor = "#258bff";

      // Left Rail
      ctx.beginPath();
      ctx.moveTo(width * 0.46, horizonY);
      ctx.lineTo(trackLeftX, trackBaseY);
      ctx.stroke();

      // Right Rail
      ctx.beginPath();
      ctx.moveTo(width * 0.54, horizonY);
      ctx.lineTo(trackRightX, trackBaseY);
      ctx.stroke();

      // Railway Sleepers (Cross ties) with rhythmic motion
      ctx.shadowBlur = 0;
      ctx.lineWidth = isMobile ? 2 : 3;
      ctx.strokeStyle = "rgba(70, 95, 130, 0.45)";

      const sleeperCount = isMobile ? 12 : 20;
      trainOffset = (trainOffset + 1.2) % 1;

      for (let i = 0; i < sleeperCount; i++) {
        const factor = Math.pow((i + trainOffset) / sleeperCount, 2.2);
        const y = horizonY + factor * (trackBaseY - horizonY);
        const leftX = width * 0.46 - factor * (width * 0.46 - trackLeftX);
        const rightX = width * 0.54 + factor * (trackRightX - width * 0.54);

        ctx.beginPath();
        ctx.moveTo(leftX - 8, y);
        ctx.lineTo(rightX + 8, y);
        ctx.stroke();
      }

      // 4. Stylized Vande Bharat / WAP-7 Aerodynamic Express Silhouette
      const trainY = horizonY + (trackBaseY - horizonY) * 0.45;
      const trainScale = isMobile ? 0.72 : 1.15;
      const trainCenterX = width * 0.5;

      ctx.save();
      ctx.translate(trainCenterX, trainY);
      ctx.scale(trainScale, trainScale);

      // Atmospheric Train Headlight Cone (Volumetric Light Beam)
      lightPulse += 0.04;
      const beamGlow = 0.7 + Math.sin(lightPulse) * 0.15;
      const beamGrad = ctx.createRadialGradient(0, -10, 10, 0, 120, 240);
      beamGrad.addColorStop(0, `rgba(234, 243, 255, ${0.85 * beamGlow})`);
      beamGrad.addColorStop(0.35, `rgba(115, 223, 255, ${0.45 * beamGlow})`);
      beamGrad.addColorStop(1, "rgba(5, 11, 24, 0)");

      ctx.beginPath();
      ctx.moveTo(-15, -10);
      ctx.lineTo(-140, 260);
      ctx.lineTo(140, 260);
      ctx.lineTo(15, -10);
      ctx.closePath();
      ctx.fillStyle = beamGrad;
      ctx.fill();

      // Locomotive Nose & Shell (Aerodynamic Vande Bharat White + Navy Trim)
      ctx.shadowBlur = 18;
      ctx.shadowColor = "rgba(37, 139, 255, 0.4)";

      // Roof & Cab
      ctx.fillStyle = "#0c1f3d";
      ctx.beginPath();
      ctx.roundRect(-65, -95, 130, 90, [25, 25, 6, 6]);
      ctx.fill();

      // Aerodynamic Front Nose (V-Profile)
      const bodyGrad = ctx.createLinearGradient(0, -90, 0, 10);
      bodyGrad.addColorStop(0, "#f8fafc"); // Bright Vande Bharat White
      bodyGrad.addColorStop(0.65, "#e2e8f0");
      bodyGrad.addColorStop(1, "#0f2b5c"); // Blue aerodynamic skirt
      ctx.fillStyle = bodyGrad;

      ctx.beginPath();
      ctx.moveTo(-60, -65);
      ctx.quadraticCurveTo(0, -85, 60, -65);
      ctx.lineTo(68, 5);
      ctx.quadraticCurveTo(0, 18, -68, 5);
      ctx.closePath();
      ctx.fill();

      // Windshield & Cyber Tinted Glass
      ctx.fillStyle = "#030816";
      ctx.strokeStyle = "#38bdf8";
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(-42, -58);
      ctx.quadraticCurveTo(0, -70, 42, -58);
      ctx.lineTo(38, -32);
      ctx.quadraticCurveTo(0, -36, -38, -32);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      // Windshield Cyan Reflection Accent
      ctx.fillStyle = "rgba(115, 223, 255, 0.35)";
      ctx.beginPath();
      ctx.moveTo(-32, -54);
      ctx.lineTo(15, -54);
      ctx.lineTo(5, -34);
      ctx.lineTo(-30, -34);
      ctx.closePath();
      ctx.fill();

      // Center High-Intensity LED Headlights
      ctx.shadowBlur = 25;
      ctx.shadowColor = "#ffffff";
      ctx.fillStyle = "#ffffff";
      ctx.beginPath();
      ctx.arc(-22, -14, 5, 0, Math.PI * 2);
      ctx.arc(22, -14, 5, 0, Math.PI * 2);
      ctx.fill();

      // Center IRCTC / RailConnect Crest
      ctx.fillStyle = "#ea580c";
      ctx.beginPath();
      ctx.arc(0, -14, 6, 0, Math.PI * 2);
      ctx.fill();

      // Lower Skirt Marker Lights (Electric Cyan)
      ctx.fillStyle = "#73dfff";
      ctx.shadowColor = "#73dfff";
      ctx.beginPath();
      ctx.rect(-50, -4, 8, 3);
      ctx.rect(42, -4, 8, 3);
      ctx.fill();

      ctx.restore();

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener("resize", handleResize);
    };
  }, [isMobile]);

  return (
    <div className="locomotive-canvas-wrapper" aria-hidden="true">
      <canvas ref={canvasRef} className="locomotive-canvas" />
      <div className="locomotive-canvas-overlay" />
    </div>
  );
}

