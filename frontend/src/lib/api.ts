export type Order = {
  id: number;
  customerName: string;
  product: string;
  quantity: number;
  totalAmount: number;
  status: string;
  createdAt: string;
};

export type OrdersResponse = {
  user: string;
  version: string;
  orders: Order[];
};

const API_BASE = typeof window !== "undefined" ? "" : "http://localhost:9080";

export async function fetchOrders(): Promise<OrdersResponse> {
  const response = await fetch(`${API_BASE}/api/orders`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch orders: ${response.status}`);
  }
  return response.json();
}

export async function fetchVersion(): Promise<string> {
  const response = await fetch(`${API_BASE}/api/orders/version`);
  if (!response.ok) {
    throw new Error(`Failed to fetch version: ${response.status}`);
  }
  const data = await response.json();
  return data.version;
}

export async function createOrder(payload: {
  customerName: string;
  product: string;
  quantity: number;
  unitPrice: number;
}): Promise<Order> {
  const response = await fetch(`${API_BASE}/api/orders`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Failed to create order: ${response.status}`);
  }
  return response.json();
}

export async function fulfillOrder(id: number): Promise<Order> {
  const response = await fetch(`${API_BASE}/api/orders/${id}/fulfill`, {
    method: "PATCH",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Failed to fulfill order: ${response.status}`);
  }
  return response.json();
}

export async function deleteOrder(id: number): Promise<void> {
  const response = await fetch(`${API_BASE}/api/orders/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Failed to delete order: ${response.status}`);
  }
}

export function getWebSocketUrl(): string {
  if (typeof window === "undefined") {
    return "ws://localhost:9080/ws/orders";
  }
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/orders`;
}
