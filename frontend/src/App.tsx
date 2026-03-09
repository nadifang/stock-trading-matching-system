import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { api, type ShareholderInfoItem } from "./api/client";
import type { CancelOrderDTO, SubmitOrderDTO, TradeReturnVO } from "./types/server";
import { connectTradeReturnWS, disconnectTradeReturnWS } from "./services/ws";
import BannerCarousel from "./components/BannerCarousel";
import TopHeader from "./components/TopHeader";
import MarketData from "./components/MarketData";
import StockDetail from "./components/StockDetail";
import AuthModal from "./components/auth/AuthModal";

/**
 * 订单状态枚举。
 * 使用受控常量代替自由字符串，避免拼写错误与非法状态流转。
 */
const OrderStatus = {
  Pending: "Pending",
  Acked: "Acked",
  PartiallyFilled: "Partially Filled",
  Filled: "Filled",
  Canceling: "Canceling",
  Canceled: "Canceled",
  Rejected: "Rejected",
} as const;

type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];


/** 买卖方向。 */
type Side = "B" | "S";

/** 订单回报事件类型。 */
type OrderEventType =
  | "ORDER_ACK"
  | "ORDER_PARTIAL_FILL"
  | "ORDER_FILL"
  | "CANCEL_REQUEST"
  | "CANCEL_ACK";

/**
 * 状态机输入事件。
 * 部成/全成事件可携带成交数量与成交价格。
 */
interface SomeEventType {
  type: OrderEventType;
  fillQty?: number;
  fillPrice?: number;
}

/** 前端订单实体。 */
interface Order {
  clOrderId: string;
  market: string;
  securityId: string;
  side: Side;
  qty: number;
  price: number;
  shareholderId: string;
  status: OrderStatus;
  cumQty: number;
  execPrice: number;
  canceledQty?: number;
  errorMsg?: string;
  errorCode?: number;
}

/** 下单表单数据。 */
interface OrderForm {
  market: string;
  securityId: string;
  side: Side;
  qty: number;
  price: number;
  shareholderId: string;
}

interface ApiResult<T> {
  success?: boolean;
  data?: T;
  errorMsg?: string | { rejectText?: string };
}

type SearchMode = "all" | "security" | "single";
const QUERY_BASE = import.meta.env?.VITE_QUERY_API_BASE || "/trade-query";
const ANALYSE_PAGE_SESSION_KEY = "stms.showAnalysePage";

/** 生成客户端订单号。 */
const generateId = (): string => {
  const now = Date.now().toString();
  const rand = Math.floor(Math.random() * 1e8)
    .toString()
    .padStart(8, "0");
  return (now + rand).slice(-16);
};

/** 判断订单是否已进入终态。 */
const isFinalStatus = (status: OrderStatus): boolean =>
  status === OrderStatus.Filled ||
  status === OrderStatus.Canceled ||
  status === OrderStatus.Rejected;

const tradeReturnStatusMap: Record<string, OrderStatus> = {
  PENDING: OrderStatus.Pending,
  ACKED: OrderStatus.Acked,
  ORDER_ACK: OrderStatus.Acked,
  PARTIALLY_FILLED: OrderStatus.PartiallyFilled,
  PARTIAL_FILLED: OrderStatus.PartiallyFilled,
  ORDER_PARTIAL_FILL: OrderStatus.PartiallyFilled,
  FILLED: OrderStatus.Filled,
  ORDER_FILL: OrderStatus.Filled,
  CANCELING: OrderStatus.Canceling,
  CANCEL_REQUEST: OrderStatus.Canceling,
  CANCELED: OrderStatus.Canceled,
  CANCELLED: OrderStatus.Canceled,
  CANCEL_ACK: OrderStatus.Canceled,
  REJECTED: OrderStatus.Rejected,
  REJECT: OrderStatus.Rejected,
};

const resolveOrderStatusFromTradeReturn = (status?: string): OrderStatus | null => {
  if (!status) return null;
  const key = status.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return tradeReturnStatusMap[key] ?? null;
};

const statusPriority: Partial<Record<OrderStatus, number>> = {
  [OrderStatus.Pending]: 0,
  [OrderStatus.Acked]: 1,
  [OrderStatus.PartiallyFilled]: 2,
  [OrderStatus.Filled]: 3,
};

const applyTradeReturnStatus = (
  order: Order,
  nextStatus: OrderStatus,
  ret: TradeReturnVO
): Order => {
  if (isFinalStatus(order.status)) {
    return order;
  }

  const newCumQty = Math.max(
    order.cumQty,
    ret.cumQty ?? (order.cumQty + (ret.execQty ?? 0))
  );
  const syncedCumQty = Math.max(0, Math.min(order.qty, newCumQty));

  const currentPriority = statusPriority[order.status];
  const nextPriority = statusPriority[nextStatus];
  const canEvolveByPriority =
    currentPriority === undefined ||
    nextPriority === undefined ||
    nextPriority >= currentPriority;

  const shouldUpdateStatus =
    canEvolveByPriority ||
    nextStatus === OrderStatus.Canceling ||
    nextStatus === OrderStatus.Canceled ||
    nextStatus === OrderStatus.Rejected;

  const baseOrder: Order = ret.execPrice !== undefined
    ? { ...order, execPrice: ret.execPrice }
    : order;
  const rejectedOrder: Order = {
    ...baseOrder,
    status: OrderStatus.Rejected,
    errorMsg: ret.rejectText ?? baseOrder.errorMsg,
    errorCode: ret.rejectCode ?? baseOrder.errorCode,
  };

  switch (nextStatus) {
    case OrderStatus.Pending:
    case OrderStatus.Acked:
    case OrderStatus.Canceling:
      return shouldUpdateStatus ? { ...baseOrder, status: nextStatus } : baseOrder;
    case OrderStatus.Rejected:
      return shouldUpdateStatus ? rejectedOrder : baseOrder;
    case OrderStatus.PartiallyFilled:
      return shouldUpdateStatus
        ? { ...baseOrder, status: OrderStatus.PartiallyFilled, cumQty: syncedCumQty }
        : { ...baseOrder, cumQty: syncedCumQty };
    case OrderStatus.Filled:
      return {
        ...baseOrder,
        status: shouldUpdateStatus ? OrderStatus.Filled : baseOrder.status,
        cumQty: Math.max(syncedCumQty, shouldUpdateStatus ? order.qty : syncedCumQty),
        execPrice: ret.execPrice ?? baseOrder.execPrice ?? order.price,
        canceledQty: 0,
      };
    case OrderStatus.Canceled:
      return {
        ...baseOrder,
        status: shouldUpdateStatus ? OrderStatus.Canceled : baseOrder.status,
        cumQty: syncedCumQty,
        canceledQty: Math.max(0, order.qty - syncedCumQty),
      };
    default:
      return baseOrder;
  }
};

/**
 * 按事件驱动订单状态流转。
 *
 * 规则：
 * 1. cumQty < qty => 部成
 * 2. cumQty == qty => 全成
 * 3. 已撤单/废单后不再进入成交态
 * 4. 撤单确认时计算剩余撤单量
 */
