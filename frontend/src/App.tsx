import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api/client";
import type { CancelOrderDTO, SubmitOrderDTO, TradeReturnVO } from "./types/server";
import { connectTradeReturnWS, disconnectTradeReturnWS } from "./services/ws";
import BannerSection from "./components/BannerSection";
import TopHeader from "./components/TopHeader";

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
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [lang, setLang] = useState<"en" | "zh">("en");
  const cancelingOrderIdsRef = useRef<Set<string>>(new Set());
  const cancelPrevStatusRef = useRef<Map<string, OrderStatus>>(new Map());

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
    const newOrder: Order = {
      ...form,
      clOrderId: generateId(),
      status: OrderStatus.Pending,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 0,
    };
    setOrders((prev) => [newOrder, ...prev]);

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

  return (
    <div
      className="p-6 min-h-screen"
      style={{
        backgroundImage: "linear-gradient(to bottom, #dbeafe 0%, #ffffff 30%, #ffffff 100%)",
      }}
    >
      <div className="w-[95%] mx-auto mb-4">
        <TopHeader
          wsConnected={wsConnected}
          lang={lang}
          onToggleLang={toggleLang}
        />
      </div>

      <BannerSection />

      <div className="w-[95%] mx-auto">
        <div className="bg-white p-4 rounded-lg shadow mb-6 grid grid-cols-1 md:grid-cols-5 gap-4">
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

          <button
            className={`md:col-span-5 mt-2 text-white py-2 rounded font-bold ${
              submitting ? "bg-blue-300 cursor-not-allowed" : "bg-blue-600 hover:bg-blue-700"
            }`}
            onClick={submitOrder}
            disabled={submitting}
          >
            {submitting ? text.submitting : text.submitOrder}
          </button>
        </div>

        {errorMsg && (
          <div className="bg-red-50 text-red-700 border border-red-200 px-4 py-2 rounded mb-4">
            {errorMsg}
          </div>
        )}

        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full text-left">
            <thead className="bg-gray-100 border-b">
              <tr>
                <th className="p-3">{text.orderId}</th>
                <th className="p-3">{text.symbol}</th>
                <th className="p-3">{text.market}</th>
                <th className="p-3">{text.side}</th>
                <th className="p-3">{text.price}</th>
                <th className="p-3">{text.qty}</th>
                <th className="p-3">{text.status}</th>
                <th className="p-3">{text.fillProgress}</th>
                <th className="p-3">{text.action}</th>
              </tr>
            </thead>

            <tbody>
              {orders.map((o) => (
                <tr key={o.clOrderId} className="border-b hover:bg-gray-50">
                  <td className="p-3 text-xs font-mono">{o.clOrderId}</td>
                  <td className="p-3">{o.securityId}</td>
                  <td className="p-3">{o.market}</td>

                  <td className={`p-3 font-bold ${o.side === "B" ? "text-red-500" : "text-green-500"}`}>
                    {o.side === "B" ? text.buy : text.sell}
                  </td>

                  <td className="p-3">{o.price}</td>
                  <td className="p-3">{o.qty}</td>

                  <td className="p-3">
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
                    {o.status === OrderStatus.Rejected && o.errorMsg && (
                      <div className="text-xs text-red-500 mt-1 truncate max-w-[120px]">
                        {o.errorMsg}
                      </div>
                    )}
                  </td>

                  <td className="p-3">
                    {(o.status === OrderStatus.PartiallyFilled || o.status === OrderStatus.Filled) && (
                      <span
                        className={`font-semibold ${
                          o.status === OrderStatus.Filled ? "text-green-600" : "text-orange-600"
                        }`}
                      >
                        {o.cumQty}/{o.qty}
                      </span>
                    )}
                    {o.status !== OrderStatus.PartiallyFilled && o.status !== OrderStatus.Filled && "-"}
                  </td>

                  <td className="p-3">
                    {canCancel(o) && (
                      <button
                        className="text-red-600 hover:underline text-sm"
                        onClick={() => handleCancel(o)}
                      >
                        {text.cancel}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

