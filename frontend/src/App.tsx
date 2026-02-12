import { useState } from "react";

/* ============================================================
   模拟股票交易对敲系统 - 前端交易员终端
   ------------------------------------------------------------
   模块说明：
   本文件实现一个简化版证券交易员终端页面，包含：
   1. 下单功能
   2. 撤单功能
   3. 订单状态流转
   4. 模拟交易所异步回报机制

   设计目标：
   - 模拟真实交易系统中的订单生命周期
   - 演示前端如何通过异步事件驱动状态更新
   - 构建基础交易终端交互模型
============================================================ */


/* ============================================================
   一、工具函数区域
============================================================ */

/**
 * generateId
 * ------------------------------------------------------------
 * 模拟生成客户订单号（Client Order ID）
 *
 * 在真实交易系统中：
 * - clOrderId 由前端生成
 * - 作为客户端唯一标识
 * - 后端/交易所回报时会原样带回
 *
 * 此处使用随机字符串模拟
 */
const generateId = (): string =>
  "CL" + Math.random().toString(36).substr(2, 9).toUpperCase();



/* ============================================================
   二、数据模型定义区域
============================================================ */

/**
 * Order 接口定义
 *
 * 说明：
 * 本结构贴近真实证券交易系统中的订单模型（简化版）
 *
 * 字段说明：
 * - clOrderId    : 客户订单号（前端生成）
 * - market       : 市场标识（如 XSHG / XSHE）
 * - securityId   : 证券代码
 * - side         : 买卖方向（B=Buy, S=Sell）
 * - qty          : 委托数量
 * - price        : 委托价格
 * - shareholderId: 股东代码
 * - status       : 当前订单状态
 * - cumQty       : 累计成交数量
 * - execPrice    : 成交均价
 * - canceledQty  : 撤单数量（可选）
 */
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
  canceledQty?: number;
}



/* ============================================================
   三、主组件：交易员终端
============================================================ */