function updateOrderStatus(o: Order, event: SomeEventType): Order {
  switch (event.type) {
    case "ORDER_ACK": {
      if (o.status === OrderStatus.Pending) {
        return { ...o, status: OrderStatus.Acked };
      }
      return o;
    }

    case "ORDER_PARTIAL_FILL": {
      if (o.status === OrderStatus.Canceled || o.status === OrderStatus.Rejected) {
        return o;
      }

      const delta = Math.max(0, event.fillQty ?? 0);
      if (delta === 0) {
        return o;
      }

      const nextCumQty = Math.min(o.qty, o.cumQty + delta);
      if (nextCumQty <= o.cumQty) {
        return o;
      }

      const nextStatus =
        nextCumQty === o.qty ? OrderStatus.Filled : OrderStatus.PartiallyFilled;

      return {
        ...o,
        cumQty: nextCumQty,
        execPrice: event.fillPrice ?? o.execPrice,
        status: nextStatus,
        canceledQty: nextStatus === OrderStatus.Filled ? 0 : o.canceledQty,
      };
    }

    case "ORDER_FILL": {
      if (o.status === OrderStatus.Canceled || o.status === OrderStatus.Rejected) {
        return o;
      }

      if (o.cumQty >= o.qty) {
        return { ...o, status: OrderStatus.Filled, canceledQty: 0 };
      }

      return {
        ...o,
        status: OrderStatus.Filled,
        cumQty: o.qty,
        execPrice: event.fillPrice ?? o.price,
        canceledQty: 0,
      };
    }

    case "CANCEL_REQUEST": {
      if (isFinalStatus(o.status)) {
        return o;
      }
      return { ...o, status: OrderStatus.Canceling };
    }

    case "CANCEL_ACK": {
      if (o.status === OrderStatus.Canceled) {
        return o;
      }

      if (o.cumQty >= o.qty) {
        return { ...o, status: OrderStatus.Filled, canceledQty: 0 };
      }

      return {
        ...o,
        status: OrderStatus.Canceled,
        canceledQty: Math.max(0, o.qty - o.cumQty),
      };
    }

    default:
      return o;
  }
}

/** 根据状态返回展示标签样式。 */
const getStatusClassName = (status: OrderStatus): string => {
  switch (status) {
    case OrderStatus.Filled:
      return "bg-green-100 text-green-700";
    case OrderStatus.PartiallyFilled:
      return "bg-orange-100 text-orange-700";
    case OrderStatus.Canceled:
      return "bg-gray-200 text-gray-700";
    case OrderStatus.Canceling:
      return "bg-yellow-100 text-yellow-700";
    case OrderStatus.Acked:
      return "bg-blue-100 text-blue-700";
    case OrderStatus.Rejected:
      return "bg-red-100 text-red-700";
    case OrderStatus.Pending:
    default:
      return "bg-slate-100 text-slate-700";
  }
};

interface HistoryPoint {
  day: string;
  buyVolume: number;
  sellVolume: number;
}

interface MyStockSummary {
  securityId: string;
  filledBuyQty: number;
  filledSellQty: number;
  netHolding: number;
  totalOrderCount: number;
}

interface AnalysisDataState {
  qtySeries: number[];
  securityAmountSeries: number[];
  shareholderAmountSeries: number[];
}

const toSafeNum = (value: unknown): number => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const normalizeHistory = (raw: unknown): HistoryPoint[] => {
  const rows = Array.isArray(raw)
    ? raw
    : raw && typeof raw === "object"
      ? (raw as Record<string, unknown>).records ??
        (raw as Record<string, unknown>).list ??
        (raw as Record<string, unknown>).items ??
        (raw as Record<string, unknown>).data ??
        []
      : [];

  if (!Array.isArray(rows)) return [];

  return rows
    .map((item, index) => {
      const row = (item ?? {}) as Record<string, unknown>;
      const dayRaw = row.day ?? row.date ?? row.tradeDate ?? row.tradingDate;
      const day = String(dayRaw ?? `D${index + 1}`);
      const buyVolume = toSafeNum(
        row.buyVolume ?? row.buyQty ?? row.buy_quantity ?? row.buy ?? row.buyAmt
      );
      const sellVolume = toSafeNum(
        row.sellVolume ?? row.sellQty ?? row.sell_quantity ?? row.sell ?? row.sellAmt
      );
      return { day, buyVolume, sellVolume };
    })
    .slice(-30);
};

const toNumberSeries = (csv: string | undefined): number[] =>
  String(csv ?? "")
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((n) => Number.isFinite(n));

const buildSmoothPath = (points: Array<{ x: number; y: number }>): string => {
  if (points.length === 0) return "";
  if (points.length === 1) {
    const p = points[0];
    return `M ${p.x} ${p.y}`;
  }

  let d = `M ${points[0].x} ${points[0].y}`;
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1];
    const curr = points[i];
    const cx = (prev.x + curr.x) / 2;
    const cy = (prev.y + curr.y) / 2;
    d += ` Q ${prev.x} ${prev.y} ${cx} ${cy}`;
  }
  const last = points[points.length - 1];
  const beforeLast = points[points.length - 2];
  d += ` Q ${beforeLast.x} ${beforeLast.y} ${last.x} ${last.y}`;
  return d;
};

function ProfileLineChart({
  title,
  color,
  values,
  emptyText,
}: {
  title: string;
  color: string;
  values: number[];
  emptyText: string;
}) {
  const width = 260;
  const height = 72;
  const padX = 6;
  const padY = 8;
  const data = values.length > 0 ? values : [0];
  const maxValue = Math.max(...data);
  const minValue = Math.min(...data);
  const range = Math.max(1, maxValue - minValue);
  const stepX = data.length > 1 ? (width - padX * 2) / (data.length - 1) : width / 2;
  const toY = (value: number): number =>
    height - padY - ((value - minValue) / range) * (height - padY * 2);
  const points = data.map((value, idx) => ({ x: padX + idx * stepX, y: toY(value) }));
  const path = buildSmoothPath(points);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-2">
      <div className="text-xs text-slate-600 mb-1">{title}</div>
      <div className="h-[72px]">
        {values.length === 0 ? (
          <div className="h-full flex items-center justify-center text-xs text-slate-400">
            {emptyText}
          </div>
        ) : (
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-full" preserveAspectRatio="none">
            <path d={path} fill="none" stroke={color} strokeWidth="2.2" strokeLinecap="round" />
          </svg>
        )}
      </div>
    </div>
  );
}

