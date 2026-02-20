import SockJS from "sockjs-client";
import { Client, type IMessage } from "@stomp/stompjs";
import type { TradeReturnVO } from "../types/server";

let client: Client | null = null;

export type TradeReturnHandler = (ret: TradeReturnVO) => void;

export function connectTradeReturnWS(userId: string, onReturn: TradeReturnHandler) {
  if (client && client.active) return;

  client = new Client({
    webSocketFactory: () => new SockJS("/ws"),
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client?.subscribe(`/user/${userId}/queue/returns`, (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body) as TradeReturnVO;
          onReturn(payload);
        } catch {
          // ignore malformed message
        }
      });
    },
  });
  client.activate();
}

export function disconnectTradeReturnWS() {
  if (client) {
    client.deactivate();
    client = null;
  }
}

