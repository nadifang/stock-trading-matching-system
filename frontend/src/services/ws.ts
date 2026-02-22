import SockJS from "sockjs-client";
import { Client, type IMessage } from "@stomp/stompjs";
import type { TradeReturnVO } from "../types/server";

let client: Client | null = null;

export type TradeReturnHandler = (ret: TradeReturnVO) => void;
export type ConnectionStatusHandler = (connected: boolean) => void;

interface ConnectOptions {
  userId: string;
  handleTradeReturn: TradeReturnHandler;
  onStatusChange?: ConnectionStatusHandler;
}

export function connectTradeReturnWS(options: ConnectOptions) {
  const { userId, handleTradeReturn, onStatusChange } = options;
  
  if (client && client.active) {
    onStatusChange?.(true);
    return;
  }

  client = new Client({
    webSocketFactory: () => new SockJS("/ws"),
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      console.log("WebSocket connected");
      onStatusChange?.(true);
      client?.subscribe(`/user/${userId}/queue/returns`, (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body) as TradeReturnVO;
          handleTradeReturn(payload);
        } catch (error) {
          console.error("Error parsing WebSocket message:", error);
          // ignore malformed message
        }
      });
    },
    onDisconnect: () => {
      console.log("WebSocket disconnected");
      onStatusChange?.(false);
    },
    onStompError: (frame) => {
      console.error("WebSocket error:", frame);
      onStatusChange?.(false);
    },
    onWebSocketError: (error) => {
      console.error("WebSocket connection error:", error);
      onStatusChange?.(false);
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

