"use client";

import { useState } from "react";

type CopyState = "idle" | "copied" | "failed";

export function CopyHashButton({ value }: { value: string }) {
  const [state, setState] = useState<CopyState>("idle");

  async function copyHash() {
    try {
      await navigator.clipboard.writeText(value);
      setState("copied");
      window.setTimeout(() => setState("idle"), 1800);
    } catch {
      setState("failed");
    }
  }

  const label = state === "copied" ? "복사됨" : state === "failed" ? "복사 실패" : "복사";

  return (
    <button className="copy-button" type="button" onClick={copyHash} aria-live="polite">
      {label}
    </button>
  );
}
