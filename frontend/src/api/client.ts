import type { CancelOrderDTO, Result, SubmitOrderDTO } from "../types/server";

const BASE_URL =
  (typeof import.meta !== "undefined" &&
    (import.meta as any).env?.VITE_API_BASE) ||
  "/api";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`, {
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    ...init,
  });
  const json: Result<T> = await res.json();
  if (!json.success) {
    let msg = "请求失败";
    if (json.errorMsg) {
      if (typeof json.errorMsg === "string") {
        msg = json.errorMsg;
      } else if (typeof json.errorMsg === "object" && json.errorMsg.rejectText) {
        msg = json.errorMsg.rejectText;
      }
    }
    throw new Error(msg);
  }
  return json.data as T;
}

export interface SubmitOrderResp {
  clOrderId: string;
  market: string;
  securityId: string;
  side: "B" | "S";
  qty: number;
  price: number;
  shareholderId: string;
  status?: string;//添加status字段
}

export const api = {
  submitOrder(dto: SubmitOrderDTO) {
    return request<SubmitOrderResp>("/trade", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },
  cancelOrder(dto: CancelOrderDTO) {
    return request<void>("/trade/cancel", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },
};

