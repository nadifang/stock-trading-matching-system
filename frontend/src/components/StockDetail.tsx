import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";

interface Order {
  clOrderId: string;
  market: string;
  securityId: string;
  side: "B" | "S";
  qty: number;
  price: number;
  shareholderId: string;
  status: string;
  cumQty: number;
  execPrice: number;
}

interface StockDetailProps {
  lang: "en" | "zh";
}

export default function StockDetail({ lang }: StockDetailProps) {
  const { symbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const [buyOrders, setBuyOrders] = useState<Order[]>([]);
  const [sellOrders, setSellOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  // 模拟订单数据
  useEffect(() => {
    const timer = setTimeout(() => {
      // 生成模拟的买入订单数据，按价格从高到低排序
      const mockBuyOrders: Order[] = Array.from({ length: 5 }, (_, index) => ({
        clOrderId: `B${Date.now() + index}`,
        market: "XSHG",
        securityId: symbol || "600030",
        side: "B" as const,
        qty: 100 * (index + 1),
        price: 20.5 + index * 0.1,
        shareholderId: `A${123456789 + index}`,
        status: "Acked",
        cumQty: 0,
        execPrice: 0
      })).sort((a, b) => b.price - a.price);

      // 生成模拟的卖出订单数据，按价格从低到高排序
      const mockSellOrders: Order[] = Array.from({ length: 5 }, (_, index) => ({
        clOrderId: `S${Date.now() + index}`,
        market: "XSHG",
        securityId: symbol || "600030",
        side: "S" as const,
        qty: 100 * (index + 1),
        price: 20.5 - index * 0.1,
        shareholderId: `A${123456789 + index + 5}`,
        status: "Acked",
        cumQty: 0,
        execPrice: 0
      })).sort((a, b) => a.price - b.price);

      setBuyOrders(mockBuyOrders);
      setSellOrders(mockSellOrders);
      setLoading(false);
    }, 1000);

    return () => clearTimeout(timer);
  }, [symbol]);

  const text = lang === "en" ? {
    stockDetail: "Stock Detail",
    stockId: "Stock ID",
    buyOrders: "Buy Orders",
    sellOrders: "Sell Orders",
    orderId: "Order ID",
    price: "Price",
    quantity: "Quantity",
    status: "Status",
    loading: "Loading order data...",
    back: "Back"
  } : {
    stockDetail: "股票详情",
    stockId: "股票代码",
    buyOrders: "买入订单",
    sellOrders: "卖出订单",
    orderId: "订单号",
    price: "价格",
    quantity: "数量",
    status: "状态",
    loading: "加载订单数据中...",
    back: "返回"
  };

  if (loading) {
    return (
      <div className="p-6 min-h-screen bg-white flex items-center justify-center">
        <p className="text-gray-600">{text.loading}</p>
      </div>
    );
  }

  return (
    <div className="p-6 min-h-screen bg-white">
      <div className="w-[95%] mx-auto">
        {/* 返回按钮 */}
        <button 
          className="mb-6 bg-gray-100 hover:bg-gray-200 text-gray-800 py-2 px-4 rounded flex items-center gap-2"
          onClick={() => navigate(-1)}
        >
          ← {text.back}
        </button>

        {/* 股票信息 */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">{text.stockDetail}</h1>
          <div className="bg-gray-50 p-4 rounded-lg border border-gray-200">
            <div className="flex items-center gap-4">
              <div className="font-semibold text-gray-700">{text.stockId}:</div>
              <div className="text-xl font-bold text-gray-900">{symbol}</div>
            </div>
          </div>
        </div>

        {/* 买入订单 */}
        <div className="mb-8">
          <h2 className="text-lg font-semibold text-gray-800 mb-4 flex items-center gap-2">
            <span className="w-3 h-3 bg-red-500 rounded-full"></span>
            {text.buyOrders}
          </h2>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <div className="grid grid-cols-4 gap-2 p-3 bg-gray-50 border-b border-gray-200 text-sm font-medium text-gray-600">
              <div>{text.orderId}</div>
              <div>{text.price}</div>
              <div>{text.quantity}</div>
              <div>{text.status}</div>
            </div>
            {buyOrders.map((order) => (
              <div key={order.clOrderId} className="grid grid-cols-4 gap-2 p-3 border-b border-gray-100 hover:bg-gray-50">
                <div className="text-sm text-gray-900">{order.clOrderId}</div>
                <div className="text-sm font-semibold text-red-500">{order.price.toFixed(2)}</div>
                <div className="text-sm text-gray-900">{order.qty}</div>
                <div className="text-sm text-gray-600">{order.status}</div>
              </div>
            ))}
          </div>
        </div>

        {/* 卖出订单 */}
        <div className="mb-8">
          <h2 className="text-lg font-semibold text-gray-800 mb-4 flex items-center gap-2">
            <span className="w-3 h-3 bg-green-500 rounded-full"></span>
            {text.sellOrders}
          </h2>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <div className="grid grid-cols-4 gap-2 p-3 bg-gray-50 border-b border-gray-200 text-sm font-medium text-gray-600">
              <div>{text.orderId}</div>
              <div>{text.price}</div>
              <div>{text.quantity}</div>
              <div>{text.status}</div>
            </div>
            {sellOrders.map((order) => (
              <div key={order.clOrderId} className="grid grid-cols-4 gap-2 p-3 border-b border-gray-100 hover:bg-gray-50">
                <div className="text-sm text-gray-900">{order.clOrderId}</div>
                <div className="text-sm font-semibold text-green-500">{order.price.toFixed(2)}</div>
                <div className="text-sm text-gray-900">{order.qty}</div>
                <div className="text-sm text-gray-600">{order.status}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