export default function App() {
  /* ============================================================
     1. 状态管理区域
  ============================================================ */

  /**
   * orders
   * ------------------------------------------------------------
   * 当前系统中所有订单的列表
   * 每次下单、撤单、成交都会更新该数组
   */
  const [orders, setOrders] = useState<Order[]>([]);

  /**
   * form
   * ------------------------------------------------------------
   * 下单面板中的表单数据
   * 用于构造新的订单对象
   */
  const [form, setForm] = useState({
    market: "XSHG",
    securityId: "600030",
    side: "B" as "B" | "S",
    qty: 100,
    price: 20.5,
    shareholderId: "A123456789",
  });



  /* ============================================================
     2. 核心业务逻辑区域
  ============================================================ */

  /**
   * submitOrder
   * ------------------------------------------------------------
   * 下单逻辑流程：
   *
   * 1. 构造新订单对象
   * 2. 状态设为“待报”
   * 3. 写入订单列表
   * 4. 模拟后端异步回报
   *
   * 注意：
   * 在真实系统中此处应发送 HTTP 请求到 OMS
   */
  const submitOrder = () => {
    const newOrder: Order = {
      ...form,
      clOrderId: generateId(),
      status: "待报",
      cumQty: 0,
      execPrice: 0,
    };

    // 将订单插入数组顶部（最新订单优先展示）
    setOrders((prev) => [newOrder, ...prev]);

    // 模拟交易所回报
    simulateBackendResponse(newOrder.clOrderId);
  };



  /**
   * handleCancel
   * ------------------------------------------------------------
   * 撤单逻辑：
   *
   * 状态流转：
   * 已报 / 待报 → 撤单中 → 已撤单
   *
   * 注意：
   * 若订单已成交，则不允许撤单
   */
  const handleCancel = (origOrder: Order) => {

    // 1 先进入“撤单中”状态
    setOrders((prev) =>
      prev.map((o) =>
        o.clOrderId === origOrder.clOrderId
          ? { ...o, status: "撤单中" }
          : o
      )
    );

    // 2 模拟撤单确认回报
    setTimeout(() => {
      setOrders((prev) =>
        prev.map((o) =>
          o.clOrderId === origOrder.clOrderId
            ? {
                ...o,
                status: "已撤单",
                canceledQty: o.qty - o.cumQty,
              }
            : o
        )
      );
    }, 1000);
  };



  /**
   * simulateBackendResponse
   * ------------------------------------------------------------
   * 模拟交易所异步回报流程
   *
   * 订单生命周期：
   *
   * 待报 → 已报 → 已成
   *
   * 延时说明：
   * - 1秒后：已报
   * - 再2秒后：成交
   *
   * 若期间发生撤单，则不会进入成交状态
   */
  const simulateBackendResponse = (id: string) => {

    // 第一步：订单被交易所接受（ACK）
    setTimeout(() => {
      setOrders((prev) =>
        prev.map((o) =>
          o.clOrderId === id ? { ...o, status: "已报" } : o
        )
      );

      // 第二步：模拟撮合成交
      setTimeout(() => {
        setOrders((prev) =>
          prev.map((o) => {
            if (o.clOrderId === id && o.status !== "已撤单") {
              return {
                ...o,
                status: "已成",
                cumQty: o.qty,
                execPrice: o.price,
              };
            }
            return o;
          })
        );
      }, 2000);
    }, 1000);
  };



  /* ============================================================
     四、UI 渲染区域
  ============================================================ */

  return (
    <div className="p-6 bg-gray-50 min-h-screen">

      {/* ===========================
          页面标题区域
      ============================ */}
      <h1 className="text-2xl font-bold mb-6 text-blue-800">
        交易员终端 - 对敲撮合系统
      </h1>


      {/* ===========================
          下单面板区域
      ============================ */}
      <div className="bg-white p-4 rounded-lg shadow mb-6 grid grid-cols-4 gap-4">

        {/* 股票代码输入 */}
        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">股票代码</label>
          <input
            className="border p-2 rounded"
            value={form.securityId}
            onChange={(e) =>
              setForm({ ...form, securityId: e.target.value })
            }
          />
        </div>

        {/* 买卖方向选择 */}
        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">买卖方向</label>
          <select
            className="border p-2 rounded"
            value={form.side}
            onChange={(e) =>
              setForm({ ...form, side: e.target.value as "B" | "S" })
            }
          >
            <option value="B">买入 (Buy)</option>
            <option value="S">卖出 (Sell)</option>
          </select>
        </div>

        {/* 委托价格输入 */}
        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">委托价格</label>
          <input
            className="border p-2 rounded"
            type="number"
            value={form.price}
            onChange={(e) =>
              setForm({ ...form, price: Number(e.target.value) })
            }
          />
        </div>

        {/* 委托数量输入 */}
        <div className="flex flex-col">
          <label className="text-sm text-gray-600 mb-1">委托数量</label>
          <input
            className="border p-2 rounded"
            type="number"
            value={form.qty}
            onChange={(e) =>
              setForm({ ...form, qty: Number(e.target.value) })
            }
          />
        </div>

        {/* 提交按钮 */}
        <button
          className="col-span-4 mt-2 bg-blue-600 text-white py-2 rounded font-bold hover:bg-blue-700"
          onClick={submitOrder}
        >
          立即下单
        </button>
      </div>



      {/* ===========================
          订单列表区域
      ============================ */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-left">

          {/* 表头 */}
          <thead className="bg-gray-100 border-b">
            <tr>
              <th className="p-3">订单号</th>
              <th className="p-3">代码</th>
              <th className="p-3">方向</th>
              <th className="p-3">价格</th>
              <th className="p-3">数量</th>
              <th className="p-3">状态</th>
              <th className="p-3">操作</th>
            </tr>
          </thead>

          {/* 表体 */}
          <tbody>
            {orders.map((o) => (
              <tr key={o.clOrderId} className="border-b hover:bg-gray-50">

                {/* 订单号 */}
                <td className="p-3 text-xs font-mono">
                  {o.clOrderId}
                </td>

                {/* 股票代码 */}
                <td className="p-3">{o.securityId}</td>

                {/* 买卖方向（红买绿卖） */}
                <td
                  className={`p-3 font-bold ${
                    o.side === "B"
                      ? "text-red-500"
                      : "text-green-500"
                  }`}
                >
                  {o.side === "B" ? "买入" : "卖出"}
                </td>

                {/* 委托价格 */}
                <td className="p-3">{o.price}</td>

                {/* 委托数量 */}
                <td className="p-3">{o.qty}</td>

                {/* 状态展示 */}
                <td className="p-3">
                  <span
                    className={`px-2 py-1 rounded text-xs ${
                      o.status === "已成"
                        ? "bg-green-100 text-green-700"
                        : o.status === "已撤单"
                        ? "bg-gray-200 text-gray-700"
                        : "bg-yellow-100 text-yellow-700"
                    }`}
                  >
                    {o.status}
                  </span>
                </td>

                {/* 操作栏 */}
                <td className="p-3">
                  {(o.status === "已报" ||
                    o.status === "待报") && (
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
