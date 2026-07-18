import { useEffect, useRef, useState, useCallback } from 'react';
import { Camera as CameraIcon, Radio, Wifi, WifiOff, Battery, Maximize } from 'lucide-react';

interface WebRTCViewerProps {
  cameraId: string;
  cameraName: string;
  serverUrl?: string;
}

export default function WebRTCViewer({ cameraId, cameraName }: WebRTCViewerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);

  const ICE_SERVERS: RTCConfiguration = {
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' },
    ],
  };

  const connect = useCallback(() => {
    const token = localStorage.getItem('token');
    if (!token) return;

    setConnecting(true);
    setError(null);

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/webrtc/client/${cameraId}?token=${token}`;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('[WebRTC] Signaling connected');
    };

    ws.onmessage = async (event) => {
      const msg = JSON.parse(event.data);

      switch (msg.type) {
        case 'room_info':
          if (!msg.data?.camera_online) {
            setError('Camera is offline');
            setConnecting(false);
          }
          break;

        case 'offer':
          await handleOffer(msg);
          break;

        case 'answer':
          await handleAnswer(msg);
          break;

        case 'ice_candidate':
          await handleIceCandidate(msg);
          break;

        case 'camera_offline':
          setConnected(false);
          setError('Camera went offline');
          break;
      }
    };

    ws.onerror = () => {
      setError('Signaling connection failed');
      setConnecting(false);
    };

    ws.onclose = () => {
      setConnected(false);
      setConnecting(false);
    };
  }, [cameraId]);

  const createPeerConnection = useCallback(() => {
    const pc = new RTCPeerConnection(ICE_SERVERS);
    pcRef.current = pc;

    pc.ontrack = (event) => {
      if (videoRef.current) {
        videoRef.current.srcObject = event.streams[0];
        videoRef.current.play().catch(() => {});
        setConnected(true);
        setConnecting(false);
      }
    };

    pc.onicecandidate = (event) => {
      if (event.candidate && wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'ice_candidate',
          candidate: {
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex,
          },
          target_peer_id: '',
        }));
      }
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
        setConnected(false);
        setError('Connection lost');
      }
    };

    return pc;
  }, []);

  const handleOffer = async (msg: any) => {
    try {
      const pc = createPeerConnection();
      await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: msg.sdp }));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'answer',
          sdp: answer.sdp,
          target_peer_id: msg.peer_id,
        }));
      }
    } catch (e) {
      console.error('[WebRTC] Offer handling error:', e);
      setError('Failed to handle offer');
    }
  };

  const handleAnswer = async (msg: any) => {
    try {
      if (pcRef.current) {
        await pcRef.current.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: msg.sdp }));
      }
    } catch (e) {
      console.error('[WebRTC] Answer handling error:', e);
    }
  };

  const handleIceCandidate = async (msg: any) => {
    try {
      if (pcRef.current && msg.candidate) {
        await pcRef.current.addIceCandidate(new RTCIceCandidate({
          candidate: msg.candidate.candidate,
          sdpMid: msg.candidate.sdpMid,
          sdpMLineIndex: msg.candidate.sdpMLineIndex,
        }));
      }
    } catch (e) {
      console.error('[WebRTC] ICE candidate error:', e);
    }
  };

  const disconnect = useCallback(() => {
    pcRef.current?.close();
    pcRef.current = null;
    wsRef.current?.close();
    wsRef.current = null;
    setConnected(false);
    setConnecting(false);
  }, []);

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  const toggleFullscreen = () => {
    const container = videoRef.current?.parentElement;
    if (container) {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else {
        container.requestFullscreen();
      }
    }
  };

  return (
    <div className="relative w-full aspect-video bg-dark-900 rounded-xl overflow-hidden group">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="w-full h-full object-cover"
      />

      {/* Connection status overlay */}
      {!connected && (
        <div className="absolute inset-0 flex items-center justify-center bg-dark-900/90">
          <div className="text-center">
            {connecting ? (
              <>
                <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                <p className="text-dark-300">Connecting to WebRTC...</p>
              </>
            ) : error ? (
              <>
                <WifiOff className="w-10 h-10 text-red-400 mx-auto mb-2" />
                <p className="text-red-400 text-sm">{error}</p>
                <button onClick={connect} className="btn-primary mt-3 text-sm py-1.5 px-3">
                  Reconnect
                </button>
              </>
            ) : (
              <>
                <CameraIcon className="w-12 h-12 text-dark-600 mx-auto mb-2" />
                <p className="text-dark-500">Click to connect</p>
                <button onClick={connect} className="btn-primary mt-3 text-sm py-1.5 px-3">
                  Connect
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {/* Status bar */}
      <div className="absolute top-3 left-3 right-3 flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity">
        <span className={`badge ${connected ? 'badge-green' : 'badge-red'}`}>
          {connected ? '● WebRTC Live' : '● Offline'}
        </span>
        <button onClick={toggleFullscreen} className="p-1.5 rounded-lg bg-dark-900/80 text-dark-300 hover:text-white">
          <Maximize className="w-4 h-4" />
        </button>
      </div>

      {/* Camera name */}
      <div className="absolute bottom-3 left-3 opacity-0 group-hover:opacity-100 transition-opacity">
        <p className="text-sm font-medium text-white bg-dark-900/80 px-2 py-1 rounded">{cameraName}</p>
      </div>
    </div>
  );
}