function OrderChart({ securityId }: { securityId: string }) {
  const [series, setSeries] = useState<HistoryPoint[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const loadHistory = async (): Promise<void> => {
      setLoading(true);
      try {
        const params = new URLSearchParams({ securityId });
        const resp = await fetch(`${QUERY_BASE}/history?${params.toString()}`, {
          method: "GET",
          credentials: "include",
          signal: controller.signal,
        });
        const raw = (await resp.json()) as unknown;
        const payload =
          raw && typeof raw === "object" && "success" in raw
            ? (((raw as ApiResult<unknown>).data as unknown) ?? [])
            : raw;
        setSeries(normalizeHistory(payload));
      } catch {
        if (controller.signal.aborted) return;
        setSeries([]);
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    };

    void loadHistory();
    return () => controller.abort();
  }, [securityId]);

  const chart = useMemo(() => {
    const width = 340;
    const height = 68;
    const padX = 4;
    const padY = 6;
    const data = series.length > 0 ? series : [{ day: "flat", buyVolume: 0, sellVolume: 0 }];
    const maxValue = Math.max(
      1,
      ...data.map((d) => d.buyVolume),
      ...data.map((d) => d.sellVolume)
    );
    const stepX = data.length > 1 ? (width - padX * 2) / (data.length - 1) : width / 2;
    const toY = (value: number): number =>
      height - padY - (value / maxValue) * (height - padY * 2);

    const buyPoints = data.map((d, idx) => ({ x: padX + idx * stepX, y: toY(d.buyVolume) }));
    const sellPoints = data.map((d, idx) => ({ x: padX + idx * stepX, y: toY(d.sellVolume) }));

    return {
      width,
      height,
      buyPath: buildSmoothPath(buyPoints),
      sellPath: buildSmoothPath(sellPoints),
      hasData: series.length > 0,
    };
  }, [series]);

  return (
    <div className="h-full w-full rounded-lg bg-slate-50/80 border border-slate-100 px-2 py-1 flex items-center justify-center">
      {loading ? (
        <span className="text-xs text-slate-400">Loading...</span>
      ) : (
        <div className="relative w-full h-full flex items-center justify-center">
          <svg
            viewBox={`0 0 ${chart.width} ${chart.height}`}
            className="w-full h-full"
            preserveAspectRatio="none"
          >
            <path d={chart.buyPath} fill="none" stroke="#2563eb" strokeWidth="2.2" strokeLinecap="round" />
            <path d={chart.sellPath} fill="none" stroke="#dc2626" strokeWidth="2.2" strokeLinecap="round" />
          </svg>
          {!chart.hasData && <span className="absolute text-[11px] text-slate-400">暂无趋势</span>}
        </div>
      )}
    </div>
  );
}

/**
 * 交易员终端页面。
 */
