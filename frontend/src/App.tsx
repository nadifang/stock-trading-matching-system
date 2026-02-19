import { useState } from "react";

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
 * 模拟回报事件类型。
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
const generateId = (): string =>
  "CL" + Math.random().toString(36).slice(2, 11).toUpperCase();

/**
 * 判断订单是否已进入终态。
 */
const isFinalStatus = (status: OrderStatus): boolean =>
  status === OrderStatus.Filled ||
  status === OrderStatus.Canceled ||
  status === OrderStatus.Rejected;

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
  const [orders, setOrders] = useState<Order[]>([]);

  /** 下单表单状态。 */
  const [form, setForm] = useState<OrderForm>({
    market: "XSHG",
    securityId: "600030",
    side: "B",
    qty: 100,
    price: 20.5,
    shareholderId: "A123456789",
  });

  /**
   * 向单个订单派发事件。
   * 所有状态变化统一通过状态机处理。
   */
  const emitOrderEvent = (clOrderId: string, event: SomeEventType): void => {
    setOrders((prev) =>
      prev.map((o) => (o.clOrderId === clOrderId ? updateOrderStatus(o, event) : o))
    );
  };

  /**
   * 按时间延迟发送模拟回报。
   * 支持已报 -> 部成 -> 全成，或已报 -> 全成两种路径。
   */
  const simulateBackendResponse = (order: Order): void => {
    const { clOrderId, qty, price } = order;

    setTimeout(() => {
      emitOrderEvent(clOrderId, { type: "ORDER_ACK" });
    }, 500);

    const canPartial = qty > 1;
    const usePartialPath = canPartial && Math.random() < 0.75;

    if (usePartialPath) {
      const partialQtyRaw = Math.floor(qty * (0.2 + Math.random() * 0.5));
      const partialQty = Math.max(1, Math.min(qty - 1, partialQtyRaw));

      setTimeout(() => {
        emitOrderEvent(clOrderId, {
          type: "ORDER_PARTIAL_FILL",
          fillQty: partialQty,
          fillPrice: price,
        });
      }, 1500);

      setTimeout(() => {
        emitOrderEvent(clOrderId, {
          type: "ORDER_FILL",
          fillPrice: price,
        });
      }, 2800);
    } else {
      setTimeout(() => {
        emitOrderEvent(clOrderId, {
          type: "ORDER_FILL",
          fillPrice: price,
        });
      }, 1800);
    }
  };

  /**
   * 提交新订单并启动模拟回报。
   */
  const submitOrder = (): void => {
    const newOrder: Order = {
      ...form,
      clOrderId: generateId(),
      status: OrderStatus.Pending,
      cumQty: 0,
      execPrice: 0,
      canceledQty: 0,
    };

    setOrders((prev) => [newOrder, ...prev]);
    simulateBackendResponse(newOrder);
  };

  /**
   * 发起撤单并延迟模拟撤单确认回报。
   */
  const handleCancel = (origOrder: Order): void => {
    emitOrderEvent(origOrder.clOrderId, { type: "CANCEL_REQUEST" });

    setTimeout(() => {
      emitOrderEvent(origOrder.clOrderId, { type: "CANCEL_ACK" });
    }, 1000);
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
      <h1 className="text-2xl font-bold mb-6 text-blue-800">交易员终端 - 模拟交易对敲系统</h1>

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
          className="md:col-span-4 mt-2 bg-blue-600 text-white py-2 rounded font-bold hover:bg-blue-700"
          onClick={submitOrder}
        >
          提交订单
        </button>
      </div>

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
