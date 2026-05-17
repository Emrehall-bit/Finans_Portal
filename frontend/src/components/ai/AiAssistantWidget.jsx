import { useRef, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { MessageSquare, Minimize2, Send, Sparkles, Loader2, Lock } from "lucide-react";
import { postAiChat } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiResponseMeta from "./AiResponseMeta";

const SUGGESTIONS = [
  "THYAO icin teknik ozet",
  "BIST 100 genel gorunumu",
  "Portfoyumun riski nedir?",
];

const INITIAL_MESSAGE = {
  role: "ai",
  text: "Merhaba! Finans Portali AI asistanina hos geldiniz. Hisse, piyasa veya portfoyunuz hakkinda soru sorabilirsiniz.",
};

export default function AiAssistantWidget({ aiContext = null }) {
  const { pathname } = useLocation();
  const { isAuthenticated, login } = useAuth();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([INITIAL_MESSAGE]);
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    if (open) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, open]);

  async function sendMessage(text) {
    const nextText = text.trim();
    if (!isAuthenticated) {
      login();
      return;
    }
    if (!nextText || loading) return;

    setMessages((prev) => [...prev, { role: "user", text: nextText }]);
    setInput("");
    setLoading(true);

    try {
      const data = await postAiChat(nextText, aiContext);
      setMessages((prev) => [...prev, { role: "ai", text: data.reply, metadata: data.metadata }]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: "ai",
          text: "Su an yanit uretilemiyor, lutfen daha sonra tekrar deneyin.",
          error: true,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  if (pathname === "/economy") return null;

  if (!open) {
    return (
      <button
        type="button"
        className="ai-assistant-fab"
        onClick={() => setOpen(true)}
        aria-label="AI Asistani ac"
      >
        <Sparkles size={23} aria-hidden="true" />
      </button>
    );
  }

  return (
    <aside className="ai-assistant-panel" aria-label="AI Asistan">
      <header className="ai-assistant-head">
        <div className="ai-assistant-title">
          <span aria-hidden="true">
            <MessageSquare size={17} />
          </span>
          <div>
            <strong>AI Asistan</strong>
            <small>Finansal verileri yorumlar</small>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setOpen(false)}
          aria-label="AI Asistani kucult"
        >
          <Minimize2 size={16} aria-hidden="true" />
        </button>
      </header>

      <div className="ai-assistant-messages">
        {!isAuthenticated ? (
          <div className="ai-assistant-locked-state">
            <span className="ai-assistant-locked-icon" aria-hidden="true">
              <Lock size={16} />
            </span>
            <strong>Bu ozelligi kullanmak icin giris yapmalisiniz.</strong>
            <button type="button" className="ai-premium-gate-button" onClick={() => login()}>
              Giris Yap
            </button>
          </div>
        ) : null}

        {isAuthenticated ? messages.map((message, index) => (
          <div
            key={`${message.role}-${index}`}
            className={`ai-assistant-message ${message.role}${message.error ? " error" : ""}`}
          >
            <div>{message.text}</div>
            {message.role === "ai" && message.metadata ? (
              <AiResponseMeta metadata={message.metadata} />
            ) : null}
          </div>
        )) : null}

        {isAuthenticated && loading ? (
          <div className="ai-assistant-message ai typing">
            <Loader2 size={14} className="ai-assistant-spinner" aria-label="Yanit bekleniyor" />
          </div>
        ) : null}

        {isAuthenticated && messages.length <= 1 && !loading ? (
          <div className="ai-assistant-suggestions">
            <span>Oneriler</span>
            {SUGGESTIONS.map((suggestion) => (
              <button
                key={suggestion}
                type="button"
                onClick={() => sendMessage(suggestion)}
              >
                {suggestion}
              </button>
            ))}
          </div>
        ) : null}

        <div ref={bottomRef} />
      </div>

      <form
        className="ai-assistant-form"
        onSubmit={(event) => {
          event.preventDefault();
          sendMessage(input);
        }}
      >
        <div>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={isAuthenticated ? "Bir sey sorun..." : "Giris yaparak AI sohbeti baslatin"}
            disabled={loading || !isAuthenticated}
            aria-label="Mesaj"
          />
          <button type="submit" aria-label="Gonder" disabled={loading || !input.trim() || !isAuthenticated}>
            {loading ? (
              <Loader2 size={16} className="ai-assistant-spinner" aria-hidden="true" />
            ) : (
              <Send size={16} aria-hidden="true" />
            )}
          </button>
        </div>
        <small>Yatirim tavsiyesi degildir; yalnizca veri analizidir.</small>
      </form>
    </aside>
  );
}