function MainPage() {
  /** 订单列表与页面状态。 */
  const [orders, setOrders] = useState<Order[]>([]);
  const [searchValue, setSearchValue] = useState("");
  const [activeSearchValue, setActiveSearchValue] = useState("");
  const [searchMode, setSearchMode] = useState<SearchMode>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);
  const [totalOrders, setTotalOrders] = useState<number | null>(null);
  const [loadingOrderList, setLoadingOrderList] = useState(false);
  const [listReloadToken, setListReloadToken] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [lang, setLang] = useState<"en" | "zh">('en');
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [activeAuthTab, setActiveAuthTab] = useState<"login" | "register">('login');
  const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('authToken'));
  const [currentUser, setCurrentUser] = useState<string | null>(localStorage.getItem('shareholderId'));
  const [showProfilePage, setShowProfilePage] = useState(false);
  const [showAnalysePage, setShowAnalysePage] = useState<boolean>(() => {
    try {
      return sessionStorage.getItem(ANALYSE_PAGE_SESSION_KEY) === "1";
    } catch {
      return false;
    }
  });
  const [userInfoRows, setUserInfoRows] = useState<ShareholderInfoItem[]>([]);
  const [loadingUserInfo, setLoadingUserInfo] = useState(false);
  const [userInfoError, setUserInfoError] = useState<string | null>(null);
  const [analysisData, setAnalysisData] = useState<AnalysisDataState>({
    qtySeries: [],
    securityAmountSeries: [],
    shareholderAmountSeries: [],
  });
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const cancelingOrderIdsRef = useRef<Set<string>>(new Set());
  const cancelPrevStatusRef = useRef<Map<string, OrderStatus>>(new Map());

  /** 下单表单状态。 */
  const [form, setForm] = useState<OrderForm>({
    market: "XSHG",
    securityId: "600030",
    side: "B",
    qty: 100,
    price: 20.5,
    shareholderId: localStorage.getItem("shareholderId") ?? "",
  });

  /** 当前用户 ID（以股东号作为订阅维度）。 */
  const currentUserId = useMemo(() => currentUser ?? "", [currentUser]);
  const wsBoundRef = useRef(false);

  /**
   * 向单个订单派发事件。
   * 所有状态变化统一通过状态机处理。
   */
  const emitOrderEvent = useCallback((clOrderId: string, event: SomeEventType): void => {
    setOrders((prev) =>
      prev.map((o) => (o.clOrderId === clOrderId ? updateOrderStatus(o, event) : o))
    );
  }, []);

  /** 处理后端回报，并同步到前端状态。 */
  const handleTradeReturn = useCallback((ret: TradeReturnVO) => {
    const orderId = ret.origClOrderId || ret.clOrderId || "";
    if (!orderId) return;

    const statusKey = (ret.status ?? "")
      .trim()
      .toUpperCase()
      .replace(/[\s-]+/g, "_");

    const isCancelSuccess =
      statusKey === "CANCEL_ACK" || statusKey === "CANCELED" || statusKey === "CANCELLED";
    const isCancelReject =
      statusKey === "CANCEL_REJECT" ||
      statusKey === "CANCEL_REJECTED" ||
      statusKey === "CANCEL_FAIL" ||
      statusKey === "CANCEL_FAILED";
    const isCancelRelated = isCancelSuccess || isCancelReject || statusKey === "CANCELING";
    const isExecutionRelated =
      statusKey === "EXECUTION" ||
      statusKey === "PARTIAL" ||
      statusKey === "PARTIAL_FILL" ||
      statusKey === "ORDER_PARTIAL_FILL" ||
      statusKey === "PARTIALLY_FILLED" ||
      statusKey === "FILL" ||
      statusKey === "ORDER_FILL" ||
      statusKey === "FILLED";
    const isRejected = statusKey === "REJECTED";

    setOrders((prev) => {
      const index = prev.findIndex((o) => o.clOrderId === orderId);
      if (index < 0) return prev;

      const current = prev[index];
      const rejectRet = ret as TradeReturnVO & { errorMsg?: string; errorCode?: number };
      const syncCumQty = Math.max(
        0,
        Math.min(current.qty, ret.cumQty ?? current.cumQty)
      );
      let nextOrder = current;

      if (isCancelRelated) {
        cancelingOrderIdsRef.current.delete(orderId);
      }

      if (isFinalStatus(current.status)) {
        return prev;
      }

      if (isRejected) {
        nextOrder = {
          ...current,
          status: OrderStatus.Rejected,
          errorMsg: rejectRet.errorMsg ?? ret.rejectText ?? current.errorMsg,
          errorCode: rejectRet.errorCode ?? ret.rejectCode ?? current.errorCode,
        };
      } else if (isCancelSuccess) {
        cancelPrevStatusRef.current.delete(orderId);
        nextOrder = {
          ...current,
          status: OrderStatus.Canceled,
          cumQty: syncCumQty,
          canceledQty: Math.max(0, current.qty - syncCumQty),
        };
      } else if (isCancelReject) {
        const previousStatus = cancelPrevStatusRef.current.get(orderId);
        const rollbackStatus =
          current.status === OrderStatus.Canceling
            ? current.cumQty > 0
              ? OrderStatus.PartiallyFilled
              : previousStatus ?? current.status
            : current.status;
        cancelPrevStatusRef.current.delete(orderId);

        nextOrder = {
          ...current,
          status: rollbackStatus,
          cumQty: current.cumQty,
          canceledQty: rollbackStatus === OrderStatus.Filled ? 0 : current.canceledQty,
          errorMsg: rejectRet.errorMsg ?? current.errorMsg,
        };
      } else if (isExecutionRelated) {
        const nextCumQty = Math.max(
          current.cumQty,
          Math.max(
            0,
            Math.min(current.qty, ret.cumQty ?? (current.cumQty + (ret.execQty ?? 0)))
          )
        );
        const nextStatus =
          nextCumQty >= current.qty
            ? OrderStatus.Filled
            : nextCumQty > 0
              ? OrderStatus.PartiallyFilled
              : current.status;

        nextOrder = {
          ...current,
          status: nextStatus,
          cumQty: nextCumQty,
          execPrice: ret.execPrice ?? current.execPrice,
          canceledQty: nextStatus === OrderStatus.Filled ? 0 : current.canceledQty,
        };
      } else {
        const mappedStatus = resolveOrderStatusFromTradeReturn(ret.status);
        if (!mappedStatus) return prev;
        nextOrder = applyTradeReturnStatus(current, mappedStatus, ret);
      }

      if (nextOrder === current) return prev;
      const next = [...prev];
      next[index] = nextOrder;
      return next;
    });
  }, []);

  /** 建立 WebSocket 订阅并接收回报。 */
  useEffect(() => {
    if (!currentUserId || wsBoundRef.current) return;
    
    // 使用回调方式接入 WS 推送
    connectTradeReturnWS({
      userId: currentUserId,
      handleTradeReturn: (ret) => {
        // 直接将解析后的 VO 传给业务处理函数
        handleTradeReturn(ret);
      },
      onStatusChange: (connected) => {
        setWsConnected(connected);
        console.log(`WebSocket connection status: ${connected ? 'connected' : 'disconnected'}`);
      },
    });
    
    wsBoundRef.current = true;
    return () => {
      disconnectTradeReturnWS();
      setWsConnected(false);
      wsBoundRef.current = false;
    };
  }, [currentUserId, handleTradeReturn]);

  useEffect(() => {
    const nextCancelingIds = new Set<string>();
    for (const o of orders) {
      if (o.status === OrderStatus.Canceling && cancelingOrderIdsRef.current.has(o.clOrderId)) {
        nextCancelingIds.add(o.clOrderId);
      }
    }
    cancelingOrderIdsRef.current = nextCancelingIds;
  }, [orders]);

  // 记住分析页开关，避免浏览器刷新后回到首页
  useEffect(() => {
    try {
      if (showAnalysePage) {
        sessionStorage.setItem(ANALYSE_PAGE_SESSION_KEY, "1");
      } else {
        sessionStorage.removeItem(ANALYSE_PAGE_SESSION_KEY);
      }
    } catch {
      // 忽略 sessionStorage 不可用场景
    }
  }, [showAnalysePage]);

  /**
   * 提交新订单，调用后端 /trade。
   * 订单状态以后端回报为准。
   */
  const submitOrder = async (): Promise<void> => {
    setErrorMsg(null);
    setCurrentPage(1);
    setTotalOrders((prev) => (prev === null ? null : prev + 1));
    const newOrder: Order = {
      ...form,
      clOrderId: generateId(),
      status: OrderStatus.Pending,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 0,
    };
    setOrders((prev) => [newOrder, ...prev].slice(0, pageSize));

    const payload: SubmitOrderDTO = {
      clOrderId: newOrder.clOrderId,
      market: newOrder.market,
      securityId: newOrder.securityId,
      side: newOrder.side,
      qty: newOrder.qty,
      price: newOrder.price,
      shareholderId: newOrder.shareholderId,
    };

    setSubmitting(true);
    try {
        const resp = await api.submitOrder(payload);  // 获取响应对象
        // 根据后端返回状态更新前端展示状态
        if (resp.status) {
            // 映射后端状态到前端状态枚举
            let frontendStatus: OrderStatus = OrderStatus.Pending;  // 显式类型注解
            switch (resp.status) {
            case "PENDING":
                frontendStatus = OrderStatus.Pending;
                break;
            case "ACKED":
                frontendStatus = OrderStatus.Acked;
                break;
            case "PARTIALLY_FILLED":
                frontendStatus = OrderStatus.PartiallyFilled;
                break;
            case "FILLED":
                frontendStatus = OrderStatus.Filled;
                break;
            case "CANCELED":
                frontendStatus = OrderStatus.Canceled;
                break;
            case "REJECTED":
                frontendStatus = OrderStatus.Rejected;
                break;
            }
            const respWithReject = resp as { rejectText?: string; rejectCode?: number };
            setOrders((prev) =>
            prev.map((o) =>
                o.clOrderId === newOrder.clOrderId
                ? isFinalStatus(o.status)
                  ? o
                  : (() => {
                      const currentPriority =
                        o.status === OrderStatus.Canceling ? 3 : statusPriority[o.status];
                      const nextPriority = statusPriority[frontendStatus];
                      const isStatusRollback =
                        currentPriority !== undefined &&
                        nextPriority !== undefined &&
                        nextPriority < currentPriority;
                      if (isStatusRollback) {
                        return o;
                      }
                      return {
                        ...o,
                        status: frontendStatus,
                        errorMsg:
                          frontendStatus === OrderStatus.Rejected
                            ? (respWithReject.rejectText ?? o.errorMsg)
                            : o.errorMsg,
                        errorCode:
                          frontendStatus === OrderStatus.Rejected
                            ? (respWithReject.rejectCode ?? o.errorCode)
                            : o.errorCode,
                      };
                    })()
                : o
            )
            );
        }
    } catch (e: unknown) {
      let msg = "涓嬪崟澶辫触";
      if (e instanceof Error) {
        msg = e.message;
      } else if (typeof e === "string") {
        msg = e;
      }
      setErrorMsg(msg);
      setOrders((prev) =>
        prev.map((o) =>
          o.clOrderId === newOrder.clOrderId
            ? isFinalStatus(o.status)
              ? o
              : { ...o, errorMsg: msg }
            : o
        )
      );
    } finally {
      setSubmitting(false);
    }
  };

  /** 发起撤单请求，最终状态以后端回报为准。 */
  const handleCancel = async (origOrder: Order): Promise<void> => {
    setErrorMsg(null);
    const clOrderId = origOrder.clOrderId;
    const latestOrder = orders.find((o) => o.clOrderId === clOrderId) ?? origOrder;
    const originalStatus = latestOrder.status;

    if (
      originalStatus === OrderStatus.Canceling ||
      cancelingOrderIdsRef.current.has(clOrderId)
    ) {
      return;
    }

    cancelingOrderIdsRef.current.add(clOrderId);
    cancelPrevStatusRef.current.set(clOrderId, originalStatus);
    emitOrderEvent(clOrderId, { type: "CANCEL_REQUEST" });

    const payload: CancelOrderDTO = {
      clOrderId: generateId(),
      origClOrderId: clOrderId,
      market: origOrder.market,
      securityId: origOrder.securityId,
      side: origOrder.side,
      qty: origOrder.qty,
      price: origOrder.price,
      shareholderId: origOrder.shareholderId,
    };

    try {
      const resp = await api.cancelOrder(payload);

      if (resp?.status?.trim().toUpperCase() === "REJECTED") {
        cancelingOrderIdsRef.current.delete(clOrderId);
        const previousStatus = cancelPrevStatusRef.current.get(clOrderId);
        cancelPrevStatusRef.current.delete(clOrderId);
        setOrders((prev) =>
          prev.map((o) =>
            o.clOrderId === clOrderId
              ? isFinalStatus(o.status)
                ? o
                : (() => {
                    const stableStatus =
                      o.status === OrderStatus.Canceling
                        ? previousStatus ?? o.status
                        : o.status;
                    const currentStablePriority = statusPriority[stableStatus];
                    const targetPriority = statusPriority[stableStatus];
                    const rollbackStatus =
                      currentStablePriority !== undefined &&
                      targetPriority !== undefined &&
                      targetPriority >= currentStablePriority
                        ? stableStatus
                        : o.status;
                    return {
                      ...o,
                      status: rollbackStatus,
                      canceledQty: o.canceledQty,
                      errorMsg: resp.rejectText ?? o.errorMsg,
                      errorCode: resp.rejectCode ?? o.errorCode,
                    };
                  })()
              : o
          )
        );
      } else {
        setTimeout(() => {
          if (!cancelingOrderIdsRef.current.has(clOrderId)) return;
          cancelingOrderIdsRef.current.delete(clOrderId);
          cancelPrevStatusRef.current.delete(clOrderId);
          setOrders((prev) =>
            prev.map((o) =>
              o.clOrderId === clOrderId && o.status === OrderStatus.Canceling
                ? { ...o, status: originalStatus }
                : o
            )
          );
        }, 5000);
      }
    } catch (e: unknown) {
      let msg = "鎾ゅ崟澶辫触";
      if (e instanceof Error) {
        msg = e.message;
      } else if (typeof e === "string") {
        msg = e;
      }
      cancelingOrderIdsRef.current.delete(clOrderId);
      cancelPrevStatusRef.current.delete(clOrderId);
      setOrders((prev) =>
        prev.map((o) =>
          o.clOrderId === clOrderId && o.status === OrderStatus.Canceling
            ? { ...o, status: originalStatus }
            : o
        )
      );
      setErrorMsg(msg);
    }
  };

  /** 判断订单是否允许撤单。 */
  const canCancel = (order: Order): boolean =>
    order.status === OrderStatus.Pending ||
    order.status === OrderStatus.Acked ||
    order.status === OrderStatus.PartiallyFilled;

  const toggleLang = useCallback(() => {
    setLang((prev) => (prev === "en" ? "zh" : "en"));
  }, []);

  const handleLoginSuccess = useCallback(() => {
    const loginUser = localStorage.getItem("shareholderId");
    setIsLoggedIn(true);
    setCurrentUser(loginUser);
    setForm((prev) => ({
      ...prev,
      shareholderId: loginUser ?? "",
    }));
  }, []);

  const handleRegisterSuccess = useCallback(() => {
    const loginUser = localStorage.getItem("shareholderId");
    setIsLoggedIn(true);
    setCurrentUser(loginUser);
    setForm((prev) => ({
      ...prev,
      shareholderId: loginUser ?? "",
    }));
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('shareholderId');
    setIsLoggedIn(false);
    setCurrentUser(null);
    setShowProfilePage(false);
    setShowAnalysePage(false);
    setUserInfoRows([]);
    setUserInfoError(null);
    setAnalysisError(null);
    setAnalysisData({
      qtySeries: [],
      securityAmountSeries: [],
      shareholderAmountSeries: [],
    });
    setForm((prev) => ({
      ...prev,
      shareholderId: "",
    }));
  }, []);

  const loadUserInfo = useCallback(async () => {
    if (!isLoggedIn) {
      setUserInfoRows([]);
      setUserInfoError(lang === "en" ? "Please login first." : "请先登录。");
      return;
    }
    setLoadingUserInfo(true);
    setUserInfoError(null);
    try {
      const rows = await api.getUserInfo();
      setUserInfoRows(Array.isArray(rows) ? rows : []);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : lang === "en" ? "Load failed" : "加载失败";
      setUserInfoError(msg);
      setUserInfoRows([]);
    } finally {
      setLoadingUserInfo(false);
    }
  }, [isLoggedIn, lang]);

  const handleOpenProfile = useCallback(() => {
    if (!isLoggedIn) return;
    setShowAnalysePage(false);
    setShowProfilePage(true);
    void loadUserInfo();
  }, [isLoggedIn, loadUserInfo]);

  const handleCloseProfile = useCallback(() => {
    setShowProfilePage(false);
  }, []);

  const loadAnalysisData = useCallback(async () => {
    setAnalysisLoading(true);
    setAnalysisError(null);
    try {
      const formatDate = (date: Date): string => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
      };
      const endDate = new Date();
      const startDate = new Date(endDate);
      startDate.setDate(endDate.getDate() - 29);
      const startTime = formatDate(startDate);
      const endTime = formatDate(endDate);

      const [qtyResp, securityResp, shareholderResp] = await Promise.all([
        api.getTop10Qty(startTime, endTime),
        api.getTop10SecurityAmount(startTime, endTime),
        api.getTop10ShareholderAmount(startTime, endTime),
      ]);
      setAnalysisData({
        qtySeries: toNumberSeries(qtyResp?.cumQty),
        securityAmountSeries: toNumberSeries(securityResp?.amount),
        shareholderAmountSeries: toNumberSeries(shareholderResp?.amount),
      });
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : lang === "en" ? "Load failed" : "加载失败";
      setAnalysisError(msg);
      setAnalysisData({
        qtySeries: [],
        securityAmountSeries: [],
        shareholderAmountSeries: [],
      });
    } finally {
      setAnalysisLoading(false);
    }
  }, [lang]);

  const handleOpenAnalyse = useCallback(() => {
    setShowProfilePage(false);
    setShowAnalysePage(true);
    void loadAnalysisData();
  }, [loadAnalysisData]);

  const handleCloseAnalyse = useCallback(() => {
    setShowAnalysePage(false);
  }, []);

  const handleOpenLogin = useCallback(() => {
    setActiveAuthTab('login');
    setIsAuthModalOpen(true);
  }, []);

  const handleOpenRegister = useCallback(() => {
    setActiveAuthTab('register');
    setIsAuthModalOpen(true);
  }, []);

  const toOrder = useCallback((raw: Partial<Order> & Record<string, unknown>): Order => {
    const sideRaw = String(raw.side ?? "B").toUpperCase();
    const side: Side = sideRaw === "S" || sideRaw === "SELL" ? "S" : "B";
    const remainingQtyRaw = Number(raw.remainingQty ?? raw.remaining_qty ?? 0);
    const qtyRaw = Number(raw.qty ?? remainingQtyRaw ?? 0);
    const qty = Number.isFinite(qtyRaw) ? Math.max(0, qtyRaw) : 0;
    const cumQtyRaw = Number(raw.cumQty ?? 0);
    const cumQty = Number.isFinite(cumQtyRaw)
      ? Math.max(0, Math.min(qty || cumQtyRaw, cumQtyRaw))
      : 0;
    const mappedStatus =
      resolveOrderStatusFromTradeReturn(typeof raw.status === "string" ? raw.status : undefined) ??
      OrderStatus.Pending;

    return {
      clOrderId: String(raw.clOrderId ?? ""),
      market: String(raw.market ?? ""),
      securityId: String(raw.securityId ?? ""),
      side,
      qty,
      price: Number(raw.price ?? 0) || 0,
      shareholderId: String(raw.shareholderId ?? ""),
      status: mappedStatus,
      cumQty,
      execPrice: Number(raw.execPrice ?? 0) || 0,
      canceledQty: Number(raw.canceledQty ?? 0) || 0,
      errorMsg: typeof raw.errorMsg === "string" ? raw.errorMsg : undefined,
      errorCode:
        typeof raw.errorCode === "number" && Number.isFinite(raw.errorCode)
          ? raw.errorCode
          : undefined,
    };
  }, []);

  const unwrapApiData = useCallback(<T,>(raw: unknown): T => {
    if (raw && typeof raw === "object" && "success" in raw) {
      const wrapped = raw as ApiResult<T>;
      if (wrapped.success === false) {
        const msg =
          typeof wrapped.errorMsg === "string"
            ? wrapped.errorMsg
            : wrapped.errorMsg?.rejectText || "Request failed";
        throw new Error(msg);
      }
      return (wrapped.data as T) ?? ([] as T);
    }
    return raw as T;
  }, []);

  const fetchOrderList = useCallback(
    async (page: number, securityId?: string): Promise<void> => {
      setLoadingOrderList(true);
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("current", String(page));
      params.set("pageSize", String(pageSize));
      params.set("size", String(pageSize));
      if (securityId) {
        params.set("securityId", securityId);
      }

      try {
        const resp = await fetch(`${QUERY_BASE}/list?${params.toString()}`, {
          method: "GET",
          credentials: "include",
        });
        const raw = (await resp.json()) as unknown;
        const payload = unwrapApiData<unknown>(raw);

        let rows: unknown[] = [];
        let nextTotalOrders: number | null = null;
        if (Array.isArray(payload)) {
          rows = payload;
        } else if (payload && typeof payload === "object") {
          const data = payload as Record<string, unknown>;
          const totalRaw = Number(
            data.total ?? data.totalCount ?? data.totalElements ?? data.count
          );
          if (Number.isFinite(totalRaw) && totalRaw >= 0) {
            nextTotalOrders = totalRaw;
          }
          const candidates = [data.records, data.list, data.items, data.content];
          const found = candidates.find(Array.isArray);
          rows = Array.isArray(found) ? found : [];
        }

        const safeOrders = rows.map((item) =>
          toOrder((item ?? {}) as Partial<Order> & Record<string, unknown>)
        );
        setErrorMsg(null);
        setOrders(safeOrders);
        setTotalOrders(nextTotalOrders);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : "Query list failed";
        setErrorMsg(msg);
        // 保留当前分页与列表，避免瞬时请求失败把页码错误重置为 1/1
      } finally {
        setLoadingOrderList(false);
      }
    },
    [currentUser, isLoggedIn, pageSize, toOrder, unwrapApiData]
  );

  const fetchSingleOrder = useCallback(
    async (clOrderId: string): Promise<void> => {
      const params = new URLSearchParams();
      params.set("clOrderId", clOrderId);

      try {
        const resp = await fetch(`${QUERY_BASE}/query?${params.toString()}`, {
          method: "GET",
          credentials: "include",
        });
        const raw = (await resp.json()) as unknown;
        const payload = unwrapApiData<unknown>(raw);
        const order =
          payload && typeof payload === "object"
            ? toOrder(payload as Partial<Order> & Record<string, unknown>)
            : null;
        setErrorMsg(null);
        setOrders(order ? [order] : []);
        setTotalOrders(order ? 1 : 0);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : "Query order failed";
        setErrorMsg(msg);
        setOrders([]);
        setTotalOrders(0);
      }
    },
    [toOrder, unwrapApiData]
  );

  const text = useMemo(
    () =>
      lang === "en"
        ? {
            market: "Market",
            securityId: "Security ID",
            side: "Side",
            price: "Price",
            quantity: "Quantity",
            buy: "Buy",
            sell: "Sell",
            submitting: "Submitting...",
            submitOrder: "Submit Order",
            orderId: "Order ID",
            symbol: "Symbol",
            qty: "Qty",
            status: "Status",
            fillProgress: "Fill Progress",
            action: "Action",
            cancel: "Cancel",
            errorCode: "Error code",
          }
        : {
            market: "市场",
            securityId: "证券代码",
            side: "方向",
            price: "委托价格",
            quantity: "委托数量",
            buy: "买入",
            sell: "卖出",
            submitting: "提交中...",
            submitOrder: "提交订单",
            orderId: "订单号",
            symbol: "证券",
            qty: "数量",
            status: "状态",
            fillProgress: "成交进度",
            action: "操作",
            cancel: "撤单",
            errorCode: "错误码",
          },
    [lang]
  );

  const statusTextMap = useMemo<Record<OrderStatus, string>>(
    () =>
      lang === "en"
        ? {
            [OrderStatus.Pending]: "Pending",
            [OrderStatus.Acked]: "Acked",
            [OrderStatus.PartiallyFilled]: "Partially Filled",
            [OrderStatus.Filled]: "Filled",
            [OrderStatus.Canceling]: "Canceling",
            [OrderStatus.Canceled]: "Canceled",
            [OrderStatus.Rejected]: "Rejected",
          }
        : {
            [OrderStatus.Pending]: "待报",
            [OrderStatus.Acked]: "已报",
            [OrderStatus.PartiallyFilled]: "部成",
            [OrderStatus.Filled]: "全成",
            [OrderStatus.Canceling]: "撤单中",
            [OrderStatus.Canceled]: "已撤单",
            [OrderStatus.Rejected]: "废单",
          },
    [lang]
  );

  const knownTotalPages = useMemo(() => {
    if (searchMode === "single") return 1;
    if (totalOrders === null) return null;
    return Math.max(1, Math.ceil(totalOrders / pageSize));
  }, [pageSize, searchMode, totalOrders]);

  const canGoNext = useMemo(() => {
    if (searchMode === "single") return false;
    if (knownTotalPages !== null) return currentPage < knownTotalPages;
    return orders.length >= pageSize;
  }, [currentPage, knownTotalPages, orders.length, pageSize, searchMode]);

  // 右侧订单展示固定最多 10 条（避免二次分页导致空列表）
  const pagedOrders = useMemo(() => orders.slice(0, pageSize), [orders, pageSize]);

  const myStockSummaries = useMemo<MyStockSummary[]>(() => {
    const map = new Map<string, MyStockSummary>();
    for (const row of userInfoRows) {
      const key = row.securityId;
      const prev = map.get(key) ?? {
        securityId: key,
        filledBuyQty: 0,
        filledSellQty: 0,
        netHolding: 0,
        totalOrderCount: 0,
      };

      const filledQty = Math.max(0, Number(row.cumQty ?? 0));
      const side = String(row.side ?? "").toUpperCase();
      if (side === "B" || side === "BUY") {
        prev.filledBuyQty += filledQty;
      } else {
        prev.filledSellQty += filledQty;
      }
      prev.totalOrderCount += 1;
      prev.netHolding = prev.filledBuyQty - prev.filledSellQty;
      map.set(key, prev);
    }

    return Array.from(map.values()).sort((a, b) => a.securityId.localeCompare(b.securityId));
  }, [userInfoRows]);

  useEffect(() => {
    if (knownTotalPages === null) return;
    setCurrentPage((prev) => Math.min(prev, knownTotalPages));
  }, [knownTotalPages]);

  useEffect(() => {
    // 账号切换后先重置分页状态，避免沿用上一个账号的页数显示
    setCurrentPage(1);
    setTotalOrders(null);
  }, [currentUser, isLoggedIn]);

  useEffect(() => {
    if (searchMode === "single") return;
    const securityId =
      searchMode === "security" && activeSearchValue.length === 6 ? activeSearchValue : undefined;
    void fetchOrderList(currentPage, securityId);
  }, [activeSearchValue, currentPage, fetchOrderList, searchMode, listReloadToken]);

  const onSearch = useCallback(async () => {
    const keyword = searchValue.trim();
    setErrorMsg(null);
    setActiveSearchValue(keyword);
    setCurrentPage(1);

    if (!keyword) {
      setSearchMode("all");
      setTotalOrders(null);
      setListReloadToken((prev) => prev + 1);
      return;
    }

    if (keyword.length === 16) {
      setSearchMode("single");
      await fetchSingleOrder(keyword);
      return;
    }

    if (keyword.length === 6) {
      setSearchMode("security");
      setTotalOrders(null);
      setListReloadToken((prev) => prev + 1);
      return;
    }

    setSearchMode("all");
    setTotalOrders(null);
    setListReloadToken((prev) => prev + 1);
  }, [fetchSingleOrder, searchValue]);

  const onClearSearch = useCallback(() => {
    setErrorMsg(null);
    setSearchValue("");
    setActiveSearchValue("");
    setSearchMode("all");
    setCurrentPage(1);
    setTotalOrders(null);
    setListReloadToken((prev) => prev + 1);
  }, []);

  // 登录后始终绑定当前股东号，避免沿用旧账号的下单/WS上下文
  useEffect(() => {
    if (!isLoggedIn || !currentUser) return;
    setForm((prev) =>
      prev.shareholderId === currentUser ? prev : { ...prev, shareholderId: currentUser }
    );
  }, [currentUser, isLoggedIn]);

  if (showAnalysePage) {
    return (
      <div
        className="p-6 min-h-screen"
        style={{
          backgroundImage: "linear-gradient(to bottom, #93c5fd 0%, #e5f0ff 32%, #ffffff 100%)",
        }}
      >
        <div className="w-[95%] mx-auto">
          <button
            type="button"
            className="mb-4 h-10 w-10 rounded-full border border-slate-300 bg-white text-slate-700 text-xl hover:bg-slate-50"
            onClick={handleCloseAnalyse}
            aria-label={lang === "en" ? "Go back" : "返回"}
            title={lang === "en" ? "Go back" : "返回"}
          >
            ←
          </button>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h2 className="text-xl font-bold text-slate-900 mb-4">
              {lang === "en" ? "Analyse" : "分析"}
            </h2>
            <div className="mb-4 flex justify-end">
              <button
                type="button"
                className="h-9 px-3 rounded-lg border border-slate-300 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                onClick={() => void loadAnalysisData()}
                disabled={analysisLoading}
              >
                {analysisLoading
                  ? lang === "en"
                    ? "Loading..."
                    : "加载中..."
                  : lang === "en"
                    ? "Refresh"
                    : "刷新"}
              </button>
            </div>
            {analysisError && (
              <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {analysisError}
              </div>
            )}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
              <ProfileLineChart
                title={lang === "en" ? "Top10 Stocks by Volume" : "前十股票成交量"}
                color="#2563eb"
                values={analysisData.qtySeries}
                emptyText={lang === "en" ? "No data" : "暂无数据"}
              />
              <ProfileLineChart
                title={lang === "en" ? "Top10 Stocks by Amount" : "前十股票成交金额"}
                color="#dc2626"
                values={analysisData.securityAmountSeries}
                emptyText={lang === "en" ? "No data" : "暂无数据"}
              />
              <ProfileLineChart
                title={lang === "en" ? "Top10 Shareholders by Amount" : "前十股东成交金额"}
                color="#16a34a"
                values={analysisData.shareholderAmountSeries}
                emptyText={lang === "en" ? "No data" : "暂无数据"}
              />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (showProfilePage) {
    return (
      <div
        className="p-6 min-h-screen"
        style={{
          backgroundImage: "linear-gradient(to bottom, #93c5fd 0%, #e5f0ff 32%, #ffffff 100%)",
        }}
      >
        <div className="w-[95%] mx-auto">
          <button
            type="button"
            className="mb-4 h-10 w-10 rounded-full border border-slate-300 bg-white text-slate-700 text-xl hover:bg-slate-50"
            onClick={handleCloseProfile}
            aria-label={lang === "en" ? "Go back" : "返回"}
            title={lang === "en" ? "Go back" : "返回"}
          >
            ←
          </button>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 max-w-xl">
            <h2 className="text-xl font-bold text-slate-900 mb-4">
              {lang === "en" ? "Personal Information" : "个人信息"}
            </h2>
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <span className="inline-flex items-center px-2 py-1 rounded-md bg-blue-50 border border-blue-200 text-blue-700 text-sm">
                  用户
                </span>
                <span className="text-sm text-slate-700">{currentUser ?? "-"}</span>
              </div>

              <div className="rounded-lg border border-slate-200">
                <div className="px-3 py-2 border-b border-slate-200 bg-slate-50 text-sm font-semibold text-slate-700">
                  {lang === "en" ? "My Stocks" : "我的股票"}
                </div>
                {loadingUserInfo && (
                  <div className="px-3 py-3 text-sm text-slate-500">
                    {lang === "en" ? "Loading..." : "加载中..."}
                  </div>
                )}
                {userInfoError && (
                  <div className="px-3 py-3 text-sm text-red-600">{userInfoError}</div>
                )}
                {!loadingUserInfo && !userInfoError && myStockSummaries.length === 0 ? (
                  <div className="px-3 py-6 text-sm text-slate-500">
                    {lang === "en" ? "No stock records yet." : "暂无股票记录"}
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-sm">
                      <thead className="bg-white">
                        <tr className="text-slate-600">
                          <th className="text-left font-medium px-3 py-2">{lang === "en" ? "Symbol" : "代码"}</th>
                          <th className="text-right font-medium px-3 py-2">{lang === "en" ? "Buy(Filled)" : "买入(已成)"}</th>
                          <th className="text-right font-medium px-3 py-2">{lang === "en" ? "Sell(Filled)" : "卖出(已成)"}</th>
                          <th className="text-right font-medium px-3 py-2">{lang === "en" ? "Net Holding" : "净持仓"}</th>
                          <th className="text-right font-medium px-3 py-2">{lang === "en" ? "Orders" : "委托数"}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {myStockSummaries.map((item) => (
                          <tr key={item.securityId} className="border-t border-slate-100 text-slate-700">
                            <td className="px-3 py-2 font-semibold">{item.securityId}</td>
                            <td className="px-3 py-2 text-right">{item.filledBuyQty}</td>
                            <td className="px-3 py-2 text-right">{item.filledSellQty}</td>
                            <td className="px-3 py-2 text-right">{item.netHolding}</td>
                            <td className="px-3 py-2 text-right">{item.totalOrderCount}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

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
          onToggleLang={toggleLang}
          searchValue={searchValue}
          onSearchValueChange={(val) => setSearchValue(val)}
          onSearch={onSearch}
          onClearSearch={onClearSearch}
          isLoggedIn={isLoggedIn}
          currentUser={currentUser}
          onLoginClick={handleOpenLogin}
          onRegisterClick={handleOpenRegister}
          onLogoutClick={handleLogout}
          onUserClick={handleOpenProfile}
          onAnalyseClick={handleOpenAnalyse}
        />
      </div>

      <section className="w-[95%] mx-auto mb-6">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_minmax(630px,840px)_1fr] gap-1 items-stretch lg:min-h-[360px]">
          <div className="hidden lg:flex w-full max-w-[280px] h-[400px] justify-self-start rounded-2xl bg-white border border-slate-200 shadow-sm p-4 flex-col">
            <MarketData lang={lang} isAdmin={false} />
          </div>

          <div className="w-full h-[400px]">
            <BannerCarousel />
          </div>

          <div className="w-full lg:max-w-[280px] lg:justify-self-end lg:h-[400px]">
            <div className="bg-white p-4 rounded-lg shadow h-full flex flex-col min-h-0">
              <div className="text-sm font-semibold text-slate-700 mb-2">
                {lang === "en" ? "Order Display" : "委托展示"}
              </div>
              {errorMsg && (
                <div className="mb-2 bg-red-50 text-red-700 border border-red-200 px-2 py-1 rounded text-xs">
                  {errorMsg}
                </div>
              )}
              <div className="flex-1 min-h-0 overflow-y-auto space-y-2">
                {pagedOrders.length === 0 ? (
                  <div className="rounded border border-slate-100 text-slate-500 text-xs px-3 py-8 text-center">
                    No orders
                  </div>
                ) : (
                  pagedOrders.map((o, idx) => (
                    <div key={`${o.clOrderId}-${idx}`} className="rounded border border-slate-200 bg-slate-50 px-2 py-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-slate-800">{o.securityId}</span>
                        <span className={`px-2 py-0.5 rounded text-[11px] ${getStatusClassName(o.status)}`}>
                          {statusTextMap[o.status] ?? o.status}
                        </span>
                      </div>
                      <div className="text-[11px] text-slate-500 mt-1">
                        {o.side === "B" ? text.buy : text.sell} · Qty {o.qty} · Cum {o.cumQty}
                      </div>
                      {canCancel(o) && (
                        <button
                          className="mt-1 text-red-600 hover:underline text-xs"
                          onClick={() => handleCancel(o)}
                        >
                          {text.cancel}
                        </button>
                      )}
                    </div>
                  ))
                )}
              </div>
              <div className="mt-2 flex items-center justify-between gap-2">
                <button
                  className="px-2 py-1 border border-gray-300 rounded text-xs disabled:opacity-50"
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={loadingOrderList || searchMode === "single" || currentPage === 1}
                >
                  Previous
                </button>
                <span className="text-[11px] text-gray-700">
                  {currentPage}/{knownTotalPages ?? 1}
                </span>
                <button
                  className="px-2 py-1 border border-gray-300 rounded text-xs disabled:opacity-50"
                  onClick={() => setCurrentPage((p) => p + 1)}
                  disabled={loadingOrderList || !canGoNext}
                >
                  Next
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="w-[95%] mx-auto">
        <div className="bg-white p-4 md:p-5 rounded-xl shadow border border-slate-100">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3 items-end">
            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.market}</label>
              <select
                className="h-10 border border-gray-300 rounded px-3 text-sm bg-white"
                value={form.market}
                onChange={(e) => setForm({ ...form, market: e.target.value })}
              >
                <option value="XSHG">XSHG</option>
                <option value="XSHE">XSHE</option>
                <option value="BJSE">BJSE</option>
              </select>
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.securityId}</label>
              <input
                className="h-10 border border-gray-300 rounded px-3 text-sm"
                value={form.securityId}
                onChange={(e) => setForm({ ...form, securityId: e.target.value })}
              />
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.side}</label>
              <select
                className="h-10 border border-gray-300 rounded px-3 text-sm bg-white"
                value={form.side}
                onChange={(e) => setForm({ ...form, side: e.target.value as Side })}
              >
                <option value="B">{text.buy}</option>
                <option value="S">{text.sell}</option>
              </select>
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.price}</label>
              <input
                className="h-10 border border-gray-300 rounded px-3 text-sm"
                type="number"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
              />
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.quantity}</label>
              <input
                className="h-10 border border-gray-300 rounded px-3 text-sm"
                type="number"
                value={form.qty}
                onChange={(e) => setForm({ ...form, qty: Number(e.target.value) })}
              />
            </div>
          </div>

          <div className="mt-3">
            <button
              className={`h-10 w-full text-white rounded font-bold ${
                submitting ? "bg-blue-300 cursor-not-allowed" : "bg-blue-600 hover:bg-blue-700"
              }`}
              onClick={submitOrder}
              disabled={submitting}
            >
              {submitting ? text.submitting : text.submitOrder}
            </button>
          </div>

          {orders[0] && (
            <div className="mt-4 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
              <div className="text-xs text-slate-500 mb-1">
                {lang === "en" ? "Latest order trend" : "最新订单趋势"}
              </div>
              <OrderChart securityId={orders[0].securityId} />
            </div>
          )}
        </div>
      </div>
      
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
        onLoginSuccess={handleLoginSuccess}
        onRegisterSuccess={handleRegisterSuccess}
        lang={lang}
        initialTab={activeAuthTab}
      />
    </div>
  );
}

export default function AppRouter() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route path="/stock/:symbol" element={<StockDetail lang="zh" />} />
      </Routes>
    </Router>
  );
}

