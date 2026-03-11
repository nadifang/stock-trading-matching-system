import { useEffect, useMemo, useState } from "react";
import ReactECharts from "echarts-for-react";
import TopHeader from "../components/TopHeader";
import { useAnalysisData } from "../hooks/useAnalysisData";
import type { AnalysisTimeRange } from "../types/analysis";

const splitCsv = (value?: string): string[] => {
  if (!value) return [];
  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
};

const parseNumbers = (value?: string): number[] =>
  splitCsv(value).map((item) => {
    const num = Number(item);
    return Number.isFinite(num) ? num : 0;
  });

const buildLineOption = (title: string, x: string[], y: number[]) => ({
  title: {
    text: title,
    left: "left",
    top: 0,
    textStyle: { fontSize: 14, fontWeight: 600 },
  },
  tooltip: { trigger: "axis" },
  grid: { left: 40, right: 20, top: 40, bottom: 30 },
  xAxis: {
    type: "category",
    data: x,
    axisLabel: { rotate: x.length > 6 ? 30 : 0 },
  },
  yAxis: { type: "value" },
  series: [
    {
      type: "line",
      data: y,
      smooth: true,
      symbolSize: 8,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.08 },
    },
  ],
});

interface AnalysisPageProps {
  lang: "en" | "zh";
  wsConnected?: boolean;
  onToggleLang: () => void;
  searchValue: string;
  onSearchValueChange: (value: string) => void;
  onSearch: () => void;
  onClearSearch: () => void;
  isLoggedIn?: boolean;
  currentUser?: string | null;
  onLoginClick?: () => void;
  onRegisterClick?: () => void;
  onLogoutClick?: () => void;
  onBack?: () => void;
}

export default function AnalysisPage({
  lang,
  wsConnected = false,
  onToggleLang,
  searchValue,
  onSearchValueChange,
  onSearch,
  onClearSearch,
  isLoggedIn = false,
  currentUser = null,
  onLoginClick,
  onRegisterClick,
  onLogoutClick,
  onBack,
}: AnalysisPageProps) {
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const { data, loading, error, load } = useAnalysisData();

  const range: AnalysisTimeRange = useMemo(
    () => ({
      startTime: startTime || undefined,
      endTime: endTime || undefined,
    }),
    [startTime, endTime]
  );

  useEffect(() => {
    void load();
  }, [load]);

  const qtyXAxis = splitCsv(data.qty?.securityId);
  const qtyYAxis = parseNumbers(data.qty?.cumQty);
  const securityXAxis = splitCsv(data.securityAmount?.securityId);
  const securityYAxis = parseNumbers(data.securityAmount?.amount);
  const shareholderXAxis = splitCsv(data.shareholderAmount?.shareholderId);
  const shareholderYAxis = parseNumbers(data.shareholderAmount?.amount);

  const errorMessage =
    error === "401"
      ? "未登录或登录已过期，请先登录。"
      : error === "403"
        ? "当前账号没有权限访问该数据。"
        : error;

  return (
    <div
      className="p-6 min-h-screen"
      style={{
        backgroundImage: "linear-gradient(to bottom, #93c5fd 0%, #e5f0ff 32%, #ffffff 100%)",
      }}
    >
      <div className="w-[95%] mx-auto mb-4">
        <TopHeader
          wsConnected={wsConnected}
          lang={lang}
          onToggleLang={onToggleLang}
          searchValue={searchValue}
          onSearchValueChange={onSearchValueChange}
          onSearch={onSearch}
          onClearSearch={onClearSearch}
          isLoggedIn={isLoggedIn}
          currentUser={currentUser}
          onLoginClick={onLoginClick}
          onRegisterClick={onRegisterClick}
          onLogoutClick={onLogoutClick}
          onAnalysisClick={() => undefined}
          analysisActive
        />
      </div>

      <section className="w-[95%] mx-auto mb-6">
        <div className="bg-white rounded-xl border border-slate-200 shadow p-5">
          <div className="flex flex-col lg:flex-row lg:items-end gap-4">
            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">开始时间</label>
              <input
                type="datetime-local"
                className="border p-2 rounded"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </div>
            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">结束时间</label>
              <input
                type="datetime-local"
                className="border p-2 rounded"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className={`px-4 py-2 rounded text-white font-medium ${
                  loading ? "bg-blue-300 cursor-not-allowed" : "bg-blue-600 hover:bg-blue-700"
                }`}
                onClick={() => void load(range)}
                disabled={loading}
              >
                {loading ? "加载中..." : "查询"}
              </button>
              <button
                type="button"
                className="px-4 py-2 rounded border border-slate-300 text-slate-700 hover:bg-slate-50"
                onClick={onBack}
              >
                返回
              </button>
            </div>
          </div>
          {errorMessage && (
            <div className="mt-3 text-sm text-red-600">{errorMessage}</div>
          )}
        </div>
      </section>

      <section className="w-[95%] mx-auto pb-6 grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
          <ReactECharts
            style={{ height: 320 }}
            option={buildLineOption("股东交易量 Top 10", qtyXAxis, qtyYAxis)}
          />
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
          <ReactECharts
            style={{ height: 320 }}
            option={buildLineOption("股票交易金额 Top 10", securityXAxis, securityYAxis)}
          />
        </div>
        <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
          <ReactECharts
            style={{ height: 320 }}
            option={buildLineOption("股东交易金额 Top 10", shareholderXAxis, shareholderYAxis)}
          />
        </div>
      </section>
    </div>
  );
}



