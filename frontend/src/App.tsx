import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, type PublishQuotationDTO, type ShareholderInfoItem } from "./api/client";
import type { CancelOrderDTO, SubmitOrderDTO, TradeReturnVO } from "./types/server";
import { connectTradeReturnWS, disconnectTradeReturnWS } from "./services/ws";
import BannerCarousel from "./components/BannerCarousel";
import TopHeader from "./components/TopHeader";
import MarketData from "./components/MarketData";
import AuthModal from "./components/auth/AuthModal";
import AnalysisPage from "./pages/AnalysisPage";

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
const QUERY_BASE = "/trade-query";

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

/**
 * 交易员终端页面。
 */
export default function App() {
  /** 订单列表与页面状态。 */
  const [orders, setOrders] = useState<Order[]>([]);
  const [searchValue, setSearchValue] = useState("");
  const [activeSearchValue, setActiveSearchValue] = useState("");
  const [searchMode, setSearchMode] = useState<SearchMode>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);
  const [totalOrders, setTotalOrders] = useState<number | null>(null);
  const [listReloadToken, setListReloadToken] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [lang, setLang] = useState<"en" | "zh">('en');
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [activeAuthTab, setActiveAuthTab] = useState<"login" | "register">('login');
  const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('authToken'));
  const [currentUser, setCurrentUser] = useState<string | null>(localStorage.getItem('shareholderId'));
  const [pageMode, setPageMode] = useState<"trade" | "quotation-access" | "user-info" | "analysis">("trade");
  const [quotationForm, setQuotationForm] = useState<PublishQuotationDTO>({
    market: "XSHG",
    securityId: "600030",
    bidPrice: 20.5,
    askPrice: 20.6,
  });
  const [publishingQuotation, setPublishingQuotation] = useState(false);
  const [quotationMessage, setQuotationMessage] = useState<string | null>(null);
  const [depthLoading, setDepthLoading] = useState(false);
  const [depthError, setDepthError] = useState<string | null>(null);
  const [depthBuyOrders, setDepthBuyOrders] = useState<Order[]>([]);
  const [depthSellOrders, setDepthSellOrders] = useState<Order[]>([]);
  const [userInfoRows, setUserInfoRows] = useState<ShareholderInfoItem[]>([]);
  const [loadingUserInfo, setLoadingUserInfo] = useState(false);
  const [userInfoMessage, setUserInfoMessage] = useState<string | null>(null);
  const cancelingOrderIdsRef = useRef<Set<string>>(new Set());
  const cancelPrevStatusRef = useRef<Map<string, OrderStatus>>(new Map());
  const listRequestSeqRef = useRef(0);
  const authHeaderRef = useRef<string | null>(localStorage.getItem("authToken"));

  const isAdmin = useMemo(() => {
    const role = (
      localStorage.getItem("role") ||
      localStorage.getItem("userRole") ||
      ""
    ).toLowerCase();
    const user = (currentUser || "").toLowerCase();
    return role === "admin" || user === "admin";
  }, [currentUser]);

  /** 下单表单状态。 */
  const [form, setForm] = useState<OrderForm>({
    market: "XSHG",
    securityId: "600030",
    side: "B",
    qty: 100,
    price: 20.5,
    shareholderId: "A123456789",
  });

  /** 当前用户 ID（以股东号作为订阅维度）。 */
  const currentUserId = useMemo(() => form.shareholderId, [form.shareholderId]);
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
    setIsLoggedIn(true);
    setCurrentUser(localStorage.getItem('shareholderId'));
    authHeaderRef.current = localStorage.getItem("authToken");
  }, []);

  const handleRegisterSuccess = useCallback(() => {
    setIsLoggedIn(true);
    setCurrentUser(localStorage.getItem('shareholderId'));
    authHeaderRef.current = localStorage.getItem("authToken");
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('shareholderId');
    setIsLoggedIn(false);
    setCurrentUser(null);
    authHeaderRef.current = null;
  }, []);

  const buildAuthHeaders = useCallback((): HeadersInit | undefined => {
    const token = authHeaderRef.current;
    if (!token) return undefined;
    return { authorization: token };
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
    const clOrderIdRaw =
      raw.clOrderId ??
      raw.orderId ??
      raw.origClOrderId ??
      raw.order_id;
    const securityIdRaw =
      raw.securityId ??
      raw.security_id ??
      raw.symbol ??
      raw.stockCode ??
      raw.stock_code;
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
      clOrderId: String(clOrderIdRaw ?? ""),
      market: String(raw.market ?? ""),
      securityId: String(securityIdRaw ?? ""),
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
      const requestSeq = listRequestSeqRef.current + 1;
      listRequestSeqRef.current = requestSeq;

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
          headers: buildAuthHeaders(),
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
        if (requestSeq !== listRequestSeqRef.current) return;
        setErrorMsg(null);
        setOrders(safeOrders);
        setTotalOrders(nextTotalOrders);
      } catch (e: unknown) {
        if (requestSeq !== listRequestSeqRef.current) return;
        const msg = e instanceof Error ? e.message : "Query list failed";
        setErrorMsg(msg);
        setOrders([]);
        setTotalOrders(0);
      }
    },
    [buildAuthHeaders, pageSize, toOrder, unwrapApiData]
  );

  const fetchSingleOrder = useCallback(
    async (clOrderId: string): Promise<void> => {
      const params = new URLSearchParams();
      params.set("clOrderId", clOrderId);

      try {
        const resp = await fetch(`${QUERY_BASE}/query?${params.toString()}`, {
          method: "GET",
          headers: buildAuthHeaders(),
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
    [buildAuthHeaders, toOrder, unwrapApiData]
  );

  const fetchOrderBySecurityId = useCallback(
    async (securityId: string): Promise<void> => {
      const params = new URLSearchParams();
      params.set("securityId", securityId);

      try {
        const resp = await fetch(`${QUERY_BASE}/query?${params.toString()}`, {
          method: "GET",
          headers: buildAuthHeaders(),
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
        const msg = e instanceof Error ? e.message : "Query security failed";
        setErrorMsg(msg);
        setOrders([]);
        setTotalOrders(0);
      }
    },
    [buildAuthHeaders, toOrder, unwrapApiData]
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

  useEffect(() => {
    if (knownTotalPages === null) return;
    setCurrentPage((prev) => Math.min(prev, knownTotalPages));
  }, [knownTotalPages]);

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
      setSearchMode("single");
      await fetchOrderBySecurityId(keyword);
      return;
    }

    setSearchMode("all");
    setOrders([]);
    setTotalOrders(0);
  }, [fetchOrderBySecurityId, fetchSingleOrder, searchValue]);

  const onClearSearch = useCallback(() => {
    setErrorMsg(null);
    setSearchValue("");
    setActiveSearchValue("");
    setSearchMode("all");
    setCurrentPage(1);
    setTotalOrders(null);
    setListReloadToken((prev) => prev + 1);
  }, []);

  const openQuotationAccessPage = useCallback(() => {
    if (!isLoggedIn || !isAdmin) return;
    setQuotationMessage(null);
    setPageMode("quotation-access");
  }, [isAdmin, isLoggedIn]);

  const loadUserInfo = useCallback(async () => {
    if (!isLoggedIn) {
      setUserInfoRows([]);
      setUserInfoMessage(lang === "en" ? "Please login first." : "请先登录。");
      return;
    }
    setLoadingUserInfo(true);
    setUserInfoMessage(null);
    try {
      const rows = await api.getUserInfo();
      setUserInfoRows(Array.isArray(rows) ? rows : []);
      if (!rows || rows.length === 0) {
        setUserInfoMessage(lang === "en" ? "No data." : "暂无数据。");
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : lang === "en" ? "Load failed" : "加载失败";
      setUserInfoMessage(msg);
      setUserInfoRows([]);
    } finally {
      setLoadingUserInfo(false);
    }
  }, [isLoggedIn, lang]);

  const openUserInfoPage = useCallback(() => {
    setPageMode("user-info");
    void loadUserInfo();
  }, [loadUserInfo]);

  const openAnalysisPage = useCallback(() => {
    setPageMode("analysis");
  }, []);

  const loadOrderDepth = useCallback(async () => {
    const securityId = quotationForm.securityId.trim();
    if (!securityId) {
      setDepthError(lang === "en" ? "Security ID is required" : "证券代码不能为空");
      setDepthBuyOrders([]);
      setDepthSellOrders([]);
      return;
    }

    setDepthLoading(true);
    setDepthError(null);
    try {
      const params = new URLSearchParams();
      params.set("securityId", securityId);
      params.set("page", "1");
      params.set("current", "1");
      params.set("pageSize", "50");
      params.set("size", "50");

      const resp = await fetch(`${QUERY_BASE}/list?${params.toString()}`, {
        method: "GET",
        headers: buildAuthHeaders(),
        credentials: "include",
      });

      if (!resp.ok) {
        throw new Error(`${lang === "en" ? "Request failed" : "请求失败"}(${resp.status})`);
      }

      const raw = (await resp.json()) as unknown;
      const payload = unwrapApiData<unknown>(raw);
      const rows = Array.isArray(payload)
        ? payload
        : payload && typeof payload === "object" && "data" in payload
          ? ((payload as { data?: unknown[] }).data ?? [])
          : [];

      const normalized = rows.map((item) => toOrder(item as Partial<Order> & Record<string, unknown>));
      const buy = normalized
        .filter((item) => item.side === "B")
        .sort((a, b) => b.price - a.price)
        .slice(0, 5);
      const sell = normalized
        .filter((item) => item.side === "S")
        .sort((a, b) => a.price - b.price)
        .slice(0, 5);

      setDepthBuyOrders(buy);
      setDepthSellOrders(sell);
      if (buy.length === 0 && sell.length === 0) {
        setDepthError(lang === "en" ? "No data." : "暂无数据。");
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : lang === "en" ? "Load failed" : "加载失败";
      setDepthError(msg);
      setDepthBuyOrders([]);
      setDepthSellOrders([]);
    } finally {
      setDepthLoading(false);
    }
  }, [buildAuthHeaders, lang, quotationForm.securityId, toOrder, unwrapApiData]);
  const handlePublishQuotation = useCallback(async () => {
    if (!isAdmin) return;
    if (!quotationForm.securityId.trim()) {
      setQuotationMessage(lang === "en" ? "Security ID is required" : "证券代码不能为空");
      return;
    }
    setPublishingQuotation(true);
    setQuotationMessage(null);
    try {
      await api.publishQuotation({
        market: quotationForm.market,
        securityId: quotationForm.securityId.trim(),
        bidPrice: quotationForm.bidPrice,
        askPrice: quotationForm.askPrice,
      });
      setQuotationMessage(lang === "en" ? "Published successfully" : "行情接入已提交");
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : lang === "en" ? "Publish failed" : "提交失败";
      setQuotationMessage(msg);
    } finally {
      setPublishingQuotation(false);
    }
  }, [isAdmin, lang, quotationForm]);

  const quotationAccessView = (
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
            onAnalysisClick={openAnalysisPage}
            analysisActive={pageMode === "analysis"}
          />
        </div>
        <section className="w-[95%] mx-auto">
          <div className="bg-white rounded-xl border border-slate-200 shadow p-5 max-w-3xl mx-auto">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold">
                {lang === "en" ? "Quotation Access" : "行情接入"}
              </h2>
              <button
                type="button"
                className="px-3 py-1.5 border border-slate-300 rounded text-sm hover:bg-slate-50"
                onClick={() => setPageMode("trade")}
              >
                {lang === "en" ? "Back" : "返回"}
              </button>
            </div>
            {!isAdmin ? (
              <div className="text-sm text-red-600">
                {lang === "en"
                  ? "Only admin can access this page."
                  : "仅管理员可使用行情接入功能。"}
              </div>
            ) : (
              <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div className="flex flex-col">
                  <label className="text-sm text-gray-600 mb-1">{lang === "en" ? "Market" : "市场"}</label>
                  <select
                    className="border p-2 rounded"
                    value={quotationForm.market}
                    onChange={(e) =>
                      setQuotationForm((prev) => ({ ...prev, market: e.target.value }))
                    }
                  >
                    <option value="XSHG">XSHG</option>
                    <option value="XSHE">XSHE</option>
                    <option value="BJSE">BJSE</option>
                  </select>
                </div>
                <div className="flex flex-col">
                  <label className="text-sm text-gray-600 mb-1">
                    {lang === "en" ? "Security ID" : "证券代码"}
                  </label>
                  <input
                    className="border p-2 rounded"
                    value={quotationForm.securityId}
                    onChange={(e) =>
                      setQuotationForm((prev) => ({ ...prev, securityId: e.target.value }))
                    }
                  />
                </div>
                <div className="flex flex-col">
                  <label className="text-sm text-gray-600 mb-1">
                    {lang === "en" ? "Latest Price" : "最新买价"}
                  </label>
                  <input
                    className="border p-2 rounded"
                    type="number"
                    value={quotationForm.bidPrice}
                    onChange={(e) =>
                      setQuotationForm((prev) => ({ ...prev, bidPrice: Number(e.target.value) }))
                    }
                  />
                </div>
                <div className="flex flex-col">
                  <label className="text-sm text-gray-600 mb-1">
                    {lang === "en" ? "Ask Price" : "最新卖价"}
                  </label>
                  <input
                    className="border p-2 rounded"
                    type="number"
                    value={quotationForm.askPrice}
                    onChange={(e) =>
                      setQuotationForm((prev) => ({ ...prev, askPrice: Number(e.target.value) }))
                    }
                  />
                </div>
                <div className="md:col-span-2">
                  <button
                    type="button"
                    className={`text-white py-2 px-5 rounded font-medium ${
                      publishingQuotation
                        ? "bg-blue-300 cursor-not-allowed"
                        : "bg-blue-600 hover:bg-blue-700"
                    }`}
                    disabled={publishingQuotation}
                    onClick={() => void handlePublishQuotation()}
                  >
                    {publishingQuotation
                      ? lang === "en"
                        ? "Submitting..."
                        : "提交中..."
                      : lang === "en"
                        ? "Submit Quotation"
                        : "提交行情"}
                  </button>
                </div>
                {quotationMessage && (
                  <div
                    className={`md:col-span-2 text-sm ${
                      quotationMessage.includes("失败") || quotationMessage.includes("failed")
                        ? "text-red-600"
                        : "text-green-600"
                    }`}
                  >
                    {quotationMessage}
                  </div>
                )}
              </div>
              <div className="border border-slate-200 rounded-lg p-4 bg-slate-50">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold text-slate-700">
                    {lang === "en" ? "Order Depth (Top 5 + Bottom 5)" : "盘口信息（最高5条 + 最低5条）"}
                  </h3>
                  <button
                    type="button"
                    className={`px-3 py-1.5 rounded text-sm ${
                      depthLoading
                        ? "bg-blue-300 text-white cursor-not-allowed"
                        : "bg-blue-600 text-white hover:bg-blue-700"
                    }`}
                    onClick={() => void loadOrderDepth()}
                    disabled={depthLoading}
                  >
                    {depthLoading
                      ? lang === "en"
                        ? "Loading..."
                        : "加载中..."
                      : lang === "en"
                        ? "Load Depth"
                        : "加载盘口"}
                  </button>
                </div>
                {depthError && (
                  <div className="text-sm text-red-600 mb-3">{depthError}</div>
                )}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                  <div className="bg-white rounded border border-slate-200 p-3">
                    <div className="font-semibold text-red-600 mb-2">
                      {lang === "en" ? "Top Buy (5)" : "买入最高5条"}
                    </div>
                    {depthBuyOrders.length === 0 ? (
                      <div className="text-slate-500 text-xs">
                        {lang === "en" ? "No data" : "暂无数据"}
                      </div>
                    ) : (
                      <ul className="space-y-1">
                        {depthBuyOrders.map((item) => (
                          <li key={item.clOrderId} className="flex justify-between text-xs">
                            <span className="text-slate-600">{item.securityId}</span>
                            <span className="text-red-600 font-semibold">{item.price.toFixed(2)}</span>
                            <span className="text-slate-500">Qty {item.qty}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div className="bg-white rounded border border-slate-200 p-3">
                    <div className="font-semibold text-green-600 mb-2">
                      {lang === "en" ? "Top Sell (5)" : "卖出最低5条"}
                    </div>
                    {depthSellOrders.length === 0 ? (
                      <div className="text-slate-500 text-xs">
                        {lang === "en" ? "No data" : "暂无数据"}
                      </div>
                    ) : (
                      <ul className="space-y-1">
                        {depthSellOrders.map((item) => (
                          <li key={item.clOrderId} className="flex justify-between text-xs">
                            <span className="text-slate-600">{item.securityId}</span>
                            <span className="text-green-600 font-semibold">{item.price.toFixed(2)}</span>
                            <span className="text-slate-500">Qty {item.qty}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              </div>
            </div>
            )}
          </div>
        </section>
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

  const userInfoView = (
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
            onAnalysisClick={openAnalysisPage}
            analysisActive={pageMode === "analysis"}
          />
        </div>
        <section className="w-[95%] mx-auto">
          <div className="bg-white rounded-xl border border-slate-200 shadow p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold">
                {lang === "en" ? "My Stocks / Personal Info" : "我的股票 / 个人信息"}
              </h2>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="px-3 py-1.5 border border-slate-300 rounded text-sm hover:bg-slate-50"
                  onClick={() => void loadUserInfo()}
                  disabled={loadingUserInfo}
                >
                  {loadingUserInfo
                    ? lang === "en"
                      ? "Loading..."
                      : "加载中..."
                    : lang === "en"
                      ? "Refresh"
                      : "刷新"}
                </button>
                <button
                  type="button"
                  className="px-3 py-1.5 border border-slate-300 rounded text-sm hover:bg-slate-50"
                  onClick={() => setPageMode("trade")}
                >
                  {lang === "en" ? "Back" : "返回"}
                </button>
              </div>
            </div>
            {userInfoMessage && (
              <div className="mb-3 text-sm text-slate-600">{userInfoMessage}</div>
            )}
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="text-left text-slate-500 border-b">
                    <th className="py-2 pr-3">{lang === "en" ? "Market" : "市场"}</th>
                    <th className="py-2 pr-3">{lang === "en" ? "Security" : "证券代码"}</th>
                    <th className="py-2 pr-3">{lang === "en" ? "Side" : "方向"}</th>
                    <th className="py-2 pr-3">Qty</th>
                    <th className="py-2 pr-3">{lang === "en" ? "Price" : "价格"}</th>
                    <th className="py-2 pr-3">{lang === "en" ? "Status" : "状态"}</th>
                    <th className="py-2 pr-3">CumQty</th>
                    <th className="py-2 pr-3">{lang === "en" ? "Remaining" : "剩余"}</th>
                  </tr>
                </thead>
                <tbody>
                  {userInfoRows.map((row, idx) => (
                    <tr key={`${row.securityId}-${idx}`} className="border-b last:border-0 text-slate-700">
                      <td className="py-2 pr-3">{row.market}</td>
                      <td className="py-2 pr-3">{row.securityId}</td>
                      <td className="py-2 pr-3">{row.side}</td>
                      <td className="py-2 pr-3">{row.qty}</td>
                      <td className="py-2 pr-3">{row.price}</td>
                      <td className="py-2 pr-3">{row.status}</td>
                      <td className="py-2 pr-3">{row.cumQty}</td>
                      <td className="py-2 pr-3">{row.remainingQty}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
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

  const mainView = (
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
            onAnalysisClick={openAnalysisPage}
            analysisActive={pageMode === "analysis"}
        />
      </div>

      <section className="w-[95%] mx-auto mb-6">
        <div className="grid grid-cols-1 lg:grid-cols-[280px_minmax(630px,1fr)_360px] gap-4 items-start">
          <div className="hidden lg:block w-full h-[500px] max-w-[280px] justify-self-start">
            <div className="h-full overflow-y-auto">
              <MarketData lang={lang} isAdmin={false} />
            </div>
          </div>

          <div className="w-full">
            <div className="aspect-[4/2]">
              <BannerCarousel />
            </div>
            <div className="mt-2 flex justify-end items-center gap-2">
              <button
                type="button"
                className={`px-3 py-1.5 text-sm rounded ${
                  isLoggedIn
                    ? "bg-blue-600 text-white hover:bg-blue-700"
                    : "bg-slate-200 text-slate-500 cursor-not-allowed"
                }`}
                onClick={openUserInfoPage}
                disabled={!isLoggedIn}
              >
                {lang === "en" ? "My Stocks" : "我的股票"}
              </button>
              {!isAdmin && (
                <span className="text-xs text-slate-500">
                  {lang === "en" ? "Admin only" : "仅管理员可用"}
                </span>
              )}
              <button
                type="button"
                className={`px-3 py-1.5 text-sm rounded ${
                  isLoggedIn && isAdmin
                    ? "bg-slate-900 text-white hover:bg-black"
                    : "bg-slate-200 text-slate-500 cursor-not-allowed"
                }`}
                onClick={openQuotationAccessPage}
                disabled={!isLoggedIn || !isAdmin}
              >
                {lang === "en" ? "Quotation Access" : "行情接入"}
              </button>
            </div>
          </div>

          <aside className="w-full bg-white rounded-xl border border-slate-200 shadow-sm h-[500px] flex flex-col">
            <div className="p-4 space-y-3 flex-1 overflow-y-auto">
              {errorMsg && (
                <div className="bg-red-50 text-red-700 border border-red-200 px-4 py-2 rounded">
                  {errorMsg}
                </div>
              )}

              {orders.map((o, idx) => {
                const remainingQty = Math.max(0, o.qty - o.cumQty);
                return (
                  <div
                    key={`${o.clOrderId || "no-clord"}-${o.securityId}-${o.market}-${idx}`}
                    className="w-full bg-slate-50 rounded-lg border border-slate-200 px-3 py-3"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-lg font-bold tracking-wide text-slate-900 truncate">{o.securityId}</div>
                        <div className="text-xs text-slate-500 font-mono mt-0.5 truncate">{o.clOrderId}</div>
                        <div className={`text-sm font-semibold mt-1 ${o.side === "B" ? "text-red-500" : "text-green-600"}`}>
                          {o.side === "B" ? text.buy : text.sell} · {o.market}
                        </div>
                      </div>

                      <div className="text-right shrink-0">
                        <div className="text-xs text-slate-500">剩余量</div>
                        <div className="text-xl font-bold text-slate-900">{remainingQty}</div>
                        <div className="text-xs text-slate-500 mt-1">
                          Cum: <span className="font-semibold text-slate-700">{o.cumQty}</span> / Qty: {o.qty}
                        </div>
                        <div className="mt-2 flex justify-end items-center gap-2">
                          <span
                            className={`px-2 py-1 rounded text-xs ${getStatusClassName(o.status)}`}
                            title={
                              o.status === OrderStatus.Rejected && o.errorMsg
                                ? `${text.errorCode}: ${o.errorCode ?? ""} ${o.errorMsg ?? ""}`
                                : undefined
                            }
                          >
                            {statusTextMap[o.status] ?? o.status}
                          </span>
                          {canCancel(o) && (
                            <button
                              className="text-red-600 hover:underline text-sm"
                              onClick={() => handleCancel(o)}
                            >
                              {text.cancel}
                            </button>
                          )}
                        </div>
                      </div>
                    </div>

                    {o.status === OrderStatus.Rejected && o.errorMsg && (
                      <div className="text-xs text-red-500 mt-2 truncate">{o.errorMsg}</div>
                    )}
                  </div>
                );
              })}

              {orders.length === 0 && (
                <div className="rounded-xl bg-white border border-slate-100 text-slate-500 text-sm px-4 py-8 text-center">
                  No orders
                </div>
              )}

            </div>
            <div className="mt-auto bg-white/95 px-4 py-3 flex items-center justify-center gap-3 border-t border-slate-100 backdrop-blur">
              <button
                className="px-3 py-1.5 border border-gray-300 rounded disabled:opacity-50"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={searchMode === "single" || currentPage === 1}
              >
                Previous
              </button>
              <span className="text-sm text-gray-700">
                Page {currentPage} / {knownTotalPages ?? "?"}
              </span>
              <button
                className="px-3 py-1.5 border border-gray-300 rounded disabled:opacity-50"
                onClick={() => setCurrentPage((p) => p + 1)}
                disabled={!canGoNext}
              >
                Next
              </button>
            </div>
          </aside>
        </div>
      </section>

      <section className="w-[95%] mx-auto pb-6">
        <div className="w-full bg-white p-4 rounded-lg shadow border border-slate-200">
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3 items-end">
            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.market}</label>
              <select
                className="border p-2 rounded"
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
                className="border p-2 rounded"
                value={form.securityId}
                onChange={(e) => setForm({ ...form, securityId: e.target.value })}
              />
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.side}</label>
              <select
                className="border p-2 rounded"
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
                className="border p-2 rounded"
                type="number"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
              />
            </div>

            <div className="flex flex-col">
              <label className="text-sm text-gray-600 mb-1">{text.quantity}</label>
              <input
                className="border p-2 rounded"
                type="number"
                value={form.qty}
                onChange={(e) => setForm({ ...form, qty: Number(e.target.value) })}
              />
            </div>

          </div>

          <div className="mt-3 flex justify-end">
            <button
              className={`px-6 text-white py-2 rounded font-bold ${
                submitting ? "bg-blue-300 cursor-not-allowed" : "bg-blue-600 hover:bg-blue-700"
              }`}
              onClick={submitOrder}
              disabled={submitting}
            >
              {submitting ? text.submitting : text.submitOrder}
            </button>
          </div>
        </div>
      </section>
      
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

  const analysisView = (
    <AnalysisPage
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
      onBack={() => setPageMode("trade")}
    />
  );

  const tradeView = pageMode === "analysis"
    ? analysisView
    : pageMode === "quotation-access"
      ? quotationAccessView
      : pageMode === "user-info"
        ? userInfoView
        : mainView;

  return tradeView;
}

