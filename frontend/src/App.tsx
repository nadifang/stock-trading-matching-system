import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api/client";
import type { CancelOrderDTO, SubmitOrderDTO, TradeReturnVO } from "./types/server";
import { connectTradeReturnWS, disconnectTradeReturnWS } from "./services/ws";

/**
 * 订单状态枚举。
 * 使用受控枚举替代自由字符串，避免状态值拼写错误和非法流转。
 */
const OrderStatus = {
  Pending: "待报",
  Acked: "已报",
  PartiallyFilled: "部成",
  Filled: "全成",
  Canceling: "撤单中",
  Canceled: "已撤单",
  Rejected: "废单",
} as const;

type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];


/**
 * 买卖方向。
 */
type Side = "B" | "S";

/**
 * 订单回报事件类型。
 */
type OrderEventType =
  | "ORDER_ACK"
  | "ORDER_PARTIAL_FILL"
  | "ORDER_FILL"
  | "CANCEL_REQUEST"
  | "CANCEL_ACK";

/**
 * 状态机输入事件。
 * 部分成交和全成事件可携带成交价/成交量。
 */
interface SomeEventType {
  type: OrderEventType;
  fillQty?: number;
  fillPrice?: number;
}

/**
 * 订单实体。
 */
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

/**
 * 下单表单数据。
 */
interface OrderForm {
  market: string;
  securityId: string;
  side: Side;
  qty: number;
  price: number;
  shareholderId: string;
}

/**
 * 生成客户端订单号。
 */
const generateId = (): string => {
  const now = Date.now().toString();
  const rand = Math.floor(Math.random() * 1e8)
    .toString()
    .padStart(8, "0");
  return (now + rand).slice(-16);
};

/**
 * 判断订单是否已进入终态。
 */
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
 * 根据事件驱动订单状态流转。
 *
 * 业务规则：
 * 1. 部分成交：cumQty < qty => 部成。
 * 2. 全部成交：cumQty == qty => 全成。
 * 3. 已撤单后不再进入任何成交态。
 * 4. 撤单回报会计算剩余撤销量。
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

/**
 * 根据状态返回用于标签展示的样式。
 */
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
   * 交易员终端主界面。
   */
