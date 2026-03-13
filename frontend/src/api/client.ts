import type { CancelOrderDTO, Result, SubmitOrderDTO } from "../types/server";

const TRADE_BASE_URL = import.meta.env?.VITE_API_BASE || "http://129.211.187.179:8066";
const USER_BASE_URL = import.meta.env?.VITE_USER_API_BASE || "http://129.211.187.179:8066";
const QUOTATION_BASE_URL = import.meta.env?.VITE_QUOTATION_API_BASE || "http://129.211.187.179:8066";
const EXPORT_BASE_URL = import.meta.env?.VITE_EXPORT_API_BASE || "http://129.211.187.179:8066";

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
  getMarketData() {
    // 由于后端没有获取所有行情的接口，我们获取几个主要股票的行情数据
    const stockSymbols = ["600030", "600519", "000858", "601318", "000001"];
    const promises = stockSymbols.map(symbol => 
      api.getQuotation(symbol)
    );
    return Promise.all(promises).catch(() => {
      // 模拟数据作为备用
      return [
        {
          market: "XSHG",
          securityId: "600030",
          bidPrice: 20.49,
          askPrice: 20.51,
          volume: 12345678
        },
        {
          market: "XSHG",
          securityId: "600519",
          bidPrice: 1789.4,
          askPrice: 1789.6,
          volume: 9876543
        },
        {
          market: "XSHE",
          securityId: "000858",
          bidPrice: 168.65,
          askPrice: 168.75,
          volume: 5678901
        },
        {
          market: "XSHG",
          securityId: "601318",
          bidPrice: 48.15,
          askPrice: 48.25,
          volume: 8765432
        },
        {
          market: "XSHE",
          securityId: "000001",
          bidPrice: 12.79,
          askPrice: 12.81,
          volume: 6543210
        }
      ];
    });
  },
  getQuotation(securityId: string) {
    return request<any>(QUOTATION_BASE_URL, `/quotation/getQuotation?securityId=${securityId}`, {
      method: "GET",
    }, { withAuth: false }).catch(() => {
      // 模拟数据作为备用
      const mockData = {
        market: "XSHG",
        securityId: securityId,
        bidPrice: 20.49,
        askPrice: 20.51,
        volume: 12345678
      };
      return mockData;
    });
  },
  getStockOrders(securityId: string) {
    return request<any[]>(TRADE_BASE_URL, `/trade-query/query?securityId=${securityId}`, {
      method: "GET",
    }, { withAuth: false }).catch(() => {
      // 模拟数据作为备用 - 买卖各5条订单
      return [
        // 买入订单 (按价格从高到低排序)
        {
          clOrderId: "B123456789",
          market: "XSHG",
          securityId: securityId,
          side: "B",
          qty: 100,
          price: 20.5,
          shareholderId: "A123456789",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "B123456790",
          market: "XSHG",
          securityId: securityId,
          side: "B",
          qty: 200,
          price: 20.4,
          shareholderId: "A123456790",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "B123456791",
          market: "XSHG",
          securityId: securityId,
          side: "B",
          qty: 150,
          price: 20.3,
          shareholderId: "A123456791",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "B123456792",
          market: "XSHG",
          securityId: securityId,
          side: "B",
          qty: 250,
          price: 20.2,
          shareholderId: "A123456792",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "B123456793",
          market: "XSHG",
          securityId: securityId,
          side: "B",
          qty: 300,
          price: 20.1,
          shareholderId: "A123456793",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        // 卖出订单 (按价格从低到高排序)
        {
          clOrderId: "S123456789",
          market: "XSHG",
          securityId: securityId,
          side: "S",
          qty: 100,
          price: 20.6,
          shareholderId: "A123456789",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "S123456790",
          market: "XSHG",
          securityId: securityId,
          side: "S",
          qty: 200,
          price: 20.7,
          shareholderId: "A123456790",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "S123456791",
          market: "XSHG",
          securityId: securityId,
          side: "S",
          qty: 150,
          price: 20.8,
          shareholderId: "A123456791",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "S123456792",
          market: "XSHG",
          securityId: securityId,
          side: "S",
          qty: 250,
          price: 20.9,
          shareholderId: "A123456792",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        },
        {
          clOrderId: "S123456793",
          market: "XSHG",
          securityId: securityId,
          side: "S",
          qty: 300,
          price: 21.0,
          shareholderId: "A123456793",
          status: "Acked",
          cumQty: 0,
          execPrice: 0
        }
      ];
    });
  },
};
