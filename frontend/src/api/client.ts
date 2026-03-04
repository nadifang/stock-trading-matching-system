import type { CancelOrderDTO, Result, SubmitOrderDTO } from "../types/server";

const TRADE_BASE_URL = import.meta.env?.VITE_API_BASE || "/api";
const USER_BASE_URL = import.meta.env?.VITE_USER_API_BASE || "/user-api";

export interface LoginDTO {
  shareholderId: string;
  password: string;
}

async function request<T>(baseUrl: string, url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  const authToken = localStorage.getItem("authToken");
  if (authToken) {
    headers.set("authorization", authToken);
  }

  const res = await fetch(`${baseUrl}${url}`, {
    headers,
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

export interface CancelOrderResp {
  status?: string;
  rejectText?: string;
  rejectCode?: number;
}

export const api = {
  login(dto: LoginDTO) {
    return request<string>(USER_BASE_URL, "/User/login", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },
  submitOrder(dto: SubmitOrderDTO) {
    return request<SubmitOrderResp>(TRADE_BASE_URL, "/trade-transmitting/submit", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },
  cancelOrder(dto: CancelOrderDTO) {
    return request<CancelOrderResp>(TRADE_BASE_URL, "/trade-transmitting/cancel", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },
};