export default function App() {
  /** 订单列表状态。 */
  const [orders, setOrders] = useState<Order[]>([
    // 模拟数据：测试不同状态的订单
    {
      clOrderId: "TEST0000000001",
      market: "XSHG",
      securityId: "600030",
      side: "B",
      qty: 500,
      price: 14.5,
      shareholderId: "A123456789",
      status: OrderStatus.Pending,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 0,
    },
    {
      clOrderId: "TEST0000000002",
      market: "XSHG",
      securityId: "601888",
      side: "S",
      qty: 300,
      price: 25.8,
      shareholderId: "A123456789",
      status: OrderStatus.Acked,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 0,
    },
    {
      clOrderId: "TEST0000000003",
      market: "XSHE",
      securityId: "000858",
      side: "B",
      qty: 800,
      price: 32.6,
      shareholderId: "A123456789",
      status: OrderStatus.PartiallyFilled,
      cumQty: 450,
      execPrice: 32.6,
      canceledQty: 0,
    },
    {
      clOrderId: "TEST0000000004",
      market: "XSHG",
      securityId: "600028",
      side: "S",
      qty: 200,
      price: 7.2,
      shareholderId: "A123456789",
      status: OrderStatus.Filled,
      cumQty: 200,
      execPrice: 7.2,
      canceledQty: 0,
    },
    {
      clOrderId: "TEST0000000005",
      market: "XSHG",
      securityId: "601318",
      side: "B",
      qty: 1200,
      price: 5.5,
      shareholderId: "A123456789",
      status: OrderStatus.Canceled,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 1200,
    },
    {
      clOrderId: "TEST0000000006",
      market: "XSHG",
      securityId: "600519",
      side: "B",
      qty: 10,
      price: 1780.0,
      shareholderId: "A123456789",
      status: OrderStatus.PartiallyFilled,
      cumQty: 5,
      execPrice: 1780.0,
      canceledQty: 0,
    },
    {
      clOrderId: "TEST0000000007",
      market: "XSHE",
      securityId: "000001",
      side: "S",
      qty: 600,
      price: 16.8,
      shareholderId: "A123456789",
      status: OrderStatus.Filled,
      cumQty: 600,
      execPrice: 16.8,
      canceledQty: 0,
    },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [wsConnected, setWsConnected] = useState(false);

  /** 下单表单状态。 */
  const [form, setForm] = useState<OrderForm>({
    market: "XSHG",
    securityId: "600030",
    side: "B",
    qty: 100,
    price: 20.5,
    shareholderId: "A123456789",
  });

  /** 当前用户 ID（用股东号作为用户维度进行订阅） */
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

  /**
   * 处理后端回报，转换为状态机事件。
   */
  const handleTradeReturn = useCallback((ret: TradeReturnVO) => {
    const clId = ret.clOrderId || ret.origClOrderId || "";
    if (!clId) return;
    const mappedStatus = resolveOrderStatusFromTradeReturn(ret.status);
    if (!mappedStatus) return;

    setOrders((prev) => {
      let changed = false;
      const next = prev.map((o) => {
        if (o.clOrderId !== clId) return o;
        changed = true;
        return applyTradeReturnStatus(o, mappedStatus, ret);
      });
      return changed ? next : prev;
    });
  }, []);

  /**
   * 建立 WebSocket 订阅回报。
   */
  useEffect(() => {
    if (!currentUserId || wsBoundRef.current) return;
    
    // 按照回调函数方案实现
    connectTradeReturnWS({
      userId: currentUserId,
      handleTradeReturn: (ret) => {
        // 直接调用回调函数，将解析好的VO对象传递给业务逻辑
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

  /**
   * 提交新订单，调用后端 /trade。
   * 订单状态由后端回报驱动。
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
        const resp = await api.submitOrder(payload);  // 【修改】获取响应对象
        // 处理后端返回的状态
        if (resp.status) {
            // 根据后端状态更新前端状态
            let frontendStatus: OrderStatus = OrderStatus.Pending;  // 【修改】添加类型注解
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
                ? {
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
                  }
                : o
            )
            );
        }
    } catch (e: unknown) {
      let msg = "下单失败";
      if (e instanceof Error) {
        msg = e.message;
      } else if (typeof e === "string") {
        msg = e;
      }
      setErrorMsg(msg);
      setOrders((prev) =>
        prev.map((o) =>
          o.clOrderId === newOrder.clOrderId
            ? { ...o, status: OrderStatus.Rejected, errorMsg: msg }
            : o
        )
      );
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * 发起撤单请求，状态由后端回报驱动。
   */
  const handleCancel = async (origOrder: Order): Promise<void> => {
    setErrorMsg(null);
    emitOrderEvent(origOrder.clOrderId, { type: "CANCEL_REQUEST" });

    const payload: CancelOrderDTO = {
      clOrderId: generateId(),
      origClOrderId: origOrder.clOrderId,
      market: origOrder.market,
      securityId: origOrder.securityId,
      side: origOrder.side,
      qty: origOrder.qty,
      price: origOrder.price,
      shareholderId: origOrder.shareholderId,
    };

    try {
      await api.cancelOrder(payload);
    } catch (e: unknown) {
      let msg = "撤单失败";
      if (e instanceof Error) {
        msg = e.message;
      } else if (typeof e === "string") {
        msg = e;
      }
      setErrorMsg(msg);
    }
  };

  /**
   * 判断订单是否允许撤单。
   */
  const canCancel = (order: Order): boolean =>
    order.status === OrderStatus.Pending ||
    order.status === OrderStatus.Acked ||
    order.status === OrderStatus.PartiallyFilled;

  return (
    <div className="p-6 bg-gray-50 min-h-screen">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-blue-800">交易员终端 - 模拟交易对敲系统</h1>
        <div className={`flex items-center px-3 py-1 rounded-full text-sm ${wsConnected ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
          <span className={`w-2 h-2 rounded-full mr-2 ${wsConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
          {wsConnected ? 'WebSocket已连接' : 'WebSocket未连接'}
        </div>
      </div>

      <div className="bg-white p-4 rounded-lg shadow mb-6 grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">证券代码</label>
          <input
            className="border p-2 rounded"
            value={form.securityId}
            onChange={(e) => setForm({ ...form, securityId: e.target.value })}
          />
        </div>

        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">买卖方向</label>
          <select
            className="border p-2 rounded"
            value={form.side}
            onChange={(e) => setForm({ ...form, side: e.target.value as Side })}
          >
            <option value="B">买入 (Buy)</option>
            <option value="S">卖出 (Sell)</option>
          </select>
        </div>

        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">委托价格</label>
          <input
            className="border p-2 rounded"
            type="number"
            value={form.price}
            onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
          />
        </div>

        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">委托数量</label>
          <input
            className="border p-2 rounded"
            type="number"
            value={form.qty}
            onChange={(e) => setForm({ ...form, qty: Number(e.target.value) })}
          />
        </div>

        <button
          className={`md:col-span-4 mt-2 text-white py-2 rounded font-bold ${
            submitting ? "bg-blue-300 cursor-not-allowed" : "bg-blue-600 hover:bg-blue-700"
          }`}
          onClick={submitOrder}
          disabled={submitting}
        >
          {submitting ? "提交中..." : "提交订单"}
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
              <th className="p-3">订单号</th>
              <th className="p-3">证券</th>
              <th className="p-3">方向</th>
              <th className="p-3">价格</th>
              <th className="p-3">数量</th>
              <th className="p-3">状态</th>
              <th className="p-3">成交进度</th>
              <th className="p-3">操作</th>
            </tr>
          </thead>

          <tbody>
            {orders.map((o) => (
              <tr key={o.clOrderId} className="border-b hover:bg-gray-50">
                <td className="p-3 text-xs font-mono">{o.clOrderId}</td>
                <td className="p-3">{o.securityId}</td>

                <td className={`p-3 font-bold ${o.side === "B" ? "text-red-500" : "text-green-500"}`}>
                  {o.side === "B" ? "买入" : "卖出"}
                </td>

                <td className="p-3">{o.price}</td>
                <td className="p-3">{o.qty}</td>

                <td className="p-3">
                  <span className={`px-2 py-1 rounded text-xs ${getStatusClassName(o.status)}`}>
                    {o.status}
                  </span>
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
                      撤单
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
