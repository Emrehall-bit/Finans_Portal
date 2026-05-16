import { useRef, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { MessageSquare, Minimize2, Send, Sparkles, Loader2 } from "lucide-react";
import { postAiChat } from "../../api/aiApi";

const SUGGESTIONS = [
  "THYAO için teknik özet",
  "BIST 100 genel görünümü",
  "Portföyümün riski nedir?",
];

const INITIAL_MESSAGE = {
  role: "ai",
  text: "Merhaba! Finans Portalı AI asistanına hoş geldiniz. Hisse, piyasa veya portföyünüz hakkında soru sorabilirsiniz.",
};

export default function AiAssistantWidget({ aiContext = null }) {
  const { pathname } = useLocation();
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
    if (!nextText || loading) return;

    setMessages((prev) => [...prev, { role: "user", text: nextText }]);
    setInput("");
    setLoading(true);

    try {
      const data = await postAiChat(nextText, aiContext);
      setMessages((prev) => [...prev, { role: "ai", text: data.reply }]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: "ai",
          text: "Şu an yanıt üretilemiyor, lütfen daha sonra tekrar deneyin.",
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
        aria-label="AI Asistanı aç"
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
          aria-label="AI Asistanı küçült"
        >
          <Minimize2 size={16} aria-hidden="true" />
        </button>
      </header>

      <div className="ai-assistant-messages">
        {messages.map((message, index) => (
          <div
            key={`${message.role}-${index}`}
            className={`ai-assistant-message ${message.role}${message.error ? " error" : ""}`}
          >
            {message.text}
          </div>
        ))}

        {loading && (
          <div className="ai-assistant-message ai typing">
            <Loader2 size={14} className="ai-assistant-spinner" aria-label="Yanıt bekleniyor" />
          </div>
        )}

        {messages.length <= 1 && !loading ? (
          <div className="ai-assistant-suggestions">
            <span>Öneriler</span>
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
            placeholder="Bir şey sorun..."
            disabled={loading}
            aria-label="Mesaj"
          />
          <button type="submit" aria-label="Gönder" disabled={loading || !input.trim()}>
            {loading ? (
              <Loader2 size={16} className="ai-assistant-spinner" aria-hidden="true" />
            ) : (
              <Send size={16} aria-hidden="true" />
            )}
          </button>
        </div>
        <small>Yatırım tavsiyesi değildir; yalnızca veri analizidir.</small>
      </form>
    </aside>
  );
}
