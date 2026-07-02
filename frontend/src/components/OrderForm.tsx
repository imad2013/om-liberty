"use client";

import { FormEvent, useState } from "react";

type Props = {
  onSubmit: (payload: {
    customerName: string;
    product: string;
    quantity: number;
    unitPrice: number;
  }) => Promise<void>;
};

export function OrderForm({ onSubmit }: Props) {
  const [customerName, setCustomerName] = useState("");
  const [product, setProduct] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [unitPrice, setUnitPrice] = useState(10);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ customerName, product, quantity, unitPrice });
      setCustomerName("");
      setProduct("");
      setQuantity(1);
      setUnitPrice(10);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create order");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-xl border border-slate-800 bg-slate-900/60 p-6"
    >
      <h2 className="mb-4 text-lg font-semibold text-white">Create Order</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm text-slate-400">
          Customer
          <input
            required
            value={customerName}
            onChange={(e) => setCustomerName(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white outline-none focus:border-blue-500"
            placeholder="Acme Corp"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-slate-400">
          Product
          <input
            required
            value={product}
            onChange={(e) => setProduct(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white outline-none focus:border-blue-500"
            placeholder="Widget Pro"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-slate-400">
          Quantity
          <input
            required
            type="number"
            min={1}
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white outline-none focus:border-blue-500"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-slate-400">
          Unit Price ($)
          <input
            required
            type="number"
            min={0.01}
            step={0.01}
            value={unitPrice}
            onChange={(e) => setUnitPrice(Number(e.target.value))}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white outline-none focus:border-blue-500"
          />
        </label>
      </div>
      {error && <p className="mt-3 text-sm text-rose-400">{error}</p>}
      <button
        type="submit"
        disabled={submitting}
        className="mt-4 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-50"
      >
        {submitting ? "Creating..." : "Create Order"}
      </button>
    </form>
  );
}
