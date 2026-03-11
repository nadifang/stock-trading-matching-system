import { useCallback, useState } from "react";
import { exportDataApi } from "../services/analysis";
import type {
  AnalysisTimeRange,
  Top10CumQtyVO,
  Top10SecurityIdVO,
  Top10ShareholderIdVO,
} from "../types/analysis";

export interface AnalysisDataState {
  qty?: Top10CumQtyVO;
  securityAmount?: Top10SecurityIdVO;
  shareholderAmount?: Top10ShareholderIdVO;
}

export interface AnalysisLoadResult {
  ok: boolean;
  error?: string;
}

export const useAnalysisData = () => {
  const [data, setData] = useState<AnalysisDataState>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (range?: AnalysisTimeRange): Promise<AnalysisLoadResult> => {
    setLoading(true);
    setError(null);
    try {
      const [qty, securityAmount, shareholderAmount] = await Promise.all([
        exportDataApi.getTopQty(range),
        exportDataApi.getTopSecurityAmount(range),
        exportDataApi.getTopShareholderAmount(range),
      ]);
      setData({ qty, securityAmount, shareholderAmount });
      return { ok: true };
    } catch (err) {
      const msg = err instanceof Error ? err.message : "未知错误";
      setError(msg);
      return { ok: false, error: msg };
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
};
