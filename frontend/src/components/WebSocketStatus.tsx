"use client";

import type { ConnectionStatus } from "@/lib/websocket";

type Props = {
  status: ConnectionStatus;
};

export function WebSocketStatus({ status }: Props) {
  const color =
    status === "connected"
      ? "bg-emerald-500"
      : status === "connecting"
        ? "bg-amber-500"
        : "bg-rose-500";

  return (
    <div className="flex items-center gap-2 rounded-full border border-slate-700 bg-slate-900 px-3 py-1 text-sm">
      <span className={`h-2 w-2 rounded-full ${color}`} />
      <span className="capitalize text-slate-300">{status}</span>
    </div>
  );
}
