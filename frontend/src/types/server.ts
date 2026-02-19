export interface Result<T = unknown> {
  success: boolean;
  data?: T;
  // 后端返回的 errorMsg 可能是一个对象 { rejectCode: number, rejectText: string }
  errorMsg?: string | { rejectCode: number; rejectText: string };
}

export interface TradeReturnVO {
  clOrderId: string;
  market: string;
  securityId: string;
  side: "B" | "S" | "BUY" | "SELL";
  qty: number;
  price: number;
  shareholderId: string;
  execId?: string;
  execQty?: number;
  execPrice?: number;
  origClOrderId?: string;
  rejectCode?: number;
  rejectText?: string;
}

export interface SubmitOrderDTO {
  clOrderId: string;
  market: string;
  securityId: string;
  side: "B" | "S";
  qty: number;
  price: number;
  shareholderId: string;
}

