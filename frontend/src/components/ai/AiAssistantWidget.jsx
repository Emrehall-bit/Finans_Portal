import { useState } from "react";
import { MessageSquare, Minimize2, Send, Sparkles } from "lucide-react";

const SUGGESTIONS = [
  "THYAO için teknik özet",
  "BIST 100 genel görünümü",
  "Portföyümün riski nedir?",
];

export default function AiAssistantWidget() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([
    {
      role: "ai",
      text: "Merhaba! Finans Portalı AI asistanına hoş geldiniz. Hisse, piyasa veya portföyünüz hakkında soru sorabilirsiniz.",
    },
  ]);

  function sendMessage(text) {
    const nextText = text.trim();
    if (!nextText) return;

    setMessages((current) => [
      ...current,
      { role: "user", text: nextText },
      {
        role: "ai",
        text: "Bu bir mock yanıttır. Gerçek AI servisi bağlandığında mevcut piyasa, hisse ve portföy verilerine göre kısa analiz burada üretilecek.",
      },
    ]);
    setInput("");
  }

  if (!open) {
    return (
      <button type="button" className="ai-assistant-fab" onClick={() => setOpen(true)} aria-label="AI Asistanı aç">
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
        <button type="button" onClick={() => setOpen(false)} aria-label="AI Asistanı küçült">
          <Minimize2 size={16} aria-hidden="true" />
        </button>
      </header>

      <div className="ai-assistant-messages">
        {messages.map((message, index) => (
          <div key={`${message.role}-${index}`} className={`ai-assistant-message ${message.role}`}>
            {message.text}
          </div>
        ))}

        {messages.length <= 1 ? (
          <div className="ai-assistant-suggestions">
            <span>Öneriler</span>
            {SUGGESTIONS.map((suggestion) => (
              <button key={suggestion} type="button" onClick={() => sendMessage(suggestion)}>
                {suggestion}
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <form
        className="ai-assistant-form"
        onSubmit={(event) => {
          event.preventDefault();
          sendMessage(input);
        }}
      >
        <div>
          <input value={input} onChange={(event) => setInput(event.target.value)} placeholder="Bir şey sorun..." />
          <button type="submit" aria-label="Gönder">
            <Send size={16} aria-hidden="true" />
          </button>
        </div>
        <small>Yatırım tavsiyesi değildir; yalnızca veri analizidir.</small>
      </form>
    </aside>
  );
}
