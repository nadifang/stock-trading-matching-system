import { useEffect, useMemo, useState } from "react";
import ReactECharts from "echarts-for-react";
import TopHeader from "../components/TopHeader";

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

const buildLineOption = (
  title: string,
  x: string[],
  y: number[],
  splitLabel = true
) => ({
  title: {
    text: title,
    left: "left",
    top: 0,
    textStyle: { fontSize: 14, fontWeight: 600 },
  },
  tooltip: { trigger: "axis" },
  grid: { left: 48, right: 28, top: 40, bottom: 64, containLabel: true },
  xAxis: {
    type: "category",
    data: x,
    axisLabel: {
      rotate: x.length > 6 ? 28 : 0,
      fontSize: 10,
      interval: 0,
      formatter: (value: string) =>
        splitLabel && value.length > 6
          ? `${value.slice(0, 6)}\n${value.slice(6)}`
          : value,
    },
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
  const [isLoading, setIsLoading] = useState(true);

  const data = useMemo(
    () => ({
      qty: {
        securityId:
          "430002,430005,430001,430003,430007,430009,430004,600276,430006,430008",
        cumQty:
          "2750.0,2300.0,2250.0,2100.0,2050.0,1950.0,1900.0,1800.0,1650.0,1500.0",
      },
      securityAmount: {
        securityId:
          "600519,601888,600276,000007,600036,000017,601318,000014,000013,000004",
        amount:
          "680000.0,135375.0,99360.0,33020.0,30880.0,28635.0,22900.0,22440.0,22330.0,22100.0",
      },
      shareholderAmount: {
        shareholderId:
          "SH000002,SH000008,SH000018,SH000007,SH000017,SZ000006,SH000005,SZ000016,SH000003,SZ000013",
        amount:
          "1360000.0,162450.0,108300.0,104880.0,93840.0,66040.0,61760.0,57270.0,45800.0,44880.0",
      },
    }),
    []
  );

  const qtyXAxis = splitCsv(data.qty?.securityId);
  const qtyYAxis = parseNumbers(data.qty?.cumQty);
  const securityXAxis = splitCsv(data.securityAmount?.securityId);
  const securityYAxis = parseNumbers(data.securityAmount?.amount);
  const shareholderXAxis = splitCsv(data.shareholderAmount?.shareholderId);
  const shareholderYAxis = parseNumbers(data.shareholderAmount?.amount);

  const errorMessage = null;

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 200);
    return () => clearTimeout(timer);
  }, []);

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
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            <div className="text-sm text-slate-600">
              近30天交易统计数据
            </div>
            <div className="flex items-center gap-2">
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
        {isLoading ? (
          <div className="col-span-full bg-white rounded-xl border border-slate-200 shadow p-6 text-slate-600 text-sm">
            数据查询中...
          </div>
        ) : (
          <>
            <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
              <ReactECharts
                style={{ height: 340 }}
                option={buildLineOption("股东交易量 Top 10", qtyXAxis, qtyYAxis)}
              />
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
              <ReactECharts
                style={{ height: 340 }}
                option={buildLineOption("股票交易金额 Top 10", securityXAxis, securityYAxis)}
              />
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow p-4">
              <ReactECharts
                style={{ height: 340 }}
                option={buildLineOption(
                  "股东交易金额 Top 10",
                  shareholderXAxis,
                  shareholderYAxis,
                  false
                )}
              />
            </div>
          </>
        )}
      </section>
    </div>
  );
}



