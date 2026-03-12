import type {
  AnalysisTimeRange,
  Top10CumQtyVO,
  Top10SecurityIdVO,
  Top10ShareholderIdVO,
} from "../types/analysis";

const normalizeBaseUrl = (url: string): string => url.replace(/\/+$/, "");

const resolveExportBaseUrl = (): string => {
  const envBase = import.meta.env?.VITE_EXPORT_API_BASE;
  if (envBase && typeof envBase === "string" && envBase.trim()) {
    return normalizeBaseUrl(envBase.trim());
  }

  // Dev server: rely on Vite proxy
  if (typeof window !== "undefined" && window.location?.port === "5173") {
    return "/export-data";
  }

  // Fallback to same-origin
  if (typeof window !== "undefined" && window.location?.origin) {
    return normalizeBaseUrl(`${window.location.origin}/export-data`);
  }

  return "http://localhost:8065/export-data";
};

const buildQuery = (range?: AnalysisTimeRange): string => {
  if (!range) return "";
  const params = new URLSearchParams();
  if (range.startTime) params.set("startTime", range.startTime);
  if (range.endTime) params.set("endTime", range.endTime);
  const qs = params.toString();
  return qs ? `?${qs}` : "";
};

const fetchExportData = async <T>(
  endpoint: string,
  range?: AnalysisTimeRange
): Promise<T> => {
  const authToken = localStorage.getItem("authToken");
  const baseUrl = resolveExportBaseUrl();
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const res = await fetch(`${baseUrl}${path}${buildQuery(range)}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      ...(authToken ? { authorization: authToken } : {}),
    },
    credentials: "include",
  });

  if (res.status === 401) {
    throw new Error("401");
  }
  if (res.status === 403) {
    throw new Error("403");
  }
  if (!res.ok) {
    throw new Error(`请求失败(${res.status})`);
  }

  const text = await res.text();
  if (!text.trim()) {
    throw new Error("响应为空");
  }
  const trimmed = text.trim().toLowerCase();
  if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
    throw new Error("服务返回了HTML页面，请检查导出服务地址/代理/部署路径");
  }
  return JSON.parse(text) as T;
};

export const exportDataApi = {
  getTopQty(range?: AnalysisTimeRange) {
    return fetchExportData<Top10CumQtyVO>("/qty", range);
  },
  getTopSecurityAmount(range?: AnalysisTimeRange) {
    return fetchExportData<Top10SecurityIdVO>("/securityId", range);
  },
  getTopShareholderAmount(range?: AnalysisTimeRange) {
    return fetchExportData<Top10ShareholderIdVO>("/shareholderId", range);
  },
};
