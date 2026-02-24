package com.stms.match.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.trade_return.domain.VO.TradeReturnVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
@Component
public class MatchResultSocketClient {

    @Value("${trade-return.socket.ip:127.0.0.1}")
    private String tradeReturnIp;

    @Value("${trade-return.socket.port:8888}")
    private int tradeReturnPort;

    @Autowired
    private ObjectMapper objectMapper;

    public boolean sendMatchResult(TradeReturnVO vo) {
        Socket socket = null;
        try {
            socket = new Socket(tradeReturnIp, tradeReturnPort);
            log.info("Socket连接成功 | IP:{} 端口:{} 订单ID:{}",
                    tradeReturnIp, tradeReturnPort, new String(vo.getClOrderId()));

            String json = objectMapper.writeValueAsString(vo) + "\n";
            OutputStream os = socket.getOutputStream();
            os.write(json.getBytes());
            os.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String resp = br.readLine();
            boolean success = resp != null && resp.startsWith("SUCCESS");

            if (success) {
                log.info("Socket发送成功 | 订单ID:{}", new String(vo.getClOrderId()));
            } else {
                log.warn("Socket发送失败 | 订单ID:{} 响应:{}", new String(vo.getClOrderId()), resp);
            }
            return success;

        } catch (IOException e) {
            log.error("Socket发送异常 | 订单ID:{} IP:{} 端口:{}",
                    new String(vo.getClOrderId()), tradeReturnIp, tradeReturnPort, e);
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log.error("关闭Socket连接失败", e);
                }
            }
        }
    }
}