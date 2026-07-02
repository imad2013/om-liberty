"use client";

import type { Order } from "@/lib/api";

type Props = {
  orders: Order[];
  loading: boolean;
  onFulfill: (id: number) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  actionId: number | null;
};

function statusClass(status: string): string {
  switch (status) {
    case "FULFILLED":
      return "bg-emerald-500/20 text-emerald-300";
    case "PENDING":
      return "bg-blue-500/20 text-blue-300";
    default:
      return "bg-slate-500/20 text-slate-300";
  }
}

export function OrderList({ orders, loading, onFulfill, onDelete, actionId }: Props) {
  if (loading) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-8 text-center text-slate-400">
        Loading orders...
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-700 bg-slate-900/40 p-8 text-center text-slate-400">
        No orders yet. Create one to get started.
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-slate-800">
      <table className="min-w-full divide-y divide-slate-800">
        <thead className="bg-slate-900">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              ID
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              Customer
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              Product
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              Qty
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              Total
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400">
              Status
            </th>
            <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-slate-400">
              Actions
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800 bg-slate-950/50">
          {orders.map((order) => {
            const isPending = order.status === "PENDING";
            const isBusy = actionId === order.id;

            return (
              <tr key={order.id} className="hover:bg-slate-900/80">
                <td className="px-4 py-3 text-sm text-slate-300">#{order.id}</td>
                <td className="px-4 py-3 text-sm font-medium text-white">
                  {order.customerName}
                </td>
                <td className="px-4 py-3 text-sm text-slate-300">{order.product}</td>
                <td className="px-4 py-3 text-sm text-slate-300">{order.quantity}</td>
                <td className="px-4 py-3 text-sm text-emerald-400">
                  ${Number(order.totalAmount).toFixed(2)}
                </td>
                <td className="px-4 py-3">
                  <span
                    className={`rounded-full px-2 py-1 text-xs font-medium ${statusClass(order.status)}`}
                  >
                    {order.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  {isPending ? (
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => onFulfill(order.id)}
                        className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {isBusy ? "..." : "Complete"}
                      </button>
                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => onDelete(order.id)}
                        className="rounded-md border border-red-500/40 px-3 py-1.5 text-xs font-medium text-red-300 transition hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Delete
                      </button>
                    </div>
                  ) : (
                    <span className="text-xs text-slate-500">—</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
