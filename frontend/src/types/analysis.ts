export interface Top10CumQtyVO {
  securityId: string;
  cumQty: string;
}

export interface Top10SecurityIdVO {
  securityId: string;
  amount: string;
}

export interface Top10ShareholderIdVO {
  shareholderId: string;
  amount: string;
}

export interface AnalysisTimeRange {
  startTime?: string;
  endTime?: string;
}
