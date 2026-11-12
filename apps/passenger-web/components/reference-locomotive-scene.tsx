"use client";

import { useEffect, useRef } from "react";
import Image from "next/image";

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

    // Track particles / sparks drifting across the cinematic train background
    const sparks = Array.from({ length: isMobile ? 30 : 65 }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 2.2 + 0.6,
      vx: -(Math.random() * 1.5 + 0.6),
      vy: (Math.random() - 0.5) * 0.4,
      alpha: Math.random() * 0.7 + 0.3,
    }));

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Atmospheric sparks and light particles drifting across the cinematic train background
      sparks.forEach((s) => {
        s.x += s.vx;
        s.y += s.vy;
        if (s.x < 0) s.x = width;
        if (s.y < 0) s.y = height;
        if (s.y > height) s.y = 0;

        ctx.beginPath();
        ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(115, 223, 255, ${s.alpha})`;
        ctx.shadowColor = "#73dfff";
        ctx.shadowBlur = 6;
        ctx.fill();
        ctx.shadowBlur = 0;
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
      <div className="ref-hero-photo-wrapper">
        <Image
          src="/images/cinematic-indian-railway-hero.jpg"
          alt="Cinematic Indian Railways Train"
          fill
          priority
          sizes="100vw"
          className="ref-hero-photo-img"
        />
        <div className="ref-hero-photo-gradient" />
      </div>
      <canvas ref={canvasRef} className="ref-loco-canvas" />
      <div className="ref-canvas-vignette" />
    </div>
  );
}
