import { useEffect, useRef, useCallback, useState } from 'react';

interface WebSocketMessage {
  type: string;
  [key: string]: any;
}

export function useWebSocket(userId: string | null) {
  const wsRef = useRef<WebSocket | null>(null);
  const [connected, setConnected] = useState(false);
  const listenersRef = useRef<Map<string, Set<(data: any) => void>>>(new Map());
  const pingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!userId) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/client/${userId}?token=${token}`);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      pingRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      try {
        const msg: WebSocketMessage = JSON.parse(event.data);
        const handlers = listenersRef.current.get(msg.type);
        if (handlers) handlers.forEach((h) => h(msg));
      } catch {
        // Ignore malformed messages
      }
    };

    ws.onclose = (event) => {
      setConnected(false);
      if (pingRef.current) {
        clearInterval(pingRef.current);
        pingRef.current = null;
      }
      // Attempt reconnect after 5 seconds if not intentional close
      if (event.code !== 1000 && event.code !== 4001) {
        setTimeout(() => {
          // Component may have unmounted
          if (wsRef.current === ws) {
            // Trigger re-render by updating state
            setConnected(false);
          }
        }, 5000);
      }
    };

    ws.onerror = () => setConnected(false);

    return () => {
      if (pingRef.current) {
        clearInterval(pingRef.current);
        pingRef.current = null;
      }
      ws.close(1000);
      wsRef.current = null;
    };
  }, [userId]);

  const subscribe = useCallback((type: string, handler: (data: any) => void) => {
    if (!listenersRef.current.has(type)) {
      listenersRef.current.set(type, new Set());
    }
    listenersRef.current.get(type)!.add(handler);
    return () => {
      listenersRef.current.get(type)?.delete(handler);
    };
  }, []);

  const send = useCallback((data: any) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data));
    }
  }, []);

  return { connected, subscribe, send };
}
