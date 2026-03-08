import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

interface MarketDataProps {
  lang: "en" | "zh";
  isAdmin?: boolean;
}

interface StockData {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  market: string;
  bidPrice: number;
  askPrice: number;
}

export default function MarketData({ lang, isAdmin = false }: MarketDataProps) {
  const [stockData, setStockData] = useState<StockData[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // 模拟多种市场的行情数据
  useEffect(() => {
    // 模拟API调用延迟
    const timer = setTimeout(() => {
      setStockData([
        {
          symbol: "600030",
          name: "中信证券",
          price: 20.5,
          change: 0.3,
          changePercent: 1.49,
          volume: 12345678,
          market: "XSHG",
          bidPrice: 20.49,
          askPrice: 20.51
        },
        {
          symbol: "600519",
          name: "贵州茅台",
          price: 1789.5,
          change: -12.3,
          changePercent: -0.68,
          volume: 9876543,
          market: "XSHG",
          bidPrice: 1789.4,
          askPrice: 1789.6
        },
        {
          symbol: "000858",
          name: "五粮液",
          price: 168.7,
          change: 2.1,
          changePercent: 1.26,
          volume: 5678901,
          market: "XSHE",
          bidPrice: 168.65,
          askPrice: 168.75
        },
        {
          symbol: "601318",
          name: "中国平安",
          price: 48.2,
          change: -0.5,
          changePercent: -1.03,
          volume: 8765432,
          market: "XSHG",
          bidPrice: 48.15,
          askPrice: 48.25
        },
        {
          symbol: "000001",
          name: "平安银行",
          price: 12.8,
          change: 0.2,
          changePercent: 1.59,
          volume: 6543210,
          market: "XSHE",
          bidPrice: 12.79,
          askPrice: 12.81
        },
        {
          symbol: "601888",
          name: "中国中免",
          price: 189.5,
          change: -3.2,
          changePercent: -1.66,
          volume: 3210987,
          market: "XSHG",
          bidPrice: 189.4,
          askPrice: 189.6
        },
        {
          symbol: "600036",
          name: "招商银行",
          price: 35.6,
          change: 0.8,
          changePercent: 2.30,
          volume: 5432109,
          market: "XSHG",
          bidPrice: 35.58,
          askPrice: 35.62
        },
        {
          symbol: "002594",
          name: "比亚迪",
          price: 245.8,
          change: 5.2,
          changePercent: 2.16,
          volume: 4321098,
          market: "XSHE",
          bidPrice: 245.7,
          askPrice: 245.9
        }
      ]);
      setLoading(false);
    }, 1000);

    return () => clearTimeout(timer);
  }, []);

  const text = lang === "en" ? {
    marketData: "Market Data",
    symbol: "Symbol",
    name: "Name",
    price: "Price",
    change: "Change",
    changePercent: "% Change",
    volume: "Volume",
    loading: "Loading market data...",
    market: "Market",
    marketAccess: "Market Access",
    adminOnly: "Admin Only"
  } : {
    marketData: "行情数据",
    symbol: "代码",
    name: "名称",
    price: "价格",
    change: "涨跌",
    changePercent: "涨跌幅",
    volume: "成交量",
    loading: "加载行情数据中...",
    market: "市场",
    marketAccess: "行情接入",
    adminOnly: "仅管理员"
  };

  const handleMarketAccess = () => {
    // 跳转到行情设置页面
    console.log("Navigating to market access page");
    // 这里可以添加路由跳转逻辑
  };

  const handleStockClick = (stock: StockData) => {
    // 跳转到订单详情页面
    navigate(`/stock/${stock.symbol}`);
  };

  if (loading) {
    return (
      <div className="w-full max-w-[280px] h-full justify-self-start rounded-2xl bg-white border border-slate-300 flex items-center justify-center">
        <p className="text-gray-600">{text.loading}</p>
      </div>
    );
  }

  return (
    <div className="w-full h-full flex flex-col">
      <h2 className="text-lg font-bold mb-4 text-gray-800 flex items-center gap-2">
        <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></span>
        {text.marketData}
      </h2>
      
      {/* 滚动行情数据 */}
      <div className="flex-1 overflow-y-auto pr-2 space-y-3 mb-4">
        {stockData.map((stock) => (
          <button 
            key={stock.symbol} 
            className="w-full bg-white p-3 rounded-lg shadow-sm transition-all duration-300 hover:shadow-md hover:translate-y-[-2px] border border-slate-100 text-left cursor-pointer"
            onClick={() => handleStockClick(stock)}
          >
            <div className="flex justify-between items-start">
              <div>
                <div className="font-semibold flex items-center gap-2">
                  <span className="text-gray-900">{stock.symbol}</span>
                  <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-800 rounded-full font-medium">
                    {stock.market}
                  </span>
                </div>
                <div className="text-sm text-gray-600 mt-1">{stock.name}</div>
              </div>
              <div className="text-right">
                <div className="font-semibold text-gray-900 text-lg">{stock.price.toFixed(2)}</div>
                <div className={`text-sm font-medium ${stock.change >= 0 ? 'text-red-500' : 'text-green-500'}`}>
                  {stock.change >= 0 ? '↑' : '↓'} {Math.abs(stock.change).toFixed(2)} ({Math.abs(stock.changePercent).toFixed(2)}%)
                </div>
              </div>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
              <div className="bg-slate-50 p-2 rounded col-span-1 transition-all duration-200 hover:bg-slate-100 flex flex-col items-center justify-center">
                <div className="text-gray-500 font-medium text-xs">{text.volume}</div>
                <div className="text-gray-800 font-semibold text-xs mt-1">{stock.volume.toLocaleString()}</div>
              </div>
              <div className="bg-red-50 p-2 rounded col-span-1 transition-all duration-200 hover:bg-red-100 flex flex-col items-center justify-center">
                <div className="text-red-600 font-medium text-xs">买价</div>
                <div className="text-red-800 font-semibold text-xs mt-1">{stock.bidPrice.toFixed(2)}</div>
              </div>
              <div className="bg-green-50 p-2 rounded col-span-1 transition-all duration-200 hover:bg-green-100 flex flex-col items-center justify-center">
                <div className="text-green-600 font-medium text-xs">卖价</div>
                <div className="text-green-800 font-semibold text-xs mt-1">{stock.askPrice.toFixed(2)}</div>
              </div>
            </div>
          </button>
        ))}
      </div>
      
      {/* 管理员专用行情接入按钮 */}
      <div className="mt-auto pt-3 border-t border-slate-200">
        <button 
          onClick={handleMarketAccess}
          className="w-full bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white py-2 rounded-lg font-medium transition-all duration-300 flex items-center justify-center gap-2 shadow-md hover:shadow-lg transform hover:-translate-y-[-1px]"
        >
          <span>{text.marketAccess}</span>
          {isAdmin && (
            <span className="text-xs bg-white text-blue-600 px-2 py-0.5 rounded-full font-medium">
              {text.adminOnly}
            </span>
          )}
        </button>
      </div>
    </div>
  );
}
