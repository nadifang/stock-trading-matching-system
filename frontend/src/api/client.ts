import type { CancelOrderDTO, Result, SubmitOrderDTO } from "../types/server";

const TRADE_BASE_URL = import.meta.env?.VITE_API_BASE || "/api";
const USER_BASE_URL = import.meta.env?.VITE_USER_API_BASE || "/user-api";
const QUOTATION_BASE_URL = import.meta.env?.VITE_QUOTATION_API_BASE || "/quotation-api";
const EXPORT_BASE_URL = import.meta.env?.VITE_EXPORT_API_BASE || "/export-data-api";

export interface LoginDTO {
  shareholderId: string;
  password: string;
}

interface RequestOptions {
  withAuth?: boolean;
}

async function request<T>(
  baseUrl: string,
  url: string,
  init?: RequestInit,
  options: RequestOptions = {}
): Promise<T> {
  const { withAuth = false } = options;
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  const authToken = withAuth ? localStorage.getItem("authToken") : null;
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

export interface PublishQuotationDTO {
  market: string;
  securityId: string;
  bidPrice: number;
  askPrice: number;
}

export interface ShareholderInfoItem {
  market: string;
  securityId: string;
  side: string;
  qty: number;
  price: number;
  status: string;
  cumQty: number;
  canceledQty: number;
  remainingQty: number;
  tradedAmount: number;
  createTime?: string;
}

export interface ExportTop10Response {
  securityId?: string;
  shareholderId?: string;
  amount?: string;
  cumQty?: string;
}

const unwrapResult = <T>(raw: unknown): T => {
  if (raw && typeof raw === "object" && "success" in raw) {
    const wrapped = raw as Result<T>;
    if (!wrapped.success) {
      let msg = "请求失败";
      if (wrapped.errorMsg) {
        if (typeof wrapped.errorMsg === "string") {
          msg = wrapped.errorMsg;
        } else if (typeof wrapped.errorMsg === "object" && wrapped.errorMsg.rejectText) {
          msg = wrapped.errorMsg.rejectText;
        }
      }
      throw new Error(msg);
    }
    return wrapped.data as T;
  }
  return raw as T;
};

const buildTimeQuery = (startTime?: string, endTime?: string): string => {
  const params = new URLSearchParams();
  if (startTime) params.set("startTime", startTime);
  if (endTime) params.set("endTime", endTime);
  const query = params.toString();
  return query ? `?${query}` : "";
};

async function requestExportData(
  path: string,
  startTime?: string,
  endTime?: string
): Promise<ExportTop10Response> {
  const res = await fetch(`${EXPORT_BASE_URL}${path}${buildTimeQuery(startTime, endTime)}`, {
    method: "GET",
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(`请求失败(${res.status})`);
  }
  const raw = (await res.json()) as unknown;
  return unwrapResult<ExportTop10Response>(raw);
}

export const api = {
  login(dto: LoginDTO) {
    return request<string>(USER_BASE_URL, "/User/login", {
      method: "POST",
      body: JSON.stringify(dto),
    }, { withAuth: false });
  },
  getUserInfo() {
    return request<ShareholderInfoItem[]>(USER_BASE_URL, "/User/getInfo", {
      method: "POST",
    }, { withAuth: true });
  },
  submitOrder(dto: SubmitOrderDTO) {
    return request<SubmitOrderResp>(TRADE_BASE_URL, "/trade-transmitting/submit", {
      method: "POST",
      body: JSON.stringify(dto),
    }, { withAuth: false });
  },
  cancelOrder(dto: CancelOrderDTO) {
    return request<CancelOrderResp>(TRADE_BASE_URL, "/trade-transmitting/cancel", {
      method: "POST",
      body: JSON.stringify(dto),
    }, { withAuth: false });
  },
  async publishQuotation(dto: PublishQuotationDTO) {
    const res = await fetch(`${QUOTATION_BASE_URL}/quotation/publishQuotation`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(dto),
    });
    if (!res.ok) {
      throw new Error(`请求失败(${res.status})`);
    }

    const text = await res.text();
    if (!text.trim()) return null;

    const parsed = JSON.parse(text) as Result<unknown>;
    if (parsed && typeof parsed === "object" && "success" in parsed && parsed.success === false) {
      let msg = "请求失败";
      if (parsed.errorMsg) {
        msg =
          typeof parsed.errorMsg === "string"
            ? parsed.errorMsg
            : parsed.errorMsg.rejectText || msg;
      }
      throw new Error(msg);
    }
    return parsed;
  },
  getTop10Qty(startTime?: string, endTime?: string) {
    return requestExportData("/qty", startTime, endTime);
  },
  getTop10SecurityAmount(startTime?: string, endTime?: string) {
    return requestExportData("/securityId", startTime, endTime);
  },
  getTop10ShareholderAmount(startTime?: string, endTime?: string) {
    return requestExportData("/shareholderId", startTime, endTime);
  },
};
