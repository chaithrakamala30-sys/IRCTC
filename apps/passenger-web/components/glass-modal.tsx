"use client";

import { useEffect, type ReactNode } from "react";

type GlassModalProps = {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  maxWidth?: string;
};

export function GlassModal({
  isOpen,
  onClose,
  title,
  children,
  maxWidth = "680px",
}: GlassModalProps) {
  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onClose();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="glass-modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="glass-modal-card" style={{ maxWidth }}>
        <div className="glass-modal-header">
          {title && <h3 className="glass-modal-title">{title}</h3>}
          <button
            className="glass-modal-close"
            onClick={onClose}
            type="button"
            aria-label="Close modal"
          >
            &times;
          </button>
        </div>
        <div className="glass-modal-body">{children}</div>
      </div>
    </div>
  );
}

