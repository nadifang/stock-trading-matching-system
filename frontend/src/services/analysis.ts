import type {
  AnalysisTimeRange,
  Top10CumQtyVO,
  Top10SecurityIdVO,
  Top10ShareholderIdVO,
} from "../types/analysis";

const EXPORT_BASE_URL =
  import.meta.env?.VITE_EXPORT_API_BASE ||
  "/export-data";

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
  const res = await fetch(`${EXPORT_BASE_URL}${endpoint}${buildQuery(range)}`, {
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
