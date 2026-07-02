"use client";

import { useCallback, useEffect, useState } from "react";
import { OrderForm } from "@/components/OrderForm";
import { OrderList } from "@/components/OrderList";
import { WebSocketStatus } from "@/components/WebSocketStatus";
import {
  createOrder,
  deleteOrder,
  fetchOrders,
  fetchVersion,
  fulfillOrder,
  type Order,
} from "@/lib/api";
import { useOrderWebSocket } from "@/lib/websocket";

export default function OrdersDashboard() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [version, setVersion] = useState("v1");
  const [loading, setLoading] = useState(true);
  const [events, setEvents] = useState<string[]>([]);
  const [actionId, setActionId] = useState<number | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const [ordersResponse, serviceVersion] = await Promise.all([
        fetchOrders(),
        fetchVersion(),
      ]);
      setOrders(ordersResponse.orders);
      setVersion(serviceVersion);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleWebSocketMessage = useCallback(
    (message: string) => {
      setEvents((prev) => [message, ...prev].slice(0, 8));
      loadOrders();
    },
    [loadOrders]
  );

  const wsStatus = useOrderWebSocket(handleWebSocketMessage);

  async function handleCreateOrder(payload: {
    customerName: string;
    product: string;
    quantity: number;
    unitPrice: number;
  }) {
    await createOrder(payload);
    await loadOrders();
  }

  async function handleFulfillOrder(id: number) {
    setActionId(id);
    try {
      await fulfillOrder(id);
      await loadOrders();
    } finally {
      setActionId(null);
    }
  }

  async function handleDeleteOrder(id: number) {
    if (!window.confirm(`Delete order #${id}?`)) {
      return;
    }
    setActionId(id);
    try {
      await deleteOrder(id);
      await loadOrders();
    } finally {
      setActionId(null);
    }
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-4 py-10">
      <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-widest text-blue-400">
            Open Liberty Platform
          </p>
          <h1 className="text-3xl font-bold text-white">Orders Service</h1>
          <p className="mt-1 text-slate-400">
            JAX-RS + PostgreSQL + Kafka + WebSocket ({version})
          </p>
        </div>
        <WebSocketStatus status={wsStatus} />
      </header>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <OrderList
            orders={orders}
            loading={loading}
            onFulfill={handleFulfillOrder}
            onDelete={handleDeleteOrder}
            actionId={actionId}
          />
        </div>
        <div className="space-y-6">
          <OrderForm onSubmit={handleCreateOrder} />
          <section className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
            <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">
              Live Kafka Events
            </h3>
            <ul className="space-y-2 text-xs text-slate-300">
              {events.length === 0 ? (
                <li className="text-slate-500">Waiting for events...</li>
              ) : (
                events.map((event, index) => (
                  <li
                    key={`${event}-${index}`}
                    className="rounded bg-slate-950/80 p-2 font-mono"
                  >
                    {event}
                  </li>
                ))
              )}
            </ul>
          </section>
        </div>
      </div>
    </main>
  );
}
